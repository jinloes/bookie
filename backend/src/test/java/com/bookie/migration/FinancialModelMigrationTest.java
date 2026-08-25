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

class FinancialModelMigrationTest {

  @Test
  void migratesRepresentativeV6RowsToActivitiesAndCategoriesWithoutLoss() throws Exception {
    String url =
        "jdbc:h2:mem:migration_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("6"))
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO properties (address, name, type)
          VALUES ('123 Oak Street', 'Oak Street', 'SINGLE_FAMILY')
          """);
      long propertyId;
      try (ResultSet result = statement.executeQuery("SELECT id FROM properties")) {
        result.next();
        propertyId = result.getLong(1);
      }
      statement.executeUpdate(
          """
          INSERT INTO incomes (amount, date, description, source, source_type, property_id)
          VALUES (1500.00, DATE '2026-01-01', 'January rent', 'Tenant', 'MANUAL', %d)
          """
              .formatted(propertyId));
      statement.executeUpdate(
          """
          INSERT INTO incomes (amount, date, description, source, source_type)
          VALUES (50.00, DATE '2026-01-02', 'Unclassified income', 'Other', 'MANUAL')
          """);
      statement.executeUpdate(
          """
          INSERT INTO expenses (amount, category, date, description, source_type, property_id)
          VALUES (250.00, 'REPAIRS', DATE '2026-01-03', 'Plumbing repair', 'MANUAL', %d)
          """
              .formatted(propertyId));
      statement.executeUpdate(
          """
          INSERT INTO expenses (amount, category, date, description, source_type)
          VALUES (25.00, 'REPAIRS', DATE '2026-01-04', 'Unclassified purchase', 'MANUAL')
          """);
      statement.executeUpdate(
          """
          INSERT INTO pending_incomes
              (status, amount, description, date, property_id, created_at)
          VALUES ('READY', 1600.00, 'February rent', DATE '2026-02-01', %d, CURRENT_TIMESTAMP)
          """
              .formatted(propertyId));
      statement.executeUpdate(
          """
          INSERT INTO pending_expenses
              (status, amount, category, description, date, property_name, created_at)
          VALUES (
              'READY',
              100.00,
              'SUPPLIES',
              'Paint supplies',
              DATE '2026-02-02',
              'Oak Street',
              CURRENT_TIMESTAMP
          )
          """);
      statement.executeUpdate(
          """
          INSERT INTO pending_expenses
              (status, email_type, amount, description, date, created_at)
          VALUES (
              'READY',
              'INCOME',
              75.00,
              'Unclassified incoming payment',
              DATE '2026-02-03',
              CURRENT_TIMESTAMP
          )
          """);
    }

    Flyway.configure().dataSource(url, "sa", "").load().migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      assertThat(queryLong(statement, "SELECT COUNT(*) FROM incomes")).isEqualTo(2);
      assertThat(queryLong(statement, "SELECT COUNT(*) FROM expenses")).isEqualTo(2);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM financial_activities
                  WHERE property_id IS NOT NULL AND tax_treatment = 'SCHEDULE_E'
                  """))
          .isEqualTo(1);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM expenses e
                  JOIN financial_activities a ON a.id = e.activity_id
                  JOIN financial_categories c ON c.id = e.category_id
                  WHERE a.system_key = 'NEEDS_CLASSIFICATION'
                    AND c.category_key = 'OTHER_EXPENSE'
                    AND CAST(e.category AS VARCHAR) = 'REPAIRS'
                  """))
          .isEqualTo(1);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM incomes i
                  JOIN financial_activities a ON a.id = i.activity_id
                  JOIN financial_categories c ON c.id = i.category_id
                  WHERE a.tax_treatment = 'SCHEDULE_E' AND c.category_key = 'RENTAL_INCOME'
                  """))
          .isEqualTo(1);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM incomes i
                  JOIN financial_activities a ON a.id = i.activity_id
                  JOIN financial_categories c ON c.id = i.category_id
                  WHERE a.system_key = 'NEEDS_CLASSIFICATION'
                    AND c.category_key = 'OTHER_INCOME'
                  """))
          .isEqualTo(1);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM expenses e
                  JOIN financial_categories c ON c.id = e.category_id
                  WHERE c.category_key = 'REPAIRS'
                  """))
          .isEqualTo(1);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM pending_incomes
                  WHERE activity_id IS NULL OR category_id IS NULL
                  """))
          .isZero();
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM pending_expenses
                  WHERE activity_id IS NULL OR category_id IS NULL
                  """))
          .isZero();
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM pending_expenses pe
                  JOIN financial_activities a ON a.id = pe.activity_id
                  JOIN financial_categories c ON c.id = pe.category_id
                  WHERE pe.email_type = 'INCOME'
                    AND a.system_key = 'NEEDS_CLASSIFICATION'
                    AND c.category_key = 'OTHER_INCOME'
                  """))
          .isEqualTo(1);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM household_members
                  WHERE system_key = 'DEFAULT_HOUSEHOLD'
                  """))
          .isEqualTo(1);
    }
  }

  @Test
  void backfillsDefaultHouseholdKeyForDatabaseCreatedByOriginalV7() throws Exception {
    String url =
        "jdbc:h2:mem:migration_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("9"))
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "ALTER TABLE household_members DROP CONSTRAINT uk_household_members_system_key");
      statement.executeUpdate("ALTER TABLE household_members DROP COLUMN system_key");
      statement.executeUpdate(
          """
          INSERT INTO household_members (name, active)
          VALUES ('Additional Member', TRUE)
          """);
    }

    Flyway.configure().dataSource(url, "sa", "").load().migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM household_members
                  WHERE system_key = 'DEFAULT_HOUSEHOLD'
                    AND id = (SELECT MIN(id) FROM household_members)
                  """))
          .isEqualTo(1);
      assertThat(
              queryLong(
                  statement,
                  """
                  SELECT COUNT(*)
                  FROM information_schema.table_constraints
                  WHERE table_schema = 'PUBLIC'
                    AND table_name = 'HOUSEHOLD_MEMBERS'
                    AND constraint_name = 'UK_HOUSEHOLD_MEMBERS_SYSTEM_KEY'
                    AND constraint_type = 'UNIQUE'
                  """))
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
