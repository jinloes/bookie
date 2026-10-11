package com.bookie.datalifecycle.portable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

/** Azure SQL / SQL Server operations. */
class SqlServerVendorOperations implements DatabaseVendorOperations {

  // SQLServerConnection.TRANSACTION_SNAPSHOT; referenced by value to keep the driver off the
  // compile path of vendor-neutral code.
  static final int TRANSACTION_SNAPSHOT = 4096;

  @Override
  public String vendor() {
    return SQL_SERVER;
  }

  @Override
  public String schema() {
    return "dbo";
  }

  @Override
  public String physicalName(String logicalName) {
    return logicalName.toLowerCase(Locale.ROOT);
  }

  @Override
  public void beginSnapshot(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT snapshot_isolation_state FROM sys.databases WHERE name = DB_NAME()")) {
      if (!result.next() || result.getInt(1) != 1) {
        throw new IllegalStateException(
            """
            Snapshot isolation is off for this database. Run \
            ALTER DATABASE CURRENT SET ALLOW_SNAPSHOT_ISOLATION ON and retry.""");
      }
    }
    connection.setAutoCommit(false);
    connection.setTransactionIsolation(TRANSACTION_SNAPSHOT);
  }

  @Override
  public void disableConstraints(Connection connection, List<String> tables) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      for (String table : tables) {
        statement.execute("ALTER TABLE " + qualified(table) + " NOCHECK CONSTRAINT ALL");
      }
    }
  }

  @Override
  public void enableConstraints(Connection connection, List<String> tables) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      for (String table : tables) {
        statement.execute("ALTER TABLE " + qualified(table) + " WITH CHECK CHECK CONSTRAINT ALL");
      }
    }
  }

  @Override
  public void setIdentityInsert(Connection connection, String table, boolean enabled)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET IDENTITY_INSERT " + qualified(table) + (enabled ? " ON" : " OFF"));
    }
  }

  @Override
  public void reseed(Connection connection, String table, String column, long floor)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "DBCC CHECKIDENT ('"
              + schema()
              + "."
              + physicalName(table)
              + "', RESEED, "
              + floor
              + ") WITH NO_INFOMSGS");
    }
  }
}
