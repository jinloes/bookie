package com.bookie.datalifecycle.portable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest.FinancialAggregate;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import com.bookie.util.BackgroundWorkControl;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class PortableBackupImporterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-03T02:00:00Z"), ZoneOffset.UTC);
  private static final int ROWS = 501;

  @TempDir Path dir;

  private JdbcDataSource dataSource;
  private final PortableBackupArchive archive = new PortableBackupArchive();
  private final MigrationIntegrityVerifier verifier = new MigrationIntegrityVerifier();
  private final MigrationIntegrityManifestCodec codec = new MigrationIntegrityManifestCodec();

  @SuppressWarnings("unchecked")
  private final ObjectProvider<BackgroundWorkControl> noControl = mock(ObjectProvider.class);

  @BeforeEach
  void setUp() throws Exception {
    dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:import_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    Flyway.configure().dataSource(dataSource).load().migrate();
    // More rows than one insert batch so the batch flush and the remainder both run.
    execute(
        """
        INSERT INTO financial_transactions (
            amount, direction, transaction_date, description, activity_id, neutral_category_id
        )
        SELECT
            X + 0.25,
            'INCOME',
            DATE '2026-08-24',
            'Row ' || X,
            (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
            (SELECT MIN(id) FROM neutral_categories WHERE direction = 'INCOME')
        FROM SYSTEM_RANGE(1, %d)
        """
            .formatted(ROWS));
  }

  private void execute(String sql) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private long single(String sql) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private MigrationIntegrityManifest capture() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      return verifier.captureBookieTables(connection, "test", "");
    }
  }

  private Path exportBackup() throws Exception {
    return new PortableBackupExporter(dataSource, verifier, archive, CLOCK, "test")
        .export(dir.resolve("backup.zip"))
        .archive();
  }

  private PortableBackupImporter importer(
      MigrationIntegrityVerifier verifier, ObjectProvider<BackgroundWorkControl> control) {
    return new PortableBackupImporter(dataSource, verifier, archive, control, "test");
  }

  private PortableBackupImporter importer() {
    return importer(verifier, noControl);
  }

  // Rewrites a valid export with altered contents so it still passes archive verification.
  private Path rewrite(
      UnaryOperator<PortableBackupArchive.Manifest> manifest,
      UnaryOperator<Map<String, List<Map<String, Object>>>> rows)
      throws Exception {
    PortableBackupArchive.Contents contents = archive.read(exportBackup());
    Map<String, List<Map<String, Object>>> copied = new TreeMap<>();
    contents.rows().forEach((table, list) -> copied.put(table, copyRows(list)));
    Path target = dir.resolve("rewritten.zip");
    archive.write(target, manifest.apply(contents.manifest()), rows.apply(copied));
    return target;
  }

  private static List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
    List<Map<String, Object>> copy = new ArrayList<>();
    rows.forEach(row -> copy.add(new HashMap<>(row)));
    return copy;
  }

  private MigrationIntegrityManifest withSchemaVersion(
      MigrationIntegrityManifest manifest, String schemaVersion) {
    return codec.seal(
        new MigrationIntegrityManifest(
            manifest.manifestVersion(),
            schemaVersion,
            manifest.applicationVersion(),
            manifest.sourceDatabaseHash(),
            manifest.tableCounts(),
            manifest.tableColumns(),
            manifest.tableIdentityColumns(),
            manifest.financialAggregates(),
            manifest.referenceCounts(),
            manifest.uniquenessChecks(),
            manifest.orphanChecks(),
            manifest.rowHashes(),
            manifest.preservedColumnHashes(),
            ""));
  }

  private MigrationIntegrityManifest withColumns(
      MigrationIntegrityManifest manifest, String table, List<String> columns) {
    TreeMap<String, List<String>> tableColumns = new TreeMap<>(manifest.tableColumns());
    if (columns == null) {
      tableColumns.remove(table);
    } else {
      tableColumns.put(table, columns);
    }
    return codec.seal(
        new MigrationIntegrityManifest(
            manifest.manifestVersion(),
            manifest.schemaVersion(),
            manifest.applicationVersion(),
            manifest.sourceDatabaseHash(),
            manifest.tableCounts(),
            tableColumns,
            manifest.tableIdentityColumns(),
            manifest.financialAggregates(),
            manifest.referenceCounts(),
            manifest.uniquenessChecks(),
            manifest.orphanChecks(),
            manifest.rowHashes(),
            manifest.preservedColumnHashes(),
            ""));
  }

  private static PortableBackupArchive.Manifest withIntegrity(
      PortableBackupArchive.Manifest manifest, MigrationIntegrityManifest integrity) {
    return new PortableBackupArchive.Manifest(
        manifest.formatVersion(),
        manifest.flywayVersion(),
        manifest.vendor(),
        manifest.createdAt(),
        manifest.tables(),
        integrity);
  }

  @Nested
  class ImportArchive {

    @Test
    void replacesEveryRowAndReseedsIdentities() throws Exception {
      MigrationIntegrityManifest original = capture();
      Path backup = exportBackup();
      long maxId = single("SELECT MAX(id) FROM financial_transactions");
      execute("DELETE FROM financial_transactions WHERE id = " + maxId);
      execute("UPDATE financial_transactions SET description = 'changed'");

      PortableBackupImporter.ImportResult result = importer().importArchive(backup);

      assertThat(result.after().rowHashes()).isEqualTo(original.rowHashes());
      assertThat(result.archiveManifest().flywayVersion()).isEqualTo("24");
      assertThat(single("SELECT COUNT(*) FROM financial_transactions")).isEqualTo(ROWS);
      execute(
          """
          INSERT INTO financial_transactions (
              amount, direction, transaction_date, description, activity_id, neutral_category_id
          )
          SELECT amount, direction, transaction_date, 'new', activity_id, neutral_category_id
          FROM financial_transactions WHERE id = %d
          """
              .formatted(maxId));
      assertThat(single("SELECT MAX(id) FROM financial_transactions")).isGreaterThan(maxId);
    }

    @Test
    void reseedsAboveRowsTheBackupDoesNotContain() throws Exception {
      Path backup = exportBackup();
      execute(
          """
          INSERT INTO financial_transactions (
              amount, direction, transaction_date, description, activity_id, neutral_category_id
          )
          SELECT amount, direction, transaction_date, 'later', activity_id, neutral_category_id
          FROM financial_transactions WHERE id = (SELECT MIN(id) FROM financial_transactions)
          """);
      long laterId = single("SELECT MAX(id) FROM financial_transactions");

      importer().importArchive(backup);
      execute(
          """
          INSERT INTO financial_transactions (
              amount, direction, transaction_date, description, activity_id, neutral_category_id
          )
          SELECT amount, direction, transaction_date, 'next', activity_id, neutral_category_id
          FROM financial_transactions WHERE id = (SELECT MIN(id) FROM financial_transactions)
          """);

      assertThat(single("SELECT MAX(id) FROM financial_transactions")).isGreaterThan(laterId);
    }

    @Test
    void releasesEngineBindingsWhenWorkControlIsAvailable() throws Exception {
      Path backup = exportBackup();
      BackgroundWorkControl control = mock(BackgroundWorkControl.class);
      @SuppressWarnings("unchecked")
      ObjectProvider<BackgroundWorkControl> provider = mock(ObjectProvider.class);
      when(provider.getIfAvailable()).thenReturn(control);

      importer(verifier, provider).importArchive(backup);

      verify(control).releaseEngineBindings();
    }
  }

  @Nested
  class Refusals {

    private void assertRefusedUnchanged(Path backup, String message) throws Exception {
      MigrationIntegrityManifest before = capture();

      assertThatThrownBy(() -> importer().importArchive(backup))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining(message);
      assertThat(capture().rowHashes()).isEqualTo(before.rowHashes());
    }

    @Test
    void refusesADifferentFlywayVersion() throws Exception {
      Path backup =
          rewrite(
              manifest ->
                  new PortableBackupArchive.Manifest(
                      manifest.formatVersion(),
                      "23",
                      manifest.vendor(),
                      manifest.createdAt(),
                      manifest.tables(),
                      manifest.integrityManifest()),
              rows -> rows);

      assertRefusedUnchanged(backup, "Backup schema version 23 does not match");
    }

    @Test
    void refusesAnIntegrityManifestFromAnotherSchemaVersion() throws Exception {
      Path backup =
          rewrite(
              manifest ->
                  withIntegrity(manifest, withSchemaVersion(manifest.integrityManifest(), "23")),
              rows -> rows);

      assertRefusedUnchanged(backup, "Backup schema version 24 does not match");
    }

    @Test
    void refusesADifferentTableSet() throws Exception {
      Path backup =
          rewrite(
              manifest -> manifest,
              rows -> {
                rows.remove("FINANCIAL_TRANSACTIONS");
                return rows;
              });

      assertRefusedUnchanged(backup, "do not match the database tables");
    }

    @Test
    void refusesATableMissingFromTheColumnManifest() throws Exception {
      Path backup =
          rewrite(
              manifest ->
                  withIntegrity(
                      manifest,
                      withColumns(manifest.integrityManifest(), "FINANCIAL_TRANSACTIONS", null)),
              rows -> rows);

      assertRefusedUnchanged(backup, "Backup columns for FINANCIAL_TRANSACTIONS null");
    }

    @Test
    void refusesDifferentColumns() throws Exception {
      Path backup =
          rewrite(
              manifest ->
                  withIntegrity(
                      manifest,
                      withColumns(
                          manifest.integrityManifest(),
                          "FINANCIAL_TRANSACTIONS",
                          List.of("ID:INTEGER"))),
              rows -> rows);

      assertRefusedUnchanged(backup, "Backup columns for FINANCIAL_TRANSACTIONS [ID:INTEGER]");
    }

    @Test
    void refusesARowWithDifferentColumns() throws Exception {
      Path backup =
          rewrite(
              manifest -> manifest,
              rows -> {
                rows.get("FINANCIAL_TRANSACTIONS").getFirst().put("extra", 1);
                return rows;
              });

      assertRefusedUnchanged(backup, "Backup row for FINANCIAL_TRANSACTIONS has columns");
    }

    @Test
    void rollsBackRowsThatDoNotMatchTheSealedManifest() throws Exception {
      Path backup =
          rewrite(
              manifest -> manifest,
              rows -> {
                rows.get("FINANCIAL_TRANSACTIONS").getFirst().put("description", "tampered");
                return rows;
              });
      MigrationIntegrityVerifier lenientReconcile = spy(verifier);
      doNothing().when(lenientReconcile).reconcile(any(), any());
      MigrationIntegrityManifest before = capture();

      assertThatThrownBy(() -> importer(lenientReconcile, noControl).importArchive(backup))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("Imported rows for FINANCIAL_TRANSACTIONS differ");
      assertThat(capture().rowHashes()).isEqualTo(before.rowHashes());
    }

    @Test
    void reconciliationRejectsChangedPreservedColumns() throws Exception {
      Path backup =
          rewrite(
              manifest -> manifest,
              rows -> {
                rows.get("FINANCIAL_TRANSACTIONS").getFirst().put("description", "tampered");
                return rows;
              });

      assertRefusedUnchanged(backup, "canonical row hashes changed");
    }

    @Test
    void rollsBackWhenReconciliationFails() throws Exception {
      Path backup =
          rewrite(
              manifest -> manifest,
              rows -> {
                rows.get("FINANCIAL_TRANSACTIONS").removeLast();
                return rows;
              });

      assertRefusedUnchanged(backup, "Migration reconciliation failed");
    }

    @Test
    void rollsBackWhenFinancialTotalsDiffer() throws Exception {
      Path backup = exportBackup();
      MigrationIntegrityManifest expected = archive.read(backup).manifest().integrityManifest();
      List<FinancialAggregate> aggregates = new ArrayList<>(expected.financialAggregates());
      FinancialAggregate first = aggregates.getFirst();
      aggregates.set(
          0,
          new FinancialAggregate(
              first.table(),
              first.direction(),
              first.year(),
              first.activityId(),
              first.categoryId(),
              first.rowCount(),
              null));
      MigrationIntegrityManifest altered =
          codec.seal(
              new MigrationIntegrityManifest(
                  expected.manifestVersion(),
                  expected.schemaVersion(),
                  expected.applicationVersion(),
                  expected.sourceDatabaseHash(),
                  expected.tableCounts(),
                  expected.tableColumns(),
                  expected.tableIdentityColumns(),
                  aggregates,
                  expected.referenceCounts(),
                  expected.uniquenessChecks(),
                  expected.orphanChecks(),
                  expected.rowHashes(),
                  expected.preservedColumnHashes(),
                  ""));
      MigrationIntegrityVerifier stubbed = spy(verifier);
      doReturn(altered)
          .when(stubbed)
          .captureBookieTables(any(Connection.class), anyString(), anyString());
      doNothing().when(stubbed).reconcile(any(), any());
      MigrationIntegrityManifest before = capture();

      assertThatThrownBy(() -> importer(stubbed, noControl).importArchive(backup))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("Imported financial totals differ");
      assertThat(capture().rowHashes()).isEqualTo(before.rowHashes());
    }
  }

  @Nested
  class Bind {

    private final PreparedStatement statement = mock(PreparedStatement.class);

    private void bind(String family, Object value) throws Exception {
      PortableBackupImporter.bind(
          statement,
          1,
          new DatabaseVendorOperations.ColumnMetadata("C", family, Types.VARCHAR, false, false),
          value);
    }

    @Test
    void bindsNullWithTheColumnType() throws Exception {
      bind("STRING", null);

      verify(statement).setNull(1, Types.VARCHAR);
    }

    @Test
    void bindsEachFamilyFromItsPortableForm() throws Exception {
      bind("INTEGER", 7);
      bind("DECIMAL", "12.50");
      bind("BOOLEAN", true);
      bind("FLOAT", 1.5);
      bind("BINARY", "AQI=");
      bind("DATE", "2026-01-02");
      bind("TIME", "03:04:05");
      bind("TIMESTAMP", "2026-01-02T03:04:05");
      bind("TIMESTAMP_TZ", "2026-01-02T03:04:05-07:00");
      bind("UUID", "abc");

      verify(statement).setLong(1, 7L);
      verify(statement).setBigDecimal(1, new BigDecimal("12.50"));
      verify(statement).setBoolean(1, true);
      verify(statement).setDouble(1, 1.5);
      verify(statement).setBytes(1, new byte[] {1, 2});
      verify(statement).setObject(1, LocalDate.of(2026, 1, 2));
      verify(statement).setObject(1, LocalTime.of(3, 4, 5));
      verify(statement).setObject(1, LocalDateTime.of(2026, 1, 2, 3, 4, 5));
      verify(statement)
          .setObject(1, OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.ofHours(-7)));
      verify(statement).setString(1, "abc");
    }
  }
}
