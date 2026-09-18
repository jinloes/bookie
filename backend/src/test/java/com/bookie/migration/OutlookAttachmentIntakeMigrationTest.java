package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutlookAttachmentIntakeMigrationTest {

  @Nested
  class UpgradeFromReleasedV17 {

    @Test
    void validatesReleasedChecksumAndNormalizesOnlySeededOutlookArtifacts() throws Exception {
      String url = databaseUrl();
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
                source_id, source_type, status, subject, created_at, activity_id, category_id,
                classification_ambiguous
            )
            VALUES (
                'pending-message', 'OUTLOOK_EMAIL', 'PROCESSING', 'Legacy Outlook pending',
                CURRENT_TIMESTAMP, %d, %d, TRUE
            )
            """
                .formatted(activityId, expenseCategoryId));
        statement.executeUpdate(
            """
            INSERT INTO pending_expense_aliases (pending_expense_id, alias)
            SELECT id, 'preserved-alias'
            FROM pending_expenses
            WHERE source_id = 'pending-message'
            """);
        statement.executeUpdate(
            """
            INSERT INTO expenses (
                amount, category, date, description, source_id, source_type, activity_id, category_id
            )
            VALUES (
                15.25, 'OTHER', DATE '2026-08-20', 'Legacy Outlook expense',
                'expense-message', 'OUTLOOK_EMAIL', %d, %d
            )
            """
                .formatted(activityId, expenseCategoryId));
        statement.executeUpdate(
            """
            INSERT INTO incomes (
                amount, date, description, source, source_id, source_type, activity_id, category_id
            )
            VALUES (
                45.75, DATE '2026-08-20', 'Legacy Outlook income', 'Fixture',
                'income-message', 'OUTLOOK_EMAIL', %d, %d
            )
            """
                .formatted(activityId, incomeCategoryId));
      }

      migrateTo(url, "17");
      Flyway flyway = Flyway.configure().dataSource(url, "sa", "").load();
      assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("17");
      assertThat(flyway.info().current().getChecksum()).isEqualTo(939314588);

      List<List<String>> pendingBefore;
      List<List<String>> expensesBefore;
      List<List<String>> incomesBefore;
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        long inboxItemId =
            queryLong(
                statement,
                """
                SELECT item.id
                FROM inbox_items item
                JOIN pending_expenses pending
                  ON item.migration_legacy_table = 'PENDING_EXPENSES'
                 AND item.migration_legacy_id = pending.id
                WHERE pending.source_id = 'pending-message'
                """);
        statement.executeUpdate(
            """
            INSERT INTO inbox_artifacts (
                inbox_item_id, type, external_id, text_value, file_name
            )
            VALUES
                (%d, 'OUTLOOK_EMAIL', 'pending-message', 'attachment-1',
                 'receipt.pdf'),
                (%d, 'OUTLOOK_EMAIL', NULL, 'different-message', NULL),
                (%d, 'UNRECOGNIZED_ALIAS', NULL, 'pending-message', NULL)
            """
                .formatted(inboxItemId, inboxItemId, inboxItemId));
        pendingBefore =
            rows(
                statement,
                """
                SELECT id, source_id, source_type, status, subject, created_at, activity_id,
                       category_id, classification_ambiguous, outlook_message_id,
                       outlook_attachment_id, outlook_attachment_name
                FROM pending_expenses
                ORDER BY id
                """);
        expensesBefore =
            rows(
                statement,
                """
                SELECT id, amount, category, date, description, source_id, source_type,
                       activity_id, category_id, outlook_message_id
                FROM expenses
                ORDER BY id
                """);
        incomesBefore =
            rows(
                statement,
                """
                SELECT id, amount, date, description, source, source_id, source_type,
                       activity_id, category_id, outlook_message_id
                FROM incomes
                ORDER BY id
                """);
      }

      MigrateResult result = flyway.migrate();

      assertThat(result.migrationsExecuted).isEqualTo(1);
      flyway.validate();
      assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("18");
      assertThat(flyway.migrate().migrationsExecuted).isZero();

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(
                rows(
                    statement,
                    """
                    SELECT id, source_id, source_type, status, subject, created_at, activity_id,
                           category_id, classification_ambiguous, outlook_message_id,
                           outlook_attachment_id, outlook_attachment_name
                    FROM pending_expenses
                    ORDER BY id
                    """))
            .isEqualTo(pendingBefore);
        assertThat(
                rows(
                    statement,
                    """
                    SELECT id, amount, category, date, description, source_id, source_type,
                           activity_id, category_id, outlook_message_id
                    FROM expenses
                    ORDER BY id
                    """))
            .isEqualTo(expensesBefore);
        assertThat(
                rows(
                    statement,
                    """
                    SELECT id, amount, date, description, source, source_id, source_type,
                           activity_id, category_id, outlook_message_id
                    FROM incomes
                    ORDER BY id
                    """))
            .isEqualTo(incomesBefore);
        assertThat(
                rows(
                    statement,
                    """
                    SELECT artifact.type, artifact.external_id, artifact.text_value,
                           artifact.file_name
                    FROM inbox_artifacts artifact
                    JOIN legacy_inbox_map legacy_map
                      ON legacy_map.inbox_item_id = artifact.inbox_item_id
                    JOIN pending_expenses pending ON pending.id = legacy_map.legacy_id
                    WHERE legacy_map.legacy_table = 'PENDING_EXPENSES'
                      AND pending.source_id = 'pending-message'
                    ORDER BY artifact.type, artifact.text_value NULLS FIRST
                    """))
            .containsExactly(
                List.of("OUTLOOK_EMAIL", "pending-message", "null", "null"),
                List.of("OUTLOOK_EMAIL", "pending-message", "attachment-1", "receipt.pdf"),
                List.of("OUTLOOK_EMAIL", "null", "different-message", "null"),
                List.of("UNRECOGNIZED_ALIAS", "null", "pending-message", "null"),
                List.of("UNRECOGNIZED_ALIAS", "null", "preserved-alias", "null"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT INDEX_NAME
                    FROM INFORMATION_SCHEMA.INDEXES
                    WHERE INDEX_NAME IN (
                        'IDX_PENDING_EXPENSES_OUTLOOK_MESSAGE',
                        'UK_PENDING_EXPENSES_OUTLOOK_ATTACHMENT',
                        'IDX_EXPENSES_OUTLOOK_MESSAGE',
                        'IDX_INCOMES_OUTLOOK_MESSAGE',
                        'IDX_INBOX_ARTIFACTS_OUTLOOK_PARENT'
                    )
                    ORDER BY INDEX_NAME
                    """))
            .containsExactly(
                List.of("IDX_EXPENSES_OUTLOOK_MESSAGE"),
                List.of("IDX_INBOX_ARTIFACTS_OUTLOOK_PARENT"),
                List.of("IDX_INCOMES_OUTLOOK_MESSAGE"),
                List.of("IDX_PENDING_EXPENSES_OUTLOOK_MESSAGE"),
                List.of("UK_PENDING_EXPENSES_OUTLOOK_ATTACHMENT"));
      }
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

  private String databaseUrl() {
    return "jdbc:h2:mem:outlook_attachment_"
        + UUID.randomUUID().toString().replace("-", "")
        + ";DB_CLOSE_DELAY=-1";
  }
}
