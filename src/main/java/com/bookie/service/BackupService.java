package com.bookie.service;

import com.microsoft.graph.models.DriveItem;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.h2.tools.RunScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

  private static final String BACKUP_FOLDER = "bookie/backups";
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

  /** Hard cap so an accidental click on a 50 GB file doesn't try to swap the whole database. */
  private static final long MAX_RESTORE_BYTES = 500L * 1024 * 1024;

  /**
   * Temp file paths are spliced into {@code SCRIPT TO '...'} as a SQL literal. H2 has no
   * parameterised form for this command, so we instead refuse to use any path that isn't plainly
   * safe. Files.createTempFile produces alphanumeric paths so a real failure here means the JDK has
   * been swapped under us.
   */
  private static final Pattern SAFE_PATH = Pattern.compile("^[A-Za-z0-9./_-]+$");

  private final DataSource dataSource;
  private final OneDriveClient oneDrive;
  private final MsalTokenService msalTokenService;
  private final Flyway flyway;

  // Runs daily at 2:00 AM — skips silently if Outlook/OneDrive is not connected
  @Scheduled(cron = "0 0 2 * * *")
  public void scheduledBackup() {
    if (!msalTokenService.isConnected()) {
      return;
    }
    try {
      backup();
      log.info("Scheduled OneDrive backup completed");
    } catch (Exception e) {
      log.error("Scheduled OneDrive backup failed", e);
    }
  }

  public BackupFile backup() throws IOException {
    Path tempFile = Files.createTempFile("bookie-backup-", ".sql");
    try {
      writeScriptTo(tempFile);
      String filename = "bookie-" + LocalDateTime.now().format(FORMATTER) + ".sql";
      try (InputStream in = Files.newInputStream(tempFile)) {
        return BackupFile.from(oneDrive.upload(BACKUP_FOLDER + "/" + filename, in));
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  public List<BackupFile> listBackups() {
    return oneDrive.listChildren(BACKUP_FOLDER).stream().map(BackupFile::from).toList();
  }

  public void delete(String fileId) {
    oneDrive.delete(fileId);
  }

  /**
   * Restores the database from a OneDrive backup. The file must live under {@link #BACKUP_FOLDER};
   * before destruction we dump the current DB to a temp file and roll back to it if the restore
   * fails mid-flight. Flyway runs after RUNSCRIPT so a pre-Flyway backup is baselined and brought
   * up to the current schema version automatically.
   */
  public RestoreResult restore(String fileId) throws IOException {
    DriveItem item = oneDrive.getItem(fileId);
    if (item == null) {
      throw new IOException("Backup not found: " + fileId);
    }
    if (!isInBackupFolder(item)) {
      throw new IllegalArgumentException("fileId is not a backup: " + fileId);
    }
    if (item.getSize() != null && item.getSize() > MAX_RESTORE_BYTES) {
      throw new IOException(
          "Backup exceeds %d-byte safety cap: %d".formatted(MAX_RESTORE_BYTES, item.getSize()));
    }

    Path restorePath = Files.createTempFile("bookie-restore-", ".sql");
    Path safetyPath = Files.createTempFile("bookie-pre-restore-", ".sql");
    try {
      try (InputStream stream = oneDrive.download(fileId)) {
        if (stream == null) {
          throw new IOException("Could not download backup file: " + fileId);
        }
        Files.copy(stream, restorePath, StandardCopyOption.REPLACE_EXISTING);
      }
      if (Files.size(restorePath) == 0) {
        throw new IOException("Backup file is empty: " + fileId);
      }

      log.warn(
          "DB restore initiated: fileId={} name={} bytes={}",
          fileId,
          item.getName(),
          Files.size(restorePath));

      writeScriptTo(safetyPath);

      try {
        runRestore(restorePath);
      } catch (Exception restoreFailure) {
        log.error("Restore failed — rolling back to pre-restore snapshot", restoreFailure);
        try {
          runRestore(safetyPath);
        } catch (Exception rollbackFailure) {
          log.error("Rollback also failed; database is in an indeterminate state", rollbackFailure);
          restoreFailure.addSuppressed(rollbackFailure);
        }
        throw new IOException("Restore failed; rolled back to pre-restore state", restoreFailure);
      }

      flyway.migrate();
      assertDatabaseReady();
      log.info("DB restore completed: fileId={}", fileId);
      return new RestoreResult(true, true);
    } finally {
      Files.deleteIfExists(restorePath);
      Files.deleteIfExists(safetyPath);
    }
  }

  /**
   * Drops all objects and re-runs the given SQL script via H2's programmatic {@code RunScript} API.
   * Avoids splicing the path into a literal SQL string.
   */
  private void runRestore(Path script) throws SQLException, IOException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        Reader reader = Files.newBufferedReader(script, StandardCharsets.UTF_8)) {
      stmt.execute("DROP ALL OBJECTS");
      RunScript.execute(conn, reader);
    }
  }

  /**
   * Confirms the restored database is queryable before reporting success to clients.
   *
   * <p>This protects the UI restore flow from a "success" toast followed by immediate query
   * failures.
   */
  private void assertDatabaseReady() {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SELECT 1");
    } catch (SQLException e) {
      throw new IllegalStateException("Restore completed but database failed readiness check", e);
    }
  }

  /** Dumps the current database to {@code path} via H2's {@code SCRIPT TO} command. */
  private void writeScriptTo(Path path) {
    String safePath = path.toAbsolutePath().toString().replace("\\", "/");
    if (!SAFE_PATH.matcher(safePath).matches()) {
      throw new IllegalStateException("Refusing to dump to unsafe temp path: " + safePath);
    }
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SCRIPT TO '" + safePath + "'");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to dump database to " + path, e);
    }
  }

  /**
   * Confirms a drive item's parent path is the backups folder. OneDrive parent paths look like
   * {@code /drive/root:/bookie/backups} or {@code /me/drive/root:/bookie/backups} depending on the
   * call. The match must be exact — a path ending in {@code /bookie/backups/sub} is rejected.
   */
  private static boolean isInBackupFolder(DriveItem item) {
    if (item.getParentReference() == null || item.getParentReference().getPath() == null) {
      return false;
    }
    String parentPath = item.getParentReference().getPath();
    return parentPath.endsWith(":/" + BACKUP_FOLDER) || parentPath.endsWith("/" + BACKUP_FOLDER);
  }

  public record BackupFile(String id, String name, long size, String lastModified) {
    static BackupFile from(DriveItem item) {
      return new BackupFile(
          item.getId() != null ? item.getId() : "",
          item.getName() != null ? item.getName() : "",
          item.getSize() != null ? item.getSize() : 0L,
          item.getLastModifiedDateTime() != null ? item.getLastModifiedDateTime().toString() : "");
    }
  }

  public record RestoreResult(boolean restored, boolean validated) {}
}
