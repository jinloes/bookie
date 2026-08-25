package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class ActivityAwareIntakeMigrationTest {

  @Test
  void migratesSyntheticV8IntakeRowsWithoutInferringClassification() throws Exception {
    String url =
        "jdbc:h2:mem:intake_migration_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("8"))
        .load()
        .migrate();

    long activityId;
    long categoryId;
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO financial_activities
              (name, activity_type, tax_treatment, owner_id, active)
          VALUES (
              'Teaching — Synthetic District',
              'EMPLOYMENT',
              'W2',
              (SELECT MIN(id) FROM household_members),
              TRUE
          )
          """);
      activityId =
          queryLong(
              statement,
              """
              SELECT id FROM financial_activities
              WHERE name = 'Teaching — Synthetic District'
              """);
      categoryId =
          queryLong(statement, "SELECT id FROM financial_categories WHERE category_key = 'WAGES'");
      statement.executeUpdate(
          """
          INSERT INTO outlook_settings (id, auto_move_enabled)
          VALUES (1, FALSE)
          """);
      statement.executeUpdate(
          """
          INSERT INTO outlook_settings_folder (settings_id, folder_id, expand_subfolders)
          VALUES (1, 'synthetic-folder', FALSE)
          """);
      statement.executeUpdate(
          """
          INSERT INTO pending_incomes
              (status, source, amount, description, date, activity_id, category_id, created_at)
          VALUES (
              'READY',
              'Synthetic District',
              100.00,
              'Synthetic deposit',
              DATE '2026-08-15',
              %d,
              %d,
              CURRENT_TIMESTAMP
          )
          """
              .formatted(activityId, categoryId));
    }

    Flyway.configure().dataSource(url, "sa", "").load().migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*) FROM outlook_settings_folder
                  WHERE folder_id = 'synthetic-folder' AND activity_id IS NULL
                  """))
          .isEqualTo(1);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*) FROM pending_incomes
                  WHERE classification_ambiguous = TRUE
                  """))
          .isEqualTo(1);
      statement.executeUpdate(
          """
          INSERT INTO email_keyword_classification_history
              (keyword, activity_id, financial_category_id, occurrences)
          VALUES ('pay-demo-001', %d, %d, 1)
          """
              .formatted(activityId, categoryId));
      assertThat(queryLong(statement, "SELECT COUNT(*) FROM email_keyword_classification_history"))
          .isEqualTo(1);
    }
  }

  private long queryLong(Statement statement, String sql) throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }
}
