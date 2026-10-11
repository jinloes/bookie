package com.bookie.datalifecycle.portable;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest;
import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import com.bookie.util.BackgroundWorkControl;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Replaces every Bookie row with a verified portable archive's rows in one transaction. The archive
 * and target schema are validated before anything changes, and the result must reconcile with the
 * archive's sealed integrity manifest before the transaction commits.
 */
@Slf4j
@Component
public class PortableBackupImporter {

  private static final int BATCH_SIZE = 500;

  private final DataSource dataSource;
  private final MigrationIntegrityVerifier verifier;
  private final PortableBackupArchive archive;
  private final ObjectProvider<BackgroundWorkControl> workControl;
  private final String applicationVersion;

  public PortableBackupImporter(
      DataSource dataSource,
      MigrationIntegrityVerifier verifier,
      PortableBackupArchive archive,
      ObjectProvider<BackgroundWorkControl> workControl,
      @Value("${bookie.application-version:dev}") String applicationVersion) {
    this.dataSource = dataSource;
    this.verifier = verifier;
    this.archive = archive;
    this.workControl = workControl;
    this.applicationVersion = applicationVersion;
  }

  /** The archive that was imported and the integrity manifest captured after the import. */
  public record ImportResult(
      PortableBackupArchive.Manifest archiveManifest, MigrationIntegrityManifest after) {}

  /**
   * Imports the archive at {@code source}.
   *
   * @throws MigrationIntegrityException when the archive is invalid, does not match the target
   *     schema, or the imported data does not reconcile; the database is unchanged
   */
  public ImportResult importArchive(Path source) throws IOException, SQLException {
    PortableBackupArchive.Contents contents = archive.read(source);
    MigrationIntegrityManifest expected = contents.manifest().integrityManifest();
    ImportResult result;
    try (Connection connection = dataSource.getConnection()) {
      DatabaseVendorOperations vendor = DatabaseVendorOperations.of(connection);
      List<DatabaseVendorOperations.TableMetadata> tables = vendor.bookieTables(connection);
      validateTarget(connection, tables, contents);
      reseedIdentities(connection, vendor, tables, contents.rows());
      result = replaceRows(connection, vendor, tables, contents, expected);
    }
    BackgroundWorkControl control = workControl.getIfAvailable();
    int released = control == null ? 0 : control.releaseEngineBindings();
    log.info(
        "Imported portable backup {} ({} tables); released {} engine bindings",
        source.getFileName(),
        contents.rows().size(),
        released);
    return result;
  }

  private void validateTarget(
      Connection connection,
      List<DatabaseVendorOperations.TableMetadata> tables,
      PortableBackupArchive.Contents contents)
      throws SQLException {
    String targetVersion = verifier.currentSchemaVersion(connection);
    String archiveVersion = contents.manifest().flywayVersion();
    if (!Objects.equals(targetVersion, archiveVersion)
        || !Objects.equals(
            archiveVersion, contents.manifest().integrityManifest().schemaVersion())) {
      throw new MigrationIntegrityException(
          "Backup schema version "
              + archiveVersion
              + " does not match the database schema version "
              + targetVersion);
    }
    Set<String> targetTables =
        tables.stream()
            .map(DatabaseVendorOperations.TableMetadata::name)
            .collect(Collectors.toCollection(TreeSet::new));
    if (!targetTables.equals(contents.rows().keySet())) {
      throw new MigrationIntegrityException(
          "Backup tables "
              + contents.rows().keySet()
              + " do not match the database tables "
              + targetTables);
    }
    Map<String, List<String>> archivedColumns =
        contents.manifest().integrityManifest().tableColumns();
    for (DatabaseVendorOperations.TableMetadata table : tables) {
      Set<String> targetColumns =
          table.columns().stream()
              .map(column -> column.name() + ":" + column.family())
              .collect(Collectors.toSet());
      List<String> archived = archivedColumns.get(table.name());
      if (archived == null || !targetColumns.equals(new HashSet<>(archived))) {
        throw new MigrationIntegrityException(
            "Backup columns for "
                + table.name()
                + " "
                + archived
                + " do not match the database columns "
                + targetColumns);
      }
      Set<String> insertable =
          table.insertableColumns().stream()
              .map(column -> column.name().toLowerCase(Locale.ROOT))
              .collect(Collectors.toSet());
      for (Map<String, Object> row : contents.rows().get(table.name())) {
        if (!row.keySet().equals(insertable)) {
          throw new MigrationIntegrityException(
              "Backup row for " + table.name() + " has columns " + row.keySet());
        }
      }
    }
  }

  // Identity counters must sit above every restored and every existing ID so rows created after
  // the import, or after a rolled-back import, never collide.
  private static void reseedIdentities(
      Connection connection,
      DatabaseVendorOperations vendor,
      List<DatabaseVendorOperations.TableMetadata> tables,
      Map<String, List<Map<String, Object>>> rows)
      throws SQLException {
    for (DatabaseVendorOperations.TableMetadata table : tables) {
      DatabaseVendorOperations.ColumnMetadata identity = table.identityColumn();
      if (identity == null) {
        continue;
      }
      String key = identity.name().toLowerCase(Locale.ROOT);
      long archived =
          rows.get(table.name()).stream()
              .map(row -> row.get(key))
              .filter(Objects::nonNull)
              .mapToLong(value -> ((Number) value).longValue())
              .max()
              .orElse(0);
      long floor = Math.max(archived, currentMax(connection, vendor, table.name(), identity));
      if (floor > 0) {
        vendor.reseed(connection, table.name(), identity.name(), floor);
      }
    }
  }

