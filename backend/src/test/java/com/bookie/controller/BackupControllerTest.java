package com.bookie.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.datalifecycle.restore.RestoreRestartCoordinator;
import com.bookie.datalifecycle.restore.RestoreState;
import com.bookie.service.BackupService;
import com.bookie.service.BackupService.BackupFile;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BackupController.class)
class BackupControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private BackupService backupService;
  @MockitoBean private RestoreRestartCoordinator restoreRestartCoordinator;

  @Nested
  class ListBackups {

    @Test
    void returnsBackupList() throws Exception {
      BackupFile item =
          new BackupFile("file-1", "bookie-2026-04-22.sql", 1024L, "2026-04-22T10:00:00Z");
      when(backupService.listBackups()).thenReturn(List.of(item));

      mockMvc
          .perform(get("/api/backup/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value("file-1"))
          .andExpect(jsonPath("$[0].name").value("bookie-2026-04-22.sql"));
    }
  }

  @Nested
  class RestoreBackup {

    @Test
    void returnsValidatedRestartRequiredRestoreResult() throws Exception {
      when(backupService.restore("file-123"))
          .thenReturn(
              new BackupService.RestoreResult(
                  "restore-123", RestoreState.VALIDATED, false, true, true, "Restart required"));

      mockMvc
          .perform(post("/api/backup/restore/file-123"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.restoreId").value("restore-123"))
          .andExpect(jsonPath("$.state").value("VALIDATED"))
          .andExpect(jsonPath("$.restored").value(false))
          .andExpect(jsonPath("$.validated").value(true))
          .andExpect(jsonPath("$.restartRequired").value(true));
    }

    @Test
    void returnsCurrentRestoreStatus() throws Exception {
      when(backupService.restoreStatus())
          .thenReturn(
              new BackupService.RestoreResult(
                  "restore-123", RestoreState.POST_START_VALIDATED, true, true, false, "Complete"));

      mockMvc
          .perform(get("/api/backup/restore/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("POST_START_VALIDATED"))
          .andExpect(jsonPath("$.restored").value(true));
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
}
