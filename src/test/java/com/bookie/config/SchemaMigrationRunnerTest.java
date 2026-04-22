package com.bookie.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SchemaMigrationRunnerTest {

  @Mock private JdbcTemplate jdbcTemplate;

  private SchemaMigrationRunner runner;

  @BeforeEach
  void setUp() {
    runner = new SchemaMigrationRunner(jdbcTemplate);
    when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString()))
        .thenReturn(List.of());
  }

  @Nested
  class ConvertSourceTypeEnumToVarchar {

    @Test
    void executesAlterTableWhenEnumColumnExists() {
      when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("PENDING_EXPENSES")))
          .thenReturn(List.of("SOURCE_TYPE"));

      runner.run(null);

      verify(jdbcTemplate)
          .execute("ALTER TABLE pending_expenses ALTER COLUMN source_type VARCHAR(255)");
    }

    @Test
    void skipsAlterTableWhenColumnAlreadyVarchar() {
      runner.run(null);

      verify(jdbcTemplate, never())
          .execute("ALTER TABLE pending_expenses ALTER COLUMN source_type VARCHAR(255)");
    }

    @Test
    void executesForExpensesTable() {
      when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("EXPENSES")))
          .thenReturn(List.of("SOURCE_TYPE"));

      runner.run(null);

      verify(jdbcTemplate).execute("ALTER TABLE expenses ALTER COLUMN source_type VARCHAR(255)");
    }
  }

  @Nested
  class DropStalePropertyNameColumn {

    @Test
    void dropsConstraintAndColumnWhenConstraintExists() {
      when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("PAYER_PROPERTY_HISTORY")))
          .thenReturn(List.of("FK_PROPERTY_NAME"));

      runner.run(null);

      verify(jdbcTemplate)
          .execute("ALTER TABLE payer_property_history DROP CONSTRAINT \"FK_PROPERTY_NAME\"");
      verify(jdbcTemplate)
          .execute("ALTER TABLE payer_property_history DROP COLUMN IF EXISTS property_name");
    }

    @Test
    void dropsOnlyColumnWhenNoConstraintExists() {
      runner.run(null);

      verify(jdbcTemplate, never()).execute(contains("DROP CONSTRAINT"));
      verify(jdbcTemplate)
          .execute("ALTER TABLE payer_property_history DROP COLUMN IF EXISTS property_name");
    }

    @Test
    void runsForBothHistoryTables() {
      runner.run(null);

      verify(jdbcTemplate)
          .execute("ALTER TABLE payer_property_history DROP COLUMN IF EXISTS property_name");
      verify(jdbcTemplate)
          .execute(
              "ALTER TABLE email_keyword_property_history DROP COLUMN IF EXISTS property_name");
    }
  }
}