  private static long currentMax(
      Connection connection,
      DatabaseVendorOperations vendor,
      String table,
      DatabaseVendorOperations.ColumnMetadata identity)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT MAX("
                    + vendor.quote(identity.name())
                    + ") FROM "
                    + vendor.qualified(table))) {
      result.next();
      return result.getLong(1);
    }
  }

  private ImportResult replaceRows(
      Connection connection,
      DatabaseVendorOperations vendor,
      List<DatabaseVendorOperations.TableMetadata> tables,
      PortableBackupArchive.Contents contents,
      MigrationIntegrityManifest expected)
      throws SQLException {
    List<String> names = tables.stream().map(DatabaseVendorOperations.TableMetadata::name).toList();
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      vendor.disableConstraints(connection, names);
      try (Statement statement = connection.createStatement()) {
        for (int index = names.size() - 1; index >= 0; index--) {
          statement.executeUpdate("DELETE FROM " + vendor.qualified(names.get(index)));
        }
      }
      for (DatabaseVendorOperations.TableMetadata table : tables) {
        insertRows(connection, vendor, table, contents.rows().get(table.name()));
      }
      vendor.enableConstraints(connection, names);
      MigrationIntegrityManifest after =
          verifier.captureBookieTables(connection, applicationVersion, "");
      verifier.reconcile(expected, after);
      assertExactRows(expected, after);
      assertExactFinancialParity(expected, after);
      connection.commit();
      return new ImportResult(contents.manifest(), after);
    } catch (SQLException | RuntimeException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  private static void insertRows(
      Connection connection,
      DatabaseVendorOperations vendor,
      DatabaseVendorOperations.TableMetadata table,
      List<Map<String, Object>> rows)
      throws SQLException {
    if (rows.isEmpty()) {
      return;
    }
    List<DatabaseVendorOperations.ColumnMetadata> columns = table.insertableColumns();
    String sql =
        "INSERT INTO "
            + vendor.qualified(table.name())
            + " ("
            + columns.stream()
                .map(DatabaseVendorOperations.ColumnMetadata::name)
                .map(vendor::quote)
                .collect(Collectors.joining(", "))
            + ") VALUES ("
            + columns.stream().map(column -> "?").collect(Collectors.joining(", "))
            + ")";
    boolean identityInsert = table.identityColumn() != null;
    if (identityInsert) {
      vendor.setIdentityInsert(connection, table.name(), true);
    }
    try (PreparedStatement insert = connection.prepareStatement(sql)) {
      int pending = 0;
      for (Map<String, Object> row : rows) {
        for (int index = 1; index <= columns.size(); index++) {
          DatabaseVendorOperations.ColumnMetadata column = columns.get(index - 1);
          bind(insert, index, column, row.get(column.name().toLowerCase(Locale.ROOT)));
        }
        insert.addBatch();
        if (++pending == BATCH_SIZE) {
          insert.executeBatch();
          pending = 0;
        }
      }
      if (pending > 0) {
        insert.executeBatch();
      }
    } finally {
      if (identityInsert) {
        vendor.setIdentityInsert(connection, table.name(), false);
      }
    }
  }

  static void bind(
      PreparedStatement statement,
      int index,
      DatabaseVendorOperations.ColumnMetadata column,
      Object value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, column.jdbcType());
      return;
    }
    switch (column.family()) {
      case "INTEGER" -> statement.setLong(index, ((Number) value).longValue());
      case "DECIMAL" -> statement.setBigDecimal(index, new BigDecimal(value.toString()));
      case "BOOLEAN" -> statement.setBoolean(index, (Boolean) value);
      case "FLOAT" -> statement.setDouble(index, ((Number) value).doubleValue());
      case "BINARY" -> statement.setBytes(index, Base64.getDecoder().decode(value.toString()));
      case "DATE" -> statement.setObject(index, LocalDate.parse(value.toString()));
      case "TIME" -> statement.setObject(index, LocalTime.parse(value.toString()));
      case "TIMESTAMP" -> statement.setObject(index, LocalDateTime.parse(value.toString()));
      case "TIMESTAMP_TZ" -> statement.setObject(index, OffsetDateTime.parse(value.toString()));
      default -> statement.setString(index, value.toString());
    }
  }

  // reconcile checks counts and preserved columns only; a restore must reproduce every row.
  private static void assertExactRows(
      MigrationIntegrityManifest expected, MigrationIntegrityManifest after) {
    for (Map.Entry<String, List<String>> table : expected.rowHashes().entrySet()) {
      if (!table.getValue().equals(after.rowHashes().get(table.getKey()))) {
        throw new MigrationIntegrityException(
            "Imported rows for " + table.getKey() + " differ from the backup");
      }
    }
  }

  private static void assertExactFinancialParity(
      MigrationIntegrityManifest expected, MigrationIntegrityManifest after) {
    if (!normalized(expected).equals(normalized(after))) {
      throw new MigrationIntegrityException(
          "Imported financial totals differ from the backup: expected="
              + expected.financialAggregates()
              + ", actual="
              + after.financialAggregates());
    }
  }

  private static Map<String, Object> normalized(MigrationIntegrityManifest manifest) {
    Map<String, Object> totals = new TreeMap<>();
    for (MigrationIntegrityManifest.FinancialAggregate aggregate : manifest.financialAggregates()) {
      totals.put(
          String.join(
              "|",
              aggregate.table(),
              String.valueOf(aggregate.direction()),
              String.valueOf(aggregate.year()),
              String.valueOf(aggregate.activityId()),
              String.valueOf(aggregate.categoryId())),
          aggregate.rowCount()
              + "|"
              + Objects.requireNonNullElse(aggregate.amountTotal(), BigDecimal.ZERO)
                  .stripTrailingZeros()
                  .toPlainString());
    }
    return totals;
  }
}
