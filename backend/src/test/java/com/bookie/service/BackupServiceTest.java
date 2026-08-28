package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.datalifecycle.restore.RestoreJournal;
import com.bookie.datalifecycle.restore.RestoreState;
import com.bookie.datalifecycle.restore.ShadowRestoreService;
import com.bookie.integrations.onedrive.OneDriveItem;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.integrations.outlook.OutlookAuthorization;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

  @Mock private DataSource dataSource;
  @Mock private OneDrivePort oneDrive;
  @Mock private OutlookAuthorization msalTokenService;
  @Mock private ShadowRestoreService shadowRestoreService;

  @InjectMocks private BackupService service;

  private static OneDriveItem backupItem(long size) {
    return OneDriveItem.builder()
        .id("file-42")
        .name("bookie-2026-04-22_10-00-00.sql")
        .size(size)
        .parentPath("/drive/root:/bookie/backups")
        .build();
  }

  @Nested
  class Restore {

    @Test
    void stagesValidatedShadowWithoutClaimingActivation() throws Exception {
      byte[] backup = "synthetic backup".getBytes();
      RestoreJournal journal = journal(RestoreState.VALIDATED);
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);
      when(oneDrive.getItem("file-42")).thenReturn(Optional.of(backupItem(backup.length)));
      when(oneDrive.download("file-42")).thenReturn(new ByteArrayInputStream(backup));
      when(shadowRestoreService.stage(
              anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), any()))
          .thenReturn(journal);

      BackupService.RestoreResult result = service.restore("file-42");

      assertThat(result.state()).isEqualTo(RestoreState.VALIDATED);
      assertThat(result.validated()).isTrue();
      assertThat(result.restored()).isFalse();
      assertThat(result.restartRequired()).isTrue();
      verify(shadowRestoreService)
          .stage(
              org.mockito.ArgumentMatchers.eq("file-42"),
              org.mockito.ArgumentMatchers.eq("bookie-2026-04-22_10-00-00.sql"),
              org.mockito.ArgumentMatchers.eq((long) backup.length),
              any());
      verifyNoInteractions(dataSource);
    }

    @Test
    void rejectsFileIdOutsideBackupsFolder() {
      OneDriveItem item =
          OneDriveItem.builder().id("file-42").parentPath("/drive/root:/Documents").build();
      when(oneDrive.getItem("file-42")).thenReturn(Optional.of(item));

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a backup");

      verifyNoInteractions(shadowRestoreService, dataSource);
    }

    @Test
    void rejectsFilesOverTheSafetyCap() throws Exception {
      long oversize = 600L * 1024 * 1024;
      when(oneDrive.getItem("file-42")).thenReturn(Optional.of(backupItem(oversize)));
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(500L * 1024 * 1024);

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("safety cap");

      verify(oneDrive, never()).download(anyString());
      verify(shadowRestoreService, never()).stage(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void rejectsSubfolderInsideBackupsFolder() {
      OneDriveItem item =
          OneDriveItem.builder()
              .id("file-42")
              .parentPath("/drive/root:/bookie/backups/archive")
              .build();
      when(oneDrive.getItem("file-42")).thenReturn(Optional.of(item));

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IllegalArgumentException.class);

      verifyNoInteractions(shadowRestoreService, dataSource);
    }

    @Test
    void rejectsMissingParentReference() {
      OneDriveItem item = OneDriveItem.builder().id("file-42").build();
      when(oneDrive.getItem("file-42")).thenReturn(Optional.of(item));

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IllegalArgumentException.class);

      verifyNoInteractions(shadowRestoreService, dataSource);
    }

    @Test
    void throwsWhenItemNotFound() {
      when(oneDrive.getItem("file-42")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("not found");

      verifyNoInteractions(shadowRestoreService, dataSource);
    }

    @Test
    void throwsWhenDownloadStreamIsNull() throws Exception {
      when(oneDrive.getItem("file-42")).thenReturn(Optional.of(backupItem(1024L)));
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(2048L);
      when(oneDrive.download("file-42")).thenReturn(null);

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("Could not download");

      verify(shadowRestoreService, never()).stage(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void closesTheDownloadWhenShadowPreflightFails() throws Exception {
      CloseTrackingInputStream stream = new CloseTrackingInputStream("backup".getBytes());
      when(oneDrive.getItem("file-42")).thenReturn(Optional.of(backupItem(6L)));
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);
      when(oneDrive.download("file-42")).thenReturn(stream);
      doThrow(new IOException("pending restore"))
          .when(shadowRestoreService)
          .stage(anyString(), anyString(), anyLong(), any());

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("pending restore");

      assertThat(stream.closed).isTrue();
    }

    @Test
    void returnsCurrentRestoreStatus() throws Exception {
      when(shadowRestoreService.currentJournal())
          .thenReturn(Optional.of(journal(RestoreState.POST_START_VALIDATED)));

      BackupService.RestoreResult result = service.restoreStatus();

      assertThat(result.state()).isEqualTo(RestoreState.POST_START_VALIDATED);
      assertThat(result.restored()).isTrue();
      assertThat(result.restartRequired()).isFalse();
    }

    @Test
    void reportsThatRollbackRequiresAnotherRestart() throws Exception {
      when(shadowRestoreService.currentJournal())
          .thenReturn(Optional.of(journal(RestoreState.ROLLBACK_REQUIRED)));

      BackupService.RestoreResult result = service.restoreStatus();

      assertThat(result.restored()).isFalse();
      assertThat(result.validated()).isFalse();
      assertThat(result.restartRequired()).isTrue();
    }

    @Test
    void returnsIdleWhenNoRestoreExists() throws Exception {
      when(shadowRestoreService.currentJournal()).thenReturn(Optional.empty());

      BackupService.RestoreResult result = service.restoreStatus();

      assertThat(result.state()).isEqualTo(RestoreState.IDLE);
      assertThat(result.validated()).isFalse();
    }
  }

  @Nested
  class ScheduledBackup {

    @Test
    void skipsWhenOutlookNotConnected() {
      when(msalTokenService.isConnected()).thenReturn(false);

      service.scheduledBackup();

      verifyNoInteractions(dataSource, oneDrive, shadowRestoreService);
    }

    @Test
    void swallowsBackupFailureSoSchedulerKeepsRunning() throws Exception {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("connection refused"));

      service.scheduledBackup();

      verify(oneDrive, never()).upload(anyString(), any());
      verifyNoInteractions(shadowRestoreService);
    }
  }

  @Nested
  class Delegations {

    @Test
    void listBackupsMapsDriveItemsToBackupFile() {
      OneDriveItem item =
          OneDriveItem.builder().id("id-1").name("bookie-x.sql").size(2048L).build();
      when(oneDrive.listChildren("bookie/backups")).thenReturn(java.util.List.of(item));

      var result = service.listBackups();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).id()).isEqualTo("id-1");
      assertThat(result.get(0).name()).isEqualTo("bookie-x.sql");
      assertThat(result.get(0).size()).isEqualTo(2048L);
    }

    @Test
    void deleteForwardsToOneDrive() {
      service.delete("file-99");
      verify(oneDrive).delete("file-99");
    }
  }

  private RestoreJournal journal(RestoreState state) {
    String now = Instant.now().toString();
    return new RestoreJournal(
        RestoreJournal.CURRENT_VERSION,
        "restore-123",
        state,
        "file-42",
        "backup.sql",
        100,
        "source-hash",
        "/tmp/bookie",
        "/tmp/bookie/bookiedb.mv.db",
        "/tmp/bookie/shadow.mv.db",
        "/tmp/bookie/rollback.mv.db",
        "/tmp/bookie/failed.mv.db",
        "/tmp/bookie/manifest.json",
        "/tmp/bookie/audit.json",
        "live-hash",
        "shadow-hash",
        "manifest-hash",
        now,
        now,
        "test",
        "journal-hash");
  }

  private static final class CloseTrackingInputStream extends ByteArrayInputStream {
    private boolean closed;

    private CloseTrackingInputStream(byte[] bytes) {
      super(bytes);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
