package com.bookie.datalifecycle.backup;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.datalifecycle.backup.BackupService.BackupCategory;
import com.bookie.datalifecycle.backup.BackupService.BackupFile;
import com.bookie.datalifecycle.backup.BackupService.BackupStatus;
import com.bookie.datalifecycle.backup.BackupService.PreMigrationResult;
import com.bookie.datalifecycle.backup.BackupService.RestoreResult;
import com.bookie.datalifecycle.restore.RestoreRestartCoordinator;
import com.bookie.datalifecycle.restore.RestoreState;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(BackupController.class)
class BackupControllerTest {

  private static final BackupFile PORTABLE =
      new BackupFile(
          "file-1",
          "bookie-2026-09-22_19-00-00.bookiebak",
          1024L,
          "2026-09-22T19:00:00Z",
          BackupCategory.PORTABLE);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private BackupService backupService;
  @MockitoBean private RestoreRestartCoordinator restoreRestartCoordinator;

  @Nested
  class CreateBackup {

    @Test
    void returnsTheUploadedBackup() throws Exception {
      when(backupService.backup()).thenReturn(PORTABLE);

      mockMvc
          .perform(post("/api/backup"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("bookie-2026-09-22_19-00-00.bookiebak"))
          .andExpect(jsonPath("$.category").value("PORTABLE"));
    }

    @Test
    void returnsConflictUntilTheKeyIsConfirmed() throws Exception {
      when(backupService.backup())
          .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "confirm the key"));

