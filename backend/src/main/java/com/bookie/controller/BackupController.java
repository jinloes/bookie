package com.bookie.controller;

import com.bookie.service.BackupService;
import com.bookie.service.BackupService.BackupFile;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

  private final BackupService backupService;

  @Operation(operationId = "createBackup")
  @PostMapping
  public BackupFile backup() throws IOException {
    return backupService.backup();
  }

  @Operation(operationId = "getBackups")
  @GetMapping("/list")
  public List<BackupFile> listBackups() {
    return backupService.listBackups();
  }

  @Operation(operationId = "restoreBackup")
  @PostMapping("/restore/{fileId}")
  public BackupService.RestoreResult restore(@PathVariable String fileId) throws IOException {
    return backupService.restore(fileId);
  }

  @Operation(operationId = "deleteBackup")
  @DeleteMapping("/{fileId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String fileId) {
    backupService.delete(fileId);
  }
}
