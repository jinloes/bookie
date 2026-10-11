package com.bookie.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifest;
import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import com.bookie.datalifecycle.portable.PortableBackupArchive;
import com.bookie.datalifecycle.portable.PortableBackupExporter;
import com.bookie.datalifecycle.portable.PortableBackupImporter;
import com.bookie.util.BackgroundWorkControl;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

/** A portable backup moves every row H2 → SQL Server → SQL Server without loss. */
@Tag("sqlserver")
class SqlServerPortableRoundTripTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
  // More rows than one import batch so batching is exercised against SQL Server too.
  private static final int TRANSACTIONS = 1_234;

  private final PortableBackupArchive archive = new PortableBackupArchive();
  private final MigrationIntegrityVerifier verifier = new MigrationIntegrityVerifier();

  @SuppressWarnings("unchecked")
  private final ObjectProvider<BackgroundWorkControl> noControl = mock(ObjectProvider.class);

  @TempDir Path dir;

  private JdbcDataSource h2;

  @BeforeEach
  void seedH2() throws Exception {
    h2 = new JdbcDataSource();
    h2.setURL(
        "jdbc:h2:mem:rt_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    Flyway.configure().dataSource(h2).load().migrate();
    execute(
        h2,
        """
        INSERT INTO household_members (name, active) VALUES ('Zoë 🏠 名前', TRUE);
        INSERT INTO financial_activities (name, activity_type, tax_treatment, owner_id)
        VALUES ('Rental — “Elm St”', 'OTHER', 'NONE', 1);
        INSERT INTO activity_reporting_profile_assignments (
            activity_id, reporting_profile_id, effective_from, effective_to
        )
        SELECT id, 4, DATE '2020-01-01', DATE '2020-12-31'
        FROM financial_activities WHERE name = 'Rental — “Elm St”';
        INSERT INTO activity_reporting_profile_assignments (
            activity_id, reporting_profile_id, effective_from, effective_to
        )
        SELECT id, 1, DATE '2021-01-01', NULL
        FROM financial_activities WHERE name = 'Rental — “Elm St”';
        INSERT INTO financial_transactions (
            amount, direction, transaction_date, description, activity_id, neutral_category_id
        )
        SELECT
            X + 0.37,
            'EXPENSE',
            DATEADD('DAY', X, DATE '2024-01-01'),
            'Row ' || X || ' — café ☕',
            (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
            (SELECT MIN(id) FROM neutral_categories WHERE direction = 'EXPENSE')
        FROM SYSTEM_RANGE(1, %d);
        DELETE FROM financial_transactions WHERE MOD(id, 7) = 0;
        """
            .formatted(TRANSACTIONS));
  }

  private static void execute(DataSource source, String sql) throws Exception {
    try (Connection connection = source.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static long single(DataSource source, String sql) throws Exception {
    try (Connection connection = source.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private PortableBackupExporter.ExportResult export(DataSource source, String file)
      throws Exception {
    return new PortableBackupExporter(source, verifier, archive, CLOCK, "test")
        .export(dir.resolve(file));
  }

  private PortableBackupImporter.ImportResult importInto(DataSource source, Path backup)
      throws Exception {
    return new PortableBackupImporter(source, verifier, archive, noControl, "test")
        .importArchive(backup);
  }

  private static SqlServerTestContainer.Database migratedSqlServer() throws Exception {
    SqlServerTestContainer.Database database = SqlServerTestContainer.createDatabase();
    database.migrate();
    return database;
  }

  @Nested
  class Export {

    @Test
    void rowsSurviveH2ToSqlServerToSqlServerUnchanged() throws Exception {
      var fromH2 = export(h2, "h2.zip");
      SqlServerTestContainer.Database first = migratedSqlServer();
      importInto(first.appDataSource(), fromH2.archive());
      var fromFirst = export(first.appDataSource(), "first.zip");
      SqlServerTestContainer.Database second = migratedSqlServer();
      importInto(second.appDataSource(), fromFirst.archive());
      var fromSecond = export(second.appDataSource(), "second.zip");

      MigrationIntegrityManifest original = fromH2.manifest().integrityManifest();
      verifier.reconcile(original, fromFirst.manifest().integrityManifest());
      verifier.reconcile(original, fromSecond.manifest().integrityManifest());
      assertThat(fromFirst.manifest().tables()).isEqualTo(fromH2.manifest().tables());
      assertThat(fromSecond.manifest().tables()).isEqualTo(fromH2.manifest().tables());
      assertThat(fromFirst.manifest().vendor()).isEqualTo("sqlserver");
      assertThat(original.tableCounts().get("FINANCIAL_TRANSACTIONS"))
          .isEqualTo(TRANSACTIONS - TRANSACTIONS / 7);
    }

    @Test
    void failsWhenSnapshotIsolationIsOff() throws Exception {
      SqlServerTestContainer.Database database = SqlServerTestContainer.createDatabase(false);
      database.migrate();

      assertThatThrownBy(() -> export(database.appDataSource(), "no-snapshot.zip"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Snapshot isolation is off");
    }
  }

  @Nested
  class Import {

    @Test
    void newRowsGetIdsAboveTheRestoredMaximum() throws Exception {
      SqlServerTestContainer.Database database = migratedSqlServer();
      importInto(database.appDataSource(), export(h2, "h2.zip").archive());
      long restoredMax =
          single(database.appDataSource(), "SELECT MAX(id) FROM financial_transactions");

      execute(
          database.appDataSource(),
          """
          INSERT INTO financial_transactions (
              amount, direction, transaction_date, description, activity_id, neutral_category_id
          )
          SELECT 1.00, N'EXPENSE', '2026-09-01', N'After restore', 1, MIN(id)
          FROM neutral_categories WHERE direction = N'EXPENSE'
          """);

      assertThat(
              single(
                  database.appDataSource(),
                  "SELECT id FROM financial_transactions WHERE description = N'After restore'"))
          .isGreaterThan(restoredMax);
    }

    @Test
    void replacesExistingRowsInTheTargetDatabase() throws Exception {
      SqlServerTestContainer.Database database = migratedSqlServer();
      execute(
          database.appDataSource(),
          "INSERT INTO household_members (name) VALUES (N'Only in the target')");

      importInto(database.appDataSource(), export(h2, "h2.zip").archive());

      assertThat(
              single(
                  database.appDataSource(),
                  "SELECT COUNT(*) FROM household_members WHERE name = N'Only in the target'"))
          .isZero();
      assertThat(
              single(
                  database.appDataSource(),
                  "SELECT COUNT(*) FROM household_members WHERE name = N'Zoë 🏠 名前'"))
          .isEqualTo(1);
    }
  }
}
