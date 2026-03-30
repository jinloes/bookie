package com.bookie.config;

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

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void run(ApplicationArguments args) {
    dropStalePropertyNameColumn("payer_property_history");
    dropStalePropertyNameColumn("email_keyword_property_history");
  }

  /** Drops the legacy property_name column (replaced by a property_id FK) if it still exists. */
  private void dropStalePropertyNameColumn(String table) {
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
