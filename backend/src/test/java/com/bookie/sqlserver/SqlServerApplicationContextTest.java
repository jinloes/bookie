package com.bookie.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import java.sql.SQLException;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.sqlserver.SQLServerStorageProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole application starts against SQL Server through the {@code BOOKIE_DB_VENDOR} switch,
 * migrates with Flyway, passes Hibernate schema validation and runs as the least-privilege user.
 */
@Tag("sqlserver")
@SpringBootTest(
    properties = {
      "BOOKIE_DB_VENDOR=sqlserver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "bookie.db.allow-env-password=true",
      "bookie.db.allow-insecure-tls=true"
    })
class SqlServerApplicationContextTest {

  @Autowired private Environment environment;
  @Autowired private StorageProvider storage;
  @Autowired private DurableBackgroundJobWorker.Settings settings;
  @Autowired private JdbcTemplate jdbc;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws SQLException {
    SqlServerTestContainer.Database database = SqlServerTestContainer.createDatabase();
    registry.add("BOOKIE_DB_URL", database::url);
    registry.add("BOOKIE_DB_USERNAME", () -> SqlServerTestContainer.APP_USER);
    registry.add("BOOKIE_DB_PASSWORD", database::appPassword);
  }

  @Test
  void activatesTheSqlServerProfileAndOnDemandIntake() {
    assertThat(environment.getActiveProfiles()).contains("sqlserver");
    assertThat(environment.getProperty("bookie.db.vendor")).isEqualTo("sqlserver");
    assertThat(settings.onDemand()).isTrue();
  }

  @Test
  void migratesTheSqlServerBaseline() {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT TOP 1 version FROM flyway_schema_history
                WHERE success = 1 ORDER BY installed_rank DESC
                """,
                String.class))
        .isEqualTo("24");
  }

  @Test
  void storesJobRunrStateInSqlServer() {
    assertThat(storage).isInstanceOf(SQLServerStorageProvider.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'jobrunr_jobs'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void connectsAsTheContainedApplicationUser() {
    assertThat(jdbc.queryForObject("SELECT USER_NAME()", String.class))
        .isEqualTo(SqlServerTestContainer.APP_USER);
    assertThat(jdbc.queryForObject("SELECT IS_SRVROLEMEMBER('sysadmin')", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT IS_ROLEMEMBER('db_owner')", Integer.class)).isZero();
  }
}
