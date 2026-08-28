package com.bookie.catalog.reportpolicy.infrastructure;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import org.h2.api.Trigger;

public class NonOverlappingReportingProfileAssignmentTrigger implements Trigger {

  private static final int ID_INDEX = 0;
  private static final int ACTIVITY_ID_INDEX = 1;
  private static final int EFFECTIVE_FROM_INDEX = 3;
  private static final int EFFECTIVE_TO_INDEX = 4;

  @Override
  public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
    Long assignmentId = number(newRow[ID_INDEX]);
    Long activityId = number(newRow[ACTIVITY_ID_INDEX]);
    LocalDate effectiveFrom = date(newRow[EFFECTIVE_FROM_INDEX]);
    LocalDate effectiveTo =
        newRow[EFFECTIVE_TO_INDEX] == null ? LocalDate.MAX : date(newRow[EFFECTIVE_TO_INDEX]);
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            SELECT COUNT(*)
            FROM activity_reporting_profile_assignments existing
            WHERE existing.activity_id = ?
              AND existing.effective_from <= ?
              AND (
                existing.effective_to IS NULL
                OR existing.effective_to >= ?
              )
              AND (? IS NULL OR existing.id <> ?)
            """)) {
      statement.setLong(1, activityId);
      statement.setObject(2, effectiveTo);
      statement.setObject(3, effectiveFrom);
      if (assignmentId == null) {
        statement.setNull(4, java.sql.Types.BIGINT);
        statement.setNull(5, java.sql.Types.BIGINT);
      } else {
        statement.setLong(4, assignmentId);
        statement.setLong(5, assignmentId);
      }
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        if (result.getLong(1) > 0) {
          throw new SQLException(
              "Reporting profile assignments for an activity must not overlap", "23514");
        }
      }
    }
  }

  private Long number(Object value) throws SQLException {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new SQLException("Reporting profile assignment identifier is not numeric", "22018");
  }

  private LocalDate date(Object value) throws SQLException {
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (value instanceof Date sqlDate) {
      return sqlDate.toLocalDate();
    }
    throw new SQLException("Reporting profile assignment date is invalid", "22007");
  }
}
