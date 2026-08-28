package com.bookie.datalifecycle.migration;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifest.ConstraintCheck;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest.FinancialAggregate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class MigrationIntegrityVerifier {

  private static final String SCHEMA = "PUBLIC";
  private static final Set<String> EXCLUDED_TABLES = Set.of("FLYWAY_SCHEMA_HISTORY");
  private static final Set<String> REFERENCE_COLUMNS =
      Set.of("SOURCE_ID", "RECEIPT_ONE_DRIVE_ID", "RECEIPT_FILE_NAME");

  private final MigrationIntegrityManifestCodec codec = new MigrationIntegrityManifestCodec();

  public MigrationIntegrityManifest capture(
      DataSource dataSource, String applicationVersion, String sourceDatabaseHash) {
    try (Connection connection = dataSource.getConnection()) {
      return capture(connection, applicationVersion, sourceDatabaseHash);
    } catch (SQLException e) {
      throw new MigrationIntegrityException("Could not capture migration integrity manifest", e);
    }
  }

  public MigrationIntegrityManifest capture(
      Connection connection, String applicationVersion, String sourceDatabaseHash) {
    try {
      connection.setReadOnly(true);
      List<TableDefinition> tables = discoverTables(connection);
      SortedMap<String, List<String>> rowHashes = new TreeMap<>();
      SortedMap<String, SortedMap<String, SortedMap<String, String>>> preservedColumnHashes =
          new TreeMap<>();
      SortedMap<String, Long> tableCounts = new TreeMap<>();
      SortedMap<String, List<String>> tableColumns = new TreeMap<>();
      SortedMap<String, List<String>> tableIdentityColumns = new TreeMap<>();
      SortedMap<String, Long> referenceCounts = new TreeMap<>();
      for (TableDefinition table : tables) {
        TableSnapshot snapshot = hashRows(connection, table);
        rowHashes.put(table.name(), snapshot.rowHashes());
        preservedColumnHashes.put(table.name(), snapshot.preservedColumnHashes());
        tableCounts.put(table.name(), (long) snapshot.rowHashes().size());
        tableColumns.put(
            table.name(),
            table.columns().stream().map(column -> column.name() + ":" + column.type()).toList());
        tableIdentityColumns.put(table.name(), table.identityColumns());
        referenceCounts.putAll(referenceCounts(connection, table));
      }
      List<ConstraintCheck> uniquenessChecks = uniquenessChecks(connection, tables);
      List<ConstraintCheck> orphanChecks = orphanChecks(connection, tables);
      List<FinancialAggregate> financialAggregates = financialAggregates(connection, tables);
      String canonicalDatabaseHash =
          MigrationIntegrityManifestCodec.sha256(
              rowHashes.entrySet().stream()
                  .map(entry -> entry.getKey() + ":" + String.join(",", entry.getValue()))
                  .collect(Collectors.joining("\n")));
      MigrationIntegrityManifest manifest =
          new MigrationIntegrityManifest(
              MigrationIntegrityManifest.CURRENT_VERSION,
              schemaVersion(connection),
              StringUtils.defaultIfBlank(applicationVersion, "unknown"),
              StringUtils.defaultIfBlank(sourceDatabaseHash, canonicalDatabaseHash),
              tableCounts,
              tableColumns,
              tableIdentityColumns,
              financialAggregates,
              referenceCounts,
              uniquenessChecks,
              orphanChecks,
              rowHashes,
              preservedColumnHashes,
              "");
      return codec.seal(manifest);
    } catch (SQLException e) {
      throw new MigrationIntegrityException("Could not capture migration integrity manifest", e);
    }
  }

  public void assertHealthy(MigrationIntegrityManifest manifest) {
    codec.verify(manifest);
    List<String> failures = new ArrayList<>();
    appendConstraintFailures("unique", manifest.uniquenessChecks(), failures);
    appendConstraintFailures("orphan", manifest.orphanChecks(), failures);
    if (!failures.isEmpty()) {
      throw new MigrationIntegrityException(
          "Migration integrity checks failed:\n" + String.join("\n", failures));
    }
  }

  public void reconcile(MigrationIntegrityManifest before, MigrationIntegrityManifest after) {
    codec.verify(before);
    codec.verify(after);
    List<String> failures = new ArrayList<>();
    for (Map.Entry<String, Long> expected : before.tableCounts().entrySet()) {
      Long observed = after.tableCounts().get(expected.getKey());
      if (!Objects.equals(expected.getValue(), observed)) {
        failures.add(
            "table "
                + expected.getKey()
                + " count changed from "
                + expected.getValue()
                + " to "
                + observed);
      }
      reconcilePreservedColumns(before, after, expected.getKey(), failures);
    }
    for (Map.Entry<String, Long> expected : before.referenceCounts().entrySet()) {
      Long observed = after.referenceCounts().get(expected.getKey());
      if (!Objects.equals(expected.getValue(), observed)) {
        failures.add(
            "reference "
                + expected.getKey()
                + " count changed from "
                + expected.getValue()
                + " to "
                + observed);
      }
    }
    Map<AggregateKey, AggregateValue> expectedAggregates =
        aggregateSummaryValues(before.financialAggregates(), before.tableCounts().keySet());
    Map<AggregateKey, AggregateValue> observedAggregates =
        aggregateSummaryValues(after.financialAggregates(), before.tableCounts().keySet());
    if (!expectedAggregates.equals(observedAggregates)) {
      failures.add("legacy financial aggregates changed");
    }
    appendConstraintFailures("unique", after.uniquenessChecks(), failures);
    appendConstraintFailures("orphan", after.orphanChecks(), failures);
    if (!failures.isEmpty()) {
      throw new MigrationIntegrityException(
          "Migration reconciliation failed:\n" + String.join("\n", failures));
    }
  }

  public void assertFinancialParity(
      MigrationIntegrityManifest manifest,
      Collection<String> sourceTables,
      Collection<String> targetTables) {
    codec.verify(manifest);
    Map<ParityKey, AggregateValue> source =
        parityValues(manifest.financialAggregates(), upperCase(sourceTables));
    Map<ParityKey, AggregateValue> target =
        parityValues(manifest.financialAggregates(), upperCase(targetTables));
    if (!source.equals(target)) {
      throw new MigrationIntegrityException(
          "Financial compatibility views diverged: source=" + source + ", target=" + target);
    }
  }

  private List<TableDefinition> discoverTables(Connection connection) throws SQLException {
    Map<String, List<ColumnDefinition>> columnsByTable = new TreeMap<>();
    DatabaseMetaData metadata = connection.getMetaData();
    try (ResultSet tables =
        metadata.getTables(null, SCHEMA, "%", new String[] {"BASE TABLE", "TABLE"})) {
      while (tables.next()) {
        String table = tables.getString("TABLE_NAME").toUpperCase();
        if (!EXCLUDED_TABLES.contains(table)) {
          columnsByTable.put(table, new ArrayList<>());
        }
      }
    }
    for (Map.Entry<String, List<ColumnDefinition>> entry : columnsByTable.entrySet()) {
      try (ResultSet columns = metadata.getColumns(null, SCHEMA, entry.getKey(), "%")) {
        while (columns.next()) {
          entry
              .getValue()
              .add(
                  new ColumnDefinition(
                      columns.getString("COLUMN_NAME").toUpperCase(),
                      columns.getString("TYPE_NAME").toUpperCase(),
                      columns.getInt("ORDINAL_POSITION")));
        }
      }
      entry.getValue().sort(Comparator.comparingInt(ColumnDefinition::ordinal));
    }
    List<TableDefinition> definitions = new ArrayList<>();
    for (Map.Entry<String, List<ColumnDefinition>> entry : columnsByTable.entrySet()) {
      List<IndexedColumn> primaryKey = new ArrayList<>();
      try (ResultSet keys = metadata.getPrimaryKeys(null, SCHEMA, entry.getKey())) {
        while (keys.next()) {
          primaryKey.add(
              new IndexedColumn(
                  keys.getString("COLUMN_NAME").toUpperCase(), keys.getShort("KEY_SEQ")));
        }
      }
      primaryKey.sort(Comparator.comparingInt(IndexedColumn::position));
      List<String> identityColumns =
          primaryKey.isEmpty()
              ? entry.getValue().stream().map(ColumnDefinition::name).toList()
              : primaryKey.stream().map(IndexedColumn::name).toList();
      definitions.add(
          new TableDefinition(
              entry.getKey(), List.copyOf(entry.getValue()), List.copyOf(identityColumns)));
    }
    return List.copyOf(definitions);
  }

  private TableSnapshot hashRows(Connection connection, TableDefinition table) throws SQLException {
    List<String> hashes = new ArrayList<>();
    SortedMap<String, SortedMap<String, String>> preservedColumnHashes = new TreeMap<>();
    Map<String, Integer> identityOccurrences = new HashMap<>();
    String selectedColumns =
        table.columns().stream()
            .map(ColumnDefinition::name)
            .map(MigrationIntegrityVerifier::quote)
            .collect(Collectors.joining(", "));
    String sql = "SELECT " + selectedColumns + " FROM " + qualified(table.name());
    try (var statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      ResultSetMetaData metadata = rows.getMetaData();
      while (rows.next()) {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var identityDigest = java.security.MessageDigest.getInstance("SHA-256");
        SortedMap<String, String> columnHashes = new TreeMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
          String column = metadata.getColumnName(index).toUpperCase();
          String type = metadata.getColumnTypeName(index).toUpperCase();
          String value = canonicalValue(rows.getObject(index));
          appendDigest(digest, column);
          appendDigest(digest, type);
          appendDigest(digest, value);
          var columnDigest = java.security.MessageDigest.getInstance("SHA-256");
          appendDigest(columnDigest, type);
          appendDigest(columnDigest, value);
          columnHashes.put(column, HexFormat.of().formatHex(columnDigest.digest()));
          if (table.identityColumns().contains(column)) {
            appendDigest(identityDigest, column);
            appendDigest(identityDigest, type);
            appendDigest(identityDigest, value);
          }
        }
        hashes.add(HexFormat.of().formatHex(digest.digest()));
        String baseIdentity = HexFormat.of().formatHex(identityDigest.digest());
        int occurrence = identityOccurrences.merge(baseIdentity, 1, Integer::sum);
        preservedColumnHashes.put(baseIdentity + "#" + occurrence, columnHashes);
      }
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
    hashes.sort(String::compareTo);
    return new TableSnapshot(List.copyOf(hashes), preservedColumnHashes);
  }

  private SortedMap<String, Long> referenceCounts(Connection connection, TableDefinition table)
      throws SQLException {
    SortedMap<String, Long> counts = new TreeMap<>();
    Set<String> columnNames =
        table.columns().stream().map(ColumnDefinition::name).collect(Collectors.toSet());
    for (String column : REFERENCE_COLUMNS) {
      if (columnNames.contains(column)) {
        String sql =
            "SELECT COUNT(*) FROM "
                + qualified(table.name())
                + " WHERE "
                + quote(column)
                + " IS NOT NULL";
        counts.put(table.name() + "." + column, queryLong(connection, sql));
      }
    }
    if (table.name().contains("ATTACHMENT") || table.name().contains("IMPORT_REFERENCE")) {
      counts.put(
          table.name() + ".*",
          queryLong(connection, "SELECT COUNT(*) FROM " + qualified(table.name())));
    }
    return counts;
  }

  private List<ConstraintCheck> uniquenessChecks(
      Connection connection, List<TableDefinition> tables) throws SQLException {
    List<ConstraintCheck> checks = new ArrayList<>();
    DatabaseMetaData metadata = connection.getMetaData();
    for (TableDefinition table : tables) {
      Map<String, List<IndexedColumn>> indexes = new TreeMap<>();
      try (ResultSet rows = metadata.getIndexInfo(null, SCHEMA, table.name(), true, false)) {
        while (rows.next()) {
          String index = rows.getString("INDEX_NAME");
          String column = rows.getString("COLUMN_NAME");
          short position = rows.getShort("ORDINAL_POSITION");
          if (index != null && column != null) {
            indexes
                .computeIfAbsent(index, ignored -> new ArrayList<>())
                .add(new IndexedColumn(column.toUpperCase(), position));
          }
        }
      }
      for (Map.Entry<String, List<IndexedColumn>> index : indexes.entrySet()) {
        index.getValue().sort(Comparator.comparingInt(IndexedColumn::position));
        List<String> columns = index.getValue().stream().map(IndexedColumn::name).toList();
        String selected =
            columns.stream()
                .map(MigrationIntegrityVerifier::quote)
                .collect(Collectors.joining(", "));
        String nonNull =
            columns.stream()
                .map(column -> quote(column) + " IS NOT NULL")
                .collect(Collectors.joining(" AND "));
        String sql =
            "SELECT COUNT(*) FROM (SELECT "
                + selected
                + " FROM "
                + qualified(table.name())
                + " WHERE "
                + nonNull
                + " GROUP BY "
                + selected
                + " HAVING COUNT(*) > 1)";
        checks.add(
            new ConstraintCheck(table.name() + "." + index.getKey(), queryLong(connection, sql)));
      }
    }
    return checks.stream().sorted(Comparator.comparing(ConstraintCheck::name)).toList();
  }

  private List<ConstraintCheck> orphanChecks(Connection connection, List<TableDefinition> tables)
      throws SQLException {
    Set<String> knownTables =
        tables.stream().map(TableDefinition::name).collect(Collectors.toSet());
    List<ConstraintCheck> checks = new ArrayList<>();
    DatabaseMetaData metadata = connection.getMetaData();
    for (TableDefinition table : tables) {
      Map<String, ForeignKey> keys = new LinkedHashMap<>();
      try (ResultSet rows = metadata.getImportedKeys(null, SCHEMA, table.name())) {
        while (rows.next()) {
          String parentTable = rows.getString("PKTABLE_NAME").toUpperCase();
          if (!knownTables.contains(parentTable)) {
            continue;
          }
          String name = StringUtils.defaultIfBlank(rows.getString("FK_NAME"), "FK_" + table.name());
          ForeignKey key = keys.computeIfAbsent(name, ignored -> new ForeignKey(name, parentTable));
          key.columns()
              .add(
                  new ForeignKeyColumn(
                      rows.getString("FKCOLUMN_NAME").toUpperCase(),
                      rows.getString("PKCOLUMN_NAME").toUpperCase(),
                      rows.getShort("KEY_SEQ")));
        }
      }
      for (ForeignKey key : keys.values()) {
        key.columns().sort(Comparator.comparingInt(ForeignKeyColumn::position));
        String join =
            key.columns().stream()
                .map(
                    column ->
                        "child." + quote(column.child()) + " = parent." + quote(column.parent()))
                .collect(Collectors.joining(" AND "));
        String childPresent =
            key.columns().stream()
                .map(column -> "child." + quote(column.child()) + " IS NOT NULL")
                .collect(Collectors.joining(" AND "));
        String parentMissing = "parent." + quote(key.columns().get(0).parent()) + " IS NULL";
        String sql =
            "SELECT COUNT(*) FROM "
                + qualified(table.name())
                + " child LEFT JOIN "
                + qualified(key.parentTable())
                + " parent ON "
                + join
                + " WHERE "
                + childPresent
                + " AND "
                + parentMissing;
        checks.add(
            new ConstraintCheck(table.name() + "." + key.name(), queryLong(connection, sql)));
      }
    }
    return checks.stream().sorted(Comparator.comparing(ConstraintCheck::name)).toList();
  }

  private List<FinancialAggregate> financialAggregates(
      Connection connection, List<TableDefinition> tables) throws SQLException {
    List<FinancialAggregate> aggregates = new ArrayList<>();
    boolean hasLegacyCategoryMap =
        tables.stream().anyMatch(table -> table.name().equals("LEGACY_CATEGORY_MAP"));
    for (TableDefinition table : tables) {
      Set<String> columns =
          table.columns().stream().map(ColumnDefinition::name).collect(Collectors.toSet());
      String dateColumn =
          columns.contains("TRANSACTION_DATE")
              ? "TRANSACTION_DATE"
              : columns.contains("DATE") ? "DATE" : null;
      if (!columns.contains("AMOUNT") || dateColumn == null) {
        continue;
      }
      String directionExpression;
      if (columns.contains("DIRECTION")) {
        directionExpression = "source_row." + quote("DIRECTION");
      } else if (table.name().contains("INCOME")) {
        directionExpression = "'INCOME'";
      } else if (table.name().contains("EXPENSE")) {
        directionExpression = "'EXPENSE'";
      } else {
        continue;
      }
      String activityExpression =
          columns.contains("ACTIVITY_ID")
              ? "source_row." + quote("ACTIVITY_ID")
              : "CAST(NULL AS BIGINT)";
      String categoryExpression = "CAST(NULL AS BIGINT)";
      String categoryJoin = "";
      if (columns.contains("NEUTRAL_CATEGORY_ID")) {
        categoryExpression = "source_row." + quote("NEUTRAL_CATEGORY_ID");
      } else if (columns.contains("CATEGORY_ID")) {
        if (hasLegacyCategoryMap && Set.of("INCOMES", "EXPENSES").contains(table.name())) {
          categoryExpression = "category_map." + quote("NEUTRAL_CATEGORY_ID");
          categoryJoin =
              " LEFT JOIN "
                  + qualified("LEGACY_CATEGORY_MAP")
                  + " category_map ON category_map."
                  + quote("LEGACY_CATEGORY_ID")
                  + " = source_row."
                  + quote("CATEGORY_ID");
        } else {
          categoryExpression = "source_row." + quote("CATEGORY_ID");
        }
      }
      String dateExpression = "source_row." + quote(dateColumn);
      String sql =
          "SELECT "
              + directionExpression
              + " AS direction, YEAR("
              + dateExpression
              + ") AS transaction_year, "
              + activityExpression
              + ", "
              + categoryExpression
              + ", COUNT(*), COALESCE(SUM("
              + "source_row."
              + quote("AMOUNT")
              + "), 0) FROM "
              + qualified(table.name())
              + " source_row"
              + categoryJoin
              + (columns.contains("DELETED_AT")
                  ? " WHERE source_row." + quote("DELETED_AT") + " IS NULL"
                  : "")
              + " GROUP BY "
              + directionExpression
              + ", YEAR("
              + dateExpression
              + ")"
              + (columns.contains("ACTIVITY_ID") ? ", " + activityExpression : "")
              + (!categoryExpression.equals("CAST(NULL AS BIGINT)")
                  ? ", " + categoryExpression
                  : "");
      try (var statement = connection.createStatement();
          ResultSet rows = statement.executeQuery(sql)) {
        while (rows.next()) {
          aggregates.add(
              new FinancialAggregate(
                  table.name(),
                  rows.getString(1),
                  rows.getInt(2),
                  nullableLong(rows, 3),
                  nullableLong(rows, 4),
                  rows.getLong(5),
                  rows.getBigDecimal(6)));
        }
      }
    }
    return aggregates.stream()
        .sorted(
            Comparator.comparing(FinancialAggregate::table)
                .thenComparing(FinancialAggregate::direction)
                .thenComparingInt(FinancialAggregate::year)
                .thenComparing(
                    FinancialAggregate::activityId, Comparator.nullsFirst(Long::compareTo))
                .thenComparing(
                    FinancialAggregate::categoryId, Comparator.nullsFirst(Long::compareTo)))
        .toList();
  }

  private String schemaVersion(Connection connection) throws SQLException {
    if (!tableExists(connection, "FLYWAY_SCHEMA_HISTORY")) {
      return "unversioned";
    }
    String sql =
        """
        SELECT "version"
        FROM "flyway_schema_history"
        WHERE "success" = TRUE AND "version" IS NOT NULL
        ORDER BY "installed_rank" DESC
        FETCH FIRST 1 ROW ONLY
        """;
    try (var statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      return rows.next() ? rows.getString(1) : "unversioned";
    }
  }

  private boolean tableExists(Connection connection, String table) throws SQLException {
    try (ResultSet rows = connection.getMetaData().getTables(null, SCHEMA, "%", null)) {
      while (rows.next()) {
        if (table.equalsIgnoreCase(rows.getString("TABLE_NAME"))) {
          return true;
        }
      }
      return false;
    }
  }

  private static void appendConstraintFailures(
      String kind, List<ConstraintCheck> checks, List<String> failures) {
    checks.stream()
        .filter(check -> check.violationCount() != 0)
        .forEach(
            check ->
                failures.add(
                    kind
                        + " check "
                        + check.name()
                        + " has "
                        + check.violationCount()
                        + " violations"));
  }

  private static void reconcilePreservedColumns(
      MigrationIntegrityManifest before,
      MigrationIntegrityManifest after,
      String table,
      List<String> failures) {
    if (Objects.equals(before.tableCounts().get(table), 0L)) {
      return;
    }
    SortedMap<String, SortedMap<String, String>> expectedRows =
        before.preservedColumnHashes().get(table);
    SortedMap<String, SortedMap<String, String>> observedRows =
        after.preservedColumnHashes().get(table);
    if (expectedRows == null || observedRows == null) {
      failures.add("table " + table + " canonical row hashes are missing");
      return;
    }
    List<String> preservedColumns =
        before.tableColumns().get(table).stream()
            .map(column -> column.substring(0, column.indexOf(':')))
            .toList();
    if (!preservedRowSignatures(expectedRows, preservedColumns)
        .equals(preservedRowSignatures(observedRows, preservedColumns))) {
      failures.add("table " + table + " canonical row hashes changed for preserved columns");
    }
  }

  private static List<String> preservedRowSignatures(
      SortedMap<String, SortedMap<String, String>> rows, List<String> columns) {
    return rows.values().stream()
        .map(
            row ->
                MigrationIntegrityManifestCodec.sha256(
                    columns.stream()
                        .map(column -> column + ":" + row.getOrDefault(column, "<MISSING>"))
                        .collect(Collectors.joining("\n"))))
        .sorted()
        .toList();
  }

  private static Map<AggregateKey, AggregateValue> aggregateSummaryValues(
      List<FinancialAggregate> aggregates, Set<String> includedTables) {
    Map<AggregateKey, AggregateValue> values = new TreeMap<>();
    aggregates.stream()
        .filter(aggregate -> includedTables.contains(aggregate.table()))
        .forEach(
            aggregate -> {
              AggregateKey key =
                  new AggregateKey(
                      aggregate.table(), aggregate.direction(), aggregate.year(), null, null);
              values.merge(
                  key,
                  new AggregateValue(aggregate.rowCount(), aggregate.amountTotal()),
                  AggregateValue::add);
            });
    return values;
  }

  private static Map<ParityKey, AggregateValue> parityValues(
      List<FinancialAggregate> aggregates, Set<String> includedTables) {
    Map<ParityKey, AggregateValue> values = new TreeMap<>();
    aggregates.stream()
        .filter(aggregate -> includedTables.contains(aggregate.table()))
        .forEach(
            aggregate -> {
              ParityKey key =
                  new ParityKey(
                      aggregate.direction(),
                      aggregate.year(),
                      aggregate.activityId(),
                      aggregate.categoryId());
              values.merge(
                  key,
                  new AggregateValue(aggregate.rowCount(), aggregate.amountTotal()),
                  AggregateValue::add);
            });
    return values;
  }

  private static Set<String> upperCase(Collection<String> values) {
    return values.stream()
        .map(String::toUpperCase)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static long queryLong(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private static Long nullableLong(ResultSet rows, int column) throws SQLException {
    long value = rows.getLong(column);
    return rows.wasNull() ? null : value;
  }

  private static String canonicalValue(Object value) {
    if (value == null) {
      return "<NULL>";
    }
    if (value instanceof BigDecimal decimal) {
      return decimal.toPlainString();
    }
    if (value instanceof byte[] bytes) {
      return Base64.getEncoder().encodeToString(bytes);
    }
    if (value instanceof Date date) {
      return date.toLocalDate().toString();
    }
    if (value instanceof Time time) {
      return time.toLocalTime().toString();
    }
    if (value instanceof Timestamp timestamp) {
      return timestamp.toLocalDateTime().toString();
    }
    if (value instanceof LocalDate
        || value instanceof LocalTime
        || value instanceof LocalDateTime
        || value instanceof OffsetDateTime
        || value instanceof OffsetTime) {
      return value.toString();
    }
    return value.getClass().getName() + ":" + value;
  }

  private static void appendDigest(java.security.MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private static String qualified(String table) {
    return quote(SCHEMA) + "." + quote(table);
  }

  private static String quote(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  private record ColumnDefinition(String name, String type, int ordinal) {}

  private record TableDefinition(
      String name, List<ColumnDefinition> columns, List<String> identityColumns) {}

  private record TableSnapshot(
      List<String> rowHashes, SortedMap<String, SortedMap<String, String>> preservedColumnHashes) {}

  private record IndexedColumn(String name, int position) {}

  private record ForeignKeyColumn(String child, String parent, int position) {}

  private record ForeignKey(String name, String parentTable, List<ForeignKeyColumn> columns) {
    private ForeignKey(String name, String parentTable) {
      this(name, parentTable, new ArrayList<>());
    }
  }

  private record AggregateKey(
      String table, String direction, int year, Long activityId, Long categoryId)
      implements Comparable<AggregateKey> {
    @Override
    public int compareTo(AggregateKey other) {
      return Comparator.comparing(AggregateKey::table)
          .thenComparing(AggregateKey::direction)
          .thenComparingInt(AggregateKey::year)
          .thenComparing(AggregateKey::activityId, Comparator.nullsFirst(Long::compareTo))
          .thenComparing(AggregateKey::categoryId, Comparator.nullsFirst(Long::compareTo))
          .compare(this, other);
    }
  }

  private record ParityKey(String direction, int year, Long activityId, Long categoryId)
      implements Comparable<ParityKey> {
    @Override
    public int compareTo(ParityKey other) {
      return Comparator.comparing(ParityKey::direction)
          .thenComparingInt(ParityKey::year)
          .thenComparing(ParityKey::activityId, Comparator.nullsFirst(Long::compareTo))
          .thenComparing(ParityKey::categoryId, Comparator.nullsFirst(Long::compareTo))
          .compare(this, other);
    }
  }

  private record AggregateValue(long rowCount, BigDecimal amountTotal) {
    private AggregateValue add(AggregateValue other) {
      return new AggregateValue(rowCount + other.rowCount, amountTotal.add(other.amountTotal));
    }
  }
}
