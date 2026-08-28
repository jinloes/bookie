package com.bookie.catalog.reportpolicy.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NonOverlappingReportingProfileAssignmentTriggerTest {

  private NonOverlappingReportingProfileAssignmentTrigger trigger;

  @BeforeEach
  void setUp() {
    trigger = new NonOverlappingReportingProfileAssignmentTrigger();
  }

  @Nested
  class Fire {

    @Test
    void rejectsAnOverlappingOpenRange() throws Exception {
      try (Connection connection = connectionWithExistingRange()) {
        Object[] newRow = row(null, 10L, LocalDate.of(2025, 6, 1), null);

        assertThatThrownBy(() -> trigger.fire(connection, null, newRow))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("must not overlap")
            .extracting("SQLState")
            .isEqualTo("23514");
      }
    }

    @Test
    void permitsAnAdjacentRangeAndExcludesTheUpdatedRowItself() throws Exception {
      try (Connection connection = connectionWithExistingRange()) {
        Object[] adjacent = row(2L, 10L, Date.valueOf("2026-01-01"), Date.valueOf("2026-12-31"));
        Object[] existing = row(1L, 10L, Date.valueOf("2025-01-01"), Date.valueOf("2025-12-31"));

        assertThatCode(() -> trigger.fire(connection, null, adjacent)).doesNotThrowAnyException();
        assertThatCode(() -> trigger.fire(connection, existing, existing))
            .doesNotThrowAnyException();
      }
    }

    @Test
    void rejectsNonNumericIdentifiers() throws Exception {
      try (Connection connection = connectionWithExistingRange()) {
        Object[] newRow =
            row("not-an-id", 10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> trigger.fire(connection, null, newRow))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("not numeric")
            .extracting("SQLState")
            .isEqualTo("22018");
      }
    }

    @Test
    void rejectsUnsupportedDateValues() throws Exception {
      try (Connection connection = connectionWithExistingRange()) {
        Object[] newRow = row(2L, 10L, "2026-01-01", null);

        assertThatThrownBy(() -> trigger.fire(connection, null, newRow))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("date is invalid")
            .extracting("SQLState")
            .isEqualTo("22007");
      }
    }
  }

  private Connection connectionWithExistingRange() throws SQLException {
    String url =
        "jdbc:h2:mem:trigger_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1";
    Connection connection = DriverManager.getConnection(url, "sa", "");
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE activity_reporting_profile_assignments (
              id BIGINT PRIMARY KEY,
              activity_id BIGINT NOT NULL,
              reporting_profile_id BIGINT NOT NULL,
              effective_from DATE NOT NULL,
              effective_to DATE
          )
          """);
      statement.executeUpdate(
          """
          INSERT INTO activity_reporting_profile_assignments
              (id, activity_id, reporting_profile_id, effective_from, effective_to)
          VALUES (1, 10, 1, DATE '2025-01-01', DATE '2025-12-31')
          """);
    }
    return connection;
  }

  private Object[] row(Object id, Object activityId, Object effectiveFrom, Object effectiveTo) {
    return new Object[] {id, activityId, 1L, effectiveFrom, effectiveTo};
  }
}
