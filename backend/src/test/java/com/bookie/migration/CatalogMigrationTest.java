package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class CatalogMigrationTest {

  @Test
  void migratesV11PayersOneToOneWithoutChangingLegacyOrFinancialRows() throws Exception {
    String url =
        "jdbc:h2:mem:catalog_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("11"))
        .load()
        .migrate();

    Map<String, List<List<String>>> legacyBefore;
    List<List<String>> expectedCounterparties;
    List<List<String>> expectedAliases;
    List<List<String>> expectedAccounts;
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      long propertyId =
          insertAndReturnId(
              statement,
              """
              INSERT INTO properties (address, name, notes, type)
              VALUES ('10 Example Lane', 'Example Rental', 'Preserve exactly', 'SINGLE_FAMILY')
              """);
      statement.executeUpdate(
          """
          INSERT INTO property_accounts (property_id, account_number)
          VALUES (%d, 'property-account-001')
          """
              .formatted(propertyId));

      long firstPayerId =
          insertAndReturnId(
              statement,
              """
              INSERT INTO payers (name, type)
              VALUES ('Example Utility', 'COMPANY')
              """);
      long secondPayerId =
          insertAndReturnId(
              statement,
              """
              INSERT INTO payers (name, type)
              VALUES ('Example Tenant', 'PERSON')
              """);
      statement.executeUpdate(
          """
          INSERT INTO payer_aliases (payer_id, alias) VALUES
              (%d, 'Utility Alias'),
              (%d, 'Example & Co.'),
              (%d, 'Tenant Alias')
          """
              .formatted(firstPayerId, firstPayerId, secondPayerId));
      statement.executeUpdate(
          """
          INSERT INTO payer_accounts (payer_id, account_number) VALUES
              (%d, 'utility-account-001'),
              (%d, 'tenant-account-002')
          """
              .formatted(firstPayerId, secondPayerId));

      long activityId =
          queryLong(
              statement,
              """
              SELECT id
              FROM financial_activities
              WHERE system_key = 'NEEDS_CLASSIFICATION'
              """);
      long incomeCategoryId =
          queryLong(
              statement, "SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");
      long expenseCategoryId =
          queryLong(
              statement,
              "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'");

      statement.executeUpdate(
          """
          INSERT INTO incomes
              (
                  amount, date, description, source, property_id, payer_id, source_id,
                  source_type, activity_id, category_id
              )
          VALUES (
              1875.25, DATE '2026-06-01', 'June rent', 'Example Tenant', %d, %d,
              'income-source-001', 'MANUAL', %d, %d
          )
          """
              .formatted(propertyId, secondPayerId, activityId, incomeCategoryId));
      statement.executeUpdate(
          """
          INSERT INTO expenses
              (
                  amount, category, date, description, payer_id, property_id, source_id,
                  source_type, activity_id, category_id
              )
          VALUES (
              142.67, 'REPAIRS', DATE '2026-06-02', 'Repair materials', %d, %d,
              'expense-source-001', 'MANUAL', %d, %d
          )
          """
              .formatted(firstPayerId, propertyId, activityId, expenseCategoryId));
      statement.executeUpdate(
          """
          INSERT INTO pending_incomes
              (
                  source_id, source_type, status, source, amount, description, date,
                  property_id, payer_id, created_at, activity_id, category_id,
                  classification_ambiguous
              )
          VALUES (
              'pending-income-001', 'VENMO', 'READY', 'Example Tenant', 900.00,
              'Partial rent', DATE '2026-06-03', %d, %d, CURRENT_TIMESTAMP, %d, %d, FALSE
          )
          """
              .formatted(propertyId, secondPayerId, activityId, incomeCategoryId));
      statement.executeUpdate(
          """
          INSERT INTO payer_property_history
              (occurrences, payer_id, property_id, version)
          VALUES (4, %d, %d, 0)
          """
              .formatted(secondPayerId, propertyId));
      statement.executeUpdate(
          """
          INSERT INTO payer_category_history
              (category, occurrences, payer_id, version)
          VALUES ('REPAIRS', 2, %d, 0)
          """
              .formatted(firstPayerId));
      statement.executeUpdate(
          """
          INSERT INTO email_keyword_payer_history
              (keyword, occurrences, payer_id, version)
          VALUES ('utility-confirmation-001', 3, %d, 0)
          """
              .formatted(firstPayerId));

      legacyBefore = snapshotLegacyTables(statement);
      expectedCounterparties =
          rows(
              statement,
              """
              SELECT id, name, CAST(type AS VARCHAR)
              FROM payers
              ORDER BY id
              """);
      expectedAliases =
          rows(
              statement,
              """
              SELECT payer_id, alias
              FROM payer_aliases
              ORDER BY payer_id, alias
              """);
      expectedAccounts =
          rows(
              statement,
              """
              SELECT payer_id, account_number
              FROM payer_accounts
              ORDER BY payer_id, account_number
              """);
    }

    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("12"))
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      assertThat(snapshotLegacyTables(statement)).isEqualTo(legacyBefore);
      assertThat(
              rows(
                  statement,
                  """
                  SELECT id, name, type
                  FROM counterparties
                  ORDER BY id
                  """))
          .isEqualTo(expectedCounterparties);
      assertThat(
              rows(
                  statement,
                  """
                  SELECT counterparty_id, alias
                  FROM counterparty_aliases
                  ORDER BY counterparty_id, alias
                  """))
          .isEqualTo(expectedAliases);
      assertThat(
              rows(
                  statement,
                  """
                  SELECT counterparty_id, account_number
                  FROM counterparty_accounts
                  ORDER BY counterparty_id, account_number
                  """))
          .isEqualTo(expectedAccounts);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM legacy_payer_map
                  WHERE payer_id <> counterparty_id
                  """))
          .isZero();
      assertThat(queryLong(statement, "SELECT COUNT(*) FROM legacy_payer_map"))
          .isEqualTo(expectedCounterparties.size());
    }
  }

  private Map<String, List<List<String>>> snapshotLegacyTables(Statement statement)
      throws SQLException {
    Map<String, List<List<String>>> snapshots = new LinkedHashMap<>();
    snapshots.put("properties", rows(statement, "SELECT * FROM properties ORDER BY id"));
    snapshots.put(
        "property_accounts",
        rows(statement, "SELECT * FROM property_accounts ORDER BY property_id, account_number"));
    snapshots.put("payers", rows(statement, "SELECT * FROM payers ORDER BY id"));
    snapshots.put(
        "payer_aliases", rows(statement, "SELECT * FROM payer_aliases ORDER BY payer_id, alias"));
    snapshots.put(
        "payer_accounts",
        rows(statement, "SELECT * FROM payer_accounts ORDER BY payer_id, account_number"));
    snapshots.put("incomes", rows(statement, "SELECT * FROM incomes ORDER BY id"));
    snapshots.put("expenses", rows(statement, "SELECT * FROM expenses ORDER BY id"));
    snapshots.put("pending_incomes", rows(statement, "SELECT * FROM pending_incomes ORDER BY id"));
    snapshots.put(
        "pending_expenses", rows(statement, "SELECT * FROM pending_expenses ORDER BY id"));
    snapshots.put(
        "payer_property_history",
        rows(statement, "SELECT * FROM payer_property_history ORDER BY id"));
    snapshots.put(
        "payer_category_history",
        rows(statement, "SELECT * FROM payer_category_history ORDER BY id"));
    snapshots.put(
        "email_keyword_payer_history",
        rows(statement, "SELECT * FROM email_keyword_payer_history ORDER BY id"));
    return snapshots;
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
}
