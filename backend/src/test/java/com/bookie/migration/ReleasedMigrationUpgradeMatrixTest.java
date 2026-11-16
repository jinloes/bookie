package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class ReleasedMigrationUpgradeMatrixTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
        "18", "19"
      })
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
      assertThat(result.getString(1)).isEqualTo("25");
    }
  }

  @Test
  void upgradingV14ReleasesMidFlightLeasesAndRetiresLegacyCategoryAndJobRunrTables() {
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
                SELECT state, attempts, max_attempts, lease_owner, lease_expires_at
                FROM background_jobs WHERE idempotency_key = 'historical-move'
                """))
        .containsEntry("STATE", "AVAILABLE")
        .containsEntry("ATTEMPTS", 2)
        .containsEntry("MAX_ATTEMPTS", 5)
        .containsEntry("LEASE_OWNER", null)
        .containsEntry("LEASE_EXPIRES_AT", null);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC'
                  AND (TABLE_NAME LIKE 'JOBRUNR_%'
                       OR TABLE_NAME IN ('FINANCIAL_CATEGORIES', 'LEGACY_CATEGORY_MAP'))
                """,
                Integer.class))
        .isZero();
  }

  private JdbcDataSource dataSource(String prefix) {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:" + prefix + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    return dataSource;
  }
}
