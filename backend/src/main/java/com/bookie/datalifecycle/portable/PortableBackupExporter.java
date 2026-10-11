package com.bookie.datalifecycle.portable;

import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Exports every Bookie table from one read-consistent snapshot into a portable archive. */
@Component
public class PortableBackupExporter {

  private final DataSource dataSource;
  private final MigrationIntegrityVerifier verifier;
  private final PortableBackupArchive archive;
  private final Clock clock;
  private final String applicationVersion;

  public PortableBackupExporter(
      DataSource dataSource,
      MigrationIntegrityVerifier verifier,
      PortableBackupArchive archive,
      Clock clock,
      @Value("${bookie.application-version:dev}") String applicationVersion) {
    this.dataSource = dataSource;
    this.verifier = verifier;
    this.archive = archive;
    this.clock = clock;
    this.applicationVersion = applicationVersion;
  }

  /** The written archive and its manifest. */
  public record ExportResult(Path archive, PortableBackupArchive.Manifest manifest) {}

  /**
   * Writes the archive to {@code target}. Rows and the integrity manifest come from the same
   * snapshot, so the manifest describes exactly the exported rows.
   *
   * @throws IllegalStateException when the database cannot provide a snapshot
   */
  public ExportResult export(Path target) throws IOException, SQLException {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseVendorOperations vendor = DatabaseVendorOperations.of(connection);
      boolean autoCommit = connection.getAutoCommit();
      vendor.beginSnapshot(connection);
      try {
        Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
        for (DatabaseVendorOperations.TableMetadata table : vendor.bookieTables(connection)) {
          rows.put(table.name(), readRows(connection, vendor, table));
        }
        var integrity = verifier.captureBookieTables(connection, applicationVersion, "");
        PortableBackupArchive.Manifest manifest =
            archive.write(
                target,
                PortableBackupArchive.Manifest.builder()
                    .flywayVersion(verifier.currentSchemaVersion(connection))
                    .vendor(vendor.vendor())
                    .createdAt(Instant.now(clock).toString())
                    .integrityManifest(integrity)
                    .build(),
                rows);
        connection.commit();
        return new ExportResult(target, manifest);
      } catch (IOException | SQLException | RuntimeException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    }
  }

  private static List<Map<String, Object>> readRows(
      Connection connection,
      DatabaseVendorOperations vendor,
      DatabaseVendorOperations.TableMetadata table)
      throws SQLException {
    List<DatabaseVendorOperations.ColumnMetadata> columns = table.insertableColumns();
    String selected =
        String.join(
            ", ",
            columns.stream()
                .map(DatabaseVendorOperations.ColumnMetadata::name)
                .map(vendor::quote)
                .toList());
    String orderBy =
        table.identityColumn() == null
            ? ""
            : " ORDER BY " + vendor.quote(table.identityColumn().name());
    List<Map<String, Object>> rows = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT " + selected + " FROM " + vendor.qualified(table.name()) + orderBy)) {
      while (result.next()) {
        Map<String, Object> row = new TreeMap<>();
        for (int index = 1; index <= columns.size(); index++) {
          DatabaseVendorOperations.ColumnMetadata column = columns.get(index - 1);
          row.put(column.name().toLowerCase(Locale.ROOT), jsonValue(result, index, column));
        }
        rows.add(row);
      }
    }
    return rows;
  }

  static Object jsonValue(
      ResultSet result, int index, DatabaseVendorOperations.ColumnMetadata column)
      throws SQLException {
    Object value =
        switch (column.family()) {
          case "INTEGER" -> result.getLong(index);
          case "DECIMAL" -> {
            BigDecimal decimal = result.getBigDecimal(index);
            yield decimal == null ? null : decimal.toPlainString();
          }
          case "BOOLEAN" -> result.getBoolean(index);
          case "FLOAT" -> result.getDouble(index);
          case "UUID" -> {
            String text = result.getString(index);
            yield text == null ? null : text.toLowerCase(Locale.ROOT);
          }
          case "BINARY" -> {
            byte[] bytes = result.getBytes(index);
            yield bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
          }
          case "DATE" -> isoText(result.getObject(index, LocalDate.class));
          case "TIME" -> isoText(result.getObject(index, LocalTime.class));
          case "TIMESTAMP" -> isoText(result.getObject(index, LocalDateTime.class));
          case "TIMESTAMP_TZ" -> isoText(result.getObject(index, OffsetDateTime.class));
          default -> result.getString(index);
        };
    return result.wasNull() ? null : value;
  }

  private static String isoText(Object value) {
    return value == null ? null : value.toString();
  }
}
