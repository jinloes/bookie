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
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReportPolicyMigrationTest {

  @Nested
  class MigrateFromV10 {

    @Test
    void preservesLegacyRowsAndBuildsDeterministicCategoryAndReportPolicyMaps() throws Exception {
      String url = databaseUrl("report_policy");
      migrateToV10(url);

      List<List<String>> categoriesBefore;
      List<List<String>> activitiesBefore;
      List<List<String>> incomesBefore;
      List<List<String>> expensesBefore;
      List<List<String>> pendingIncomesBefore;
      List<List<String>> pendingExpensesBefore;
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        insertRepresentativeFinancialRows(statement);
        statement.executeUpdate(
            """
            INSERT INTO financial_categories
                (
                    category_key,
                    label,
                    direction,
                    tax_treatment,
                    tax_line,
                    active,
                    system
                )
            VALUES (
                'CUSTOM_CLASSROOM_BOOKS',
                'Classroom books',
                'EXPENSE',
                'W2',
                'Custom educator detail',
                FALSE,
                FALSE
            )
            """);
        categoriesBefore = snapshot(statement, "financial_categories");
        activitiesBefore = snapshot(statement, "financial_activities");
        incomesBefore = snapshot(statement, "incomes");
        expensesBefore = snapshot(statement, "expenses");
        pendingIncomesBefore = snapshot(statement, "pending_incomes");
        pendingExpensesBefore = snapshot(statement, "pending_expenses");
      }

      Flyway.configure().dataSource(url, "sa", "").load().migrate();

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(snapshot(statement, "financial_categories")).isEqualTo(categoriesBefore);
        assertThat(snapshot(statement, "financial_activities")).isEqualTo(activitiesBefore);
        assertThat(snapshot(statement, "incomes")).isEqualTo(incomesBefore);
        assertThat(snapshot(statement, "expenses")).isEqualTo(expensesBefore);
        assertThat(snapshot(statement, "pending_incomes")).isEqualTo(pendingIncomesBefore);
        assertThat(snapshot(statement, "pending_expenses")).isEqualTo(pendingExpensesBefore);

        assertAlias(statement, "ADVERTISING", "ADVERTISING", "SCHEDULE_C_ADVERTISING");
        assertAlias(statement, "INSURANCE", "INSURANCE", "SCHEDULE_C_INSURANCE");
        assertAlias(
            statement,
            "LEGAL_AND_PROFESSIONAL",
            "LEGAL_AND_PROFESSIONAL",
            "SCHEDULE_C_LEGAL_AND_PROFESSIONAL");
        assertAlias(statement, "REPAIRS_AND_MAINTENANCE", "REPAIRS", "SCHEDULE_C_REPAIRS");
        assertAlias(statement, "SUPPLIES", "SUPPLIES", "SCHEDULE_C_SUPPLIES");
        assertAlias(statement, "UTILITIES", "UTILITIES", "SCHEDULE_C_UTILITIES");
        assertAlias(
            statement, "OTHER_EXPENSE", "OTHER", "SCHEDULE_C_OTHER_EXPENSE", "OTHER_EXPENSE");
        assertAlias(statement, "REIMBURSEMENT", "REIMBURSEMENT", "OTHER_REIMBURSEMENT");

        assertThat(
                queryRows(
                    statement,
                    """
                    SELECT
                        neutral.category_key,
                        neutral.label,
                        neutral.direction,
                        neutral.active,
                        neutral.system
                    FROM legacy_category_map legacy_map
                    JOIN neutral_categories neutral
                      ON neutral.id = legacy_map.neutral_category_id
                    WHERE legacy_map.legacy_category_key = 'CUSTOM_CLASSROOM_BOOKS'
                    """))
            .containsExactly(
                List.of("CUSTOM_CLASSROOM_BOOKS", "Classroom books", "EXPENSE", "false", "false"));

        long legacyCategoryCount =
            queryLong(statement, "SELECT COUNT(*) FROM financial_categories");
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM legacy_category_map"))
            .isEqualTo(legacyCategoryCount);
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM category_reporting_mappings"))
            .isEqualTo(legacyCategoryCount);
        assertThat(
                queryLong(
                    statement,
                    """
                    SELECT COUNT(*)
                    FROM financial_categories legacy_category
                    JOIN legacy_category_map legacy_map
                      ON legacy_map.legacy_category_id = legacy_category.id
                    JOIN neutral_categories neutral
                      ON neutral.id = legacy_map.neutral_category_id
                    WHERE legacy_category.category_key NOT IN (
                        'ADVERTISING',
                        'SCHEDULE_C_ADVERTISING',
                        'INSURANCE',
                        'SCHEDULE_C_INSURANCE',
                        'LEGAL_AND_PROFESSIONAL',
                        'SCHEDULE_C_LEGAL_AND_PROFESSIONAL',
                        'REPAIRS',
                        'SCHEDULE_C_REPAIRS',
                        'SUPPLIES',
                        'SCHEDULE_C_SUPPLIES',
                        'UTILITIES',
                        'SCHEDULE_C_UTILITIES',
                        'OTHER',
                        'SCHEDULE_C_OTHER_EXPENSE',
                        'OTHER_EXPENSE',
                        'REIMBURSEMENT',
                        'OTHER_REIMBURSEMENT'
                    )
                      AND neutral.category_key <> legacy_category.category_key
                    """))
            .isZero();
        assertThat(
                queryLong(
                    statement,
                    """
                    SELECT COUNT(*)
                    FROM category_reporting_mappings mapping
                    JOIN financial_categories legacy_category
                      ON legacy_category.id = mapping.legacy_category_id
                    JOIN reporting_profiles profile
                      ON profile.id = mapping.reporting_profile_id
                    WHERE profile.profile_key <> legacy_category.tax_treatment
                       OR mapping.report_line IS DISTINCT FROM legacy_category.tax_line
                       OR mapping.active <> legacy_category.active
                    """))
            .isZero();
        assertThat(
                queryLong(
                    statement,
                    """
                    SELECT COUNT(*)
                    FROM activity_reporting_profile_assignments assignment
                    JOIN financial_activities activity
                      ON activity.id = assignment.activity_id
                    JOIN reporting_profiles profile
                      ON profile.id = assignment.reporting_profile_id
                    WHERE profile.profile_key <> activity.tax_treatment
                       OR assignment.effective_from <> DATE '0001-01-01'
                       OR assignment.effective_to IS NOT NULL
                    """))
            .isZero();
        assertThat(queryLong(statement, "SELECT COUNT(*) FROM financial_activities"))
            .isEqualTo(
                queryLong(
                    statement, "SELECT COUNT(*) FROM activity_reporting_profile_assignments"));
      }
    }

    @Test
    void rejectsAnUnknownSystemCategoryInsteadOfGuessingItsMapping() throws Exception {
      String url = databaseUrl("unknown_system_category");
      migrateToV10(url);
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            """
            INSERT INTO financial_categories
                (
                    category_key,
                    label,
                    direction,
                    tax_treatment,
                    active,
                    system
                )
            VALUES (
                'UNKNOWN_SYSTEM_CATEGORY',
                'Unknown system category',
                'EXPENSE',
                'NONE',
                TRUE,
                TRUE
            )
            """);
      }

      assertThatThrownBy(() -> Flyway.configure().dataSource(url, "sa", "").load().migrate())
          .isInstanceOf(FlywayException.class);
    }
  }

  @Nested
  class EffectiveAssignmentConstraint {

    @Test
    void rejectsOverlappingRangesButAllowsAdjacentRanges() throws Exception {
      String url = databaseUrl("report_policy_overlap");
      Flyway.configure().dataSource(url, "sa", "").load().migrate();

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        long activityId =
            queryLong(
                statement,
                """
                SELECT id
                FROM financial_activities
                WHERE system_key = 'NEEDS_CLASSIFICATION'
                """);
        statement.executeUpdate(
            """
            UPDATE activity_reporting_profile_assignments
            SET effective_to = DATE '2025-12-31'
            WHERE activity_id = %d
            """
                .formatted(activityId));
        statement.executeUpdate(
            """
            INSERT INTO activity_reporting_profile_assignments
                (activity_id, reporting_profile_id, effective_from, effective_to)
            SELECT %d, id, DATE '2026-01-01', NULL
            FROM reporting_profiles
            WHERE profile_key = 'W2'
            """
                .formatted(activityId));

        assertThatThrownBy(
                () ->
                    statement.executeUpdate(
                        """
                        INSERT INTO activity_reporting_profile_assignments
                            (
                                activity_id,
                                reporting_profile_id,
                                effective_from,
                                effective_to
                            )
                        SELECT %d, id, DATE '2025-06-01', DATE '2026-06-01'
                        FROM reporting_profiles
                        WHERE profile_key = 'SCHEDULE_C'
                        """
                            .formatted(activityId)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("must not overlap");
      }
    }
  }

  private void migrateToV10(String url) {
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("10"))
        .load()
        .migrate();
  }

  private void insertRepresentativeFinancialRows(Statement statement) throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO incomes
            (
                amount,
                date,
                description,
                source,
                source_type,
                activity_id,
                category_id
            )
        SELECT
            101.25,
            DATE '2026-01-02',
            'Synthetic migration income',
            'Synthetic source',
            'MANUAL',
            activity.id,
            category.id
        FROM financial_activities activity
        CROSS JOIN financial_categories category
        WHERE activity.system_key = 'NEEDS_CLASSIFICATION'
          AND category.category_key = 'OTHER_INCOME'
        """);
    statement.executeUpdate(
        """
        INSERT INTO expenses
            (
                amount,
                category,
                date,
                description,
                source_type,
                activity_id,
                category_id
            )
        SELECT
            22.75,
            'OTHER',
            DATE '2026-01-03',
            'Synthetic migration expense',
            'MANUAL',
            activity.id,
            category.id
        FROM financial_activities activity
        CROSS JOIN financial_categories category
        WHERE activity.system_key = 'NEEDS_CLASSIFICATION'
          AND category.category_key = 'OTHER_EXPENSE'
        """);
    statement.executeUpdate(
        """
        INSERT INTO pending_incomes
            (
                status,
                amount,
                description,
                date,
                created_at,
                activity_id,
                category_id
            )
        SELECT
            'READY',
            33.50,
            'Synthetic pending income',
            DATE '2026-01-04',
            CURRENT_TIMESTAMP,
            activity.id,
            category.id
        FROM financial_activities activity
        CROSS JOIN financial_categories category
        WHERE activity.system_key = 'NEEDS_CLASSIFICATION'
          AND category.category_key = 'OTHER_INCOME'
        """);
    statement.executeUpdate(
        """
        INSERT INTO pending_expenses
            (
                status,
                email_type,
                amount,
                category,
                description,
                date,
                created_at,
                activity_id,
                category_id
            )
        SELECT
            'READY',
            'EXPENSE',
            44.00,
            'OTHER',
            'Synthetic pending expense',
            DATE '2026-01-05',
            CURRENT_TIMESTAMP,
            activity.id,
            category.id
        FROM financial_activities activity
        CROSS JOIN financial_categories category
        WHERE activity.system_key = 'NEEDS_CLASSIFICATION'
          AND category.category_key = 'OTHER_EXPENSE'
        """);
  }

  private void assertAlias(Statement statement, String expectedNeutralKey, String... legacyKeys)
      throws SQLException {
    String quotedKeys =
        java.util.Arrays.stream(legacyKeys)
            .map(key -> "'" + key.replace("'", "''") + "'")
            .collect(java.util.stream.Collectors.joining(", "));
    assertThat(
            queryRows(
                statement,
                """
                SELECT DISTINCT neutral.category_key
                FROM legacy_category_map legacy_map
                JOIN neutral_categories neutral
                  ON neutral.id = legacy_map.neutral_category_id
                WHERE legacy_map.legacy_category_key IN (%s)
                """
                    .formatted(quotedKeys)))
        .containsExactly(List.of(expectedNeutralKey));
  }

  private List<List<String>> snapshot(Statement statement, String table) throws SQLException {
    String columns =
        switch (table) {
          case "financial_categories" ->
              "id, category_key, label, direction, tax_treatment, tax_line, active, system";
          case "financial_activities" ->
              "id, name, activity_type, tax_treatment, owner_id, property_id, active, system_key";
          case "incomes" ->
              """
              id, amount, date, description, source, property_id, source_id, source_type,
              receipt_file_name, receipt_one_drive_id, payer_id, activity_id, category_id
              """;
          case "expenses" ->
              """
              id, amount, category, date, description, source_id, source_type, payer_id,
              property_id, receipt_file_name, receipt_one_drive_id, activity_id, category_id
              """;
          case "pending_incomes" ->
              """
              id, source_id, source_type, status, source, amount, description, date, property_id,
              payer_id, receipt_one_drive_id, receipt_file_name, error_message, created_at,
              activity_id, category_id, classification_ambiguous
              """;
          case "pending_expenses" ->
              """
              id, amount, category, created_at, date, description, error_message, payer_name,
              property_name, source_id, source_type, status, subject, email_type, activity_id,
              category_id, configured_activity_id, classification_ambiguous
              """;
          default -> throw new IllegalArgumentException("Unsupported legacy snapshot: " + table);
        };
    return queryRows(statement, "SELECT " + columns + " FROM " + table + " ORDER BY id");
  }

  private List<List<String>> queryRows(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      ResultSetMetaData metadata = result.getMetaData();
      List<List<String>> rows = new ArrayList<>();
      while (result.next()) {
        List<String> row = new ArrayList<>();
        for (int column = 1; column <= metadata.getColumnCount(); column++) {
          Object value = result.getObject(column);
          row.add(
              value instanceof byte[] bytes
                  ? HexFormat.of().formatHex(bytes)
                  : String.valueOf(value));
        }
        rows.add(List.copyOf(row));
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

  private String databaseUrl(String prefix) {
    return "jdbc:h2:mem:"
        + prefix
        + "_"
        + UUID.randomUUID().toString().replace("-", "")
        + ";DB_CLOSE_DELAY=-1";
  }
}
