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

class UnifiedLedgerMigrationTest {

  @Nested
  class Backfill {

    @Test
    void copiesEveryFinancialRowAndNormalizesReferencesWithoutChangingLegacyData()
        throws Exception {
      String url = databaseUrl("ledger_backfill");
      migrateTo(url, "10");

      List<List<String>> incomesBefore;
      List<List<String>> expensesBefore;
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        long propertyId =
            insertAndReturnId(
                statement,
                """
                INSERT INTO properties (address, name, notes, type)
                VALUES ('45 Example Road', 'Migration rental', 'Retain exactly', 'SINGLE_FAMILY')
                """);
        long payerId =
            insertAndReturnId(
                statement,
                """
                INSERT INTO payers (name, type)
                VALUES ('Synthetic Counterparty', 'COMPANY')
                """);
        long ownerId = queryLong(statement, "SELECT MIN(id) FROM household_members");
        long activityId =
            insertAndReturnId(
                statement,
                """
                INSERT INTO financial_activities
                    (name, activity_type, tax_treatment, owner_id, property_id, active)
                VALUES (
                    'Migration rental activity',
                    'OTHER',
                    'NONE',
                    %d,
                    %d,
                    TRUE
                )
                """
                    .formatted(ownerId, propertyId));
        statement.executeUpdate(
            """
            INSERT INTO financial_categories
                (category_key, label, direction, tax_treatment, active, system)
            VALUES (
                'CUSTOM_LEDGER_EXPENSE',
                'Custom ledger expense',
                'EXPENSE',
                'NONE',
                TRUE,
                FALSE
            )
            """);
        long incomeCategoryId =
            queryLong(
                statement,
                "SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");
        long expenseCategoryId =
            queryLong(
                statement,
                """
                SELECT id
                FROM financial_categories
                WHERE category_key = 'CUSTOM_LEDGER_EXPENSE'
                """);

        statement.executeUpdate(
            """
            INSERT INTO incomes (
                amount,
                date,
                description,
                source,
                property_id,
                payer_id,
                source_id,
                source_type,
                receipt_file_name,
                receipt_one_drive_id,
                activity_id,
                category_id
            )
            VALUES (
                1200.50,
                DATE '2026-07-01',
                'Synthetic migration income',
                'Synthetic source label',
                %d,
                %d,
                'income-external-001',
                'VENMO',
                'income-receipt.pdf',
                'income-receipt-001',
                %d,
                %d
            )
            """
                .formatted(propertyId, payerId, activityId, incomeCategoryId));
        statement.executeUpdate(
            """
            INSERT INTO expenses (
                amount,
                category,
                date,
                description,
                source_id,
                source_type,
                payer_id,
                property_id,
                receipt_file_name,
                receipt_one_drive_id,
                activity_id,
                category_id
            )
            VALUES (
                89.25,
                'SUPPLIES',
                DATE '2026-07-02',
                'Synthetic migration expense',
                'expense-external-001',
                'RECEIPT',
                %d,
                %d,
                'expense-receipt.pdf',
                'expense-receipt-001',
                %d,
                %d
            )
            """
                .formatted(payerId, propertyId, activityId, expenseCategoryId));
        incomesBefore =
            rows(
                statement,
                """
                SELECT id, amount, date, description, source, property_id, source_id, source_type,
                       receipt_file_name, receipt_one_drive_id, payer_id, activity_id, category_id
                FROM incomes
                ORDER BY id
                """);
        expensesBefore =
            rows(
                statement,
                """
                SELECT id, amount, category, date, description, source_id, source_type, payer_id,
                       property_id, receipt_file_name, receipt_one_drive_id, activity_id, category_id
                FROM expenses
                ORDER BY id
                """);
      }

      Flyway.configure().dataSource(url, "sa", "").load().migrate();

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(
                rows(
                    statement,
                    """
                    SELECT id, amount, date, description, source, property_id, source_id, source_type,
                           receipt_file_name, receipt_one_drive_id, payer_id, activity_id, category_id
                    FROM incomes
                    ORDER BY id
                    """))
            .isEqualTo(incomesBefore);
        assertThat(
                rows(
                    statement,
                    """
                    SELECT id, amount, category, date, description, source_id, source_type, payer_id,
                           property_id, receipt_file_name, receipt_one_drive_id, activity_id, category_id
                    FROM expenses
                    ORDER BY id
                    """))
            .isEqualTo(expensesBefore);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM financial_transactions"))
            .isEqualTo(2);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM legacy_transaction_map"))
            .isEqualTo(2);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM transaction_import_references"))
            .isEqualTo(2);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM transaction_attachments"))
            .isEqualTo(2);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM ledger_migration_quarantine"))
            .isZero();

