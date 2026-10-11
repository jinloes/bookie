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
    Flyway.configure().dataSource(url, "sa", "").target("19").load().migrate();
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

      assertThat(first.schemaVersion()).isEqualTo("19");
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
    void ignoresTheEventPublicationRegistryOfABackupTakenBeforeV22() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute(
            """
            CREATE TABLE event_publication (
                id UUID NOT NULL PRIMARY KEY,
                completion_date TIMESTAMP(9) WITH TIME ZONE,
                event_type VARCHAR(512) NOT NULL,
                listener_id VARCHAR(512) NOT NULL,
                publication_date TIMESTAMP(9) WITH TIME ZONE NOT NULL,
                serialized_event VARCHAR(4000) NOT NULL
            )
            """);
        statement.executeUpdate(
            """
            INSERT INTO event_publication (
                id, event_type, listener_id, publication_date, serialized_event
            )
            VALUES (
                RANDOM_UUID(), 'Event', 'listener', TIMESTAMP WITH TIME ZONE '2026-01-02 00:00:00Z',
                '{}'
            )
            """);
      }
      MigrationIntegrityManifest before = capture();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM event_publication");
      }
      MigrationIntegrityManifest after = capture();

      assertThat(before.tableCounts()).doesNotContainKey("EVENT_PUBLICATION");
      assertThat(before.rowHashes()).doesNotContainKey("EVENT_PUBLICATION");
      verifier.reconcile(before, after);
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
  class RewrittenColumns {

    private String upgradeUrl;
    private long assignmentId;

    @BeforeEach
    void setUpActivityCreatedAfterV11() throws Exception {
      upgradeUrl =
          "jdbc:h2:mem:integrity_rewrite_"
              + UUID.randomUUID().toString().replace("-", "")
              + ";DB_CLOSE_DELAY=-1";
      Flyway.configure().dataSource(upgradeUrl, "sa", "").target("22").load().migrate();
      try (Connection connection = DriverManager.getConnection(upgradeUrl, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            """
            INSERT INTO financial_activities (name, activity_type, tax_treatment, owner_id)
            VALUES ('Studio', 'SELF_EMPLOYMENT', 'SCHEDULE_C', (SELECT MIN(id) FROM household_members))
            """);
        statement.executeUpdate(
            """
            INSERT INTO activity_reporting_profile_assignments (
                activity_id, reporting_profile_id, effective_from
            )
            VALUES (
                (SELECT id FROM financial_activities WHERE name = 'Studio'),
                (SELECT id FROM reporting_profiles WHERE profile_key = 'SCHEDULE_C'),
                DATE '2026-09-01'
            )
            """,
            Statement.RETURN_GENERATED_KEYS);
        try (var keys = statement.getGeneratedKeys()) {
          assertThat(keys.next()).isTrue();
          assignmentId = keys.getLong(1);
        }
      }
    }

    @Test
    void allowsV23ToExtendAFirstAssignmentBackToTheStartOfHistory() throws Exception {
      MigrationIntegrityManifest before = capture(upgradeUrl);
      Flyway.configure().dataSource(upgradeUrl, "sa", "").target("23").load().migrate();
      MigrationIntegrityManifest after = capture(upgradeUrl);

      assertThat(before.schemaVersion()).isEqualTo("22");
      assertThat(after.schemaVersion()).isEqualTo("23");
      verifier.reconcile(before, after);
    }

    @Test
    void stillComparesTheOtherColumnsOfARewrittenTable() throws Exception {
      MigrationIntegrityManifest before = capture(upgradeUrl);
      Flyway.configure().dataSource(upgradeUrl, "sa", "").target("23").load().migrate();
      execute(
          """
          UPDATE activity_reporting_profile_assignments
          SET reporting_profile_id = (SELECT id FROM reporting_profiles WHERE profile_key = 'W2')
          WHERE id = %d
          """
              .formatted(assignmentId));
      MigrationIntegrityManifest after = capture(upgradeUrl);

      assertThatThrownBy(() -> verifier.reconcile(before, after))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining(
              "ACTIVITY_REPORTING_PROFILE_ASSIGNMENTS canonical row hashes changed");
    }

    @Test
    void failsClosedWhenTheColumnChangesWithoutCrossingV23() throws Exception {
      MigrationIntegrityManifest before = capture(upgradeUrl);
      execute(
          "UPDATE activity_reporting_profile_assignments SET effective_from = DATE '2026-01-01'"
              + " WHERE id = "
              + assignmentId);
      MigrationIntegrityManifest after = capture(upgradeUrl);

      assertThatThrownBy(() -> verifier.reconcile(before, after))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining(
              "ACTIVITY_REPORTING_PROFILE_ASSIGNMENTS canonical row hashes changed");
    }

    @Test
    void comparesEveryColumnOfADatabaseThatPredatesFlyway() throws Exception {
      String preFlywayUrl =
          "jdbc:h2:mem:integrity_pre_flyway_"
              + UUID.randomUUID().toString().replace("-", "")
              + ";DB_CLOSE_DELAY=-1";
      try (Connection connection = DriverManager.getConnection(preFlywayUrl, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE legacy_rows(id BIGINT PRIMARY KEY, label VARCHAR(20))");
        statement.execute("INSERT INTO legacy_rows VALUES (1, 'kept')");
      }
      MigrationIntegrityManifest before = capture(preFlywayUrl);
      try (Connection connection = DriverManager.getConnection(preFlywayUrl, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute("UPDATE legacy_rows SET label = 'changed'");
      }
      MigrationIntegrityManifest after = capture(preFlywayUrl);

      assertThat(before.schemaVersion()).isEqualTo("unversioned");
      assertThatThrownBy(() -> verifier.reconcile(before, after))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("LEGACY_ROWS canonical row hashes changed");
    }

    private void execute(String sql) throws Exception {
      try (Connection connection = DriverManager.getConnection(upgradeUrl, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate(sql);
      }
    }
  }

  @Nested
  class RetiredTables {

    @Test
    void allowsRetiredTablesToDisappearWhenActiveUnifiedTotalsMatch() throws Exception {
      createUnifiedLedger("100.00", "25.00");
      insertMirroredPayer();
      MigrationIntegrityManifest before = capture();

      retireLegacyTables();

      verifier.reconcile(before, capture());
    }

    @Test
    void rejectsRetiredLedgerTotalsThatDifferFromActiveUnifiedTransactions() throws Exception {
      createUnifiedLedger("100.00", "24.00");
      MigrationIntegrityManifest before = capture();

      retireLegacyTables();

      assertThatThrownBy(() -> verifier.reconcile(before, capture()))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("retired legacy ledger totals differ");
    }

    @Test
    void rejectsTheDisappearanceOfATableThatIsNotRetired() throws Exception {
      createUnifiedLedger("100.00", "25.00");
      MigrationIntegrityManifest before = capture();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute("DROP TABLE receipt_hashes");
      }
      retireLegacyTables();

      assertThatThrownBy(() -> verifier.reconcile(before, capture()))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("table RECEIPT_HASHES disappeared but is not retired");
    }

    @Test
    void rejectsASuccessorThatLostRetiredRows() throws Exception {
      createUnifiedLedger("100.00", "25.00");
      insertMirroredPayer();
      MigrationIntegrityManifest before = capture();
      retireLegacyTables();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM counterparty_accounts");
      }

      assertThatThrownBy(() -> verifier.reconcile(before, capture()))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining(
              "retired table PAYER_ACCOUNTS count 1 does not match successor"
                  + " COUNTERPARTY_ACCOUNTS count 0");
    }

    @Test
    void enumAndVarcharColumnsShareTheStringFamilySoEqualValuesHashEqually() throws Exception {
      insertMirroredPayer();

      MigrationIntegrityManifest manifest = capture();

      assertThat(manifest.tableColumns().get("PAYERS")).contains("TYPE:STRING");
      assertThat(manifest.tableColumns().get("COUNTERPARTIES")).contains("TYPE:STRING");
      String payerType =
          manifest.preservedColumnHashes().get("PAYERS").values().iterator().next().get("TYPE");
      String mirrorType =
          manifest
              .preservedColumnHashes()
              .get("COUNTERPARTIES")
              .values()
              .iterator()
              .next()
              .get("TYPE");
      assertThat(payerType).isEqualTo(mirrorType);
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

  @Nested
  class CaptureBookieTables {

    @Test
    void leavesOutJobRunrTables() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
        MigrationIntegrityManifest manifest =
            verifier.captureBookieTables(connection, "test", null);

        assertThat(manifest.tableCounts()).containsKey("INCOMES");
        assertThat(manifest.tableCounts().keySet()).noneMatch(name -> name.startsWith("JOBRUNR_"));
      }
    }

    @Test
    void doesNotSwitchACallerOwnedTransactionToReadOnly() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
        connection.setAutoCommit(false);

        verifier.captureBookieTables(connection, "test", null);

        assertThat(connection.isReadOnly()).isFalse();
      }
    }
  }

  @Nested
  class DuplicateConstraintDefinitions {

    @Test
    void checksEachUniqueKeyAndForeignKeyOnce() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE dup_parent (id BIGINT PRIMARY KEY)");
        statement.execute(
            """
            CREATE TABLE dup_child (
                id BIGINT PRIMARY KEY,
                code VARCHAR(10),
                parent_id BIGINT,
                CONSTRAINT dup_child_code_a UNIQUE (code),
                CONSTRAINT dup_child_parent_a FOREIGN KEY (parent_id) REFERENCES dup_parent (id),
                CONSTRAINT dup_child_parent_b FOREIGN KEY (parent_id) REFERENCES dup_parent (id)
            )
            """);
        statement.execute("CREATE UNIQUE INDEX dup_child_code_b ON dup_child (code)");
      }

      MigrationIntegrityManifest manifest = capture();

      assertThat(manifest.uniquenessChecks())
          .extracting(MigrationIntegrityManifest.ConstraintCheck::name)
          .containsOnlyOnce("DUP_CHILD.UNIQUE(CODE)");
      assertThat(manifest.orphanChecks())
          .extracting(MigrationIntegrityManifest.ConstraintCheck::name)
          .containsOnlyOnce("DUP_CHILD.FK(PARENT_ID)->DUP_PARENT");
    }
  }

  @Nested
  class CurrentSchemaVersion {

    @Test
    void skipsFailedAndRepeatableMigrations() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            """
            INSERT INTO "flyway_schema_history" (
                "installed_rank", "version", "description", "type", "script", "installed_by",
                "execution_time", "success"
            )
            VALUES
                (1000, '20', 'broken', 'SQL', 'V20__broken.sql', 'sa', 0, FALSE),
                (1001, NULL, 'views', 'SQL', 'R__views.sql', 'sa', 0, TRUE)
            """);

        assertThat(verifier.currentSchemaVersion(connection)).isEqualTo("19");
      }
    }

    @Test
    void isUnversionedWhenNoMigrationSucceeded() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM \"flyway_schema_history\"");

        assertThat(verifier.currentSchemaVersion(connection))
            .isEqualTo(MigrationIntegrityVerifier.UNVERSIONED);
      }
    }
  }

  @Nested
  class CanonicalValue {

    @Test
    void readsEachTypeFamilyThroughItsVendorNeutralGetter() throws Exception {
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement();
          var rows =
              statement.executeQuery(
                  """
                  SELECT CAST(1.5 AS DOUBLE), CAST(NULL AS DOUBLE),
                         CAST('6F1D0A6E-0000-4000-8000-000000000001' AS UUID), CAST(NULL AS UUID),
                         X'CAFE', CAST(NULL AS VARBINARY),
                         TIME '10:15:30', TIMESTAMP WITH TIME ZONE '2026-01-02 03:04:05+02:00'
                  """)) {
        rows.next();

        assertThat(MigrationIntegrityVerifier.canonicalValue(rows, 1, "FLOAT")).isEqualTo("1.5");
        assertThat(MigrationIntegrityVerifier.canonicalValue(rows, 2, "FLOAT")).isEqualTo("<NULL>");
        assertThat(MigrationIntegrityVerifier.canonicalValue(rows, 3, "UUID"))
            .isEqualTo("6f1d0a6e-0000-4000-8000-000000000001");
        assertThat(MigrationIntegrityVerifier.canonicalValue(rows, 4, "UUID")).isEqualTo("<NULL>");
        assertThat(MigrationIntegrityVerifier.canonicalValue(rows, 5, "BINARY")).isEqualTo("yv4=");
        assertThat(MigrationIntegrityVerifier.canonicalValue(rows, 6, "BINARY"))
            .isEqualTo("<NULL>");
        assertThat(MigrationIntegrityVerifier.canonicalValue(rows, 7, "TIME"))
            .isEqualTo("10:15:30");
        assertThat(MigrationIntegrityVerifier.canonicalValue(rows, 8, "TIMESTAMP_TZ"))
            .isEqualTo("2026-01-02T03:04:05+02:00");
      }
    }
  }

  private MigrationIntegrityManifest capture() throws Exception {
    return capture(url);
  }

  private MigrationIntegrityManifest capture(String databaseUrl) throws Exception {
    try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "")) {
      return verifier.capture(connection, "test", null);
    }
  }

  private void insertMirroredPayer() throws Exception {
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO payers (id, name, type) VALUES (7, 'Acme', 'COMPANY')");
      statement.executeUpdate("INSERT INTO payer_aliases (payer_id, alias) VALUES (7, 'ACME')");
      statement.executeUpdate(
          "INSERT INTO payer_accounts (payer_id, account_number) VALUES (7, 'acct-1')");
      statement.executeUpdate(
          "INSERT INTO counterparties (id, name, type) VALUES (7, 'Acme', 'COMPANY')");
      statement.executeUpdate(
          "INSERT INTO counterparty_aliases (counterparty_id, alias) VALUES (7, 'ACME')");
      statement.executeUpdate(
          "INSERT INTO counterparty_accounts (counterparty_id, account_number)"
              + " VALUES (7, 'acct-1')");
      statement.executeUpdate(
          "INSERT INTO legacy_payer_map (payer_id, counterparty_id) VALUES (7, 7)");
    }
  }

  /** Applies only V20's DDL, so these tests exercise the verifier rather than its prechecks. */
  private void retireLegacyTables() throws Exception {
    String migration;
    try (var input = getClass().getResourceAsStream("/db/migration/V20__retire_legacy_flows.sql")) {
      migration = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    String ddl = migration.substring(migration.indexOf("-- DDL."));
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      for (String sql : ddl.split(";")) {
        String executable =
            sql.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .collect(java.util.stream.Collectors.joining("\n"))
                .trim();
        if (!executable.isEmpty()) {
          statement.execute(executable);
        }
      }
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
