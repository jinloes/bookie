package com.bookie.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookie.config.ClockConfiguration;
import com.bookie.ledger.application.ResolvedTransactionReferences;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

// Runs the mapping SQL against the Flyway schema; SqlServerLedgerReferenceResolverTest runs it on
// the T-SQL schema.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(ClockConfiguration.class)
class JpaLedgerReferenceResolverTest {

  static final String SEEDED_MAPPING_SQL =
      """
      SELECT mapping.id, activity.id AS activity_id, mapping.neutral_category_id,
             mapping.legacy_category_id
      FROM category_reporting_mappings mapping
      JOIN activity_reporting_profile_assignments assignment
        ON assignment.reporting_profile_id = mapping.reporting_profile_id
      JOIN financial_activities activity ON activity.id = assignment.activity_id
      WHERE activity.system_key = 'NEEDS_CLASSIFICATION'
      ORDER BY mapping.id
      """;

  private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 10, 14);

  @Autowired private TestEntityManager entityManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbcTemplate;
  private JpaLedgerReferenceResolver resolver;
  private Map<String, Object> mapping;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(dataSource);
    resolver = new JpaLedgerReferenceResolver(entityManager.getEntityManager(), jdbcTemplate);
    mapping = jdbcTemplate.queryForList(SEEDED_MAPPING_SQL).getFirst();
  }

  private long longValue(String column) {
    return ((Number) mapping.get(column)).longValue();
  }

  @Nested
  class Resolve {

    @Test
    void resolvesTheActiveMappingForTheActivityProfile() {
      ResolvedTransactionReferences references =
          resolver.resolve(
              longValue("activity_id"), longValue("neutral_category_id"), null, EFFECTIVE_ON);

      assertThat(references.financialCategoryId()).isEqualTo(longValue("legacy_category_id"));
      assertThat(references.activity().getId()).isEqualTo(longValue("activity_id"));
      assertThat(references.neutralCategory().getId()).isEqualTo(longValue("neutral_category_id"));
    }

    @Test
    void ignoresAnInactiveMapping() {
      jdbcTemplate.update(
          "UPDATE category_reporting_mappings SET active = ? WHERE id = ?", false, longValue("id"));

      assertThatThrownBy(
              () ->
                  resolver.resolve(
                      longValue("activity_id"),
                      longValue("neutral_category_id"),
                      null,
                      EFFECTIVE_ON))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).contains("no unique mapping");
              });
    }
  }
}
