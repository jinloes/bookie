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
      var manifest = new MigrationIntegrityManifestCodec().read(Path.of(journal.manifestFile()));
      assertThat(manifest.tableCounts())
          .containsEntry("JOBRUNR_JOBS", 6L)
          .containsEntry("BACKGROUND_JOBS", 6L);
      String shadowUrl = "jdbc:h2:file:" + journal.shadowFile().replace(".mv.db", "");
      try (Connection connection = DriverManager.getConnection(shadowUrl, "sa", "");
          Statement statement = connection.createStatement();
          var rows =
              statement.executeQuery("SELECT state, COUNT(*) FROM jobrunr_jobs GROUP BY state")) {
        java.util.Set<String> states = new java.util.HashSet<>();
        while (rows.next()) {
          states.add(rows.getString(1));
          assertThat(rows.getInt(2)).isEqualTo(1);
        }
        assertThat(states)
            .containsExactlyInAnyOrder(
                "ENQUEUED", "SCHEDULED", "PROCESSING", "FAILED", "SUCCEEDED", "DELETED");
      }
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"6", "14"})
    void reconcilesEveryLegacyCellWhileUpgradingAnOlderBackup(String version) throws Exception {
      Path legacyScript = directory.resolve("legacy-backup.sql");
      createLegacyDatabaseAndScript(directory.resolve("legacy-source"), legacyScript, version);
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
      assertThat(manifest.schemaVersion()).isEqualTo("15");
      assertThat(manifest.tableCounts()).containsEntry("INCOMES", 1L);
      if ("14".equals(version)) {
        assertThat(manifest.tableCounts())
            .containsEntry("BACKGROUND_JOBS", 1L)
            .containsEntry("JOBRUNR_JOBS", 0L);
        String shadowUrl = "jdbc:h2:file:" + journal.shadowFile().replace(".mv.db", "");
        try (Connection connection = DriverManager.getConnection(shadowUrl, "sa", "");
            Statement statement = connection.createStatement();
            var job =
                statement.executeQuery(
                    "SELECT * FROM background_jobs WHERE idempotency_key='legacy-history'")) {
          assertThat(job.next()).isTrue();
          assertThat(job.getInt("attempts")).isEqualTo(2);
          assertThat(job.getInt("max_attempts")).isEqualTo(5);
          assertThat(job.getString("lease_owner")).isEqualTo("legacy-executor");
          assertThat(job.getObject("execution_id")).isNull();
          assertThat(job.getBoolean("execution_started")).isFalse();
        }
      }
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
    seedEngineAndBindings(url);
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

  private void createLegacyDatabaseAndScript(Path databaseBase, Path script, String version)
      throws Exception {
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
      if ("14".equals(version)) {
        Flyway.configure().dataSource(url, "sa", "").target("14").load().migrate();
        statement.executeUpdate(
            """
            INSERT INTO inbox_items(id,origin,state,external_sync_state,raw_status,
                classification_ambiguous,created_at,updated_at,version)
            VALUES (901,'RECEIPT','SAVED','PENDING','READY',FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,2)
            """);
        statement.executeUpdate(
            """
            INSERT INTO background_jobs(inbox_item_id,type,idempotency_key,state,attempts,max_attempts,
                available_at,lease_owner,lease_expires_at,created_at,updated_at,version)
            VALUES (901,'MOVE_RECEIPT','legacy-history','LEASED',2,5,CURRENT_TIMESTAMP,
                'legacy-executor',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,3)
            """);
      }
      String safeScript = script.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
      statement.execute("SCRIPT TO '" + safeScript + "'");
      statement.execute("SHUTDOWN");
    }
  }

  private void seedEngineAndBindings(String url) {
    org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
    source.setURL(url);
    source.setUser("sa");
    var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
    try (var raw =
        new org.jobrunr.storage.sql.h2.H2StorageProvider(
            source, org.jobrunr.storage.StorageProviderUtils.DatabaseOptions.SKIP_CREATE)) {
      raw.setJobMapper(
          new org.jobrunr.jobs.mappers.JobMapper(
              new org.jobrunr.utils.mapper.jackson.JacksonJsonMapper()));
      int index = 100;
      for (var state :
          java.util.List.of(
              org.jobrunr.jobs.states.StateName.ENQUEUED,
              org.jobrunr.jobs.states.StateName.SCHEDULED,
              org.jobrunr.jobs.states.StateName.PROCESSING,
              org.jobrunr.jobs.states.StateName.FAILED,
              org.jobrunr.jobs.states.StateName.SUCCEEDED,
              org.jobrunr.jobs.states.StateName.DELETED)) {
        var details =
            new org.jobrunr.jobs.JobDetails(
                com.bookie.intake.application.DurableBackgroundJobWorker.class.getName(),
                null,
                "executeV1",
                java.util.List.of(
                    new org.jobrunr.jobs.JobParameter(String.class, "MOVE_OUTLOOK"),
                    org.jobrunr.jobs.JobParameter.JobContext));
        var job = new org.jobrunr.jobs.Job(details);
        switch (state) {
          case PROCESSING, SUCCEEDED -> {
            job =
                new org.jobrunr.jobs.Job(
                    java.util.UUID.randomUUID(),
                    0,
                    details,
                    java.util.List.of(
                        new org.jobrunr.jobs.states.EnqueuedState(
                            java.time.Instant.now().minusSeconds(130)),
                        new org.jobrunr.jobs.states.ProcessingState(
                            java.util.UUID.randomUUID(),
                            "fixture",
                            java.time.Instant.now().minusSeconds(120))),
                    new java.util.concurrent.ConcurrentHashMap<>());
            if (state == org.jobrunr.jobs.states.StateName.SUCCEEDED) {
              job.succeeded();
            }
          }
          case FAILED, SCHEDULED -> {
            job.failed("preserved failure", new IllegalStateException("fixture"));
            if (state == org.jobrunr.jobs.states.StateName.SCHEDULED) {
              job.scheduleAt(java.time.Instant.now().plusSeconds(59049), "native retry fixture");
            }
          }
          case DELETED -> job.delete("preserved deletion");
          default -> {}
        }
        raw.save(job);
        jdbc.update(
            """
              INSERT INTO inbox_items(id, origin, state, external_sync_state, raw_status,
                  classification_ambiguous, created_at, updated_at, version)
              VALUES (?, 'OUTLOOK_EMAIL', 'SAVED', 'PENDING', 'READY', FALSE,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 2)
              """,
            index);
        jdbc.update(
            """
              INSERT INTO background_jobs(inbox_item_id, type, idempotency_key, state, attempts,
                  max_attempts, available_at, created_at, updated_at, version,
                  execution_id, execution_attempt_base, execution_previous_max_attempts, execution_started)
              VALUES (?, 'MOVE_OUTLOOK', ?, 'AVAILABLE', 3, 13, CURRENT_TIMESTAMP,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 4, ?, 2, 5, ?)
              """,
            index,
            "restore-" + index,
            job.getId(),
            state != org.jobrunr.jobs.states.StateName.ENQUEUED);
        index++;
      }
    }
  }
}
