package com.bookie.service;

import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

  private static final String BACKUP_FOLDER = "bookie/backups";
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

  private final JdbcTemplate jdbcTemplate;
  private final GraphServiceClient graphClient;
  private final MsalTokenService msalTokenService;

  // Runs daily at 2:00 AM — skips silently if Outlook/OneDrive is not connected
  @Scheduled(cron = "0 0 2 * * *")
  public void scheduledBackup() {
    if (!msalTokenService.isConnected()) return;
    try {
      backup();
      log.info("Scheduled OneDrive backup completed");
    } catch (Exception e) {
      log.error("Scheduled OneDrive backup failed", e);
    }
  }

  public DriveItem backup() throws IOException {
    Path tempFile = Files.createTempFile("bookie-backup-", ".sql");
    try {
      String path = tempFile.toAbsolutePath().toString().replace("\\", "/");
      jdbcTemplate.execute("SCRIPT TO '" + path + "'");
      String filename = "bookie-" + LocalDateTime.now().format(FORMATTER) + ".sql";
      byte[] content = Files.readAllBytes(tempFile);
      return uploadToOneDrive(filename, content);
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  public List<DriveItem> listBackups() {
    try {
      String driveId = graphClient.me().drive().get().getId();
      // Uses path notation: root:/{path}: to reference folder by path
      var resp =
          graphClient
              .drives()
              .byDriveId(driveId)
              .items()
              .byDriveItemId("root:/" + BACKUP_FOLDER + ":")
              .children()
              .get(
                  config ->
                      config.queryParameters.orderby = new String[] {"lastModifiedDateTime desc"});
      return resp != null && resp.getValue() != null ? resp.getValue() : List.of();
    } catch (Exception e) {
      log.warn("Could not list backups (folder may not exist yet): {}", e.getMessage());
      return List.of();
    }
  }

  public void restore(String fileId) throws IOException {
    String driveId = graphClient.me().drive().get().getId();
    InputStream stream =
        graphClient.drives().byDriveId(driveId).items().byDriveItemId(fileId).content().get();

    Path tempFile = Files.createTempFile("bookie-restore-", ".sql");
    try {
      if (stream == null) {
        throw new IOException("Could not download backup file: " + fileId);
      }
      Files.write(tempFile, stream.readAllBytes());
      String path = tempFile.toAbsolutePath().toString().replace("\\", "/");
      jdbcTemplate.execute("DROP ALL OBJECTS");
      jdbcTemplate.execute("RUNSCRIPT FROM '" + path + "'");
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private DriveItem uploadToOneDrive(String filename, byte[] content) {
    String driveId = graphClient.me().drive().get().getId();
    // Uses path notation: root:/{folder}/{filename}: to upload by path
    return graphClient
        .drives()
        .byDriveId(driveId)
        .items()
        .byDriveItemId("root:/" + BACKUP_FOLDER + "/" + filename + ":")
        .content()
        .put(new ByteArrayInputStream(content));
  }
}
