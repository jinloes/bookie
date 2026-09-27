package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Backups taken before the legacy tables were retired must still restore: through every migration,
 * V20's prechecks and the verifier's retired-table rules.
 */
@Tag("slow")
class LegacyBackupRestoreRegressionTest {

  private static final long MAX_BYTES = 20L * 1024 * 1024;
  private static final List<String> RETIRED_TABLES =
      List.of(
          "EXPENSES",
          "INCOMES",
          "PENDING_EXPENSES",
          "PENDING_INCOMES",
          "PAYERS",
          "PAYER_ALIASES",
          "PAYER_ACCOUNTS",
          "LEGACY_PAYER_MAP",
          "LEGACY_TRANSACTION_MAP",
          "LEGACY_INBOX_MAP");

  @TempDir Path directory;

  private RestorePaths paths;
  private RestoreJournalStore journalStore;
  private ShadowRestoreService service;
  private String originalLiveHash;

  @BeforeEach
  void setUp() throws Exception {
    paths = new RestorePaths(directory);
    journalStore = new RestoreJournalStore(paths);
    String liveUrl = h2Url(directory.resolve("bookiedb"));
    Flyway.configure().dataSource(liveUrl, "sa", "").load().migrate();
    try (Connection connection = DriverManager.getConnection(liveUrl, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    originalLiveHash = MigrationIntegrityManifestCodec.sha256(paths.liveDatabaseFile());
    service =
        new ShadowRestoreService(
            new MigrationIntegrityVerifier(),
            paths,
            journalStore,
            new RestoreDiskSpaceChecker(),
            MAX_BYTES,
            0,
            "test",
            Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC));
  }

  @ParameterizedTest
  @ValueSource(strings = {"12", "19"})
  void restoresAPreRetirementBackupThroughTheLatestMigration(String version) throws Exception {
    byte[] backup = backupAt(version, false);

    RestoreJournal journal =
        service.stage(
            "legacy-" + version, "legacy.sql", backup.length, new ByteArrayInputStream(backup));

    assertThat(journal.state()).isEqualTo(RestoreState.VALIDATED);
    var manifest = new MigrationIntegrityManifestCodec().read(Path.of(journal.manifestFile()));
    assertThat(manifest.schemaVersion()).isEqualTo("24");
    assertThat(manifest.tableCounts()).doesNotContainKeys(RETIRED_TABLES.toArray(String[]::new));
    String shadowUrl = "jdbc:h2:file:" + journal.shadowFile().replace(".mv.db", "");
    try (Connection connection = DriverManager.getConnection(shadowUrl, "sa", "");
        Statement statement = connection.createStatement()) {
      assertThat(
              rows(
                  statement,
                  """
                  SELECT transaction.direction, CAST(transaction.amount AS VARCHAR),
                         transaction.description, counterparty.name
                  FROM financial_transactions transaction
                  LEFT JOIN counterparties counterparty
                    ON counterparty.id = transaction.counterparty_id
                  WHERE transaction.deleted_at IS NULL
                  """))
          .containsExactlyInAnyOrder(
              List.of("EXPENSE", "15.25", "Faucet repair", "Acme Plumbing"),
              List.of("INCOME", "1200.00", "August rent", "null"));
      assertThat(
              rows(
                  statement,
                  """
                  SELECT counterparty.name, CAST(counterparty.type AS VARCHAR), alias.alias,
                         account.account_number
                  FROM counterparties counterparty
                  JOIN counterparty_aliases alias ON alias.counterparty_id = counterparty.id
                  JOIN counterparty_accounts account
                    ON account.counterparty_id = counterparty.id
                  """))
          .containsExactly(List.of("Acme Plumbing", "COMPANY", "ACME", "acct-1"));
      assertThat(
              rows(
                  statement,
                  """
                  SELECT origin, state, CAST(proposed_amount AS VARCHAR), proposed_description
                  FROM inbox_items
                  """))
          .containsExactly(List.of("RECEIPT", "READY", "42.00", "Receipt to review"));
    }
    assertThat(MigrationIntegrityManifestCodec.sha256(paths.liveDatabaseFile()))
        .isEqualTo(originalLiveHash);
  }

  @Test
  void rejectsADriftedV19BackupBeforeActivationWithoutTouchingLive() throws Exception {
    byte[] backup = backupAt("19", true);

    assertThatThrownBy(
            () ->
                service.stage(
                    "drifted", "drifted.sql", backup.length, new ByteArrayInputStream(backup)))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Flyway validation or migration");

    assertThat(paths.liveDatabaseFile()).isRegularFile();
    assertThat(MigrationIntegrityManifestCodec.sha256(paths.liveDatabaseFile()))
        .isEqualTo(originalLiveHash);
    assertThat(journalStore.read()).isEmpty();
  }

  /**
   * Builds an H2 SCRIPT backup the way a release at {@code version} would have left it: legacy rows
   * written at V11 and carried forward by the released migrations that mirror them.
   */
  private byte[] backupAt(String version, boolean driftUnifiedLedger) throws Exception {
    Path database = directory.resolve("source-" + version + (driftUnifiedLedger ? "-drift" : ""));
    String url = h2Url(database);
    Flyway.configure().dataSource(url, "sa", "").target("11").load().migrate();
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      long payerId =
          insertReturningId(
              statement, "INSERT INTO payers (name, type) VALUES ('Acme Plumbing', 'COMPANY')");
      statement.executeUpdate(
          "INSERT INTO payer_aliases (payer_id, alias) VALUES (%d, 'ACME')".formatted(payerId));
      statement.executeUpdate(
          "INSERT INTO payer_accounts (payer_id, account_number) VALUES (%d, 'acct-1')"
              .formatted(payerId));
      statement.executeUpdate(
          """
          INSERT INTO expenses (
              amount, category, date, description, source_id, source_type, payer_id,
              activity_id, category_id
          )
          VALUES (
              15.25, 'OTHER', DATE '2025-08-20', 'Faucet repair', 'manual-1', 'MANUAL', %d,
              (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
              (SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE')
          )
          """
              .formatted(payerId));
      statement.executeUpdate(
          """
          INSERT INTO incomes (amount, date, description, source, activity_id, category_id)
          VALUES (
              1200.00, DATE '2025-08-01', 'August rent', 'Tenant',
              (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
              (SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME')
          )
          """);
      statement.executeUpdate(
          """
          INSERT INTO pending_expenses (
              amount, created_at, date, description, source_id, source_type, status,
              activity_id, category_id, classification_ambiguous
          )
          VALUES (
              42.00, TIMESTAMP '2025-08-02 10:00:00', DATE '2025-08-02', 'Receipt to review',
              'drive-1', 'RECEIPT', 'READY',
              (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
              (SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'),
              TRUE
          )
          """);
    }
    Flyway.configure().dataSource(url, "sa", "").target(version).load().migrate();
    Path script = directory.resolve("backup-" + version + (driftUnifiedLedger ? "-drift" : ""));
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      if (driftUnifiedLedger) {
        statement.executeUpdate(
            "UPDATE financial_transactions SET amount = amount + 1 WHERE direction = 'EXPENSE'");
      }
      String safeScript = script.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
      statement.execute("SCRIPT TO '" + safeScript + "'");
      statement.execute("SHUTDOWN");
    }
    return Files.readAllBytes(script);
  }

  private static String h2Url(Path databaseBase) {
    return "jdbc:h2:file:" + databaseBase.toAbsolutePath().toString().replace("\\", "/");
  }

  private static long insertReturningId(Statement statement, String sql) throws SQLException {
    statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    try (ResultSet keys = statement.getGeneratedKeys()) {
      assertThat(keys.next()).isTrue();
      return keys.getLong(1);
    }
  }

  private static List<List<String>> rows(Statement statement, String sql) throws SQLException {
    List<List<String>> rows = new ArrayList<>();
    try (ResultSet result = statement.executeQuery(sql)) {
      int columns = result.getMetaData().getColumnCount();
      while (result.next()) {
        List<String> row = new ArrayList<>();
        for (int column = 1; column <= columns; column++) {
          row.add(String.valueOf(result.getString(column)));
        }
        rows.add(row);
      }
    }
    return rows;
  }
}
