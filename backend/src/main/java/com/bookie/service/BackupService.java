package com.bookie.service;

import com.bookie.datalifecycle.restore.RestoreJournal;
import com.bookie.datalifecycle.restore.RestoreState;
import com.bookie.datalifecycle.restore.ShadowRestoreService;
import com.bookie.integrations.onedrive.OneDriveItem;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.integrations.outlook.OutlookAuthorization;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

  private static final String BACKUP_FOLDER = "bookie/backups";
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

  /**
   * Temp file paths are spliced into {@code SCRIPT TO '...'} as a SQL literal. H2 has no
   * parameterised form for this command, so we instead refuse to use any path that isn't plainly
   * safe. Files.createTempFile produces alphanumeric paths so a real failure here means the JDK has
   * been swapped under us.
   */
  private static final Pattern SAFE_PATH = Pattern.compile("^[A-Za-z0-9./_-]+$");

  private final DataSource dataSource;
  private final OneDrivePort oneDrive;
  private final OutlookAuthorization outlookAuthorization;
  private final ShadowRestoreService shadowRestoreService;

  // Runs daily at 2:00 AM — skips silently if Outlook/OneDrive is not connected
  @Scheduled(cron = "0 0 2 * * *")
  public void scheduledBackup() {
    if (!outlookAuthorization.isConnected()) {
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
   * Stages a OneDrive backup in an isolated shadow database. The active database is never changed
   * in this request. Activation happens before datasource initialization on a controlled restart.
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
  private static boolean isInBackupFolder(OneDriveItem item) {
    if (item.parentPath() == null) {
      return false;
    }
    String parentPath = item.parentPath();
    return parentPath.endsWith(":/" + BACKUP_FOLDER) || parentPath.endsWith("/" + BACKUP_FOLDER);
  }

  public record BackupFile(String id, String name, long size, String lastModified) {
    static BackupFile from(OneDriveItem item) {
      return new BackupFile(
          item.id() != null ? item.id() : "",
          item.name() != null ? item.name() : "",
          item.size(),
          item.lastModified() != null ? item.lastModified() : "");
    }
  }

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
  }
}
