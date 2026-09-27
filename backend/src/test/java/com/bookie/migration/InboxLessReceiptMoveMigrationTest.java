package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InboxLessReceiptMoveMigrationTest {

  private static final String RECORDED = "com.bookie.ledger.application.TransactionRecorded";

  @Nested
  class UpgradeFromReleasedV21 {

    @Test
    void convertsUnfinishedReceiptFilingsIntoMoveJobsAndDropsTheRegistry() throws Exception {
      String url = databaseUrl();
      migrateTo(url, "21");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        insertInboxJob(statement);
        publication(
            statement,
            RECORDED,
            """
            {"transactionId":41,"origin":"RECEIPT","attachmentExternalId":"drive-41",\
            "date":"2025-03-09"}""",
            false);
        publication(
            statement,
            RECORDED,
            """
            {"transactionId":42,"origin":"RECEIPT","attachmentExternalId":"drive-42",\
            "date":[2024,12,31]}""",
            false);
        publication(
            statement,
            RECORDED,
            """
            {"transactionId":43,"origin":"RECEIPT","attachmentExternalId":"drive-43",\
            "date":"2025-01-01"}""",
            true);
        publication(
            statement,
            "com.bookie.intake.application.InboxItemQueued",
            "{\"inboxItemId\":5}",
            false);
      }

      migrateTo(url, "22");

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(
                rows(
                    statement,
                    """
                    SELECT idempotency_key, type, state, source_external_id, target_year,
                           inbox_item_id, attempts, max_attempts
                    FROM background_jobs WHERE inbox_item_id IS NULL ORDER BY idempotency_key
                    """))
            .containsExactly(
                List.of(
                    "move_receipt:transaction:41",
                    "MOVE_RECEIPT",
                    "AVAILABLE",
                    "drive-41",
                    "2025",
                    "null",
                    "0",
                    "11"),
                List.of(
                    "move_receipt:transaction:42",
                    "MOVE_RECEIPT",
                    "AVAILABLE",
                    "drive-42",
                    "2024",
                    "null",
                    "0",
                    "11"));
        assertThat(
                rows(
                    statement,
                    "SELECT idempotency_key FROM background_jobs WHERE inbox_item_id = 101"))
            .containsExactly(List.of("historical-move"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'PUBLIC' AND table_name = 'EVENT_PUBLICATION'
                    """))
            .isEmpty();
      }
    }

    @Test
    void theSubjectConstraintAllowsOnlyInboxJobsOrInboxLessReceiptMoves() throws Exception {
      String url = databaseUrl();
      migrateTo(url, "22");

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        statement.executeUpdate(inboxLessJob("move-ok", "MOVE_RECEIPT", "'drive-1'"));
        assertThatThrownBy(
                () -> statement.executeUpdate(inboxLessJob("parse-bad", "PARSE_RECEIPT", "'x'")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("CK_BACKGROUND_JOBS_SUBJECT");
        assertThatThrownBy(
                () -> statement.executeUpdate(inboxLessJob("neither", "MOVE_RECEIPT", "NULL")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("CK_BACKGROUND_JOBS_SUBJECT");
      }
    }

    @Test
    void anUnreadableUnfinishedFilingAbortsBeforeAnySchemaChange() throws Exception {
      String url = databaseUrl();
      migrateTo(url, "21");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        publication(statement, RECORDED, "{\"transactionId\":44,\"origin\":\"RECEIPT\"}", false);
      }

      assertThatThrownBy(() -> migrateTo(url, "22")).isInstanceOf(FlywayException.class);

      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        assertThat(rows(statement, "SELECT COUNT(*) FROM event_publication"))
            .containsExactly(List.of("1"));
        assertThat(
                rows(
                    statement,
                    """
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_name = 'BACKGROUND_JOBS' AND column_name = 'INBOX_ITEM_ID'
                    """))
            .containsExactly(List.of("NO"));
      }
    }
  }

  private static void insertInboxJob(Statement statement) throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO inbox_items(id, origin, state, external_sync_state, raw_status,
            classification_ambiguous, created_at, updated_at, version)
        VALUES (101, 'RECEIPT', 'SAVED', 'PENDING', 'READY', FALSE,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """);
    statement.executeUpdate(
        """
        INSERT INTO background_jobs(inbox_item_id, type, idempotency_key, state, attempts,
            max_attempts, available_at, target_year, created_at, updated_at, version)
        VALUES (101, 'MOVE_RECEIPT', 'historical-move', 'AVAILABLE', 0, 11,
                CURRENT_TIMESTAMP, 2025, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """);
  }

  private static String inboxLessJob(String key, String type, String sourceExternalId) {
    return """
        INSERT INTO background_jobs(type, idempotency_key, state, attempts, max_attempts,
            available_at, source_external_id, created_at, updated_at, version)
        VALUES ('%s', '%s', 'AVAILABLE', 0, 11, CURRENT_TIMESTAMP, %s,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """
        .formatted(type, key, sourceExternalId);
  }

  private static void publication(
      Statement statement, String eventType, String serializedEvent, boolean completed)
      throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO event_publication (
            id, completion_date, event_type, listener_id, publication_date, serialized_event)
        VALUES (RANDOM_UUID(), %s, '%s', 'listener', CURRENT_TIMESTAMP, '%s')
        """
            .formatted(completed ? "CURRENT_TIMESTAMP" : "NULL", eventType, serializedEvent));
  }

  private static List<List<String>> rows(Statement statement, String sql) throws SQLException {
    List<List<String>> rows = new ArrayList<>();
    try (ResultSet result = statement.executeQuery(sql)) {
      int columns = result.getMetaData().getColumnCount();
      while (result.next()) {
        List<String> row = new ArrayList<>();
        for (int column = 1; column <= columns; column++) {
          row.add(String.valueOf(result.getObject(column)));
        }
        rows.add(row);
      }
    }
    return rows;
  }

  private static void migrateTo(String url, String version) {
    Flyway.configure().dataSource(url, "sa", "").target(version).load().migrate();
  }

  private static String databaseUrl() {
    return "jdbc:h2:mem:inbox_less_receipt_move_"
        + UUID.randomUUID().toString().replace("-", "")
        + ";DB_CLOSE_DELAY=-1";
  }
}