        assertThat(
                rows(
                    statement,
                    """
                    SELECT direction, amount
                    FROM financial_transactions
                    ORDER BY direction
                    """))
            .containsExactly(List.of("INCOME", "1200.50"), List.of("EXPENSE", "89.25"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT category.category_key
                    FROM financial_transactions transaction
                    JOIN neutral_categories category
                      ON category.id = transaction.neutral_category_id
                    WHERE transaction.direction = 'EXPENSE'
                    """))
            .containsExactly(List.of("CUSTOM_LEDGER_EXPENSE"));
        assertThat(
                queryLong(
                    statement,
                    """
                    SELECT COUNT(*)
                    FROM financial_transactions transaction
                    JOIN financial_activities activity ON activity.id = transaction.activity_id
                    WHERE activity.property_id IS NOT NULL
                    """))
            .isEqualTo(2);
        assertThat(
                rows(
                    statement,
                    """
                    SELECT origin, external_id, source_label
                    FROM transaction_import_references
                    ORDER BY origin
                    """))
            .containsExactly(
                List.of("RECEIPT", "expense-external-001", "null"),
                List.of("VENMO", "income-external-001", "Synthetic source label"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT storage_provider, external_id, file_name
                    FROM transaction_attachments
                    ORDER BY external_id
                    """))
            .containsExactly(
                List.of("ONEDRIVE", "expense-receipt-001", "expense-receipt.pdf"),
                List.of("ONEDRIVE", "income-receipt-001", "income-receipt.pdf"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT canonical_hash
                    FROM legacy_transaction_map
                    ORDER BY legacy_table, legacy_id
                    """))
            .allSatisfy(row -> assertThat(row.getFirst()).matches("[0-9a-f]{64}"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT legacy_table, legacy_expense_category
                    FROM legacy_transaction_map
                    ORDER BY legacy_table
                    """))
            .containsExactly(List.of("INCOMES", "null"), List.of("EXPENSES", "SUPPLIES"));
      }
    }
  }

  @Nested
  class SafetyChecks {

    @Test
    void rejectsDuplicateSourceIdentityAcrossLegacyDirections() throws Exception {
      String url = databaseUrl("ledger_duplicate_source");
      migrateTo(url, "12");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        insertMinimalRows(statement, "duplicate-source", "duplicate-source");
      }

      assertThatThrownBy(() -> Flyway.configure().dataSource(url, "sa", "").load().migrate())
          .isInstanceOf(FlywayException.class);
    }

    @Test
    void rejectsNonPositiveAmountsInsteadOfChangingThem() throws Exception {
      String url = databaseUrl("ledger_invalid_amount");
      migrateTo(url, "12");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        long activityId =
            queryLong(
                statement,
                "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
        long categoryId =
            queryLong(
                statement,
                "SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");
        statement.executeUpdate(
            """
            INSERT INTO incomes
                (amount, date, description, source_type, activity_id, category_id)
            VALUES (0, DATE '2026-01-01', 'Invalid amount', 'MANUAL', %d, %d)
            """
                .formatted(activityId, categoryId));
      }

      assertThatThrownBy(() -> Flyway.configure().dataSource(url, "sa", "").load().migrate())
          .isInstanceOf(FlywayException.class);
    }
  }

  private void insertMinimalRows(Statement statement, String incomeSourceId, String expenseSourceId)
      throws SQLException {
    long activityId =
        queryLong(
            statement,
            "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
    long incomeCategoryId =
        queryLong(
            statement, "SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");
    long expenseCategoryId =
        queryLong(
            statement, "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'");
    statement.executeUpdate(
        """
        INSERT INTO incomes
            (amount, date, description, source_id, source_type, activity_id, category_id)
        VALUES (10.00, DATE '2026-01-01', 'Income', '%s', 'MANUAL', %d, %d)
        """
            .formatted(incomeSourceId, activityId, incomeCategoryId));
    statement.executeUpdate(
        """
        INSERT INTO expenses
            (amount, category, date, description, source_id, source_type, activity_id, category_id)
        VALUES (5.00, 'OTHER', DATE '2026-01-02', 'Expense', '%s', 'MANUAL', %d, %d)
        """
            .formatted(expenseSourceId, activityId, expenseCategoryId));
  }

  private void migrateTo(String url, String version) {
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion(version))
        .load()
        .migrate();
  }

  private long insertAndReturnId(Statement statement, String sql) throws SQLException {
    statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    try (ResultSet keys = statement.getGeneratedKeys()) {
      assertThat(keys.next()).isTrue();
      return keys.getLong(1);
    }
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
