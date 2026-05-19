package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.ItemReference;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

  @Mock private DataSource dataSource;
  @Mock private OneDriveClient oneDrive;
  @Mock private MsalTokenService msalTokenService;
  @Mock private Flyway flyway;

  @Mock private Connection connection;
  @Mock private Statement statement;

  @InjectMocks private BackupService service;

  /** Wires the DataSource → Connection → Statement chain that SCRIPT TO / DROP ALL OBJECTS use. */
  private void stubJdbc() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
  }

  private static DriveItem backupItem(long size) {
    DriveItem item = new DriveItem();
    item.setId("file-42");
    item.setName("bookie-2026-04-22_10-00-00.sql");
    item.setSize(size);
    ItemReference parent = new ItemReference();
    parent.setPath("/drive/root:/bookie/backups");
    item.setParentReference(parent);
    return item;
  }

  @Nested
  class Restore {

    @Test
    void runsSnapshotThenDropThenFlywayMigrateInOrder() throws Exception {
      stubJdbc();
      when(oneDrive.getItem("file-42")).thenReturn(backupItem(1024L));
      when(oneDrive.download("file-42"))
          .thenReturn(new ByteArrayInputStream("CREATE TABLE t(id INT);".getBytes()));

      service.restore("file-42");

      // SCRIPT TO (snapshot) happens before DROP ALL OBJECTS, and Flyway runs after both.
      // RUNSCRIPT itself uses H2's Connection-based API so it's not on the Statement mock.
      InOrder order = inOrder(statement, flyway);
      order.verify(statement).execute(contains("SCRIPT TO"));
      order.verify(statement).execute("DROP ALL OBJECTS");
      order.verify(flyway).migrate();
    }

    @Test
    void rejectsFileIdOutsideBackupsFolder() {
      DriveItem item = new DriveItem();
      ItemReference parent = new ItemReference();
      parent.setPath("/drive/root:/Documents");
      item.setParentReference(parent);
      when(oneDrive.getItem("file-42")).thenReturn(item);

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a backup");

      verifyNoInteractions(flyway, dataSource);
    }

    @Test
    void rejectsFilesOverTheSafetyCap() {
      long oversize = 600L * 1024 * 1024;
      when(oneDrive.getItem("file-42")).thenReturn(backupItem(oversize));

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("safety cap");

      verifyNoInteractions(flyway, dataSource);
    }

    @Test
    void rejectsSubfolderInsideBackupsFolder() {
      DriveItem item = new DriveItem();
      ItemReference parent = new ItemReference();
      parent.setPath("/drive/root:/bookie/backups/archive");
      item.setParentReference(parent);
      when(oneDrive.getItem("file-42")).thenReturn(item);

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IllegalArgumentException.class);

      verifyNoInteractions(flyway, dataSource);
    }

    @Test
    void rejectsMissingParentReference() {
      DriveItem item = new DriveItem();
      item.setParentReference(null);
      when(oneDrive.getItem("file-42")).thenReturn(item);

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IllegalArgumentException.class);

      verifyNoInteractions(flyway, dataSource);
    }

    @Test
    void throwsWhenItemNotFound() {
      when(oneDrive.getItem("file-42")).thenReturn(null);

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("not found");

      verifyNoInteractions(flyway, dataSource);
    }

    @Test
    void throwsWhenDownloadStreamIsNull() {
      when(oneDrive.getItem("file-42")).thenReturn(backupItem(1024L));
      when(oneDrive.download("file-42")).thenReturn(null);

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("Could not download");

      verifyNoInteractions(flyway, dataSource);
    }

    @Test
    void throwsWhenBackupFileIsEmpty() {
      when(oneDrive.getItem("file-42")).thenReturn(backupItem(1024L));
      when(oneDrive.download("file-42")).thenReturn(new ByteArrayInputStream(new byte[0]));

      assertThatThrownBy(() -> service.restore("file-42"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("empty");

      verifyNoInteractions(flyway, dataSource);
    }
  }

  @Nested
  class ScheduledBackup {

    @Test
    void skipsWhenOutlookNotConnected() {
      when(msalTokenService.isConnected()).thenReturn(false);

      service.scheduledBackup();

      verifyNoInteractions(dataSource, oneDrive, flyway);
    }

    @Test
    void swallowsBackupFailureSoSchedulerKeepsRunning() throws Exception {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

      // Failure inside backup() is logged and swallowed — must not propagate to the scheduler.
      service.scheduledBackup();

      verifyNoInteractions(flyway);
      verify(oneDrive, never()).upload(anyString(), any());
    }
  }

  @Nested
  class Delegations {

    @Test
    void listBackupsMapsDriveItemsToBackupFile() {
      DriveItem item = new DriveItem();
      item.setId("id-1");
      item.setName("bookie-x.sql");
      item.setSize(2048L);
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
}