      mockMvc.perform(post("/api/backup")).andExpect(status().isConflict());
    }
  }

  @Nested
  class ListBackups {

    @Test
    void returnsEveryCategory() throws Exception {
      when(backupService.listBackups())
          .thenReturn(
              List.of(
                  PORTABLE,
                  new BackupFile("file-2", "old.sql", 10L, "", BackupCategory.LEGACY),
                  new BackupFile(
                      "file-3", "pre.zip.bookiebak", 10L, "", BackupCategory.PRE_MIGRATION),
                  new BackupFile("file-4", "rb.bookiebak", 10L, "", BackupCategory.ROLLBACK)));

      mockMvc
          .perform(get("/api/backup/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value("file-1"))
          .andExpect(jsonPath("$[1].category").value("LEGACY"))
          .andExpect(jsonPath("$[2].category").value("PRE_MIGRATION"))
          .andExpect(jsonPath("$[3].category").value("ROLLBACK"));
    }
  }

  @Nested
  class RestoreBackup {

    @Test
    void returnsAPortableRestoreThatNeedsNoRestart() throws Exception {
      when(backupService.restore("file-1"))
          .thenReturn(
              new RestoreResult(
                  null, RestoreState.POST_START_VALIDATED, true, true, false, "restored"));

      mockMvc
          .perform(post("/api/backup/restore/file-1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("POST_START_VALIDATED"))
          .andExpect(jsonPath("$.restored").value(true))
          .andExpect(jsonPath("$.restartRequired").value(false));
    }

    @Test
    void returnsAValidatedShadowRestoreThatNeedsARestart() throws Exception {
      when(backupService.restore("file-123"))
          .thenReturn(
              new RestoreResult(
                  "restore-123", RestoreState.VALIDATED, false, true, true, "Restart required"));

      mockMvc
          .perform(post("/api/backup/restore/file-123"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.restoreId").value("restore-123"))
          .andExpect(jsonPath("$.state").value("VALIDATED"))
          .andExpect(jsonPath("$.validated").value(true))
          .andExpect(jsonPath("$.restartRequired").value(true));
    }

    @Test
    void returnsBadRequestForFilesThatCannotBeRestored() throws Exception {
      when(backupService.restore("file-9"))
          .thenThrow(new IllegalArgumentException("Encrypted .sql copies are kept"));

      mockMvc.perform(post("/api/backup/restore/file-9")).andExpect(status().isBadRequest());
    }

    @Test
    void returnsCurrentRestoreStatus() throws Exception {
      when(backupService.restoreStatus())
          .thenReturn(
              new RestoreResult(
                  "restore-123", RestoreState.POST_START_VALIDATED, true, true, false, "Complete"));

      mockMvc
          .perform(get("/api/backup/restore/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("POST_START_VALIDATED"));
    }

    @Test
    void acceptsAControlledShutdownAfterStaging() throws Exception {
      mockMvc.perform(post("/api/backup/restore/shutdown")).andExpect(status().isAccepted());

      verify(restoreRestartCoordinator).requestShutdown();
    }
  }

  @Nested
  class DeleteBackup {

    @Test
    void returnsNoContentOnSuccess() throws Exception {
      mockMvc.perform(delete("/api/backup/file-123")).andExpect(status().isNoContent());

      verify(backupService).delete("file-123");
    }

    @Test
    void returns500WhenServiceThrows() throws Exception {
      doThrow(new RuntimeException("OneDrive error")).when(backupService).delete("bad-id");

      mockMvc.perform(delete("/api/backup/bad-id")).andExpect(status().isInternalServerError());
    }
  }

  @Nested
  class MigrationBackups {

    @Test
    void returnsVendorFreezeAndKeyStatus() throws Exception {
      when(backupService.status()).thenReturn(new BackupStatus("sqlserver", true, false));

      mockMvc
          .perform(get("/api/backups/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.vendor").value("sqlserver"))
          .andExpect(jsonPath("$.frozen").value(true))
          .andExpect(jsonPath("$.keyConfirmed").value(false));
    }

    @Test
    void returnsThePreMigrationUploadsAndLocalDirectory() throws Exception {
      when(backupService.preMigrationBackup())
          .thenReturn(
              new PreMigrationResult(
                  List.of(
                      new BackupFile(
                          "file-5", "b.zip.bookiebak", 10L, "", BackupCategory.PRE_MIGRATION)),
                  "/data/pre-migration/2026-09-22_19-00-00"));

      mockMvc
          .perform(post("/api/backups/pre-migration"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.uploaded[0].category").value("PRE_MIGRATION"))
          .andExpect(jsonPath("$.localDirectory").value("/data/pre-migration/2026-09-22_19-00-00"));
    }

    @Test
    void returnsConflictWhenIntakeWorkDoesNotDrain() throws Exception {
      when(backupService.preMigrationBackup())
          .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "did not finish"));

      mockMvc.perform(post("/api/backups/pre-migration")).andExpect(status().isConflict());
    }

    @Test
    void returnsTheRollbackBackup() throws Exception {
      when(backupService.rollbackBackup())
          .thenReturn(new BackupFile("file-6", "rb.bookiebak", 10L, "", BackupCategory.ROLLBACK));

      mockMvc
          .perform(post("/api/backups/rollback"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.category").value("ROLLBACK"));
    }

    @Test
    void liftsTheWriteFreeze() throws Exception {
      mockMvc.perform(delete("/api/backups/freeze")).andExpect(status().isNoContent());

      verify(backupService).liftFreeze();
    }
  }

  @Nested
  class BackupKey {

    @Test
    void revealsTheKey() throws Exception {
      when(backupService.revealBackupKey()).thenReturn("secret-key");

      mockMvc
          .perform(post("/api/backups/key/reveal"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.key").value("secret-key"));
    }

    @Test
    void returnsConflictOnceTheKeyWasConfirmed() throws Exception {
      when(backupService.revealBackupKey())
          .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "already confirmed"));

      mockMvc.perform(post("/api/backups/key/reveal")).andExpect(status().isConflict());
    }

    @Test
    void confirmsTheKey() throws Exception {
      mockMvc.perform(post("/api/backups/key/confirm")).andExpect(status().isNoContent());

      verify(backupService).confirmBackupKey();
    }
  }
}
