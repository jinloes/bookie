package com.bookie.datalifecycle.backup;

import com.bookie.datalifecycle.backup.BackupService.BackupFile;
import com.bookie.datalifecycle.backup.BackupService.BackupKey;
import com.bookie.datalifecycle.backup.BackupService.BackupStatus;
import com.bookie.datalifecycle.backup.BackupService.PreMigrationResult;
import com.bookie.datalifecycle.restore.RestoreRestartCoordinator;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BackupController {

  private final BackupService backupService;
  private final RestoreRestartCoordinator restoreRestartCoordinator;

  @Operation(operationId = "createBackup")
  @PostMapping("/api/backup")
  public BackupFile backup() throws IOException {
    return backupService.backup();
  }

  @Operation(operationId = "getBackups")
  @GetMapping("/api/backup/list")
  public List<BackupFile> listBackups() {
    return backupService.listBackups();
  }

  @Operation(operationId = "restoreBackup")
  @PostMapping("/api/backup/restore/{fileId}")
  public BackupService.RestoreResult restore(@PathVariable String fileId) throws IOException {
    return backupService.restore(fileId);
  }

  @Operation(operationId = "getRestoreStatus")
  @GetMapping("/api/backup/restore/status")
  public BackupService.RestoreResult restoreStatus() throws IOException {
    return backupService.restoreStatus();
  }

  @Hidden
  @PostMapping("/api/backup/restore/shutdown")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void shutdownForRestore() throws IOException {
    restoreRestartCoordinator.requestShutdown();
  }

  @Operation(operationId = "deleteBackup")
  @DeleteMapping("/api/backup/{fileId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String fileId) {
    backupService.delete(fileId);
  }

  @Operation(operationId = "getBackupStatus")
  @GetMapping("/api/backups/status")
  public BackupStatus status() throws IOException {
    return backupService.status();
  }

  @Operation(operationId = "createPreMigrationBackup")
  @PostMapping("/api/backups/pre-migration")
  public PreMigrationResult preMigrationBackup() throws IOException {
    return backupService.preMigrationBackup();
  }

  @Operation(operationId = "createRollbackBackup")
  @PostMapping("/api/backups/rollback")
  public BackupFile rollbackBackup() throws IOException {
    return backupService.rollbackBackup();
  }

  @Operation(operationId = "liftWriteFreeze")
  @DeleteMapping("/api/backups/freeze")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void liftFreeze() {
    backupService.liftFreeze();
  }

  @Operation(operationId = "revealBackupKey")
  @PostMapping("/api/backups/key/reveal")
  public BackupKey revealBackupKey() throws IOException {
    return new BackupKey(backupService.revealBackupKey());
  }

  @Operation(operationId = "confirmBackupKey")
  @PostMapping("/api/backups/key/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmBackupKey() throws IOException {
    backupService.confirmBackupKey();
  }
}
