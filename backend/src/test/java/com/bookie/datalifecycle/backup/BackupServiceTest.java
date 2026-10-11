package com.bookie.datalifecycle.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.datalifecycle.backup.BackupService.BackupCategory;
import com.bookie.datalifecycle.backup.BackupService.BackupFile;
import com.bookie.datalifecycle.backup.BackupService.RestoreResult;
import com.bookie.datalifecycle.portable.BackupEncryption;
import com.bookie.datalifecycle.portable.PortableBackupArchive;
import com.bookie.datalifecycle.portable.PortableBackupExporter;
import com.bookie.datalifecycle.portable.PortableBackupImporter;
import com.bookie.datalifecycle.restore.RestoreJournal;
import com.bookie.datalifecycle.restore.RestoreState;
import com.bookie.datalifecycle.restore.ShadowRestoreService;
import com.bookie.integrations.onedrive.OneDriveItem;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.integrations.outlook.OutlookAuthorization;
import com.bookie.util.BackgroundWorkControl;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-22T19:00:00Z");
  private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(7);

  @TempDir Path dataDirectory;

  @Mock private DataSource mockDataSource;
  @Mock private OutlookAuthorization outlook;
  @Mock private ShadowRestoreService shadowRestoreService;
  @Mock private PortableBackupExporter exporter;
  @Mock private PortableBackupImporter importer;
  @Mock private PortableBackupArchive archiveFormat;
  @Mock private BackupEncryption encryption;
  @Mock private BackgroundWorkControl backgroundWork;

  private final MutableClock clock = new MutableClock(NOW);
  private final FakeOneDrive oneDrive = new FakeOneDrive();
  private WriteFreeze writeFreeze;

  @BeforeEach
  void setUp() {
    writeFreeze = new WriteFreeze(dataDirectory);
  }

  private BackupService service(String vendor) {
    return service(vendor, mockDataSource);
  }

  private BackupService service(String vendor, DataSource dataSource) {
    return service(vendor, dataSource, dataDirectory);
  }

  private BackupService service(String vendor, DataSource dataSource, Path directory) {
    return BackupService.builder()
        .clock(clock)
        .dataSource(dataSource)
        .oneDrive(oneDrive)
        .outlookAuthorization(outlook)
        .shadowRestoreService(shadowRestoreService)
        .exporter(exporter)
        .importer(importer)
        .archiveFormat(archiveFormat)
        .encryption(encryption)
        .writeFreeze(writeFreeze)
        .backgroundWork(backgroundWork)
        .vendor(vendor)
        .dataDirectory(directory)
        .drainTimeout(DRAIN_TIMEOUT)
        .build();
  }

  private DataSource h2() throws SQLException {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:file:" + dataDirectory.resolve("db").toAbsolutePath());
    dataSource.setUser("sa");
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE IF NOT EXISTS marker (note VARCHAR(20))");
      statement.execute("INSERT INTO marker VALUES ('h2-script-row')");
    }
    return dataSource;
  }

  private void keyConfirmed() throws IOException {
    when(encryption.isKeyConfirmed()).thenReturn(true);
  }

  private void connected() {
    when(outlook.isConnected()).thenReturn(true);
  }

  /** The fake cipher prefixes the plaintext so tests can tell encrypted bytes apart. */
  private void encryptionPrefixes() throws IOException {
    doAnswer(
            invocation -> {
              Path source = invocation.getArgument(0);
              Path target = invocation.getArgument(1);
              byte[] plain = Files.readAllBytes(source);
              byte[] sealed = new byte[plain.length + 4];
              System.arraycopy("ENC:".getBytes(StandardCharsets.UTF_8), 0, sealed, 0, 4);
              System.arraycopy(plain, 0, sealed, 4, plain.length);
              Files.write(target, sealed);
              return null;
            })
        .when(encryption)
        .encrypt(any(), any());
  }

  private void exporterWrites(String content) throws Exception {
    doAnswer(
            invocation -> {
              Files.writeString(invocation.getArgument(0), content);
              return null;
            })
        .when(exporter)
        .export(any());
  }

  private void portableBackupsWork() throws Exception {
    keyConfirmed();
    encryptionPrefixes();
    exporterWrites("archive");
  }

  private void drains(boolean idle) {
    when(backgroundWork.drainUntilIdle(DRAIN_TIMEOUT)).thenReturn(idle);
  }

  private static ResponseStatusException conflict(Throwable thrown) {
    assertThat(thrown).isInstanceOf(ResponseStatusException.class);
    ResponseStatusException exception = (ResponseStatusException) thrown;
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    return exception;
  }

  @Nested
  class BackupFilename {

    @Test
    void usesTheInjectedClockForTheTimestamp() {
      assertThat(service("h2").backupFilename()).isEqualTo("bookie-2026-09-22_19-00-00.sql");
    }
  }

  @Nested
  class ScheduledBackup {

    @Test
    void doesNothingInSqlServerMode() {
      service("sqlserver").scheduledBackup();

      verifyNoInteractions(outlook, mockDataSource);
      assertThat(oneDrive.items).isEmpty();
    }

    @Test
    void skipsWhenOutlookIsNotConnected() {
      service("h2").scheduledBackup();

      verifyNoInteractions(mockDataSource);
      assertThat(oneDrive.items).isEmpty();
    }

    @Test
    void uploadsAnH2ScriptAtTheUnchangedTwoAmSchedule() throws Exception {
      connected();

      service("h2", h2()).scheduledBackup();

      assertThat(oneDrive.items.values())
          .singleElement()
          .satisfies(
              file -> {
                assertThat(file.item().name()).isEqualTo("bookie-2026-09-22_19-00-00.sql");
                assertThat(file.item().parentPath()).isEqualTo("/drive/root:/bookie/backups");
                assertThat(new String(file.content(), StandardCharsets.UTF_8))
                    .contains("h2-script-row");
              });
      assertThat(
              BackupService.class
                  .getMethod("scheduledBackup")
                  .getAnnotation(Scheduled.class)
                  .cron())
          .isEqualTo("0 0 2 * * *");
    }

    @Test
    void swallowsFailuresSoTheSchedulerKeepsRunning() throws Exception {
      connected();
      when(mockDataSource.getConnection()).thenThrow(new SQLException("connection refused"));

      service("h2").scheduledBackup();

      assertThat(oneDrive.items).isEmpty();
    }
  }

  @Nested
  class Backup {

    @Test
    void writesAVerifiedEncryptedPortableArchiveInSqlServerMode() throws Exception {
      portableBackupsWork();

      BackupFile file = service("sqlserver").backup();

      assertThat(file.name()).isEqualTo("bookie-2026-09-22_19-00-00.bookiebak");
      assertThat(file.category()).isEqualTo(BackupCategory.PORTABLE);
      assertThat(oneDrive.content(file.id())).isEqualTo("ENC:archive");
      verify(archiveFormat).read(any());
      verifyNoInteractions(mockDataSource);
    }

    @Test
    void refusesWithoutAConfirmedKey() throws Exception {
      Throwable thrown = catchThrowable(service("sqlserver")::backup);

      assertThat(conflict(thrown).getReason()).contains("confirm it on the Backup page");
      verifyNoInteractions(exporter);
    }

    @Test
    void reportsAFailedExport() throws Exception {
      keyConfirmed();
      when(exporter.export(any())).thenThrow(new SQLException("snapshot"));

      assertThatThrownBy(service("sqlserver")::backup)
          .isInstanceOf(IOException.class)
          .hasMessage("Portable export failed");
      assertThat(oneDrive.items).isEmpty();
    }

    @Test
    void deletesAnUploadWhoseReadBackDiffers() throws Exception {
      portableBackupsWork();
      oneDrive.corruptDownloads = true;

      assertThatThrownBy(service("sqlserver")::backup)
          .isInstanceOf(IOException.class)
          .hasMessageStartingWith("Uploaded backup failed read-back verification");
      assertThat(oneDrive.items).isEmpty();
    }

    @Test
    void treatsAMissingReadBackAsAMismatchEvenIfTheDeleteFails() throws Exception {
      portableBackupsWork();
      oneDrive.nullDownloads = true;
      oneDrive.failDeletes = true;

      assertThatThrownBy(service("sqlserver")::backup)
          .isInstanceOf(IOException.class)
          .hasMessageStartingWith("Uploaded backup failed read-back verification");
    }

    @Test
    void uploadsAnH2ScriptInH2Mode() throws Exception {
      BackupFile file = service("h2", h2()).backup();

      assertThat(file.category()).isEqualTo(BackupCategory.LEGACY);
      assertThat(oneDrive.content(file.id())).contains("h2-script-row");
      verifyNoInteractions(encryption, exporter);
    }

    @Test
    void reportsAnH2ScriptFailure() throws Exception {
      when(mockDataSource.getConnection()).thenThrow(new SQLException("closed"));

      assertThatThrownBy(service("h2")::backup)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageStartingWith("Failed to write database backup to");
    }
  }

  @Nested
  class Retention {

    @Test
    void keepsTheNewestThirtyPortableBackupsAndNeverPrunesOtherFiles() throws Exception {
      IntStream.range(0, 31)
          .forEach(
              day ->
                  oneDrive.put(
                      "bookie/backups",
                      "bookie-day-%02d.bookiebak".formatted(day),
                      "2026-08-%02dT00:00:00Z".formatted(day + 1)));
      oneDrive.put("bookie/backups", "bookie-unparseable.bookiebak", "not-a-date");
      oneDrive.put("bookie/backups", "bookie-undated.bookiebak", null);
      oneDrive.put("bookie/backups", "bookie-legacy.sql", "2020-01-01T00:00:00Z");
      oneDrive.putFolder("bookie/backups", "pre-migration");
      oneDrive.put("bookie/backups/pre-migration", "old.zip.bookiebak", "2020-01-01T00:00:00Z");
      oneDrive.put("bookie/backups/rollback", "old.bookiebak", "2020-01-01T00:00:00Z");

      service("sqlserver").pruneDailyBackups();

      assertThat(oneDrive.names())
          .doesNotContain(
              "bookie-day-00.bookiebak", "bookie-unparseable.bookiebak", "bookie-undated.bookiebak")
          .contains(
              "bookie-day-01.bookiebak",
              "bookie-day-30.bookiebak",
              "bookie-legacy.sql",
              "pre-migration",
              "old.zip.bookiebak",
              "old.bookiebak");
      assertThat(oneDrive.names().stream().filter(name -> name.startsWith("bookie-day-")))
          .hasSize(30);
    }

    @Test
    void prunesAfterEachOnDemandPortableBackup() throws Exception {
      portableBackupsWork();
      IntStream.range(0, 30)
          .forEach(
              day ->
                  oneDrive.put(
                      "bookie/backups",
                      "bookie-day-%02d.bookiebak".formatted(day),
                      "2026-08-%02dT00:00:00Z".formatted(day + 1)));

      service("sqlserver").backup();

      assertThat(oneDrive.names()).doesNotContain("bookie-day-00.bookiebak").hasSize(30);
    }
  }

  @Nested
  class StartupBackup {

    @Test
    void doesNothingInH2Mode() {
      service("h2").startupBackup();

      verifyNoInteractions(encryption, outlook);
    }

    @Test
    void alwaysTakesAPortableBackupInSqlServerMode() throws Exception {
      portableBackupsWork();
      connected();

      service("sqlserver").startupBackup();

      assertThat(oneDrive.names()).containsExactly("bookie-2026-09-22_19-00-00.bookiebak");
    }

    @Test
    void skipsUntilTheKeyIsConfirmed() throws Exception {
      service("sqlserver").startupBackup();

      verifyNoInteractions(outlook, exporter);
    }

    @Test
    void skipsWhenOutlookIsNotConnected() throws Exception {
      keyConfirmed();

      service("sqlserver").startupBackup();

      verifyNoInteractions(exporter);
    }

    @Test
    void swallowsFailures() throws Exception {
      keyConfirmed();
      connected();
      when(exporter.export(any())).thenThrow(new SQLException("asleep"));

      service("sqlserver").startupBackup();

      assertThat(oneDrive.items).isEmpty();
    }
  }

  @Nested
  class HourlyCheck {

    @Test
    void doesNothingInH2Mode() {
      service("h2").hourlyCheck();

      verifyNoInteractions(backgroundWork, encryption);
    }

    @Test
    void backsUpWithoutDrainingWhenNoBackupWasTakenYet() throws Exception {
      portableBackupsWork();
      connected();

      service("sqlserver").hourlyCheck();

      verifyNoInteractions(backgroundWork);
      assertThat(oneDrive.items).hasSize(1);
    }

    @Test
    void doesNotRecheckOutlookWithinADayOfASkippedBackup() throws Exception {
      keyConfirmed();
      BackupService service = service("sqlserver");
      service.startupBackup();
      clock.advance(Duration.ofHours(23));

      service.hourlyCheck();

      verify(outlook, times(1)).isConnected();
      verifyNoInteractions(exporter);
    }

    @Test
    void doesNotRetryAFailedExportWithinADay() throws Exception {
      keyConfirmed();
      connected();
      when(exporter.export(any())).thenThrow(new SQLException("snapshot"));
      BackupService service = service("sqlserver");
      service.startupBackup();
      clock.advance(Duration.ofHours(1));

      service.hourlyCheck();

      verify(exporter, times(1)).export(any());
    }

    @Test
    void retriesAFailedExportOnceTheAttemptIsOverADayOld() throws Exception {
      keyConfirmed();
      connected();
      when(exporter.export(any())).thenThrow(new SQLException("snapshot"));
      BackupService service = service("sqlserver");
      service.startupBackup();
      clock.advance(Duration.ofHours(24).plusSeconds(1));

      service.hourlyCheck();

      verify(exporter, times(2)).export(any());
    }

    @Test
    void backsUpAtTheNextCheckOnceTheKeyIsConfirmed() throws Exception {
      when(encryption.isKeyConfirmed()).thenReturn(false, true);
      encryptionPrefixes();
      exporterWrites("archive");
      connected();
      BackupService service = service("sqlserver");
      service.startupBackup();
      clock.advance(Duration.ofHours(1));

      service.hourlyCheck();

      assertThat(oneDrive.items).hasSize(1);
    }

    @Test
    void checksStalenessWithoutTouchingTheDatabaseOrOneDrive() throws Exception {
      portableBackupsWork();
      connected();
      BackupService service = service("sqlserver");
      service.startupBackup();
      oneDrive.calls = 0;
      clock.advance(Duration.ofHours(23));

      service.hourlyCheck();

      assertThat(oneDrive.calls).isZero();
      verifyNoInteractions(mockDataSource);
      verify(exporter).export(any());
    }

    @Test
    void backsUpAgainOnceTheNewestBackupIsOverADayOld() throws Exception {
      portableBackupsWork();
      connected();
      BackupService service = service("sqlserver");
      service.startupBackup();
      clock.advance(Duration.ofHours(24).plusSeconds(1));

      service.hourlyCheck();

      assertThat(oneDrive.names())
          .containsExactlyInAnyOrder(
              "bookie-2026-09-22_19-00-00.bookiebak", "bookie-2026-09-23_19-00-01.bookiebak");
    }
  }

  @Nested
  class Listing {

    @Test
    void listsRootPreMigrationAndRollbackFilesWithTheirCategories() {
      oneDrive.put("bookie/backups", "bookie-a.bookiebak", "2026-09-01T00:00:00Z");
      oneDrive.put("bookie/backups", "bookie-b.sql", "2026-09-01T00:00:00Z");
      oneDrive.putFolder("bookie/backups", "rollback");
      oneDrive.putFolder("bookie/backups/pre-migration", "nested");
      oneDrive.putFolder("bookie/backups/rollback", "nested");
      oneDrive.put("bookie/backups/pre-migration", "bookie-c.zip.bookiebak", null);
      oneDrive.put("bookie/backups/rollback", "bookie-rollback-d.bookiebak", null);

      List<BackupFile> files = service("sqlserver").listBackups();

      assertThat(files)
          .extracting(BackupFile::name, BackupFile::category)
          .containsExactly(
              tuple("bookie-a.bookiebak", BackupCategory.PORTABLE),
              tuple("bookie-b.sql", BackupCategory.LEGACY),
              tuple("bookie-c.zip.bookiebak", BackupCategory.PRE_MIGRATION),
              tuple("bookie-rollback-d.bookiebak", BackupCategory.ROLLBACK));
      assertThat(files.get(2).lastModified()).isEmpty();
    }

    @Test
    void defaultsMissingFieldsToEmptyText() {
      BackupFile file = BackupFile.from(OneDriveItem.builder().size(3).build());

      assertThat(file).isEqualTo(new BackupFile("", "", 3, "", BackupCategory.LEGACY));
    }

    @Test
    void deleteForwardsToOneDrive() {
      oneDrive.put("bookie/backups", "bookie-a.bookiebak", null);
      String id = oneDrive.items.keySet().iterator().next();

      service("h2").delete(id);

      assertThat(oneDrive.items).isEmpty();
    }
  }

  @Nested
  class KeyAndStatus {

    @Test
    void reportsVendorFreezeAndKeyState() throws Exception {
      keyConfirmed();
      writeFreeze.freeze();

      assertThat(service("sqlserver").status())
          .isEqualTo(new BackupService.BackupStatus("sqlserver", true, true));
    }

    @Test
    void revealsTheKeyUntilItIsConfirmed() throws Exception {
      when(encryption.revealKey()).thenReturn("secret-key");

      assertThat(service("h2").revealBackupKey()).isEqualTo("secret-key");
    }

    @Test
    void answersConflictOnceTheKeyWasConfirmed() throws Exception {
      when(encryption.revealKey()).thenThrow(new IllegalStateException("already confirmed"));

      Throwable thrown = catchThrowable(service("h2")::revealBackupKey);

      assertThat(conflict(thrown).getReason()).isEqualTo("already confirmed");
    }

    @Test
    void confirmsTheKey() throws Exception {
      service("h2").confirmBackupKey();

      verify(encryption).confirmKey();
    }

    @Test
    void liftsTheFreeze() {
      writeFreeze.freeze();

      service("h2").liftFreeze();

      assertThat(writeFreeze.isFrozen()).isFalse();
    }
  }

  @Nested
  class PreMigrationBackup {

    @Test
    void isOnlyTakenFromH2() {
      Throwable thrown = catchThrowable(service("sqlserver")::preMigrationBackup);

      assertThat(conflict(thrown).getReason()).contains("local H2 database");
      assertThat(writeFreeze.isFrozen()).isFalse();
    }

    @Test
    void requiresAConfirmedKey() {
      Throwable thrown = catchThrowable(service("h2")::preMigrationBackup);

      assertThat(conflict(thrown).getReason()).contains("backup key");
      assertThat(writeFreeze.isFrozen()).isFalse();
    }

    @Test
    void requiresOutlook() throws Exception {
      keyConfirmed();

      Throwable thrown = catchThrowable(service("h2")::preMigrationBackup);

      assertThat(conflict(thrown).getReason()).contains("Connect Outlook");
      assertThat(writeFreeze.isFrozen()).isFalse();
    }

    @Test
    void staysFrozenWhenIntakeWorkDoesNotDrain() throws Exception {
      keyConfirmed();
      connected();
      drains(false);

      Throwable thrown = catchThrowable(service("h2")::preMigrationBackup);

      assertThat(conflict(thrown).getReason()).contains("did not finish within PT7S");
      assertThat(writeFreeze.isFrozen()).isTrue();
      verifyNoInteractions(exporter);
    }

    @Test
    void freezesThenWritesVerifiedLocalAndEncryptedOneDriveCopies() throws Exception {
      portableBackupsWork();
      connected();
      drains(true);

      BackupService.PreMigrationResult result = service("h2", h2()).preMigrationBackup();

      Path local = dataDirectory.resolve("pre-migration").resolve("2026-09-22_19-00-00");
      assertThat(result.localDirectory()).isEqualTo(local.toString());
      assertThat(local.resolve("bookie-2026-09-22_19-00-00.zip")).hasContent("archive");
      assertThat(Files.readString(local.resolve("bookie-2026-09-22_19-00-00.sql")))
          .contains("h2-script-row");
      assertThat(local.resolve("bookie-h2-2026-09-22_19-00-00.zip")).isNotEmptyFile();
      assertThat(result.uploaded())
          .extracting(BackupFile::name, BackupFile::category)
          .containsExactly(
              tuple("bookie-2026-09-22_19-00-00.zip.bookiebak", BackupCategory.PRE_MIGRATION),
              tuple("bookie-2026-09-22_19-00-00.sql.bookiebak", BackupCategory.PRE_MIGRATION));
      assertThat(oneDrive.items.values())
          .allSatisfy(
              file ->
                  assertThat(file.item().parentPath())
                      .isEqualTo("/drive/root:/bookie/backups/pre-migration"));
      assertThat(oneDrive.content(result.uploaded().getFirst().id())).isEqualTo("ENC:archive");
      assertThat(writeFreeze.isFrozen()).isTrue();
    }

    @Test
    void failsWithoutUploadingAnythingWhenTheReadBackDiffers() throws Exception {
      portableBackupsWork();
      connected();
      drains(true);
      oneDrive.corruptDownloads = true;
      BackupService service = service("h2", h2());

      assertThatThrownBy(service::preMigrationBackup)
          .isInstanceOf(IOException.class)
          .hasMessageStartingWith("Uploaded backup failed read-back verification");
      assertThat(oneDrive.items).isEmpty();
    }

    @Test
    void refusesToSpliceAnUnsafePathIntoSql(@TempDir Path parent) throws Exception {
      Path unsafe = Files.createDirectories(parent.resolve("has space"));
      keyConfirmed();
      connected();
      exporterWrites("archive");
      drains(true);
      BackupService service = service("h2", mockDataSource, unsafe);

      assertThatThrownBy(service::preMigrationBackup)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageStartingWith("Refusing to write to unsafe path");
      verifyNoInteractions(mockDataSource);
    }
  }

  @Nested
  class RollbackBackup {

    @Test
    void isOnlyTakenFromSqlServer() {
      Throwable thrown = catchThrowable(service("h2")::rollbackBackup);

      assertThat(conflict(thrown).getReason()).contains("SQL Server database");
    }

    @Test
    void freezesDrainsAndWritesAVerifiedRollbackArchive() throws Exception {
      portableBackupsWork();
      connected();
      drains(true);

      BackupFile file = service("sqlserver").rollbackBackup();

      assertThat(file.name()).isEqualTo("bookie-rollback-2026-09-22_19-00-00.bookiebak");
      assertThat(file.category()).isEqualTo(BackupCategory.ROLLBACK);
      assertThat(oneDrive.items.get(file.id()).item().parentPath())
          .isEqualTo("/drive/root:/bookie/backups/rollback");
      verify(archiveFormat).read(any());
      assertThat(writeFreeze.isFrozen()).isTrue();
    }
  }

  @Nested
  class Restore {

    private String put(String folder, String name, long size) {
      return oneDrive.put(folder, name, null, size, "ENC:archive");
    }

    @Test
    void throwsWhenTheItemIsMissing() {
      assertThatThrownBy(() -> service("h2").restore("missing"))
          .isInstanceOf(IOException.class)
          .hasMessage("Backup not found: missing");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/drive/root:/Documents", "/drive/root:/bookie/backups/archive"})
    void rejectsItemsOutsideTheBackupFolders(String parentPath) {
      oneDrive.items.put(
          "file-1",
          new StoredFile(
              OneDriveItem.builder().id("file-1").name("x.sql").parentPath(parentPath).build(),
              new byte[0]));

      assertThatThrownBy(() -> service("h2").restore("file-1"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a backup");
      verifyNoInteractions(shadowRestoreService);
    }

    @Test
    void acceptsParentPathsWithoutTheDriveRootPrefix() throws Exception {
      oneDrive.items.put(
          "file-1",
          new StoredFile(
              OneDriveItem.builder()
                  .id("file-1")
                  .name("bookie-a.sql")
                  .size(6)
                  .parentPath("/drives/abc/items/bookie/backups")
                  .build(),
              "script".getBytes(StandardCharsets.UTF_8)));
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);
      when(shadowRestoreService.stage(eq("file-1"), eq("bookie-a.sql"), eq(6L), any()))
          .thenReturn(journal(RestoreState.VALIDATED));

      assertThat(service("h2").restore("file-1").state()).isEqualTo(RestoreState.VALIDATED);
    }

    @Test
    void rejectsItemsWithoutAParentPath() {
      oneDrive.items.put(
          "file-1", new StoredFile(OneDriveItem.builder().id("file-1").build(), new byte[0]));

      assertThatThrownBy(() -> service("h2").restore("file-1"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFilesOverTheSafetyCap() {
      String id = put("bookie/backups", "bookie-a.sql", 2048);
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);

      assertThatThrownBy(() -> service("h2").restore(id))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("safety cap");
    }

    @Test
    void rejectsEncryptedSqlCopies() {
      String id = put("bookie/backups/pre-migration", "bookie-a.sql.bookiebak", 10);
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);

      assertThatThrownBy(() -> service("sqlserver").restore(id))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Restore the matching portable archive instead");
      verifyNoInteractions(importer);
    }

    @Test
    void rejectsLegacyScriptsInSqlServerMode() {
      String id = put("bookie/backups", "bookie-a.sql", 10);
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);

      assertThatThrownBy(() -> service("sqlserver").restore(id))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("only be restored into the local H2 database");
    }

    @Test
    void stagesLegacyScriptsInAShadowH2Database() throws Exception {
      String id = oneDrive.put("bookie/backups", "bookie-a.sql", null, 6, "script");
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);
      when(shadowRestoreService.stage(eq(id), eq("bookie-a.sql"), eq(6L), any()))
          .thenReturn(journal(RestoreState.VALIDATED));

      RestoreResult result = service("h2").restore(id);

      assertThat(result.state()).isEqualTo(RestoreState.VALIDATED);
      assertThat(result.restartRequired()).isTrue();
      verifyNoInteractions(mockDataSource, importer);
    }

    @Test
    void reportsAMissingLegacyDownload() throws Exception {
      String id = put("bookie/backups", "bookie-a.sql", 10);
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);
      oneDrive.nullDownloads = true;

      assertThatThrownBy(() -> service("h2").restore(id))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("Could not download");
      verify(shadowRestoreService, never()).stage(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void closesTheLegacyDownloadWhenStagingFails() throws Exception {
      String id = put("bookie/backups", "bookie-a.sql", 10);
      when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);
      doThrow(new IOException("pending restore"))
          .when(shadowRestoreService)
          .stage(anyString(), anyString(), anyLong(), any());

      assertThatThrownBy(() -> service("h2").restore(id)).hasMessage("pending restore");
      assertThat(oneDrive.openStreams).allMatch(stream -> stream.closed);
    }

    @Nested
    class Portable {

      private String id;

      @BeforeEach
      void putBackup() {
        id = put("bookie/backups/rollback", "bookie-rollback-x.bookiebak", 10);
        when(shadowRestoreService.maxRestoreBytes()).thenReturn(1024L);
      }

      private void decryptionStrips() throws IOException {
        doAnswer(
                invocation -> {
                  byte[] sealed = Files.readAllBytes(invocation.<Path>getArgument(0));
                  Files.write(
                      invocation.<Path>getArgument(1),
                      Arrays.copyOfRange(sealed, 4, sealed.length));
                  return null;
                })
            .when(encryption)
            .decrypt(any(), any());
      }

      @Test
      void requiresAConfirmedKey() {
        Throwable thrown = catchThrowable(() -> service("sqlserver").restore(id));

        assertThat(conflict(thrown).getReason()).contains("backup key");
        assertThat(writeFreeze.isFrozen()).isFalse();
        verifyNoInteractions(importer);
      }

      @Test
      void reportsAMissingKeyBeforeChangingAnything() throws Exception {
        doThrow(new IllegalStateException("backup key missing/mismatched: no key in Keychain"))
            .when(encryption)
            .decrypt(any(), any());

        assertThatThrownBy(() -> service("sqlserver").restore(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageStartingWith("backup key missing/mismatched");
        assertThat(writeFreeze.isFrozen()).isFalse();
        assertThat(oneDrive.names()).containsExactly("bookie-rollback-x.bookiebak");
        verifyNoInteractions(importer, backgroundWork);
      }

      @Test
      void reportsAMissingDownload() throws Exception {
        oneDrive.nullDownloads = true;

        assertThatThrownBy(() -> service("sqlserver").restore(id))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Could not download");
      }

      @Test
      void takesASafetyBackupThenReplacesTheDataAndLiftsATemporaryFreeze() throws Exception {
        portableBackupsWork();
        decryptionStrips();
        drains(true);
        List<Path> imported = new ArrayList<>();
        doAnswer(
                invocation -> {
                  Path archive = invocation.getArgument(0);
                  assertThat(archive).hasContent("archive");
                  assertThat(writeFreeze.isFrozen()).isTrue();
                  imported.add(archive);
                  return null;
                })
            .when(importer)
            .importArchive(any());

        RestoreResult result = service("sqlserver").restore(id);

        assertThat(result.restored()).isTrue();
        assertThat(result.restartRequired()).isFalse();
        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().getParent()).doesNotExist();
        assertThat(oneDrive.names()).contains("bookie-safety-2026-09-22_19-00-00.bookiebak");
        assertThat(writeFreeze.isFrozen()).isFalse();
      }

      @Test
      void commitsTheCutoverByLiftingTheMigrationFreeze() throws Exception {
        portableBackupsWork();
        decryptionStrips();
        drains(true);
        writeFreeze.freeze();

        service("sqlserver").restore(id);

        verify(importer).importArchive(any());
        assertThat(writeFreeze.isFrozen()).isFalse();
      }

      @Test
      void keepsTheMigrationFreezeWhenTheImportFails() throws Exception {
        portableBackupsWork();
        decryptionStrips();
        drains(true);
        writeFreeze.freeze();
        when(importer.importArchive(any())).thenThrow(new SQLException("mismatch"));

        assertThatThrownBy(() -> service("sqlserver").restore(id))
            .isInstanceOf(IOException.class)
            .hasMessage("Portable restore failed; the database is unchanged");
        assertThat(writeFreeze.isFrozen()).isTrue();
      }

      @Test
      void liftsATemporaryFreezeWhenTheDrainTimesOut() throws Exception {
        keyConfirmed();
        decryptionStrips();
        drains(false);

        Throwable thrown = catchThrowable(() -> service("sqlserver").restore(id));

        assertThat(conflict(thrown).getReason()).contains("did not finish");
        assertThat(writeFreeze.isFrozen()).isFalse();
        verifyNoInteractions(importer);
      }
    }
  }

  @Nested
  class RestoreStatus {

    @ParameterizedTest
    @CsvSource({
      "VALIDATED, false, true, true",
      "LIVE_RETAINED, false, true, true",
      "SHADOW_ACTIVATED, false, true, false",
      "POST_START_VALIDATED, true, true, false",
      "ROLLBACK_REQUIRED, false, false, true",
      "ROLLED_BACK, false, false, false",
      "FAILED, false, false, false"
    })
    void describesTheCurrentJournal(
        RestoreState state, boolean restored, boolean validated, boolean restartRequired)
        throws Exception {
      when(shadowRestoreService.currentJournal()).thenReturn(Optional.of(journal(state)));

      RestoreResult result = service("h2").restoreStatus();

      assertThat(result.state()).isEqualTo(state);
      assertThat(result.restored()).isEqualTo(restored);
      assertThat(result.validated()).isEqualTo(validated);
      assertThat(result.restartRequired()).isEqualTo(restartRequired);
    }

    @Test
    void isIdleWithoutAJournal() throws Exception {
      when(shadowRestoreService.currentJournal()).thenReturn(Optional.empty());

      assertThat(service("h2").restoreStatus().state()).isEqualTo(RestoreState.IDLE);
    }
  }

  private static RestoreJournal journal(RestoreState state) {
    String at = NOW.toString();
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
        at,
        at,
        "test",
        "journal-hash");
  }

  private record StoredFile(OneDriveItem item, byte[] content) {}

  private static final class TrackedStream extends ByteArrayInputStream {
    private boolean closed;

    private TrackedStream(byte[] bytes) {
      super(bytes);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }

  private final class FakeOneDrive implements OneDrivePort {

    private final Map<String, StoredFile> items = new LinkedHashMap<>();
    private final List<TrackedStream> openStreams = new ArrayList<>();
    private int nextId;
    private int calls;
    private boolean corruptDownloads;
    private boolean nullDownloads;
    private boolean failDeletes;

    String put(String folder, String name, String lastModified) {
      return put(folder, name, lastModified, 1, "x");
    }

    String put(String folder, String name, String lastModified, long size, String content) {
      String id = "id-" + nextId++;
      items.put(
          id,
          new StoredFile(
              OneDriveItem.builder()
                  .id(id)
                  .name(name)
                  .size(size)
                  .lastModified(lastModified)
                  .parentPath("/drive/root:/" + folder)
                  .build(),
              content.getBytes(StandardCharsets.UTF_8)));
      return id;
    }

    void putFolder(String parent, String name) {
      String id = "id-" + nextId++;
      items.put(
          id,
          new StoredFile(
              OneDriveItem.builder()
                  .id(id)
                  .name(name)
                  .folder(true)
                  .parentPath("/drive/root:/" + parent)
                  .build(),
              new byte[0]));
    }

    List<String> names() {
      return items.values().stream().map(file -> file.item().name()).toList();
    }

    String content(String id) {
      return new String(items.get(id).content(), StandardCharsets.UTF_8);
    }

    @Override
    public OneDriveItem upload(String path, InputStream content) {
      calls++;
      int slash = path.lastIndexOf('/');
      byte[] bytes;
      try {
        bytes = content.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      String id =
          put(
              path.substring(0, slash),
              path.substring(slash + 1),
              clock.instant().toString(),
              bytes.length,
              new String(bytes, StandardCharsets.UTF_8));
      return items.get(id).item();
    }

    @Override
    public List<OneDriveItem> listChildren(String folderPath) {
      calls++;
      return items.values().stream()
          .map(StoredFile::item)
          .filter(item -> item.parentPath().equals("/drive/root:/" + folderPath))
          .toList();
    }

    @Override
    public List<OneDriveItem> listChildrenById(String itemId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<OneDriveItem> getItem(String fileId) {
      calls++;
      return Optional.ofNullable(items.get(fileId)).map(StoredFile::item);
    }

    @Override
    public InputStream download(String fileId) {
      calls++;
      if (nullDownloads) {
        return null;
      }
      byte[] bytes = items.get(fileId).content();
      if (corruptDownloads) {
        bytes = "tampered".getBytes(StandardCharsets.UTF_8);
      }
      TrackedStream stream = new TrackedStream(bytes);
      openStreams.add(stream);
      return stream;
    }

    @Override
    public void delete(String fileId) {
      calls++;
      if (failDeletes) {
        throw new IllegalStateException("offline");
      }
      items.remove(fileId);
    }

    @Override
    public String ensureFolder(String parentPath, String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void move(String itemId, String destinationFolderId) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
