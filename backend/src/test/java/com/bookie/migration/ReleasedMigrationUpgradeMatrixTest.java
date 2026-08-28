package com.bookie.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReleasedMigrationUpgradeMatrixTest {

  @ParameterizedTest
  @ValueSource(strings = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"})
  void upgradesEveryReleasedSchemaVersionToTheCurrentSchema(String startingVersion)
      throws Exception {
    String url =
        "jdbc:h2:mem:released_upgrade_"
            + startingVersion
            + "_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion(startingVersion))
        .load()
        .migrate();

    Flyway.configure().dataSource(url, "sa", "").load().migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT "version"
                FROM "flyway_schema_history"
                WHERE "success" = TRUE AND "version" IS NOT NULL
                ORDER BY "installed_rank" DESC
                FETCH FIRST 1 ROW ONLY
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).isEqualTo("14");
    }
  }
}
