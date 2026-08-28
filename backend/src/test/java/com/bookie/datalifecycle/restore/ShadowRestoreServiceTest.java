package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShadowRestoreServiceTest {

  private static final long MAX_BYTES = 20L * 1024 * 1024;

  @TempDir Path directory;

  private RestorePaths paths;
  private RestoreJournalStore journalStore;
  private ShadowRestoreService service;
  private byte[] validBackup;
  private String originalLiveHash;

  @BeforeEach
  void setUp() throws Exception {
    paths = new RestorePaths(directory);
    journalStore = new RestoreJournalStore(paths);
    Path backupScript = directory.resolve("fixture-backup.sql");
    createDatabaseAndScript(directory.resolve("bookiedb"), backupScript, false);
    validBackup = Files.readAllBytes(backupScript);
    originalLiveHash = MigrationIntegrityManifestCodec.sha256(paths.liveDatabaseFile());
    service =
        new ShadowRestoreService(
            new MigrationIntegrityVerifier(),
            paths,
            journalStore,
            new RestoreDiskSpaceChecker(),
            MAX_BYTES,
            0,
            "test");
  }

  @Nested
  class Stage {

    @Test
    void restoresMigratesAndValidatesShadowWithoutChangingLive() throws Exception {
      RestoreJournal journal =
          service.stage(
              "file-1", "backup.sql", validBackup.length, new ByteArrayInputStream(validBackup));

      assertThat(journal.state()).isEqualTo(RestoreState.VALIDATED);
      assertThat(journal.sourceChecksum()).hasSize(64);
      assertThat(journal.liveDatabaseHash()).isEqualTo(originalLiveHash);
      assertThat(MigrationIntegrityManifestCodec.sha256(paths.liveDatabaseFile()))
          .isEqualTo(originalLiveHash);
      assertThat(Path.of(journal.shadowFile())).isRegularFile();
      assertThat(Path.of(journal.manifestFile())).isRegularFile();
      assertThat(journalStore.read()).contains(journal);
    }

    @Test
    void rejectsAnotherRestoreWhileAValidatedCandidateIsPending() throws Exception {
      RestoreJournal first =
          service.stage(
              "file-1", "backup.sql", validBackup.length, new ByteArrayInputStream(validBackup));

      assertThatThrownBy(
              () ->
                  service.stage(
                      "file-2",
                      "newer.sql",
                      validBackup.length,
                      new ByteArrayInputStream(validBackup)))
          .isInstanceOf(IOException.class)
          .hasMessageContaining(first.restoreId())
          .hasMessageContaining("already");
      assertThat(journalStore.read()).contains(first);
      assertThat(paths.liveDatabaseFile()).isRegularFile();
      assertThat(hash(paths.liveDatabaseFile())).isEqualTo(originalLiveHash);
    }

    @Test
    void reconcilesEveryLegacyCellWhileUpgradingAnOlderBackup() throws Exception {
      Path legacyScript = directory.resolve("legacy-backup.sql");
      createLegacyDatabaseAndScript(directory.resolve("legacy-source"), legacyScript);
      byte[] legacyBackup = Files.readAllBytes(legacyScript);

      RestoreJournal journal =
          service.stage(
              "legacy-file",
              "legacy.sql",
              legacyBackup.length,
              new ByteArrayInputStream(legacyBackup));

      var manifest =
          new com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec()
              .read(Path.of(journal.manifestFile()));
      assertThat(manifest.schemaVersion()).isEqualTo("14");
      assertThat(manifest.tableCounts()).containsEntry("INCOMES", 1L);
      assertThat(hash(paths.liveDatabaseFile())).isEqualTo(originalLiveHash);
    }

    @Test
    void rejectsCorruptSqlWithoutChangingLive() {
      assertPreSwapFailure(new ByteArrayInputStream("not valid SQL".getBytes()), 13, "could not");
    }

    @Test
    void rejectsEmptyBackupWithoutChangingLive() {
      assertPreSwapFailure(new ByteArrayInputStream(new byte[0]), 0, "empty");
    }

    @Test
    void rejectsDeclaredOversizeWithoutReadingTheBackup() {
      assertPreSwapFailure(new ByteArrayInputStream(validBackup), MAX_BYTES + 1, "safety cap");
    }

    @Test
    void enforcesTheCapWhenTheRemoteSizeWasUnknown() {
      ShadowRestoreService tinyService =
          new ShadowRestoreService(
              new MigrationIntegrityVerifier(),
              paths,
              journalStore,
              new RestoreDiskSpaceChecker(),
              10,
              0,
              "test");

      assertThatThrownBy(
              () ->
                  tinyService.stage(
                      "file-1",
                      "backup.sql",
                      0,
                      new ByteArrayInputStream("01234567890".getBytes())))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("safety cap");
      assertLiveUnchanged();
    }

    @Test
    void rejectsInsufficientDiskWithoutChangingLive() throws Exception {
      RestoreDiskSpaceChecker checker = mock(RestoreDiskSpaceChecker.class);
      doThrow(new IOException("synthetic disk exhaustion"))
          .when(checker)
          .requireAvailable(any(Path.class), anyLong());
      ShadowRestoreService diskLimitedService =
          new ShadowRestoreService(
              new MigrationIntegrityVerifier(), paths, journalStore, checker, MAX_BYTES, 0, "test");

      assertThatThrownBy(
              () ->
                  diskLimitedService.stage(
                      "file-1",
                      "backup.sql",
                      validBackup.length,
                      new ByteArrayInputStream(validBackup)))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("disk exhaustion");
      assertLiveUnchanged();
    }

    @Test
    void rejectsAnIncompatibleUnversionedSchemaWithoutChangingLive() {
      byte[] incompatible = "CREATE TABLE unrelated(id BIGINT);".getBytes();

      assertPreSwapFailure(new ByteArrayInputStream(incompatible), incompatible.length, "Flyway");
    }

    @Test
    void rejectsANewerFlywaySchemaWithoutChangingLive() throws Exception {
      Path futureScript = directory.resolve("future-backup.sql");
      createDatabaseAndScript(directory.resolve("future-source"), futureScript, true);
      byte[] futureBackup = Files.readAllBytes(futureScript);

      assertPreSwapFailure(
          new ByteArrayInputStream(futureBackup), futureBackup.length, "newer than supported");
    }
  }

  private void assertPreSwapFailure(
      ByteArrayInputStream backup, long declaredSize, String expectedMessage) {
    assertThatThrownBy(() -> service.stage("file-1", "backup.sql", declaredSize, backup))
        .isInstanceOf(IOException.class)
        .hasMessageContaining(expectedMessage);
    assertLiveUnchanged();
  }

  private void assertLiveUnchanged() {
    assertThat(paths.liveDatabaseFile()).isRegularFile();
    assertThat(hash(paths.liveDatabaseFile())).isEqualTo(originalLiveHash);
    assertThat(journalStoreRead()).isEmpty();
  }

  private String hash(Path path) {
    try {
      return MigrationIntegrityManifestCodec.sha256(path);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private java.util.Optional<RestoreJournal> journalStoreRead() {
    try {
      return journalStore.read();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private void createDatabaseAndScript(Path databaseBase, Path script, boolean addFutureMigration)
      throws Exception {
    String url = "jdbc:h2:file:" + databaseBase.toAbsolutePath().toString().replace("\\", "/");
    Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO incomes (
              amount, date, description, source, source_id, source_type, activity_id, category_id
          )
          VALUES (
              123.45,
              DATE '2026-08-24',
              'Synthetic backup row',
              'Fixture',
              'fixture-source',
              'MANUAL',
              (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
              (SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME')
          )
          """);
      if (addFutureMigration) {
        statement.executeUpdate(
            """
            INSERT INTO "flyway_schema_history" (
                "installed_rank",
                "version",
                "description",
                "type",
                "script",
                "checksum",
                "installed_by",
                "installed_on",
                "execution_time",
                "success"
            )
            VALUES (
                99,
                '99',
                'future schema',
                'SQL',
                'V99__future_schema.sql',
                1,
                'SA',
                CURRENT_TIMESTAMP,
                1,
                TRUE
            )
            """);
      }
      String safeScript = script.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
      statement.execute("SCRIPT TO '" + safeScript + "'");
      statement.execute("SHUTDOWN");
    }
  }

  private void createLegacyDatabaseAndScript(Path databaseBase, Path script) throws Exception {
    String url = "jdbc:h2:file:" + databaseBase.toAbsolutePath().toString().replace("\\", "/");
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("6"))
        .load()
        .migrate();
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO incomes (amount, date, description, source, source_id, source_type)
          VALUES (
              75.50,
              DATE '2025-12-31',
              'Preserved legacy income',
              'Fixture',
              'legacy-source',
              'MANUAL'
          )
          """);
      statement.executeUpdate(
          "INSERT INTO outlook_settings (id, auto_move_enabled) VALUES (1, FALSE)");
      statement.executeUpdate(
          """
          INSERT INTO outlook_settings_folder (settings_id, expand_subfolders, folder_id)
          VALUES (1, TRUE, 'legacy-folder')
          """);
      String safeScript = script.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
      statement.execute("SCRIPT TO '" + safeScript + "'");
      statement.execute("SHUTDOWN");
    }
  }
}
