package com.bookie.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.service.BackupService;
import com.microsoft.graph.models.DriveItem;
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

  @Nested
  class ListBackups {

    @Test
    void returnsBackupList() throws Exception {
      DriveItem item = new DriveItem();
      item.setId("file-1");
      item.setName("bookie-2026-04-22.sql");
      item.setSize(1024L);
      when(backupService.listBackups()).thenReturn(List.of(item));

      mockMvc
          .perform(get("/api/backup/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value("file-1"))
          .andExpect(jsonPath("$[0].name").value("bookie-2026-04-22.sql"));
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
