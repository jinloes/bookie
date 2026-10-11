package com.bookie.sqlserver;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One SQL Server container shared by every {@code sqlserver}-tagged test. Each test gets its own
 * database that matches the Azure SQL settings Bookie relies on: a case-sensitive collation (H2
 * compares strings case-sensitively), snapshot isolation for consistent exports, and a contained
 * application user holding only the runbook's least-privilege roles.
 */
public final class SqlServerTestContainer {

  public static final String COLLATION = "Latin1_General_100_CS_AS_SC";
  public static final String APP_USER = "bookie_app";

  private static final MSSQLServerContainer<?> CONTAINER =
      new MSSQLServerContainer<>(
              DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
          .acceptLicense();

  private static boolean configured;

  private SqlServerTestContainer() {}

  /** A test database and the password of its contained application user. */
  public record Database(String name, String appPassword) {

    public String url() {
      return "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=true;trustServerCertificate=true"
          .formatted(
              CONTAINER.getHost(),
              CONTAINER.getMappedPort(MSSQLServerContainer.MS_SQL_SERVER_PORT),
              name);
    }

    /** Connects as the contained application user, as Bookie does. */
    public DataSource appDataSource() {
      return dataSource(APP_USER, appPassword);
    }

    /** Connects as {@code sa}, for setup the application user is not allowed to do. */
    public DataSource adminDataSource() {
      return dataSource(CONTAINER.getUsername(), CONTAINER.getPassword());
    }

    /** Applies Bookie's SQL Server migrations as the application user. */
    public void migrate() {
      Flyway.configure()
          .dataSource(appDataSource())
          .locations("classpath:db/migration-sqlserver")
          .load()
          .migrate();
    }

    /** Runs {@code sql} as {@code sa}. */
    public void executeAsAdmin(String sql) throws SQLException {
      try (Connection connection = adminDataSource().getConnection();
          Statement statement = connection.createStatement()) {
        statement.execute(sql);
      }
    }

    private DataSource dataSource(String user, String password) {
      SQLServerDataSource source = new SQLServerDataSource();
      source.setURL(url());
      source.setUser(user);
      source.setPassword(password);
      return source;
    }
  }

  /** Creates an empty database with snapshot isolation on and a least-privilege app user. */
  public static Database createDatabase() throws SQLException {
    return createDatabase(true);
  }

  public static Database createDatabase(boolean snapshotIsolation) throws SQLException {
    start();
    String name = "bk_" + UUID.randomUUID().toString().replace("-", "");
    // Satisfies the SQL Server password complexity policy.
    String password = "Aa1!" + UUID.randomUUID();
    try (Connection connection = CONTAINER.createConnection("");
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE DATABASE %s CONTAINMENT = PARTIAL COLLATE %s".formatted(name, COLLATION));
      statement.execute(
          "ALTER DATABASE %s SET ALLOW_SNAPSHOT_ISOLATION %s"
              .formatted(name, snapshotIsolation ? "ON" : "OFF"));
      statement.execute("USE " + name);
      statement.execute("CREATE USER %s WITH PASSWORD = '%s'".formatted(APP_USER, password));
      for (String role : new String[] {"db_datareader", "db_datawriter", "db_ddladmin"}) {
        statement.execute("ALTER ROLE %s ADD MEMBER %s".formatted(role, APP_USER));
      }
    }
    return new Database(name, password);
  }

  private static synchronized void start() throws SQLException {
    if (configured) {
      return;
    }
    CONTAINER.start();
    try (Connection connection = CONTAINER.createConnection("");
        Statement statement = connection.createStatement()) {
      statement.execute("EXEC sp_configure 'contained database authentication', 1");
      statement.execute("RECONFIGURE");
    }
    configured = true;
  }
}
