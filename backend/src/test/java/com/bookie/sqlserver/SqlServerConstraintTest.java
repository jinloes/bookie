package com.bookie.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The T-SQL baseline enforces the same constraints H2 does, as the least-privilege app user. */
@Tag("sqlserver")
class SqlServerConstraintTest {

  private static final int UNIQUE_CONSTRAINT = 2627;
  private static final int UNIQUE_INDEX = 2601;
  private static final int FOREIGN_KEY = 547;
  private static final int OVERLAP_TRIGGER = 50001;

  private static SqlServerTestContainer.Database database;

  @BeforeAll
  static void migrate() throws SQLException {
    database = SqlServerTestContainer.createDatabase();
    database.migrate();
  }

  private static void execute(String sql) throws SQLException {
    try (Connection connection = database.appDataSource().getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private static long single(String sql) throws SQLException {
    try (Connection connection = database.appDataSource().getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static void assertRejected(String sql, Integer... errorCodes) {
    assertThatThrownBy(() -> execute(sql))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getErrorCode()).isIn((Object[]) errorCodes));
  }

  @Nested
  class ReportingProfileAssignments {

    private long activityId;

    @BeforeEach
    void createActivity() throws SQLException {
      String name = "Activity " + UUID.randomUUID();
      execute(
          """
          INSERT INTO financial_activities (name, activity_type, tax_treatment, owner_id)
          VALUES (N'%s', N'OTHER', N'NONE', 1)
          """
              .formatted(name));
      activityId = single("SELECT id FROM financial_activities WHERE name = N'" + name + "'");
    }

    private String assignment(String from, String to) {
      return """
          INSERT INTO activity_reporting_profile_assignments (
              activity_id, reporting_profile_id, effective_from, effective_to
          )
          VALUES (%d, 4, '%s', %s)
          """
          .formatted(activityId, from, to == null ? "NULL" : "'" + to + "'");
    }

    @Test
    void acceptsAdjacentClosedAssignmentsFollowedByOneOpenAssignment() {
      assertThatCode(
              () -> {
                execute(assignment("2020-01-01", "2020-12-31"));
                execute(assignment("2021-01-01", "2021-12-31"));
                execute(assignment("2022-01-01", null));
              })
          .doesNotThrowAnyException();
    }

    @Test
    void rejectsASecondOpenAssignment() throws SQLException {
      execute(assignment("2022-01-01", null));

      assertRejected(assignment("2023-01-01", null), UNIQUE_INDEX);
    }

    @Test
    void rejectsAnInsertOverlappingAClosedAssignment() throws SQLException {
      execute(assignment("2020-01-01", "2020-12-31"));

      assertRejected(assignment("2020-06-01", "2020-07-01"), OVERLAP_TRIGGER);
    }

    @Test
    void rejectsAnUpdateThatCreatesAnOverlap() throws SQLException {
      execute(assignment("2020-01-01", "2020-12-31"));
      execute(assignment("2021-01-01", "2021-12-31"));

      assertRejected(
          """
          UPDATE activity_reporting_profile_assignments SET effective_to = '2021-03-01'
          WHERE activity_id = %d AND effective_from = '2020-01-01'
          """
              .formatted(activityId),
          OVERLAP_TRIGGER);
    }

    @Test
    void rejectsTwoAssignmentsStartingOnTheSameDay() throws SQLException {
      execute(assignment("2020-01-01", "2020-01-31"));

      assertRejected(assignment("2020-01-01", "2020-01-15"), UNIQUE_CONSTRAINT, OVERLAP_TRIGGER);
    }
  }

  @Nested
  class TableConstraints {

    @Test
    void rejectsADuplicateUniqueValue() {
      assertRejected(
          """
          INSERT INTO financial_activities (name, activity_type, tax_treatment, owner_id)
          VALUES (N'Needs classification', N'OTHER', N'NONE', 1)
          """,
          UNIQUE_CONSTRAINT,
          UNIQUE_INDEX);
    }

    @Test
    void rejectsAMissingForeignKeyTarget() {
      assertRejected(
          """
          INSERT INTO financial_activities (name, activity_type, tax_treatment, owner_id)
          VALUES (N'Orphan', N'OTHER', N'NONE', 999)
          """,
          FOREIGN_KEY);
    }

    @Test
    void allowsSeveralRowsWithoutAFilteredUniqueValue() {
      assertThatCode(
              () -> {
                execute("INSERT INTO household_members (name) VALUES (N'First member')");
                execute("INSERT INTO household_members (name) VALUES (N'Second member')");
              })
          .doesNotThrowAnyException();
    }

    @Test
    void comparesTextCaseSensitivelyLikeH2() {
      assertThatCode(() -> execute("INSERT INTO household_members (name) VALUES (N'household')"))
          .doesNotThrowAnyException();
    }

    @Test
    void storesUnicodeTextUnchanged() throws SQLException {
      execute("INSERT INTO household_members (name) VALUES (N'Zoë 🏠 名前')");

      assertThat(single("SELECT COUNT(*) FROM household_members WHERE name = N'Zoë 🏠 名前'"))
          .isEqualTo(1);
    }
  }
}
