package com.bookie.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.sqlserver.SqlServerTestContainer;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** The mapping SQL must parse on SQL Server, which has no TRUE/FALSE literals. */
@Tag("sqlserver")
class SqlServerLedgerReferenceResolverTest {

  private static JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void migrate() throws SQLException {
    SqlServerTestContainer.Database database = SqlServerTestContainer.createDatabase();
    database.migrate();
    jdbcTemplate = new JdbcTemplate(database.appDataSource());
  }

  @Test
  void resolvesTheSeededMappingOnSqlServer() {
    Map<String, Object> mapping =
        jdbcTemplate.queryForList(JpaLedgerReferenceResolverTest.SEEDED_MAPPING_SQL).getFirst();
    long activityId = ((Number) mapping.get("activity_id")).longValue();
    long neutralCategoryId = ((Number) mapping.get("neutral_category_id")).longValue();
    EntityManager entityManager = mock(EntityManager.class);
    FinancialActivity activity = mock(FinancialActivity.class);
    when(activity.isActive()).thenReturn(true);
    when(entityManager.find(FinancialActivity.class, activityId)).thenReturn(activity);
    when(entityManager.find(NeutralCategory.class, neutralCategoryId))
        .thenReturn(mock(NeutralCategory.class));

    var references =
        new JpaLedgerReferenceResolver(entityManager, jdbcTemplate)
            .resolve(activityId, neutralCategoryId, null, LocalDate.of(2026, 10, 14));

    assertThat(references.financialCategoryId())
        .isEqualTo(((Number) mapping.get("legacy_category_id")).longValue());
  }
}
