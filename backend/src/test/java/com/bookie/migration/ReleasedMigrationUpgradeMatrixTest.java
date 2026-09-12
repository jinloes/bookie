package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.jobrunr.storage.sql.h2.H2StorageProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class ReleasedMigrationUpgradeMatrixTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14"})
  void upgradesEveryReleasedSchemaVersionToTheCurrentSchema(String startingVersion)
      throws Exception {
    String url =
        "jdbc:h2:mem:released_upgrade_"
            + startingVersion
            + "_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion(startingVersion))
        .load()
        .migrate();

    Flyway.configure().dataSource(url, "sa", "").load().migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT "version"
                FROM "flyway_schema_history"
                WHERE "success" = TRUE AND "version" IS NOT NULL
                ORDER BY "installed_rank" DESC
                FETCH FIRST 1 ROW ONLY
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).isEqualTo("15");
    }
  }

  @Test
  void upgradingV14PreservesEveryHistoricalCellIncludingLegacyLeases() {
    JdbcDataSource source = dataSource("history");
    Flyway.configure().dataSource(source).target("14").load().migrate();
    JdbcTemplate jdbc = new JdbcTemplate(source);
    jdbc.update(
        """
          INSERT INTO inbox_items(id, origin, state, external_sync_state, raw_status,
              classification_ambiguous, created_at, updated_at, version)
          VALUES (101, 'RECEIPT', 'SAVED', 'PENDING', 'READY', FALSE,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 3)
          """);
    jdbc.update(
        """
          INSERT INTO background_jobs(inbox_item_id, type, idempotency_key, state, attempts,
              max_attempts, available_at, lease_owner, lease_expires_at, last_error,
              target_year, created_at, updated_at, version)
          VALUES (101, 'MOVE_RECEIPT', 'historical-move', 'LEASED', 2, 5,
                  CURRENT_TIMESTAMP, 'legacy-worker', CURRENT_TIMESTAMP, 'old failure',
                  2025, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 7)
          """);
    var verifier = new com.bookie.datalifecycle.migration.MigrationIntegrityVerifier();
    var before = verifier.capture(source, "test", "fixture");
    Flyway.configure().dataSource(source).load().migrate();
    verifier.reconcile(before, verifier.capture(source, "test", "fixture"));
    assertThat(
            jdbc.queryForMap(
                """
          SELECT execution_id, execution_attempt_base, execution_previous_max_attempts,
                 execution_started FROM background_jobs WHERE idempotency_key = 'historical-move'
          """))
        .containsEntry("EXECUTION_ID", null)
        .containsEntry("EXECUTION_ATTEMPT_BASE", 0)
        .containsEntry("EXECUTION_PREVIOUS_MAX_ATTEMPTS", null)
        .containsEntry("EXECUTION_STARTED", false);
  }

  @Test
  void flywayVendorSchemaMatchesPinnedProviderCreatedH2Schema() {
    JdbcDataSource migrated = dataSource("migrated");
    JdbcDataSource vendor = dataSource("vendor");
    Flyway.configure().dataSource(migrated).load().migrate();
    try (H2StorageProvider ignored = new H2StorageProvider(vendor)) {
      assertThat(columns(migrated)).isEqualTo(columns(vendor));
      assertThat(indexes(migrated)).isEqualTo(indexes(vendor));
      JdbcTemplate migratedJdbc = new JdbcTemplate(migrated);
      JdbcTemplate vendorJdbc = new JdbcTemplate(vendor);
      for (JdbcTemplate jdbc : List.of(migratedJdbc, vendorJdbc)) {
        for (String state :
            List.of("ENQUEUED", "SCHEDULED", "PROCESSING", "FAILED", "SUCCEEDED", "DELETED")) {
          jdbc.update(
              """
                INSERT INTO jobrunr_jobs(id, version, jobAsJson, jobSignature, state, createdAt, updatedAt)
                VALUES (?, 0, '{}', 'fixture', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
              UUID.randomUUID().toString(),
              state);
        }
      }
      assertThat(migratedJdbc.queryForMap("SELECT * FROM jobrunr_jobs_stats"))
          .isEqualTo(vendorJdbc.queryForMap("SELECT * FROM jobrunr_jobs_stats"));
      assertThat(
              migratedJdbc.queryForObject(
                  "SELECT `value` FROM jobrunr_metadata WHERE id = 'succeeded-jobs-counter-cluster'",
                  String.class))
          .isEqualTo(
              vendorJdbc.queryForObject(
                  "SELECT `value` FROM jobrunr_metadata WHERE id = 'succeeded-jobs-counter-cluster'",
                  String.class));
    }
  }

  private JdbcDataSource dataSource(String prefix) {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:" + prefix + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    return dataSource;
  }

  private List<Map<String, Object>> columns(JdbcDataSource source) {
    return new JdbcTemplate(source)
        .queryForList(
            """
          SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, DATA_TYPE, IS_NULLABLE,
                 CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, DATETIME_PRECISION,
                 COLUMN_DEFAULT
          FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME LIKE 'JOBRUNR_%'
          ORDER BY TABLE_NAME, ORDINAL_POSITION
          """);
  }

  private List<Map<String, Object>> indexes(JdbcDataSource source) {
    return new JdbcTemplate(source)
        .queryForList(
            """
          SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, ORDERING_SPECIFICATION, IS_UNIQUE
          FROM INFORMATION_SCHEMA.INDEX_COLUMNS
          WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME LIKE 'JOBRUNR_%'
          ORDER BY TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION
          """);
  }
}
