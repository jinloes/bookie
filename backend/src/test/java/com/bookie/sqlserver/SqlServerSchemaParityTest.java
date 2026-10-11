package com.bookie.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The SQL Server baseline must describe the same schema the H2 migration chain builds, so a
 * portable backup taken on either vendor restores onto the other.
 *
 * <p>Vendor type differences are normalized to families: every character type becomes {@code
 * STRING(n)} (unbounded types become {@code STRING(MAX)}), {@code BIT} and {@code BOOLEAN} become
 * {@code BOOLEAN}, binary types become {@code BINARY}, and H2 {@code UUID} and SQL Server {@code
 * UNIQUEIDENTIFIER} become {@code UUID}. H2 {@code ENUM} columns compare as {@code STRING(50)}
 * because the SQL Server baseline stores enum values as {@code NVARCHAR(50)} text. JobRunr tables
 * are excluded: H2 creates them through Flyway, while on SQL Server JobRunr creates its own.
 * Identifier case is ignored.
 */
@Tag("sqlserver")
class SqlServerSchemaParityTest {

  private static final String HISTORY_TABLE = "FLYWAY_SCHEMA_HISTORY";
  private static final String JOBRUNR_PREFIX = "JOBRUNR_";
  private static final int UNBOUNDED_THRESHOLD = 1_000_000;

  private static Schema h2Schema;

