package com.bookie.datalifecycle.restore;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifest;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ShadowRestoreService {

  private final MigrationIntegrityVerifier integrityVerifier;
  private final MigrationIntegrityManifestCodec manifestCodec;
  private final RestorePaths paths;
  private final RestoreJournalStore journalStore;
  private final RestoreDiskSpaceChecker diskSpaceChecker;
  private final long maxRestoreBytes;
  private final long minimumFreeHeadroomBytes;
  private final String applicationVersion;

  public ShadowRestoreService(
      MigrationIntegrityVerifier integrityVerifier,
      RestorePaths paths,
      RestoreJournalStore journalStore,
      RestoreDiskSpaceChecker diskSpaceChecker,
      @Value("${bookie.restore.max-bytes:524288000}") long maxRestoreBytes,
      @Value("${bookie.restore.minimum-free-headroom-bytes:67108864}")
          long minimumFreeHeadroomBytes,
      @Value("${bookie.application-version:dev}") String applicationVersion) {
    this.integrityVerifier = integrityVerifier;
    this.manifestCodec = new MigrationIntegrityManifestCodec();
    this.paths = paths;
    this.journalStore = journalStore;
    this.diskSpaceChecker = diskSpaceChecker;
    this.maxRestoreBytes = maxRestoreBytes;
    this.minimumFreeHeadroomBytes = minimumFreeHeadroomBytes;
    this.applicationVersion = applicationVersion;
  }

  public long maxRestoreBytes() {
    return maxRestoreBytes;
  }

  public Optional<RestoreJournal> currentJournal() throws IOException {
    return journalStore.read();
  }

  public synchronized RestoreJournal stage(
      String sourceFileId, String sourceName, long declaredSize, InputStream source)
      throws IOException {
    ensureNoPendingRestore();
    Path dataDirectory = prepareDataDirectory();
    Path liveDatabase = paths.liveDatabaseFile();
    requireRegularFile(liveDatabase, "Live database");
    if (declaredSize > maxRestoreBytes) {
      throw new IOException(
          "Backup exceeds %d-byte safety cap: %d".formatted(maxRestoreBytes, declaredSize));
    }

    long liveBytes = Files.size(liveDatabase);
    long expectedBytes = Math.max(0, declaredSize);
    diskSpaceChecker.requireAvailable(dataDirectory, requiredFreeBytes(liveBytes, expectedBytes));

    String restoreId = UUID.randomUUID().toString();
    RestorePaths.RestoreAttemptPaths attempt = paths.attempt(restoreId);
    Files.createDirectories(attempt.stagingDirectory());
    ensureContained(attempt.stagingDirectory(), dataDirectory);

    DownloadedBackup backup = copyWithLimit(source, attempt.sourceScript());
    diskSpaceChecker.requireAvailable(dataDirectory, requiredFreeBytes(liveBytes, backup.bytes()));

    ValidatedShadow shadow = restoreShadowDatabase(attempt, backup.checksum());
    MigrationIntegrityManifest manifest = shadow.manifest();
    shutdown(shadow.dataSource());

    requireRegularFile(attempt.shadowDatabaseFile(), "Shadow database");
    manifestCodec.write(attempt.manifestFile(), manifest);
    String liveHash = MigrationIntegrityManifestCodec.sha256(liveDatabase);
    String shadowHash = MigrationIntegrityManifestCodec.sha256(attempt.shadowDatabaseFile());
    String now = Instant.now().toString();
    RestoreJournal journal =
        new RestoreJournal(
            RestoreJournal.CURRENT_VERSION,
            restoreId,
            RestoreState.VALIDATED,
            sourceFileId,
            sourceName,
            backup.bytes(),
            backup.checksum(),
            dataDirectory.toString(),
            liveDatabase.toString(),
            attempt.shadowDatabaseFile().toString(),
            attempt.rollbackFile().toString(),
            attempt.failedFile().toString(),
            attempt.manifestFile().toString(),
            attempt.auditJournalFile().toString(),
            liveHash,
            shadowHash,
            manifest.contentChecksum(),
            now,
            now,
            "Shadow database validated; restart is required for activation",
            "");
    return journalStore.write(journal);
  }

  private void ensureNoPendingRestore() throws IOException {
    Optional<RestoreJournal> current = journalStore.read();
    if (current.isPresent() && !current.get().state().isTerminal()) {
      throw new IOException(
          "Restore " + current.get().restoreId() + " is already in state " + current.get().state());
    }
  }

  private Path prepareDataDirectory() throws IOException {
    Files.createDirectories(paths.dataDirectory());
    Path realDirectory = paths.dataDirectory().toRealPath();
    if (!realDirectory.equals(paths.dataDirectory())) {
      throw new IOException(
          "Restore data directory must not resolve through a symbolic link: "
              + paths.dataDirectory());
    }
    return realDirectory;
  }

  private DownloadedBackup copyWithLimit(InputStream source, Path target) throws IOException {
    var digest = sha256Digest();
    long bytes = 0;
    try (InputStream input = source;
        var output =
            Files.newOutputStream(
                target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        bytes = Math.addExact(bytes, read);
        if (bytes > maxRestoreBytes) {
          throw new IOException(
              "Backup exceeds %d-byte safety cap while downloading".formatted(maxRestoreBytes));
        }
        digest.update(buffer, 0, read);
        output.write(buffer, 0, read);
      }
    }
    if (bytes == 0) {
      throw new IOException("Backup file is empty");
    }
    try (var channel = java.nio.channels.FileChannel.open(target, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
    return new DownloadedBackup(bytes, java.util.HexFormat.of().formatHex(digest.digest()));
  }

  private ValidatedShadow restoreShadowDatabase(
      RestorePaths.RestoreAttemptPaths attempt, String sourceChecksum) throws IOException {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(h2Url(attempt.shadowDatabaseBase()));
    dataSource.setUser("sa");
    dataSource.setPassword("");
    try (Connection connection = dataSource.getConnection();
        Reader reader = Files.newBufferedReader(attempt.sourceScript(), StandardCharsets.UTF_8)) {
      RunScript.execute(connection, reader);
    } catch (SQLException e) {
      throw new IOException(
          "Backup SQL could not be restored into the shadow database (checksum "
              + sourceChecksum
              + ")",
          e);
    }

    MigrationIntegrityManifest beforeMigration =
        integrityVerifier.capture(dataSource, applicationVersion, sourceChecksum);
    integrityVerifier.assertHealthy(beforeMigration);
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion(MigrationVersion.fromVersion("1"))
            .load();
    try {
      flyway.migrate();
      flyway.validate();
      rejectNewerSchema(flyway);
      MigrationIntegrityManifest afterMigration =
          integrityVerifier.capture(dataSource, applicationVersion, sourceChecksum);
      integrityVerifier.assertHealthy(afterMigration);
      integrityVerifier.reconcile(beforeMigration, afterMigration);
      return new ValidatedShadow(dataSource, afterMigration);
    } catch (RuntimeException e) {
      throw new IOException("Shadow database failed Flyway validation or migration", e);
    }
  }

  private void rejectNewerSchema(Flyway flyway) throws IOException {
    MigrationInfo current = flyway.info().current();
    MigrationVersion newestResolved = null;
    for (MigrationInfo migration : flyway.info().all()) {
      if (migration.getVersion() != null
          && !migration.getState().name().startsWith("FUTURE")
          && !migration.getState().name().startsWith("MISSING")
          && (newestResolved == null || migration.getVersion().compareTo(newestResolved) > 0)) {
        newestResolved = migration.getVersion();
      }
    }
    if (current != null
        && newestResolved != null
        && current.getVersion().compareTo(newestResolved) > 0) {
      throw new IOException(
          "Backup schema "
              + current.getVersion()
              + " is newer than supported schema "
              + newestResolved);
    }
  }

  private void shutdown(JdbcDataSource dataSource) throws IOException {
    try (Connection connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    } catch (SQLException e) {
      throw new IOException("Could not close the validated shadow database", e);
    }
  }

  private long requiredFreeBytes(long liveBytes, long backupBytes) throws IOException {
    try {
      return Math.addExact(
          minimumFreeHeadroomBytes,
          Math.addExact(Math.multiplyExact(liveBytes, 2), Math.multiplyExact(backupBytes, 3)));
    } catch (ArithmeticException e) {
      throw new IOException("Restore disk-space requirement overflowed", e);
    }
  }

  private static void requireRegularFile(Path path, String label) throws IOException {
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(label + " is not a regular file: " + path);
    }
  }

  private static void ensureContained(Path candidate, Path dataDirectory) throws IOException {
    Path realCandidate = candidate.toRealPath();
    if (!realCandidate.startsWith(dataDirectory)) {
      throw new IOException("Restore staging path escapes data directory: " + realCandidate);
    }
  }

  private static String h2Url(Path databaseBase) {
    return "jdbc:h2:file:" + databaseBase.toAbsolutePath().toString().replace("\\", "/");
  }

  private static java.security.MessageDigest sha256Digest() {
    try {
      return java.security.MessageDigest.getInstance("SHA-256");
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private record DownloadedBackup(long bytes, String checksum) {}

  private record ValidatedShadow(JdbcDataSource dataSource, MigrationIntegrityManifest manifest) {}
}
