package com.bookie.config;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs one-time idempotent DDL fixes that Hibernate's ddl-auto=update cannot handle (e.g. dropping
 * stale columns left over from a rename/refactor).
 */
@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner implements ApplicationRunner {

  private static final Set<String> CONVERT_ENUM_TABLES = Set.of("PENDING_EXPENSES", "EXPENSES");
  private static final Set<String> DROP_PROPERTY_NAME_TABLES =
      Set.of("PAYER_PROPERTY_HISTORY", "EMAIL_KEYWORD_PROPERTY_HISTORY");

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void run(ApplicationArguments args) {
    dropStalePropertyNameColumn("payer_property_history");
    dropStalePropertyNameColumn("email_keyword_property_history");
    convertSourceTypeEnumToVarchar("pending_expenses");
    convertSourceTypeEnumToVarchar("expenses");
  }

  /**
   * Converts the source_type column from H2 ENUM to VARCHAR if needed. H2 2.x maps
   * {@code @Enumerated(EnumType.STRING)} columns as native ENUM types with the allowed values baked
   * into the column definition. Adding a new Java enum value (e.g. RECEIPT) does not update the
   * column type, causing {@code Value not permitted} errors on insert. Converting to VARCHAR lets
   * Hibernate manage the column as a plain string; existing data is preserved.
   */
  private void convertSourceTypeEnumToVarchar(String table) {
    if (!CONVERT_ENUM_TABLES.contains(table.toUpperCase())) {
      throw new IllegalArgumentException("Table not whitelisted for enum conversion: " + table);
    }
    List<String> enumCols =
        jdbcTemplate.queryForList(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE UPPER(TABLE_NAME) = ?"
                + " AND UPPER(COLUMN_NAME) = 'SOURCE_TYPE'"
                + " AND DATA_TYPE = 'ENUM'",
            String.class,
            table.toUpperCase());
    if (!enumCols.isEmpty()) {
      jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN source_type VARCHAR(255)");
    }
  }

  /** Drops the legacy property_name column (replaced by a property_id FK) if it still exists. */
  private void dropStalePropertyNameColumn(String table) {
    if (!DROP_PROPERTY_NAME_TABLES.contains(table.toUpperCase())) {
      throw new IllegalArgumentException("Table not whitelisted for property_name drop: " + table);
    }
    jdbcTemplate
        .queryForList(
            "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE"
                + " WHERE UPPER(TABLE_NAME) = ?"
                + " AND UPPER(COLUMN_NAME) = 'PROPERTY_NAME'",
            String.class,
            table.toUpperCase())
        .forEach(
            name ->
                jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + name + "\""));
    jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN IF EXISTS property_name");
  }
}
