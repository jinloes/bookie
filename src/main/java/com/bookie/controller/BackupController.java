package com.bookie.controller;

import com.bookie.service.BackupService;
import com.microsoft.graph.models.DriveItem;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

  private final BackupService backupService;

  public BackupController(BackupService backupService) {
    this.backupService = backupService;
  }

  @PostMapping
  public ResponseEntity<?> backup() {
    try {
      DriveItem item = backupService.backup();
      return ResponseEntity.ok(Map.of("name", item.getName(), "id", item.getId()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/list")
  public ResponseEntity<?> listBackups() {
    try {
      List<DriveItem> items = backupService.listBackups();
      var files =
          items.stream()
              .map(
                  item ->
                      Map.of(
                          "id",
                          item.getId() != null ? item.getId() : "",
                          "name",
                          item.getName() != null ? item.getName() : "",
                          "size",
                          item.getSize() != null ? item.getSize() : 0L,
                          "lastModified",
                          item.getLastModifiedDateTime() != null
                              ? item.getLastModifiedDateTime().toString()
                              : ""))
              .toList();
      return ResponseEntity.ok(files);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping("/restore/{fileId}")
  public ResponseEntity<?> restore(@PathVariable String fileId) {
    try {
      backupService.restore(fileId);
      return ResponseEntity.ok(Map.of("status", "restored"));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }
}
