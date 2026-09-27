package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SharedTransactionAttachmentsMigrationTest {

  private static final String STATEMENT_FILE = "drive-item!statement";

  @Nested
  class UpgradeFromV23 {

    @Test
    void letsOneStatementFileBackSeveralTransactions() throws Exception {
      String url = databaseUrl();
      migrate(url, "23");
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        insertAttachment(statement, insertTransaction(statement));
        long second = insertTransaction(statement);
        assertThatThrownBy(() -> insertAttachment(statement, second))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("UK_TRANSACTION_ATTACHMENTS_EXTERNAL");
      }

      Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("24").load();
      MigrateResult result = flyway.migrate();

      assertThat(result.migrationsExecuted).isEqualTo(1);
      flyway.validate();
      try (Connection connection = DriverManager.getConnection(url, "sa", "");
          Statement statement = connection.createStatement()) {
        insertAttachment(statement, insertTransaction(statement));
        assertThat(
                queryLong(
                    statement,
                    """
                    SELECT COUNT(DISTINCT transaction_id)
                    FROM transaction_attachments
                    WHERE storage_provider = 'ONEDRIVE' AND external_id = '%s'
                    """
                        .formatted(STATEMENT_FILE)))
            .isEqualTo(2);
        assertThat(
                queryLong(
                    statement,
                    """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.INDEXES
                    WHERE INDEX_NAME = 'IX_TRANSACTION_ATTACHMENTS_EXTERNAL'
                    """))
            .isEqualTo(1);
      }
    }
  }

  private void migrate(String url, String target) {
    Flyway.configure().dataSource(url, "sa", "").target(target).load().migrate();
  }

  private long insertTransaction(Statement statement) throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO financial_transactions (
            amount, direction, transaction_date, description, activity_id, neutral_category_id
        )
        VALUES (
            1000.00,
            'INCOME',
            DATE '2026-08-31',
            'Venmo payment',
            (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
            (SELECT MIN(id) FROM neutral_categories WHERE direction = 'INCOME' AND active = TRUE)
        )
        """,
        Statement.RETURN_GENERATED_KEYS);
    try (ResultSet keys = statement.getGeneratedKeys()) {
      assertThat(keys.next()).isTrue();
      return keys.getLong(1);
    }
  }

  private void insertAttachment(Statement statement, long transactionId) throws SQLException {
    statement.executeUpdate(
        """
        INSERT INTO transaction_attachments (transaction_id, storage_provider, external_id, file_name)
        VALUES (%d, 'ONEDRIVE', '%s', 'VenmoStatement_August_2026.csv')
        """
            .formatted(transactionId, STATEMENT_FILE));
  }

  private long queryLong(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private String databaseUrl() {
    return "jdbc:h2:mem:shared_attachments_"
        + UUID.randomUUID().toString().replace("-", "")
        + ";DB_CLOSE_DELAY=-1";
  }
}
