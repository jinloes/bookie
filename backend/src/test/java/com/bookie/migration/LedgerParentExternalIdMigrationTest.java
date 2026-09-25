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
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LedgerParentExternalIdMigrationTest {

  private static final String HASH = "0".repeat(64);

  @Nested
  class UpgradeFromReleasedV18 {

    @Test
    void backfillsParentOnlyForMappedOutlookRowsThatOwnAnImportReference() throws Exception {
      String url = databaseUrl();
      migrateTo(url, "18");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        Fixture fixture = fixture(statement);
        long outlookExpense =
            insertExpense(
                statement, fixture, "OUTLOOK_EMAIL", "message-a:attachment-1", "message-a");
        mapWithReference(
            statement,
            fixture,
            "EXPENSES",
            outlookExpense,
            "OUTLOOK_EMAIL",
            "message-a:attachment-1");
        long outlookIncome =
            insertIncome(
                statement, fixture, "OUTLOOK_EMAIL", "message-b:attachment-1", "message-b");
        mapWithReference(
            statement,
            fixture,
            "INCOMES",
            outlookIncome,
            "OUTLOOK_EMAIL",
            "message-b:attachment-1");
        long manualExpense = insertExpense(statement, fixture, "MANUAL", "manual-c", null);
        mapWithReference(statement, fixture, "EXPENSES", manualExpense, "MANUAL", "manual-c");
        // Legacy rows with no source fields map to a transaction without an import reference.
        long nullOriginExpense = insertExpense(statement, fixture, null, null, "message-d");
        map(statement, "EXPENSES", nullOriginExpense, insertTransaction(statement, fixture, true));
        long unmappedTransaction = insertTransaction(statement, fixture, true);
        insertReference(statement, unmappedTransaction, "OUTLOOK_EMAIL", "orphan:attachment-1");
      }

      Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("19").load();
      MigrateResult result = flyway.migrate();

      assertThat(result.migrationsExecuted).isEqualTo(1);
      flyway.validate();
      assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(
                rows(
                    statement,
                    """
                    SELECT origin, external_id, parent_external_id
                    FROM transaction_import_references
                    ORDER BY external_id
                    """))
            .containsExactly(
                List.of("MANUAL", "manual-c", "null"),
                List.of("OUTLOOK_EMAIL", "message-a:attachment-1", "message-a"),
                List.of("OUTLOOK_EMAIL", "message-b:attachment-1", "message-b"),
                List.of("OUTLOOK_EMAIL", "orphan:attachment-1", "null"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT COUNT(*)
                    FROM transaction_import_references import_reference
                    JOIN legacy_transaction_map legacy_map
                      ON legacy_map.transaction_id = import_reference.transaction_id
                    WHERE legacy_map.legacy_table = 'EXPENSES'
                      AND legacy_map.legacy_id = %d
                    """
                        .formatted(nullOriginExpenseId(statement))))
            .containsExactly(List.of("0"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT INDEX_NAME
                    FROM INFORMATION_SCHEMA.INDEXES
                    WHERE INDEX_NAME = 'IDX_TIR_ORIGIN_PARENT'
                    """))
            .containsExactly(List.of("IDX_TIR_ORIGIN_PARENT"));
      }
    }

    @Test
    void abortsWhenAMappedOutlookRowCannotBeReconciledToExactlyOneReference() throws Exception {
      String url = databaseUrl();
      migrateTo(url, "18");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        Fixture fixture = fixture(statement);
        long expense =
            insertExpense(
                statement, fixture, "OUTLOOK_EMAIL", "message-f:attachment-1", "message-f");
        long transaction = insertTransaction(statement, fixture, true);
        map(statement, "EXPENSES", expense, transaction);
        insertReference(statement, transaction, "OUTLOOK_EMAIL", "message-f:attachment-1");
        insertReference(statement, transaction, "OUTLOOK_EMAIL", "message-f:attachment-2");
      }

      Flyway flyway = Flyway.configure().dataSource(url, "sa", "").load();

      assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);
      // H2 DDL is not transactional, so Flyway records the aborted script as a failed V19.
      assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
      assertThat(flyway.info().current().getState()).isEqualTo(MigrationState.FAILED);
    }
  }

  private record Fixture(
      long activityId,
      long expenseCategoryId,
      long incomeCategoryId,
      long neutralExpenseId,
      long neutralIncomeId) {}

  private Fixture fixture(Statement statement) throws SQLException {
    return new Fixture(
        queryLong(
            statement,
            "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'"),
        queryLong(
            statement, "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'"),
        queryLong(
            statement, "SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'"),
        queryLong(
            statement,
            "SELECT MIN(id) FROM neutral_categories WHERE direction = 'EXPENSE' AND active = TRUE"),
        queryLong(
            statement,
            "SELECT MIN(id) FROM neutral_categories WHERE direction = 'INCOME' AND active = TRUE"));
  }

  private long insertExpense(
      Statement statement,
      Fixture fixture,
      String sourceType,
      String sourceId,
      String outlookMessageId)
      throws SQLException {
    return insertReturningId(
        statement,
        """
        INSERT INTO expenses (
            amount, category, date, description, source_id, source_type, activity_id,
            category_id, outlook_message_id
        )
        VALUES (15.25, 'OTHER', DATE '2026-08-20', 'Legacy expense', %s, %s, %d, %d, %s)
        """
            .formatted(
                literal(sourceId),
                literal(sourceType),
                fixture.activityId(),
                fixture.expenseCategoryId(),
                literal(outlookMessageId)));
  }

  private long insertIncome(
      Statement statement,
      Fixture fixture,
      String sourceType,
      String sourceId,
      String outlookMessageId)
      throws SQLException {
    return insertReturningId(
        statement,
        """
        INSERT INTO incomes (
            amount, date, description, source, source_id, source_type, activity_id, category_id,
            outlook_message_id
        )
        VALUES (45.75, DATE '2026-08-20', 'Legacy income', 'Fixture', %s, %s, %d, %d, %s)
        """
            .formatted(
                literal(sourceId),
                literal(sourceType),
                fixture.activityId(),
                fixture.incomeCategoryId(),
                literal(outlookMessageId)));
  }

  private void mapWithReference(
      Statement statement,
      Fixture fixture,
      String legacyTable,
      long legacyId,
      String origin,
      String externalId)
      throws SQLException {
    long transaction = insertTransaction(statement, fixture, "EXPENSES".equals(legacyTable));
    map(statement, legacyTable, legacyId, transaction);
    insertReference(statement, transaction, origin, externalId);
  }

  private long insertTransaction(Statement statement, Fixture fixture, boolean expense)
      throws SQLException {
    return insertReturningId(
        statement,
        """
        INSERT INTO financial_transactions (
            amount, direction, transaction_date, description, activity_id, neutral_category_id
        )
        VALUES (10.00, '%s', DATE '2026-08-20', 'Ledger row', %d, %d)
        """
            .formatted(
                expense ? "EXPENSE" : "INCOME",
                fixture.activityId(),
                expense ? fixture.neutralExpenseId() : fixture.neutralIncomeId()));
  }

  private void map(Statement statement, String legacyTable, long legacyId, long transactionId)
      throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO legacy_transaction_map (
            legacy_table, legacy_id, transaction_id, legacy_expense_category, canonical_hash
        )
        VALUES ('%s', %d, %d, %s, '%s')
        """
            .formatted(
                legacyTable,
                legacyId,
                transactionId,
                "EXPENSES".equals(legacyTable) ? "'OTHER'" : "NULL",
                HASH));
  }

  private void insertReference(
      Statement statement, long transactionId, String origin, String externalId)
      throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO transaction_import_references (transaction_id, origin, external_id)
        VALUES (%d, '%s', '%s')
        """
            .formatted(transactionId, origin, externalId));
  }

  private long nullOriginExpenseId(Statement statement) throws SQLException {
    return queryLong(
        statement,
        "SELECT id FROM expenses WHERE source_type IS NULL AND outlook_message_id = 'message-d'");
  }

  private long insertReturningId(Statement statement, String sql) throws SQLException {
    statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    try (ResultSet keys = statement.getGeneratedKeys()) {
      assertThat(keys.next()).isTrue();
      return keys.getLong(1);
    }
  }

  private String literal(String value) {
    return value == null ? "NULL" : "'" + value + "'";
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
    return "jdbc:h2:mem:ledger_parent_"
        + UUID.randomUUID().toString().replace("-", "")
        + ";DB_CLOSE_DELAY=-1";
  }
}
