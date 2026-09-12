package com.bookie.datalifecycle.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MigrationIntegrityVerifierTest {

  private final MigrationIntegrityVerifier verifier = new MigrationIntegrityVerifier();
  private String url;

  @BeforeEach
  void setUp() throws Exception {
    url =
        "jdbc:h2:mem:integrity_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO incomes (
              amount, date, description, source, source_id, source_type, activity_id, category_id
          )
          VALUES (
              100.00,
              DATE '2026-01-02',
              'Synthetic income',
              'Fixture',
              'income-source',
              'MANUAL',
              (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
              (SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME')
          )
          """);
      statement.executeUpdate(
          """
          INSERT INTO expenses (
              amount, category, date, description, source_id, source_type, activity_id, category_id
          )
          VALUES (
              25.00,
              'OTHER',
              DATE '2026-01-03',
              'Synthetic expense',
              'expense-source',
              'MANUAL',
              (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
              (SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE')
          )
          """);
    }
  }

  @Nested
  class Capture {

    @Test
    void recordsCountsTotalsReferencesConstraintsAndCanonicalHashes() throws Exception {
      MigrationIntegrityManifest first = capture();
      MigrationIntegrityManifest second = capture();

      assertThat(first.schemaVersion()).isEqualTo("15");
      assertThat(first.tableCounts()).containsKey("JOBRUNR_METADATA").containsKey("JOBRUNR_JOBS");
      assertThat(first.tableCounts()).containsEntry("INCOMES", 1L).containsEntry("EXPENSES", 1L);
      assertThat(first.referenceCounts())
          .containsEntry("INCOMES.SOURCE_ID", 1L)
          .containsEntry("EXPENSES.SOURCE_ID", 1L);
      assertThat(first.financialAggregates())
          .extracting(MigrationIntegrityManifest.FinancialAggregate::direction)
          .containsExactlyInAnyOrder("INCOME", "EXPENSE");
      assertThat(first.rowHashes().get("INCOMES")).singleElement().asString().hasSize(64);
      assertThat(first.tableIdentityColumns()).containsEntry("INCOMES", List.of("ID"));
      assertThat(first.preservedColumnHashes().get("INCOMES").values())
          .singleElement()
          .satisfies(columns -> assertThat(columns).containsKeys("ID", "AMOUNT"));
      assertThat(first.sourceDatabaseHash()).isEqualTo(second.sourceDatabaseHash());
      verifier.assertHealthy(first);
    }

    @Test
    void reportsForeignKeyOrphansInsteadOfOmittingThem() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
        statement.executeUpdate("UPDATE incomes SET activity_id = 999999");
      }

      MigrationIntegrityManifest manifest = capture();

      assertThatThrownBy(() -> verifier.assertHealthy(manifest))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("orphan check");
    }
  }

  @Nested
  class Reconcile {

    @Test
    void vendorMetadataMutationCannotBeExcludedFromRestoreReconciliation() throws Exception {
      MigrationIntegrityManifest before = capture();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE jobrunr_metadata SET `value` = '1'");
      }
      assertThatThrownBy(() -> verifier.reconcile(before, capture()))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("JOBRUNR_METADATA");
    }

    @Test
    void allowsAdditiveTablesWhenEveryReleasedRowIsUnchanged() throws Exception {
      MigrationIntegrityManifest before = capture();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE additive_table(id BIGINT PRIMARY KEY)");
        statement.execute("INSERT INTO additive_table VALUES (1)");
      }
      MigrationIntegrityManifest after = capture();

      verifier.reconcile(before, after);
    }

    @Test
    void allowsAdditiveColumnsWhileProvingEveryExistingCellIsUnchanged() throws Exception {
      MigrationIntegrityManifest before = capture();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute(
            "ALTER TABLE incomes ADD COLUMN migration_marker VARCHAR(20) DEFAULT 'copied' NOT NULL");
      }
      MigrationIntegrityManifest after = capture();

      verifier.reconcile(before, after);
    }

    @Test
    void reconcilesPendingRowsWhoseAmountsAndDatesAreNotKnownYet() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            """
            INSERT INTO pending_expenses
                (created_at, status, activity_id, category_id)
            SELECT
                CURRENT_TIMESTAMP,
                'FAILED',
                (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
                id
            FROM financial_categories
            WHERE category_key IN ('OTHER_EXPENSE', 'UTILITIES')
            """);
      }
      MigrationIntegrityManifest before = capture();
      MigrationIntegrityManifest after = capture();

      verifier.reconcile(before, after);
    }

    @Test
    void preservesRowsWithoutPrimaryKeysAcrossAdditiveColumns() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE legacy_tags(label VARCHAR(30) NOT NULL)");
        statement.execute("INSERT INTO legacy_tags VALUES ('preserve-me')");
      }
      MigrationIntegrityManifest before = capture();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute("ALTER TABLE legacy_tags ADD COLUMN context VARCHAR(20)");
      }
      MigrationIntegrityManifest after = capture();

      verifier.reconcile(before, after);
    }

    @Test
    void failsClosedWhenAReleasedFinancialRowChanges() throws Exception {
      MigrationIntegrityManifest before = capture();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE incomes SET amount = 99.00");
      }
      MigrationIntegrityManifest after = capture();

      assertThatThrownBy(() -> verifier.reconcile(before, after))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("canonical row hashes changed")
          .hasMessageContaining("financial aggregates changed");
    }

    @Test
    void failsClosedWhenAReleasedRowDisappears() throws Exception {
      MigrationIntegrityManifest before = capture();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM expenses");
      }
      MigrationIntegrityManifest after = capture();

      assertThatThrownBy(() -> verifier.reconcile(before, after))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("EXPENSES count changed from 1 to 0");
    }
  }

  @Nested
  class CompatibilityParity {

    @Test
    void acceptsEquivalentUnifiedAndLegacyLedgerViews() throws Exception {
      createUnifiedLedger("100.00", "25.00");

      MigrationIntegrityManifest manifest = capture();

      verifier.assertFinancialParity(
          manifest, List.of("incomes", "expenses"), List.of("financial_transactions"));
    }

    @Test
    void rejectsDivergentUnifiedAndLegacyLedgerViews() throws Exception {
      createUnifiedLedger("100.00", "24.00");

      MigrationIntegrityManifest manifest = capture();

      assertThatThrownBy(
              () ->
                  verifier.assertFinancialParity(
                      manifest, List.of("incomes", "expenses"), List.of("financial_transactions")))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("diverged");
    }
  }

  private MigrationIntegrityManifest capture() throws Exception {
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      return verifier.capture(connection, "test", null);
    }
  }

  private void createUnifiedLedger(String incomeAmount, String expenseAmount) throws Exception {
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO financial_transactions (
              id, amount, direction, transaction_date, description, activity_id,
              neutral_category_id, created_at, updated_at, version
          )
          SELECT
              1, %s, 'INCOME', income.date, income.description, income.activity_id,
              category_map.neutral_category_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
          FROM incomes income
          JOIN legacy_category_map category_map
            ON category_map.legacy_category_id = income.category_id
          """
              .formatted(incomeAmount));
      statement.executeUpdate(
          """
          INSERT INTO financial_transactions (
              id, amount, direction, transaction_date, description, activity_id,
              neutral_category_id, created_at, updated_at, version
          )
          SELECT
              2, %s, 'EXPENSE', expense.date, expense.description, expense.activity_id,
              category_map.neutral_category_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
          FROM expenses expense
          JOIN legacy_category_map category_map
            ON category_map.legacy_category_id = expense.category_id
          """
              .formatted(expenseAmount));
    }
  }
}
