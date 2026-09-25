package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyRetirementMigrationTest {

  private static final String HASH = "0".repeat(64);
  private static final List<String> RETIRED_TABLES =
      List.of(
          "PENDING_EXPENSE_IDENTITY_EVIDENCE",
          "PENDING_INCOME_IDENTITY_EVIDENCE",
          "PENDING_EXPENSE_ALIASES",
          "PENDING_EXPENSES",
          "PENDING_INCOMES",
          "LEGACY_INBOX_MAP",
          "LEGACY_TRANSACTION_MAP",
          "LEDGER_MIGRATION_QUARANTINE",
          "EXPENSES",
          "INCOMES",
          "LEGACY_PAYER_MAP",
          "PAYERS",
          "PAYER_ALIASES",
          "PAYER_ACCOUNTS");

  @Nested
  class UpgradeFromReleasedV19 {

    @Test
    void dropsTheLegacyTablesAndRenamesThePayerTablesToCounterparties() throws Exception {
      String url = databaseUrl();
      migrateTo(url, "19");
      Fixture fixture;
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        fixture = consistentDualWrite(statement);
      }

      Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("20").load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      flyway.validate();

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
        assertThat(tables(statement))
            .doesNotContainAnyElementsOf(RETIRED_TABLES)
            .contains("COUNTERPARTIES", "COUNTERPARTY_ALIASES", "COUNTERPARTY_ACCOUNTS");
        assertThat(columns(statement, "COUNTERPARTY_ALIASES"))
            .containsExactly("COUNTERPARTY_ID", "ALIAS");
        assertThat(columns(statement, "COUNTERPARTY_ACCOUNTS"))
            .containsExactly("COUNTERPARTY_ID", "ACCOUNT_NUMBER");
        assertThat(
                rows(
                    statement,
                    "SELECT id, name, CAST(type AS VARCHAR) FROM counterparties ORDER BY id"))
            .containsExactly(
                List.of(String.valueOf(fixture.payerId()), "Acme Plumbing", "COMPANY"),
                List.of(String.valueOf(fixture.otherPayerId()), "Jane Tenant", "PERSON"));
        assertThat(
                rows(
                    statement,
                    "SELECT counterparty_id, alias FROM counterparty_aliases ORDER BY alias"))
            .containsExactly(List.of(String.valueOf(fixture.payerId()), "ACME"));
        assertThat(
                rows(
                    statement, "SELECT counterparty_id, account_number FROM counterparty_accounts"))
            .containsExactly(List.of(String.valueOf(fixture.payerId()), "acct-1"));
        assertThat(foreignKeyTarget(statement, "INBOX_ITEMS", "SELECTED_COUNTERPARTY_ID"))
            .isEqualTo("COUNTERPARTIES");
        assertThat(foreignKeyTarget(statement, "FINANCIAL_TRANSACTIONS", "COUNTERPARTY_ID"))
            .isEqualTo("COUNTERPARTIES");
        assertThat(foreignKeyTarget(statement, "PAYER_CATEGORY_HISTORY", "PAYER_ID"))
            .isEqualTo("COUNTERPARTIES");
        assertThat(foreignKeyTarget(statement, "PAYER_PROPERTY_HISTORY", "PAYER_ID"))
            .isEqualTo("COUNTERPARTIES");
        assertThat(foreignKeyTarget(statement, "EMAIL_KEYWORD_PAYER_HISTORY", "PAYER_ID"))
            .isEqualTo("COUNTERPARTIES");
        assertThat(foreignKeyTarget(statement, "COUNTERPARTY_ALIASES", "COUNTERPARTY_ID"))
            .isEqualTo("COUNTERPARTIES");
        assertThat(foreignKeyTarget(statement, "COUNTERPARTY_ACCOUNTS", "COUNTERPARTY_ID"))
            .isEqualTo("COUNTERPARTIES");
        // The live session's backup script must name the renamed columns.
        assertThat(strings(statement, "SCRIPT NODATA"))
            .filteredOn(line -> line.contains("FOREIGN KEY"))
            .noneMatch(line -> line.contains("\"PAYER_ID\"") && line.contains("COUNTERPARTY_"));
        assertThat(
                queryLong(
                    statement,
                    "SELECT COUNT(*) FROM financial_transactions WHERE counterparty_id = "
                        + fixture.payerId()))
            .isEqualTo(1);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM inbox_items")).isEqualTo(2);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM payer_category_history"))
            .isEqualTo(1);

        long inserted =
            insertReturningId(
                statement, "INSERT INTO counterparties (name, type) VALUES ('New', 'COMPANY')");
        assertThat(inserted).isGreaterThan(fixture.otherPayerId());
        statement.executeUpdate(
            "INSERT INTO counterparty_aliases (counterparty_id, alias) VALUES (%d, 'NEW')"
                .formatted(inserted));
        assertThatThrownBy(
                () ->
                    statement.executeUpdate(
                        "UPDATE financial_transactions SET counterparty_id = 999999"))
            .isInstanceOf(SQLException.class);
      }
    }

    @Test
    void upgradesADatabaseWhosePayerKeysHaveHibernateGeneratedNames() throws Exception {
      String url = databaseUrl();
      migrateTo(url, "19");
      Fixture fixture;
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        fixture = consistentDualWrite(statement);
        // Databases whose payer tables predate Flyway carry Hibernate's hashed key names.
        statement.execute(
            "ALTER TABLE payer_aliases RENAME CONSTRAINT fk_payer_aliases_payer"
                + " TO FK8F74KNMKL4TNHFKFY6A880D9Q");
        statement.execute(
            "ALTER TABLE payer_accounts RENAME CONSTRAINT fk_payer_accounts_payer"
                + " TO FKHPIGNNVLT6FM3BKLEDDNDD9F3");
      }

      Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("20").load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(tables(statement))
            .doesNotContainAnyElementsOf(RETIRED_TABLES)
            .contains("COUNTERPARTIES", "COUNTERPARTY_ALIASES", "COUNTERPARTY_ACCOUNTS");
        assertThat(foreignKeyTarget(statement, "COUNTERPARTY_ALIASES", "COUNTERPARTY_ID"))
            .isEqualTo("COUNTERPARTIES");
        assertThat(foreignKeyTarget(statement, "COUNTERPARTY_ACCOUNTS", "COUNTERPARTY_ID"))
            .isEqualTo("COUNTERPARTIES");
        assertThat(
                strings(
                    statement,
                    """
                    SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                    WHERE CONSTRAINT_TYPE = 'FOREIGN KEY'
                      AND TABLE_NAME IN ('COUNTERPARTY_ALIASES', 'COUNTERPARTY_ACCOUNTS')
                    ORDER BY 1
                    """))
            .containsExactly(
                "FK_COUNTERPARTY_ACCOUNT_COUNTERPARTY", "FK_COUNTERPARTY_ALIAS_COUNTERPARTY");
        assertThat(
                rows(
                    statement,
                    "SELECT counterparty_id, alias FROM counterparty_aliases ORDER BY alias"))
            .containsExactly(List.of(String.valueOf(fixture.payerId()), "ACME"));
      }
    }

    @Test
    void migratesAnEmptyReleasedDatabase() throws Exception {
      String url = databaseUrl();
      migrateTo(url, "19");

      Flyway.configure().dataSource(url, "sa", "").load().migrate();

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(tables(statement)).doesNotContainAnyElementsOf(RETIRED_TABLES);
      }
    }
  }

  @Nested
  class Prechecks {

    @ParameterizedTest
    @ValueSource(
        strings = {
          // P1: a legacy expense disagrees with its unified transaction.
          "UPDATE financial_transactions SET amount = amount + 1 WHERE direction = 'EXPENSE'",
          // P2: a legacy income maps to a deleted transaction.
          "UPDATE financial_transactions SET deleted_at = CURRENT_TIMESTAMP"
              + " WHERE direction = 'INCOME'",
          // P3: a legacy row was quarantined.
          "INSERT INTO ledger_migration_quarantine (legacy_table, legacy_id, reason,"
              + " salted_identity_hash) VALUES ('EXPENSES', 999, 'TEST', REPEAT('0', 64))",
          // P4: a pending expense disagrees with its inbox item.
          "UPDATE inbox_items SET proposed_amount = 1.00"
              + " WHERE migration_legacy_table = 'PENDING_EXPENSES'",
          // P5: a pending income's inbox item was already saved.
          "UPDATE inbox_items SET state = 'SAVED'"
              + " WHERE migration_legacy_table = 'PENDING_INCOMES'",
          // P6: a pending alias is missing from the inbox item.
          "DELETE FROM inbox_artifacts WHERE type = 'UNRECOGNIZED_ALIAS'",
          // P7: the counterparty mirror drifted from the payer table.
          "UPDATE counterparties SET name = 'Drifted' WHERE type = 'COMPANY'",
          // P7: a mirrored alias is missing.
          "DELETE FROM counterparty_aliases",
          // P8: pending identity evidence was not copied.
          "DELETE FROM inbox_identity_evidence"
        })
    void driftFailsBeforeAnyLegacyTableIsDropped(String drift) throws Exception {
      String url = databaseUrl();
      migrateTo(url, "19");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        consistentDualWrite(statement);
        statement.executeUpdate(drift);
      }

      assertThatThrownBy(() -> Flyway.configure().dataSource(url, "sa", "").load().migrate())
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("V20__retire_legacy_flows.sql");

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(tables(statement)).containsAll(RETIRED_TABLES);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM expenses")).isOne();
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM incomes")).isOne();
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM pending_expenses")).isOne();
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM payers")).isEqualTo(2);
        assertThat(
                queryLong(
                    statement,
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
                        + " WHERE CONSTRAINT_NAME = 'FK_FINANCIAL_TRANSACTIONS_COUNTERPARTY'"))
            .isOne();
      }
    }
  }

  /**
   * Writes the shape a V19 dual-writing release leaves behind: every legacy row mirrored into the
   * unified ledger, durable inbox and normalized counterparty tables.
   */
  private Fixture consistentDualWrite(Statement statement) throws SQLException {
    long activityId =
        queryLong(
            statement,
            "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
    long expenseCategoryId =
        queryLong(
            statement, "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'");
    long incomeCategoryId =
        queryLong(
            statement, "SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");
    long neutralExpenseId = neutralFor(statement, expenseCategoryId);
    long neutralIncomeId = neutralFor(statement, incomeCategoryId);

    long payerId =
        insertReturningId(
            statement, "INSERT INTO payers (name, type) VALUES ('Acme Plumbing', 'COMPANY')");
    long otherPayerId =
        insertReturningId(
            statement, "INSERT INTO payers (name, type) VALUES ('Jane Tenant', 'PERSON')");
    statement.executeUpdate(
        "INSERT INTO payer_aliases (payer_id, alias) VALUES (%d, 'ACME')".formatted(payerId));
    statement.executeUpdate(
        "INSERT INTO payer_accounts (payer_id, account_number) VALUES (%d, 'acct-1')"
            .formatted(payerId));
    statement.executeUpdate(
        """
        INSERT INTO counterparties (id, name, type)
        SELECT id, name, CAST(type AS VARCHAR) FROM payers
        """);
    statement.executeUpdate(
        """
        INSERT INTO counterparty_aliases (counterparty_id, alias)
        SELECT payer_id, alias FROM payer_aliases
        """);
    statement.executeUpdate(
        """
        INSERT INTO counterparty_accounts (counterparty_id, account_number)
        SELECT payer_id, account_number FROM payer_accounts
        """);
    statement.executeUpdate(
        "INSERT INTO legacy_payer_map (payer_id, counterparty_id) SELECT id, id FROM payers");
    statement.executeUpdate(
        """
        INSERT INTO payer_category_history (category, occurrences, payer_id)
        VALUES ('REPAIRS', 1, %d)
        """
            .formatted(payerId));

    long expenseId =
        insertReturningId(
            statement,
            """
            INSERT INTO expenses (
                amount, category, date, description, source_id, source_type, payer_id,
                activity_id, category_id
            )
            VALUES (15.25, 'OTHER', DATE '2026-08-20', 'Faucet', 'manual-1', 'MANUAL', %d, %d, %d)
            """
                .formatted(payerId, activityId, expenseCategoryId));
    long expenseTransaction =
        insertReturningId(
            statement,
            """
            INSERT INTO financial_transactions (
                amount, direction, transaction_date, description, activity_id,
                neutral_category_id, counterparty_id
            )
            VALUES (15.25, 'EXPENSE', DATE '2026-08-20', 'Faucet', %d, %d, %d)
            """
                .formatted(activityId, neutralExpenseId, payerId));
    map(statement, "EXPENSES", expenseId, expenseTransaction, "'OTHER'");
    long incomeId =
        insertReturningId(
            statement,
            """
            INSERT INTO incomes (amount, date, description, source, activity_id, category_id)
            VALUES (1200.00, DATE '2026-08-01', 'Rent', 'Jane', %d, %d)
            """
                .formatted(activityId, incomeCategoryId));
    long incomeTransaction =
        insertReturningId(
            statement,
            """
            INSERT INTO financial_transactions (
                amount, direction, transaction_date, description, activity_id,
                neutral_category_id
            )
            VALUES (1200.00, 'INCOME', DATE '2026-08-01', 'Rent', %d, %d)
            """
                .formatted(activityId, neutralIncomeId));
    map(statement, "INCOMES", incomeId, incomeTransaction, "NULL");

    long pendingExpenseId =
        insertReturningId(
            statement,
            """
            INSERT INTO pending_expenses (
                amount, created_at, date, description, source_id, source_type, status,
                activity_id, category_id, configured_activity_id, classification_ambiguous
            )
            VALUES (
                42.00, TIMESTAMP '2026-08-02 10:00:00', DATE '2026-08-02', 'Receipt',
                'drive-1', 'RECEIPT', 'READY', %d, %d, %d, FALSE
            )
            """
                .formatted(activityId, expenseCategoryId, activityId));
    statement.executeUpdate(
        "INSERT INTO pending_expense_aliases (pending_expense_id, alias) VALUES (%d, 'acct-9')"
            .formatted(pendingExpenseId));
    long expenseItem =
        insertInboxItem(
            statement,
            "PENDING_EXPENSES",
            pendingExpenseId,
            "RECEIPT",
            "drive-1",
            "42.00",
            "DATE '2026-08-02'",
            "'Receipt'",
            activityId,
            expenseCategoryId,
            payerId);
    statement.executeUpdate(
        """
        INSERT INTO inbox_artifacts (inbox_item_id, type, text_value)
        VALUES (%d, 'UNRECOGNIZED_ALIAS', 'acct-9')
        """
            .formatted(expenseItem));
    statement.executeUpdate(
        """
        INSERT INTO pending_expense_identity_evidence (
            pending_expense_id, snapshot_order, target_kind, candidate_rank, target_id,
            target_name, evidence_rank, evidence_type, evidence_strength, occurrences,
            total_occurrences
        )
        VALUES (%d, 0, 'ACTIVITY', 1, %d, 'Needs classification', 1,
                'CONFIGURED_ACTIVITY_ID', 'AUTHORITATIVE', 1, 1)
        """
            .formatted(pendingExpenseId, activityId));
    statement.executeUpdate(
        """
        INSERT INTO inbox_identity_evidence (
            inbox_item_id, snapshot_order, target_kind, candidate_rank, target_id,
            target_name, evidence_rank, evidence_type, evidence_strength, occurrences,
            total_occurrences
        )
        VALUES (%d, 0, 'ACTIVITY', 1, %d, 'Needs classification', 1,
                'CONFIGURED_ACTIVITY_ID', 'AUTHORITATIVE', 1, 1)
        """
            .formatted(expenseItem, activityId));

    long pendingIncomeId =
        insertReturningId(
            statement,
            """
            INSERT INTO pending_incomes (
                source_id, source_type, status, source, created_at, activity_id, category_id,
                classification_ambiguous
            )
            VALUES (
                'venmo-1', 'VENMO', 'READY', 'Jane', TIMESTAMP '2026-08-03 10:00:00', %d, %d,
                TRUE
            )
            """
                .formatted(activityId, incomeCategoryId));
    insertInboxItem(
        statement,
        "PENDING_INCOMES",
        pendingIncomeId,
        "VENMO",
        "venmo-1",
        "NULL",
        "NULL",
        "NULL",
        activityId,
        incomeCategoryId,
        null);
    return new Fixture(payerId, otherPayerId);
  }

  private long insertInboxItem(
      Statement statement,
      String legacyTable,
      long legacyId,
      String origin,
      String sourceId,
      String amount,
      String date,
      String description,
      long activityId,
      long categoryId,
      Long selectedCounterpartyId)
      throws SQLException {
    long itemId =
        insertReturningId(
            statement,
            """
            INSERT INTO inbox_items (
                origin, legacy_source_type, legacy_source_id, state, external_sync_state,
                raw_status, proposed_amount, proposed_date, proposed_description,
                legacy_activity_id, legacy_category_id, classification_ambiguous,
                selected_counterparty_id, migration_legacy_table, migration_legacy_id,
                created_at, updated_at
            )
            VALUES (
                '%s', '%s', '%s', 'READY', 'NOT_REQUIRED', 'READY', %s, %s, %s, %d, %d,
                FALSE, %s, '%s', %d, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            """
                .formatted(
                    origin,
                    origin,
                    sourceId,
                    amount,
                    date,
                    description,
                    activityId,
                    categoryId,
                    selectedCounterpartyId == null ? "NULL" : selectedCounterpartyId,
                    legacyTable,
                    legacyId));
    statement.executeUpdate(
        """
        INSERT INTO legacy_inbox_map (legacy_table, legacy_id, inbox_item_id)
        VALUES ('%s', %d, %d)
        """
            .formatted(legacyTable, legacyId, itemId));
    return itemId;
  }

  private void map(
      Statement statement, String table, long legacyId, long transactionId, String category)
      throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO legacy_transaction_map (
            legacy_table, legacy_id, transaction_id, legacy_expense_category, canonical_hash
        )
        VALUES ('%s', %d, %d, %s, '%s')
        """
            .formatted(table, legacyId, transactionId, category, HASH));
  }

  private long neutralFor(Statement statement, long legacyCategoryId) throws SQLException {
    return queryLong(
        statement,
        "SELECT neutral_category_id FROM legacy_category_map WHERE legacy_category_id = "
            + legacyCategoryId);
  }

  private static List<String> tables(Statement statement) throws SQLException {
    return strings(
        statement,
        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'");
  }

  private static List<String> columns(Statement statement, String table) throws SQLException {
    return strings(
        statement,
        """
        SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = '%s'
        ORDER BY ORDINAL_POSITION
        """
            .formatted(table));
  }

  private static String foreignKeyTarget(Statement statement, String table, String column)
      throws SQLException {
    List<String> targets =
        strings(
            statement,
            """
            SELECT referenced.TABLE_NAME
            FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE child
            JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS reference
              ON reference.CONSTRAINT_NAME = child.CONSTRAINT_NAME
            JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS referenced
              ON referenced.CONSTRAINT_NAME = reference.UNIQUE_CONSTRAINT_NAME
            WHERE child.TABLE_NAME = '%s' AND child.COLUMN_NAME = '%s'
            """
                .formatted(table, column));
    assertThat(targets).as("%s.%s foreign key", table, column).hasSize(1);
    return targets.getFirst();
  }

  private static List<String> strings(Statement statement, String sql) throws SQLException {
    List<String> values = new ArrayList<>();
    try (ResultSet result = statement.executeQuery(sql)) {
      while (result.next()) {
        values.add(result.getString(1));
      }
    }
    return values;
  }

  private static List<List<String>> rows(Statement statement, String sql) throws SQLException {
    List<List<String>> rows = new ArrayList<>();
    try (ResultSet result = statement.executeQuery(sql)) {
      int columns = result.getMetaData().getColumnCount();
      while (result.next()) {
        List<String> row = new ArrayList<>();
        for (int column = 1; column <= columns; column++) {
          row.add(result.getString(column));
        }
        rows.add(row);
      }
    }
    return rows;
  }

  private static long insertReturningId(Statement statement, String sql) throws SQLException {
    statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    try (ResultSet keys = statement.getGeneratedKeys()) {
      assertThat(keys.next()).isTrue();
      return keys.getLong(1);
    }
  }

  private static long queryLong(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static void migrateTo(String url, String version) {
    Flyway.configure().dataSource(url, "sa", "").target(version).load().migrate();
  }

  private static String databaseUrl() {
    return "jdbc:h2:mem:legacy_retirement_"
        + UUID.randomUUID().toString().replace("-", "")
        + ";DB_CLOSE_DELAY=-1";
  }

  private record Fixture(long payerId, long otherPayerId) {}
}
