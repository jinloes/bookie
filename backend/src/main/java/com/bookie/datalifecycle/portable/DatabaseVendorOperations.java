package com.bookie.datalifecycle.portable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Database operations that differ between H2 and SQL Server. Logical table and column names are
 * upper case on both vendors; {@link #physicalName} maps them to the catalog's own spelling, which
 * matters because SQL Server databases use a case-sensitive collation with lower-case identifiers.
 */
public interface DatabaseVendorOperations {

  String H2 = "h2";
  String SQL_SERVER = "sqlserver";

  // EVENT_PUBLICATION held transient delivery state (V21, dropped by V22), not financial data.
  Set<String> NON_BOOKIE_TABLES = Set.of("FLYWAY_SCHEMA_HISTORY", "EVENT_PUBLICATION");
  String JOBRUNR_TABLE_PREFIX = "JOBRUNR_";

  String vendor();

  String schema();

  String physicalName(String logicalName);

  /**
   * Starts a read-consistent snapshot transaction on {@code connection}: H2 {@code REPEATABLE
   * READ}, SQL Server {@code SNAPSHOT}.
   *
   * @throws IllegalStateException when the database cannot provide a snapshot
   */
  void beginSnapshot(Connection connection) throws SQLException;

  /** Disables foreign-key and check constraints on {@code tables} for the current transaction. */
  void disableConstraints(Connection connection, List<String> tables) throws SQLException;

  /** Re-enables and re-validates the constraints {@link #disableConstraints} turned off. */
  void enableConstraints(Connection connection, List<String> tables) throws SQLException;

  /** Allows or forbids explicit values in {@code table}'s identity column. */
  void setIdentityInsert(Connection connection, String table, boolean enabled) throws SQLException;

  /**
   * Moves {@code table}'s identity so the next generated value is above {@code floor}. Run on an
   * auto-commit connection: H2 commits any DDL.
   */
  void reseed(Connection connection, String table, String column, long floor) throws SQLException;

  default String quote(String logicalName) {
    return "\"" + physicalName(logicalName).replace("\"", "\"\"") + "\"";
  }

  default String qualified(String logicalTable) {
    return "\"" + schema() + "\"." + quote(logicalTable);
  }

  static DatabaseVendorOperations of(Connection connection) throws SQLException {
    String product = connection.getMetaData().getDatabaseProductName().toUpperCase(Locale.ROOT);
    if (product.contains("H2")) {
      return new H2VendorOperations();
    }
    if (product.contains("SQL SERVER")) {
      return new SqlServerVendorOperations();
    }
    throw new IllegalStateException("Unsupported database: " + product);
  }

  static boolean isBookieTable(String logicalName) {
    return !NON_BOOKIE_TABLES.contains(logicalName)
        && !logicalName.startsWith(JOBRUNR_TABLE_PREFIX);
  }

  /**
   * The vendor-neutral family of a column type, so hashes and archives agree across vendors (H2
   * {@code BOOLEAN} and SQL Server {@code BIT}, H2 {@code ENUM} and SQL Server {@code NVARCHAR}).
   */
  static String typeFamily(int jdbcType, String typeName) {
    String name = typeName == null ? "" : typeName.toUpperCase(Locale.ROOT);
    if (name.equals("UUID") || name.equals("UNIQUEIDENTIFIER")) {
      return "UUID";
    }
    if (name.startsWith("ENUM")) {
      return "STRING";
    }
    return switch (jdbcType) {
      case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "INTEGER";
      case Types.DECIMAL, Types.NUMERIC -> "DECIMAL";
      case Types.BOOLEAN, Types.BIT -> "BOOLEAN";
      case Types.CHAR,
              Types.VARCHAR,
              Types.NCHAR,
              Types.NVARCHAR,
              Types.LONGVARCHAR,
              Types.LONGNVARCHAR,
              Types.CLOB,
              Types.NCLOB ->
          "STRING";
      case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "BINARY";
      case Types.DOUBLE, Types.FLOAT, Types.REAL -> "FLOAT";
      case Types.DATE -> "DATE";
      case Types.TIME -> "TIME";
      case Types.TIMESTAMP -> "TIMESTAMP";
      // -155 is SQL Server's DATETIMEOFFSET.
      case Types.TIMESTAMP_WITH_TIMEZONE, -155 -> "TIMESTAMP_TZ";
      default -> "OTHER:" + name;
    };
  }

  /** One column of a Bookie table. */
  record ColumnMetadata(
      String name, String family, int jdbcType, boolean autoIncrement, boolean generated) {}

  /** One Bookie table, with its columns in ordinal order and the Bookie tables it references. */
  record TableMetadata(String name, List<ColumnMetadata> columns, Set<String> parents) {

    public List<ColumnMetadata> insertableColumns() {
      return columns.stream().filter(column -> !column.generated()).toList();
    }

    public ColumnMetadata identityColumn() {
      return columns.stream().filter(ColumnMetadata::autoIncrement).findFirst().orElse(null);
    }
  }

  /** Bookie tables in foreign-key order: every table follows the tables it references. */
  default List<TableMetadata> bookieTables(Connection connection) throws SQLException {
    DatabaseMetaData metadata = connection.getMetaData();
    Map<String, String> physicalByLogical = new TreeMap<>();
    try (ResultSet tables =
        metadata.getTables(null, schema(), "%", new String[] {"BASE TABLE", "TABLE"})) {
      while (tables.next()) {
        String physical = tables.getString("TABLE_NAME");
        String logical = physical.toUpperCase(Locale.ROOT);
        if (isBookieTable(logical)) {
          physicalByLogical.put(logical, physical);
        }
      }
    }
    Map<String, TableMetadata> byName = new LinkedHashMap<>();
    for (Map.Entry<String, String> table : physicalByLogical.entrySet()) {
      Map<Integer, ColumnMetadata> columns = new TreeMap<>();
      try (ResultSet rows = metadata.getColumns(null, schema(), table.getValue(), "%")) {
        while (rows.next()) {
          int jdbcType = rows.getInt("DATA_TYPE");
          columns.put(
              rows.getInt("ORDINAL_POSITION"),
              new ColumnMetadata(
                  rows.getString("COLUMN_NAME").toUpperCase(Locale.ROOT),
                  typeFamily(jdbcType, rows.getString("TYPE_NAME")),
                  jdbcType,
                  "YES".equalsIgnoreCase(rows.getString("IS_AUTOINCREMENT")),
                  "YES".equalsIgnoreCase(rows.getString("IS_GENERATEDCOLUMN"))));
        }
      }
      Set<String> parents = new LinkedHashSet<>();
      try (ResultSet keys = metadata.getImportedKeys(null, schema(), table.getValue())) {
        while (keys.next()) {
          String parent = keys.getString("PKTABLE_NAME").toUpperCase(Locale.ROOT);
          if (!parent.equals(table.getKey()) && physicalByLogical.containsKey(parent)) {
            parents.add(parent);
          }
        }
      }
      byName.put(
          table.getKey(),
          new TableMetadata(table.getKey(), List.copyOf(columns.values()), Set.copyOf(parents)));
    }
    return foreignKeyOrder(byName.values());
  }

  private static List<TableMetadata> foreignKeyOrder(Iterable<TableMetadata> tables) {
    List<TableMetadata> remaining = new ArrayList<>();
    tables.forEach(remaining::add);
    remaining.sort(Comparator.comparing(TableMetadata::name));
    List<TableMetadata> ordered = new ArrayList<>();
    Set<String> placed = new LinkedHashSet<>();
    while (!remaining.isEmpty()) {
      List<TableMetadata> ready =
          remaining.stream().filter(table -> placed.containsAll(table.parents())).toList();
      if (ready.isEmpty()) {
        throw new IllegalStateException("Foreign keys between Bookie tables form a cycle");
      }
      ready.forEach(table -> placed.add(table.name()));
      ordered.addAll(ready);
      remaining.removeAll(ready);
    }
    return List.copyOf(ordered);
  }
}
