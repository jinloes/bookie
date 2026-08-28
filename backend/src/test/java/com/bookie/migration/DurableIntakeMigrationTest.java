package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DurableIntakeMigrationTest {

  @Nested
  class Backfill {

    @Test
    void copiesEveryPendingFieldAndQueuesInterruptedWorkWithoutChangingLegacyRows()
        throws Exception {
      String url = databaseUrl("durable_intake");
      migrateTo(url, "13");

      List<List<String>> pendingExpensesBefore;
      List<List<String>> pendingIncomesBefore;
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        long activityId =
            queryLong(
                statement,
                "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
        long expenseCategoryId =
            queryLong(
                statement,
                "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'");
        long incomeCategoryId =
            queryLong(
                statement,
                "SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");

        statement.executeUpdate(
            """
            INSERT INTO pending_expenses (
                amount,
                category,
                created_at,
                date,
                description,
                error_message,
                payer_name,
                property_name,
                source_id,
                source_type,
                status,
                subject,
                email_type,
                activity_id,
                category_id,
                configured_activity_id,
                classification_ambiguous
            )
            VALUES (
                42.25,
                'UTILITIES',
                TIMESTAMP '2026-08-20 12:30:00',
                DATE '2026-08-19',
                'Synthetic pending expense',
                NULL,
                'Synthetic payer proposal',
                'Synthetic property proposal',
                'legacy-outlook-001',
                'OUTLOOK_EMAIL',
                'PROCESSING',
                'Synthetic email subject',
                'EXPENSE',
                %d,
                %d,
                %d,
                TRUE
            )
            """
                .formatted(activityId, expenseCategoryId, activityId));
        long pendingExpenseId =
            queryLong(
                statement,
                "SELECT id FROM pending_expenses WHERE source_id = 'legacy-outlook-001'");
        statement.executeUpdate(
            """
            INSERT INTO pending_expense_aliases (pending_expense_id, alias)
            VALUES (%d, 'Synthetic unresolved alias')
            """
                .formatted(pendingExpenseId));

        statement.executeUpdate(
            """
            INSERT INTO pending_incomes (
                source_id,
                source_type,
                status,
                source,
                amount,
                description,
                date,
                receipt_one_drive_id,
                receipt_file_name,
                error_message,
                created_at,
                activity_id,
                category_id,
                classification_ambiguous
            )
            VALUES (
                'venmo-row-001',
                'VENMO',
                'READY',
                'Synthetic source',
                150.75,
                'Synthetic pending income',
                DATE '2026-08-18',
                'receipt-drive-001',
                'synthetic.pdf',
                'Retained warning',
                TIMESTAMP '2026-08-20 11:15:00',
                %d,
                %d,
                FALSE
            )
            """
                .formatted(activityId, incomeCategoryId));
        statement.executeUpdate(
            """
            INSERT INTO receipt_hashes (drive_item_id, sha256, uploaded_at)
            VALUES (
                'receipt-drive-001',
                'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                TIMESTAMP '2026-08-20 11:00:00'
            )
            """);

        pendingExpensesBefore = rows(statement, "SELECT * FROM pending_expenses ORDER BY id");
        pendingIncomesBefore = rows(statement, "SELECT * FROM pending_incomes ORDER BY id");
      }

      Flyway.configure().dataSource(url, "sa", "").load().migrate();

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(rows(statement, "SELECT * FROM pending_expenses ORDER BY id"))
            .isEqualTo(pendingExpensesBefore);
        assertThat(rows(statement, "SELECT * FROM pending_incomes ORDER BY id"))
            .isEqualTo(pendingIncomesBefore);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM inbox_items")).isEqualTo(2);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM legacy_inbox_map")).isEqualTo(2);

        assertThat(
                rows(
                    statement,
                    """
                    SELECT
                        origin,
                        legacy_source_id,
                        state,
                        proposed_direction,
                        proposed_amount,
                        proposed_description,
                        proposed_category,
                        proposed_property_name,
                        proposed_counterparty_name,
                        configured_activity_id,
                        classification_ambiguous
                    FROM inbox_items
                    WHERE legacy_source_id = 'legacy-outlook-001'
                    """))
            .containsExactly(
                List.of(
                    "OUTLOOK_EMAIL",
                    "legacy-outlook-001",
                    "PROCESSING",
                    "EXPENSE",
                    "42.25",
                    "Synthetic pending expense",
                    "UTILITIES",
                    "Synthetic property proposal",
                    "Synthetic payer proposal",
                    String.valueOf(
                        queryLong(
                            statement,
                            "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'")),
                    "true"));

        assertThat(
                rows(
                    statement,
                    """
                    SELECT type, text_value, external_id, file_name, sha256
                    FROM inbox_artifacts
                    ORDER BY type
                    """))
            .containsExactly(
                List.of(
                    "RECEIPT",
                    "null",
                    "receipt-drive-001",
                    "synthetic.pdf",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                List.of(
                    "UNRECOGNIZED_ALIAS", "Synthetic unresolved alias", "null", "null", "null"));

        assertThat(
                rows(
                    statement,
                    """
                    SELECT type, state, attempts, max_attempts
                    FROM background_jobs
                    ORDER BY type
                    """))
            .containsExactly(
                List.of("PARSE_OUTLOOK", "AVAILABLE", "0", "5"),
                List.of("TRANSLATE_OUTLOOK_ID", "AVAILABLE", "0", "5"));
      }
    }
  }

  @Nested
  class SafetyChecks {

    @Test
    void rejectsDuplicateIdentityAcrossLegacyPendingTables() throws Exception {
      String url = databaseUrl("durable_intake_duplicate");
      migrateTo(url, "13");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        long activityId =
            queryLong(
                statement,
                "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
        long expenseCategoryId =
            queryLong(
                statement,
                "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'");
        long incomeCategoryId =
            queryLong(
                statement,
                "SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");
        statement.executeUpdate(
            """
            INSERT INTO pending_expenses (
                source_id, source_type, status, created_at, activity_id, category_id
            )
            VALUES ('duplicate-pending', 'OUTLOOK_EMAIL', 'READY', CURRENT_TIMESTAMP, %d, %d)
            """
                .formatted(activityId, expenseCategoryId));
        statement.executeUpdate(
            """
            INSERT INTO pending_incomes (
                source_id, source_type, status, created_at, activity_id, category_id
            )
            VALUES ('duplicate-pending', 'OUTLOOK_EMAIL', 'READY', CURRENT_TIMESTAMP, %d, %d)
            """
                .formatted(activityId, incomeCategoryId));
      }

      assertThatThrownBy(() -> Flyway.configure().dataSource(url, "sa", "").load().migrate())
          .isInstanceOf(FlywayException.class);
    }
  }

  private void migrateTo(String url, String version) {
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion(version))
        .load()
        .migrate();
  }

  private long queryLong(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private List<List<String>> rows(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      ResultSetMetaData metadata = result.getMetaData();
      List<List<String>> rows = new ArrayList<>();
      while (result.next()) {
        List<String> row = new ArrayList<>();
        for (int column = 1; column <= metadata.getColumnCount(); column++) {
          row.add(String.valueOf(result.getObject(column)));
        }
        rows.add(List.copyOf(row));
      }
      return List.copyOf(rows);
    }
  }

  private String databaseUrl(String prefix) {
    return "jdbc:h2:mem:"
        + prefix
        + "_"
        + UUID.randomUUID().toString().replace("-", "")
        + ";DB_CLOSE_DELAY=-1";
  }
}
