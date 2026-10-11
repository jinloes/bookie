package com.bookie.datalifecycle.backup;

import com.bookie.datalifecycle.portable.BackupEncryption;
import com.bookie.datalifecycle.portable.PortableBackupArchive;
import com.bookie.datalifecycle.portable.PortableBackupExporter;
import com.bookie.datalifecycle.portable.PortableBackupImporter;
import com.bookie.datalifecycle.restore.RestoreJournal;
import com.bookie.datalifecycle.restore.RestoreState;
import com.bookie.datalifecycle.restore.ShadowRestoreService;
import com.bookie.integrations.onedrive.OneDriveItem;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.integrations.outlook.OutlookAuthorization;
import com.bookie.util.BackgroundWorkControl;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class BackupService {

  static final String BACKUP_FOLDER = "bookie/backups";
  static final String PRE_MIGRATION_FOLDER = "pre-migration";
  static final String ROLLBACK_FOLDER = "rollback";
  static final int RETAINED_DAILY_BACKUPS = 30;
  static final Duration STALE_AFTER = Duration.ofHours(24);
  static final String SQL_SERVER = "sqlserver";

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

  /**
   * File paths are spliced into {@code SCRIPT TO '...'} and {@code BACKUP TO '...'} as SQL
   * literals. H2 has no parameterised form for these commands, so we instead refuse to use any path
   * that isn't plainly safe.
   */
  private static final Pattern SAFE_PATH = Pattern.compile("^[A-Za-z0-9./_-]+$");

  private final Clock clock;
  private final DataSource dataSource;
  private final OneDrivePort oneDrive;
  private final OutlookAuthorization outlookAuthorization;
  private final ShadowRestoreService shadowRestoreService;
  private final PortableBackupExporter exporter;
  private final PortableBackupImporter importer;
  private final PortableBackupArchive archiveFormat;
  private final BackupEncryption encryption;
  private final WriteFreeze writeFreeze;
  private final BackgroundWorkControl backgroundWork;
  private final String vendor;
  private final Path dataDirectory;
  private final Duration drainTimeout;

  /** In-process record of the newest verified portable backup; startup always takes one. */
  private volatile Instant lastDailyBackupAttempt;

  @Builder
  public BackupService(
      Clock clock,
      DataSource dataSource,
      OneDrivePort oneDrive,
      OutlookAuthorization outlookAuthorization,
      ShadowRestoreService shadowRestoreService,
      PortableBackupExporter exporter,
      PortableBackupImporter importer,
      PortableBackupArchive archiveFormat,
      BackupEncryption encryption,
      WriteFreeze writeFreeze,
      BackgroundWorkControl backgroundWork,
      @Value("${bookie.db.vendor:h2}") String vendor,
      @Value("${bookie.data-dir}") Path dataDirectory,
      @Value("${bookie.backup.drain-timeout:PT5M}") Duration drainTimeout) {
    this.clock = clock;
    this.dataSource = dataSource;
    this.oneDrive = oneDrive;
    this.outlookAuthorization = outlookAuthorization;
    this.shadowRestoreService = shadowRestoreService;
    this.exporter = exporter;
    this.importer = importer;
    this.archiveFormat = archiveFormat;
    this.encryption = encryption;
    this.writeFreeze = writeFreeze;
    this.backgroundWork = backgroundWork;
    this.vendor = vendor;
    this.dataDirectory = dataDirectory;
    this.drainTimeout = drainTimeout;
  }

  // Runs daily at 2:00 AM in H2 mode — skips silently if Outlook/OneDrive is not connected
  @Scheduled(cron = "0 0 2 * * *")
  public void scheduledBackup() {
    if (isSqlServer() || !outlookAuthorization.isConnected()) {
      return;
    }
    try {
      backup();
      log.info("Scheduled OneDrive backup completed");
    } catch (Exception e) {
      log.error("Scheduled OneDrive backup failed", e);
    }
  }

  /** In SQL Server mode, takes a portable backup at every startup. */
  @EventListener(ApplicationReadyEvent.class)
  public void startupBackup() {
    if (isSqlServer()) {
      dailyPortableBackup("Startup");
    }
  }

  /**
   * In SQL Server mode, takes a portable backup once this process's last daily-backup attempt is
   * over a day old. The check touches neither OneDrive nor the database, and keying it on attempts
   * rather than successes means a skipped or failing backup wakes a paused database at most once a
   * day instead of every hour.
   */
  @Scheduled(cron = "0 0 * * * *")
  public void hourlyCheck() {
    if (!isSqlServer()) {
      return;
    }
    Instant last = lastDailyBackupAttempt;
    if (last == null || last.isBefore(clock.instant().minus(STALE_AFTER))) {
      dailyPortableBackup("Hourly");
    }
  }

  /**
   * Creates an on-demand backup: an H2 {@code .sql} script in H2 mode, or an encrypted portable
   * archive in SQL Server mode.
   */
  public BackupFile backup() throws IOException {
    if (isSqlServer()) {
      BackupFile file = portableBackup(BACKUP_FOLDER, "bookie-");
      pruneDailyBackups();
      return file;
    }
    Path tempFile = Files.createTempFile("bookie-backup-", ".sql");
    try {
      writeScriptTo(tempFile);
      String filename = backupFilename();
      try (InputStream in = Files.newInputStream(tempFile)) {
        return BackupFile.from(oneDrive.upload(BACKUP_FOLDER + "/" + filename, in));
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  String backupFilename() {
    return "bookie-" + timestamp() + ".sql";
  }

  /** Lists root backups plus the pre-migration and rollback folders. */
  public List<BackupFile> listBackups() {
    List<BackupFile> files = new ArrayList<>();
    oneDrive.listChildren(BACKUP_FOLDER).stream()
        .filter(item -> !item.folder())
        .map(BackupFile::from)
        .forEach(files::add);
    oneDrive.listChildren(BACKUP_FOLDER + "/" + PRE_MIGRATION_FOLDER).stream()
        .filter(item -> !item.folder())
        .map(item -> BackupFile.from(item, BackupCategory.PRE_MIGRATION))
        .forEach(files::add);
    oneDrive.listChildren(BACKUP_FOLDER + "/" + ROLLBACK_FOLDER).stream()
        .filter(item -> !item.folder())
        .map(item -> BackupFile.from(item, BackupCategory.ROLLBACK))
        .forEach(files::add);
    return files;
  }

  public void delete(String fileId) {
    oneDrive.delete(fileId);
  }

  public BackupStatus status() throws IOException {
    return new BackupStatus(vendor, writeFreeze.isFrozen(), encryption.isKeyConfirmed());
  }

  /**
   * Freezes writes, drains intake work, then writes a verified portable archive and {@code .sql}
   * script (both encrypted) to OneDrive and plaintext copies plus an H2 file backup locally. H2
   * mode only. The freeze stays on until a portable restore commits or the user lifts it.
   */
  public PreMigrationResult preMigrationBackup() throws IOException {
    if (isSqlServer()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Pre-migration backups are only taken from the local H2 database");
    }
    requireBackupPreconditions();
    writeFreeze.freeze();
    drainOrFail();
    String stamp = timestamp();
    Path localDirectory = dataDirectory.resolve(PRE_MIGRATION_FOLDER).resolve(stamp);
    Files.createDirectories(localDirectory);
    Path archive = localDirectory.resolve("bookie-" + stamp + ".zip");
    exportVerified(archive);
    Path script = localDirectory.resolve("bookie-" + stamp + ".sql");
    writeScriptTo(script);
    executeWithSafePath("BACKUP TO", localDirectory.resolve("bookie-h2-" + stamp + ".zip"));
    String folder = BACKUP_FOLDER + "/" + PRE_MIGRATION_FOLDER;
    List<BackupFile> uploaded =
        List.of(
            uploadEncrypted(
                archive,
                folder + "/" + archive.getFileName() + BackupEncryption.FILE_EXTENSION,
                BackupCategory.PRE_MIGRATION),
            uploadEncrypted(
                script,
                folder + "/" + script.getFileName() + BackupEncryption.FILE_EXTENSION,
                BackupCategory.PRE_MIGRATION));
    log.warn("Pre-migration backup completed; writes stay frozen: {}", localDirectory);
    return new PreMigrationResult(uploaded, localDirectory.toString());
  }

  /**
   * Freezes writes, drains intake work, and writes a verified encrypted portable archive to the
   * rollback folder. SQL Server mode only.
   */
  public BackupFile rollbackBackup() throws IOException {
    if (!isSqlServer()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Rollback backups are only taken from the SQL Server database");
    }
    requireBackupPreconditions();
    writeFreeze.freeze();
    drainOrFail();
    BackupFile file = portableBackup(BACKUP_FOLDER + "/" + ROLLBACK_FOLDER, "bookie-rollback-");
    log.warn("Rollback backup completed; writes stay frozen: {}", file.name());
    return file;
  }

  public void liftFreeze() {
    writeFreeze.lift();
  }

  /** Returns the backup key once, until the user confirms saving it. */
  public String revealBackupKey() throws IOException {
    try {
      return encryption.revealKey();
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
    }
  }

  public void confirmBackupKey() throws IOException {
    encryption.confirmKey();
  }

  /**
   * Restores a OneDrive backup. Encrypted portable archives replace all data in place after a
   * safety backup; legacy {@code .sql} scripts are staged in a shadow H2 database (H2 mode only).
   */
  public RestoreResult restore(String fileId) throws IOException {
    OneDriveItem item = oneDrive.getItem(fileId).orElse(null);
    if (item == null) {
      throw new IOException("Backup not found: " + fileId);
    }
    if (!isInBackupFolder(item)) {
      throw new IllegalArgumentException("fileId is not a backup: " + fileId);
    }
    if (item.size() > shadowRestoreService.maxRestoreBytes()) {
      throw new IOException(
          "Backup exceeds %d-byte safety cap: %d"
              .formatted(shadowRestoreService.maxRestoreBytes(), item.size()));
    }
    if (Strings.CS.endsWith(item.name(), ".sql" + BackupEncryption.FILE_EXTENSION)) {
      throw new IllegalArgumentException(
          "Encrypted .sql copies are kept for manual recovery. Restore the matching portable"
              + " archive instead.");
    }
    if (isPortable(item)) {
      return restorePortable(item);
    }
    if (isSqlServer()) {
      throw new IllegalArgumentException(
          "Legacy .sql backups can only be restored into the local H2 database. Restore a"
              + " portable (.bookiebak) backup instead.");
    }
    InputStream downloaded = oneDrive.download(fileId);
    if (downloaded == null) {
      throw new IOException("Could not download backup file: " + fileId);
    }
    log.warn(
        "Shadow restore staging initiated: fileId={} name={} declaredBytes={}",
        fileId,
        item.name(),
        item.size());
    RestoreJournal journal;
    try (InputStream stream = downloaded) {
      journal = shadowRestoreService.stage(fileId, item.name(), item.size(), stream);
    }
    log.info("Shadow restore validated: restoreId={} fileId={}", journal.restoreId(), fileId);
    return RestoreResult.from(journal);
  }

  public RestoreResult restoreStatus() throws IOException {
    return shadowRestoreService
        .currentJournal()
        .map(RestoreResult::from)
        .orElseGet(RestoreResult::idle);
  }

  private RestoreResult restorePortable(OneDriveItem item) throws IOException {
    Path work = Files.createTempDirectory("bookie-restore-");
    try {
      Path encrypted = work.resolve("restore" + BackupEncryption.FILE_EXTENSION);
      try (InputStream in = oneDrive.download(item.id())) {
        if (in == null) {
          throw new IOException("Could not download backup file: " + item.id());
        }
        Files.copy(in, encrypted);
      }
      Path archive = work.resolve("restore.zip");
      // Decrypting first reports a missing or wrong key as such, rather than as unconfirmed.
      encryption.decrypt(encrypted, archive);
      if (!encryption.isKeyConfirmed()) {
        throw keyNotConfirmed();
      }
      archiveFormat.read(archive);
      boolean wasFrozen = writeFreeze.isFrozen();
      if (!wasFrozen) {
        writeFreeze.freeze();
      }
      boolean committed = false;
      try {
        drainOrFail();
        BackupFile safety = portableBackup(BACKUP_FOLDER, "bookie-safety-");
        log.warn("Portable restore of {} starting; safety backup {}", item.name(), safety.name());
        importer.importArchive(archive);
        committed = true;
      } catch (SQLException e) {
        throw new IOException("Portable restore failed; the database is unchanged", e);
      } finally {
        if (committed || !wasFrozen) {
          writeFreeze.lift();
        }
      }
      log.info("Portable restore of {} committed", item.name());
      return RestoreResult.portableRestored();
    } finally {
      FileSystemUtils.deleteRecursively(work);
    }
  }

  private void dailyPortableBackup(String trigger) {
    try {
      if (!encryption.isKeyConfirmed()) {
        log.warn("{} portable backup skipped: confirm the backup key on the Backup page", trigger);
        return;
      }
      // Checking the Outlook connection reads the token cache from the database.
      lastDailyBackupAttempt = clock.instant();
      if (!outlookAuthorization.isConnected()) {
        return;
      }
      BackupFile file = portableBackup(BACKUP_FOLDER, "bookie-");
      pruneDailyBackups();
      log.info("{} portable backup completed: {}", trigger, file.name());
    } catch (Exception e) {
      log.error("{} portable backup failed", trigger, e);
    }
  }

  private BackupFile portableBackup(String folder, String prefix) throws IOException {
    if (!encryption.isKeyConfirmed()) {
      throw keyNotConfirmed();
    }
    Path work = Files.createTempDirectory("bookie-portable-");
    try {
      Path archive = work.resolve("backup.zip");
      exportVerified(archive);
      BackupFile file =
          uploadEncrypted(
              archive,
              folder + "/" + prefix + timestamp() + BackupEncryption.FILE_EXTENSION,
              categoryOf(folder));
      return file;
    } finally {
      FileSystemUtils.deleteRecursively(work);
    }
  }

  private static BackupCategory categoryOf(String folder) {
    if (folder.endsWith("/" + ROLLBACK_FOLDER)) {
      return BackupCategory.ROLLBACK;
    }
    return BackupCategory.PORTABLE;
  }

  /** Deletes encrypted root backups beyond the newest {@link #RETAINED_DAILY_BACKUPS}. */
  void pruneDailyBackups() {
    oneDrive.listChildren(BACKUP_FOLDER).stream()
        .filter(item -> !item.folder() && isPortable(item))
        .sorted(Comparator.comparing(BackupService::modifiedAt).reversed())
        .skip(RETAINED_DAILY_BACKUPS)
        .forEach(
            item -> {
              log.info("Pruning old portable backup {}", item.name());
              oneDrive.delete(item.id());
            });
  }

  private static Instant modifiedAt(OneDriveItem item) {
    try {
      return OffsetDateTime.parse(item.lastModified()).toInstant();
    } catch (DateTimeParseException | NullPointerException e) {
      return Instant.EPOCH;
    }
  }

  private void requireBackupPreconditions() throws IOException {
    if (!encryption.isKeyConfirmed()) {
      throw keyNotConfirmed();
    }
    if (!outlookAuthorization.isConnected()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Connect Outlook so backups can be uploaded to OneDrive");
    }
  }

  private static ResponseStatusException keyNotConfirmed() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Save the backup key in your password manager and confirm it on the Backup page first");
  }

  private void drainOrFail() {
    if (!backgroundWork.drainUntilIdle(drainTimeout)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Background intake work did not finish within %s; writes stay frozen. Try again."
              .formatted(drainTimeout));
    }
  }

  private void exportVerified(Path archive) throws IOException {
    try {
      exporter.export(archive);
    } catch (SQLException e) {
      throw new IOException("Portable export failed", e);
    }
    archiveFormat.read(archive);
  }

  private BackupFile uploadEncrypted(Path plain, String remotePath, BackupCategory category)
      throws IOException {
    Path encrypted = Files.createTempFile("bookie-upload-", BackupEncryption.FILE_EXTENSION);
    try {
      encryption.encrypt(plain, encrypted);
      String expected = sha256(Files.newInputStream(encrypted));
      OneDriveItem uploaded;
      try (InputStream in = Files.newInputStream(encrypted)) {
        uploaded = oneDrive.upload(remotePath, in);
      }
      verifyReadBack(uploaded, expected, remotePath);
      return BackupFile.from(uploaded, category);
    } finally {
      Files.deleteIfExists(encrypted);
    }
  }

  private void verifyReadBack(OneDriveItem uploaded, String expected, String remotePath)
      throws IOException {
    String actual = null;
    try (InputStream in = oneDrive.download(uploaded.id())) {
      if (in != null) {
        actual = sha256(in);
      }
    }
    if (!expected.equals(actual)) {
      try {
        oneDrive.delete(uploaded.id());
      } catch (RuntimeException e) {
        log.warn("Could not delete unverified upload {}", remotePath, e);
      }
      throw new IOException("Uploaded backup failed read-back verification: " + remotePath);
    }
  }

  private static String sha256(InputStream source) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
    try (DigestInputStream in = new DigestInputStream(source, digest)) {
      in.transferTo(OutputStream.nullOutputStream());
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private String timestamp() {
    return LocalDateTime.now(clock).format(FORMATTER);
  }

  private boolean isSqlServer() {
    return SQL_SERVER.equals(vendor);
  }

  private static boolean isPortable(OneDriveItem item) {
    return Strings.CS.endsWith(item.name(), BackupEncryption.FILE_EXTENSION);
  }

  /** Dumps the current database to {@code path} via H2's {@code SCRIPT TO} command. */
  private void writeScriptTo(Path path) {
    executeWithSafePath("SCRIPT TO", path);
  }

  private void executeWithSafePath(String command, Path path) {
    String safePath = path.toAbsolutePath().toString().replace("\\", "/");
    if (!SAFE_PATH.matcher(safePath).matches()) {
      throw new IllegalStateException("Refusing to write to unsafe path: " + safePath);
    }
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(command + " '" + safePath + "'");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to write database backup to " + path, e);
    }
  }

  /**
   * Confirms a drive item's parent path is the backups folder or its pre-migration or rollback
   * folder. OneDrive parent paths look like {@code /drive/root:/bookie/backups} or {@code
   * /me/drive/root:/bookie/backups} depending on the call. The match must be exact.
   */
  private static boolean isInBackupFolder(OneDriveItem item) {
    if (item.parentPath() == null) {
      return false;
    }
    String parentPath = item.parentPath();
    return List.of(
            BACKUP_FOLDER,
            BACKUP_FOLDER + "/" + PRE_MIGRATION_FOLDER,
            BACKUP_FOLDER + "/" + ROLLBACK_FOLDER)
        .stream()
        .anyMatch(
            folder -> parentPath.endsWith(":/" + folder) || parentPath.endsWith("/" + folder));
  }

  /** Where a backup came from, so the Backup page can label it. */
  public enum BackupCategory {
    PORTABLE,
    LEGACY,
    PRE_MIGRATION,
    ROLLBACK
  }

  public record BackupFile(
      String id, String name, long size, String lastModified, BackupCategory category) {

    static BackupFile from(OneDriveItem item) {
      return from(item, isPortable(item) ? BackupCategory.PORTABLE : BackupCategory.LEGACY);
    }

    static BackupFile from(OneDriveItem item, BackupCategory category) {
      return new BackupFile(
          item.id() != null ? item.id() : "",
          item.name() != null ? item.name() : "",
          item.size(),
          item.lastModified() != null ? item.lastModified() : "",
          category);
    }
  }

  public record BackupStatus(String vendor, boolean frozen, boolean keyConfirmed) {}

  public record PreMigrationResult(List<BackupFile> uploaded, String localDirectory) {}

  public record BackupKey(String key) {}

  public record RestoreResult(
      String restoreId,
      RestoreState state,
      boolean restored,
      boolean validated,
      boolean restartRequired,
      String message) {

    static RestoreResult from(RestoreJournal journal) {
      RestoreState state = journal.state();
      boolean validated =
          state == RestoreState.VALIDATED
              || state == RestoreState.LIVE_RETAINED
              || state == RestoreState.SHADOW_ACTIVATED
              || state == RestoreState.POST_START_VALIDATED;
      return new RestoreResult(
          journal.restoreId(),
          state,
          state == RestoreState.POST_START_VALIDATED,
          validated,
          state == RestoreState.VALIDATED
              || state == RestoreState.LIVE_RETAINED
              || state == RestoreState.ROLLBACK_REQUIRED,
          journal.message());
    }

    static RestoreResult idle() {
      return new RestoreResult(
          null, RestoreState.IDLE, false, false, false, "No restore is staged");
    }

    static RestoreResult portableRestored() {
      return new RestoreResult(
          null,
          RestoreState.POST_START_VALIDATED,
          true,
          true,
          false,
          "Portable backup restored and verified; no restart is needed");
    }
  }
}