  @BeforeAll
  static void readH2Schema() throws SQLException {
    JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL(
        "jdbc:h2:mem:parity_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    Flyway.configure().dataSource(h2).load().migrate();
    h2Schema = Schema.read(h2, "PUBLIC");
  }

  @Nested
  class Baseline {

    @Test
    void matchesTheH2MigrationChain() throws SQLException {
      SqlServerTestContainer.Database database = SqlServerTestContainer.createDatabase();
      database.migrate();

      Schema sqlServer = Schema.read(database.adminDataSource(), "dbo");

      assertThat(h2Schema.tables()).isNotEmpty();
      assertThat(differences(h2Schema, sqlServer)).isEmpty();
    }
  }

  @Nested
  class Control {

    @Test
    void reportsADroppedColumn() throws SQLException {
      SqlServerTestContainer.Database database = SqlServerTestContainer.createDatabase();
      database.migrate();
      database.executeAsAdmin(
          """
          ALTER TABLE household_members DROP CONSTRAINT df_household_members_active;
          ALTER TABLE household_members DROP COLUMN active;
          """);

      Schema sqlServer = Schema.read(database.adminDataSource(), "dbo");

      assertThat(differences(h2Schema, sqlServer))
          .anySatisfy(line -> assertThat(line).contains("HOUSEHOLD_MEMBERS").contains("ACTIVE"));
    }
  }

  private static List<String> differences(Schema expected, Schema actual) {
    List<String> differences = new ArrayList<>();
    compare(differences, "tables", expected.tables().keySet(), actual.tables().keySet());
    for (Map.Entry<String, Table> entry : expected.tables().entrySet()) {
      Table other = actual.tables().get(entry.getKey());
      if (other == null) {
        continue;
      }
      String table = entry.getKey();
      compare(differences, table + " columns", entry.getValue().columns(), other.columns());
      compare(
          differences, table + " unique keys", entry.getValue().uniqueKeys(), other.uniqueKeys());
      compare(
          differences,
          table + " foreign keys",
          entry.getValue().foreignKeys(),
          other.foreignKeys());
    }
    return differences;
  }

  private static <T> void compare(
      List<String> differences, String what, Set<T> expected, Set<T> actual) {
    for (T item : expected) {
      if (!actual.contains(item)) {
        differences.add(what + ": only in H2: " + item);
      }
    }
    for (T item : actual) {
      if (!expected.contains(item)) {
        differences.add(what + ": only in SQL Server: " + item);
      }
    }
  }

  private record Table(
      SortedSet<String> columns, SortedSet<String> uniqueKeys, SortedSet<String> foreignKeys) {}

  private record Schema(Map<String, Table> tables) {

    static Schema read(DataSource dataSource, String schema) throws SQLException {
      Map<String, Table> tables = new TreeMap<>();
      try (Connection connection = dataSource.getConnection()) {
        DatabaseMetaData metaData = connection.getMetaData();
        List<String> names = new ArrayList<>();
        try (ResultSet rows = metaData.getTables(null, schema, "%", new String[] {"TABLE"})) {
          while (rows.next()) {
            names.add(rows.getString("TABLE_NAME"));
          }
        }
        for (String name : names) {
          String key = upper(name);
          if (!HISTORY_TABLE.equals(key) && !key.startsWith(JOBRUNR_PREFIX)) {
            tables.put(
                key,
                new Table(
                    columns(metaData, schema, name),
                    uniqueKeys(metaData, schema, name),
                    foreignKeys(metaData, schema, name)));
          }
        }
      }
      return new Schema(tables);
    }

    private static SortedSet<String> columns(DatabaseMetaData metaData, String schema, String table)
        throws SQLException {
      SortedSet<String> columns = new TreeSet<>();
      try (ResultSet rows = metaData.getColumns(null, schema, table, "%")) {
        while (rows.next()) {
          columns.add(
              upper(rows.getString("COLUMN_NAME"))
                  + " "
                  + family(
                      rows.getString("TYPE_NAME"),
                      rows.getInt("DATA_TYPE"),
                      rows.getInt("COLUMN_SIZE"),
                      rows.getInt("DECIMAL_DIGITS"))
                  + (rows.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
                      ? " NOT NULL"
                      : " NULL"));
        }
      }
      return columns;
    }

    private static SortedSet<String> uniqueKeys(
        DatabaseMetaData metaData, String schema, String table) throws SQLException {
      Map<String, SortedSet<String>> indexes = new TreeMap<>();
      try (ResultSet rows = metaData.getIndexInfo(null, schema, table, true, false)) {
        while (rows.next()) {
          String column = rows.getString("COLUMN_NAME");
          if (column != null && !rows.getBoolean("NON_UNIQUE")) {
            indexes
                .computeIfAbsent(rows.getString("INDEX_NAME"), ignored -> new TreeSet<>())
                .add(upper(column));
          }
        }
      }
      SortedSet<String> keys = new TreeSet<>();
      indexes.values().forEach(columns -> keys.add(String.join(",", columns)));
      return keys;
    }

    private static SortedSet<String> foreignKeys(
        DatabaseMetaData metaData, String schema, String table) throws SQLException {
      Map<String, List<String>> keys = new TreeMap<>();
      try (ResultSet rows = metaData.getImportedKeys(null, schema, table)) {
        while (rows.next()) {
          keys.computeIfAbsent(rows.getString("FK_NAME"), ignored -> new ArrayList<>())
              .add(
                  upper(rows.getString("FKCOLUMN_NAME"))
                      + "->"
                      + upper(rows.getString("PKTABLE_NAME"))
                      + "."
                      + upper(rows.getString("PKCOLUMN_NAME")));
        }
      }
      SortedSet<String> result = new TreeSet<>();
      keys.values().forEach(columns -> result.add(String.join(",", new TreeSet<>(columns))));
      return result;
    }

    private static String family(String typeName, int jdbcType, int size, int scale) {
      String name = upper(typeName);
      if (name.equals("UUID") || name.equals("UNIQUEIDENTIFIER")) {
        return "UUID";
      }
      if (name.startsWith("ENUM")) {
        return "STRING(50)";
      }
      return switch (jdbcType) {
        case Types.CHAR,
                Types.VARCHAR,
                Types.NCHAR,
                Types.NVARCHAR,
                Types.LONGVARCHAR,
                Types.LONGNVARCHAR,
                Types.CLOB,
                Types.NCLOB ->
            size <= 0 || size >= UNBOUNDED_THRESHOLD ? "STRING(MAX)" : "STRING(" + size + ")";
        case Types.DECIMAL, Types.NUMERIC -> "DECIMAL(" + size + "," + scale + ")";
        case Types.BIT, Types.BOOLEAN -> "BOOLEAN";
        case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "BINARY";
        case Types.REAL, Types.FLOAT, Types.DOUBLE -> "FLOAT";
        default -> "JDBC" + jdbcType;
      };
    }

    private static String upper(String identifier) {
      return identifier.toUpperCase(Locale.ROOT);
    }
  }
}
