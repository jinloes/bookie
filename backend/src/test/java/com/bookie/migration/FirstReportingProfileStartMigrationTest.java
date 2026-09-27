package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FirstReportingProfileStartMigrationTest {

  @Nested
  class UpgradeFromV22 {

    @Test
    void extendsOnlyEachActivitysEarliestAssignmentBackToTheStartOfHistory() throws Exception {
      String url = databaseUrl();
      Flyway.configure()
          .dataSource(url, "sa", "")
          .target(MigrationVersion.fromVersion("22"))
          .load()
          .migrate();
      long createdLater;
      long reclassified;
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        createdLater = insertActivity(statement, "Created later", "SCHEDULE_C");
        insertAssignment(statement, createdLater, "SCHEDULE_C", "2026-09-01", null);
        reclassified = insertActivity(statement, "Reclassified", "SCHEDULE_C");
        insertAssignment(statement, reclassified, "W2", "2026-02-01", "2026-05-31");
        insertAssignment(statement, reclassified, "SCHEDULE_C", "2026-06-01", null);
      }

      Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("23").load();
      MigrateResult result = flyway.migrate();

      assertThat(result.migrationsExecuted).isEqualTo(1);
      flyway.validate();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(assignments(statement, createdLater))
            .containsExactly(List.of("SCHEDULE_C", "0001-01-01", "null"));
        assertThat(assignments(statement, reclassified))
            .containsExactly(
                List.of("W2", "0001-01-01", "2026-05-31"),
                List.of("SCHEDULE_C", "2026-06-01", "null"));
        assertThat(
                queryLong(
                    statement,
                    """
                    SELECT COUNT(*)
                    FROM activity_reporting_profile_assignments assignment
                    JOIN financial_activities activity ON activity.id = assignment.activity_id
                    WHERE activity.system_key = 'NEEDS_CLASSIFICATION'
                      AND assignment.effective_from = DATE '0001-01-01'
                      AND assignment.effective_to IS NULL
                    """))
            .isEqualTo(1);
      }
    }
  }

  private long insertActivity(Statement statement, String name, String taxTreatment)
      throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO financial_activities (name, activity_type, tax_treatment, owner_id, active)
        VALUES ('%s', 'SELF_EMPLOYMENT', '%s', (SELECT MIN(id) FROM household_members), TRUE)
        """
            .formatted(name, taxTreatment),
        Statement.RETURN_GENERATED_KEYS);
    try (ResultSet keys = statement.getGeneratedKeys()) {
      assertThat(keys.next()).isTrue();
      return keys.getLong(1);
    }
  }

  private void insertAssignment(
      Statement statement, long activityId, String profileKey, String from, String to)
      throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO activity_reporting_profile_assignments (
            activity_id, reporting_profile_id, effective_from, effective_to
        )
        VALUES (
            %d,
            (SELECT id FROM reporting_profiles WHERE profile_key = '%s'),
            DATE '%s',
            %s
        )
        """
            .formatted(activityId, profileKey, from, to == null ? "NULL" : "DATE '" + to + "'"));
  }

  private List<List<String>> assignments(Statement statement, long activityId) throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            """
            SELECT profile.profile_key, assignment.effective_from, assignment.effective_to
            FROM activity_reporting_profile_assignments assignment
            JOIN reporting_profiles profile ON profile.id = assignment.reporting_profile_id
            WHERE assignment.activity_id = %d
            ORDER BY assignment.effective_from
            """
                .formatted(activityId))) {
      List<List<String>> rows = new ArrayList<>();
      while (result.next()) {
        rows.add(
            List.of(result.getString(1), result.getString(2), String.valueOf(result.getString(3))));
      }
      return List.copyOf(rows);
    }
  }

  private long queryLong(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private String databaseUrl() {
    return "jdbc:h2:mem:first_reporting_profile_"
        + UUID.randomUUID().toString().replace("-", "")
        + ";DB_CLOSE_DELAY=-1";
  }
}
