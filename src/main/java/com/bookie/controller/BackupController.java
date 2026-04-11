package com.bookie.controller;

import com.bookie.service.BackupService;
import com.microsoft.graph.models.DriveItem;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

  private final BackupService backupService;

  public record BackupResult(String name, String id) {}

  public record BackupFile(String id, String name, long size, String lastModified) {
    static BackupFile from(DriveItem item) {
      return new BackupFile(
          item.getId() != null ? item.getId() : "",
          item.getName() != null ? item.getName() : "",
          item.getSize() != null ? item.getSize() : 0L,
          item.getLastModifiedDateTime() != null ? item.getLastModifiedDateTime().toString() : "");
    }
  }

  @PostMapping
  public BackupResult backup() throws IOException {
    DriveItem item = backupService.backup();
    return new BackupResult(item.getName(), item.getId());
  }

  @GetMapping("/list")
  public List<BackupFile> listBackups() {
    return backupService.listBackups().stream().map(BackupFile::from).toList();
  }

  @PostMapping("/restore/{fileId}")
  public void restore(@PathVariable String fileId) throws IOException {
    backupService.restore(fileId);
  }
}
