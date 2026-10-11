package com.bookie.datalifecycle.portable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortableBackupExporterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-03T02:00:00Z"), ZoneOffset.UTC);

  @TempDir Path dir;

  private JdbcDataSource dataSource;
  private final PortableBackupArchive archive = new PortableBackupArchive();
  private final MigrationIntegrityVerifier verifier = new MigrationIntegrityVerifier();

  @BeforeEach
  void setUp() throws Exception {
    dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:export_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    Flyway.configure().dataSource(dataSource).load().migrate();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO financial_transactions (
              amount, direction, transaction_date, description, activity_id, neutral_category_id
          )
          VALUES (
              123.45,
              'INCOME',
              DATE '2026-08-24',
              'Synthetic backup row',
              (SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'),
              (SELECT MIN(id) FROM neutral_categories WHERE direction = 'INCOME')
          )
          """);
    }
  }

  private PortableBackupExporter exporter(PortableBackupArchive archive) {
    return new PortableBackupExporter(dataSource, verifier, archive, CLOCK, "test");
  }

  @Nested
  class Export {

    @Test
    void writesEveryBookieTableWithASealedManifest() throws Exception {
      Path target = dir.resolve("backup.zip");

      PortableBackupExporter.ExportResult result = exporter(archive).export(target);

      assertThat(result.archive()).isEqualTo(target);
      PortableBackupArchive.Manifest manifest = result.manifest();
      assertThat(manifest.vendor()).isEqualTo("h2");
      assertThat(manifest.flywayVersion()).isEqualTo("24");
      assertThat(manifest.createdAt()).isEqualTo("2026-10-03T02:00:00Z");
      assertThat(manifest.integrityManifest().applicationVersion()).isEqualTo("test");
      assertThat(manifest.integrityManifest().tableCounts())
          .containsEntry("FINANCIAL_TRANSACTIONS", 1L)
          .doesNotContainKeys("FLYWAY_SCHEMA_HISTORY");
      assertThat(manifest.integrityManifest().tableCounts().keySet())
          .noneMatch(table -> table.startsWith("JOBRUNR_"));

      PortableBackupArchive.Contents contents = archive.read(target);
      assertThat(contents.rows().keySet())
          .isEqualTo(manifest.integrityManifest().tableCounts().keySet());
      Map<String, Object> row = contents.rows().get("FINANCIAL_TRANSACTIONS").getFirst();
      assertThat(row)
          .containsEntry("amount", "123.45")
          .containsEntry("direction", "INCOME")
          .containsEntry("transaction_date", "2026-08-24")
          .containsEntry("description", "Synthetic backup row");
    }

    @Test
    void rollsBackAndRethrowsWhenWritingFails() throws Exception {
      PortableBackupArchive failing = mock(PortableBackupArchive.class);
      when(failing.write(any(), any(), any())).thenThrow(new IOException("disk full"));

      assertThatThrownBy(() -> exporter(failing).export(dir.resolve("backup.zip")))
          .isInstanceOf(IOException.class)
          .hasMessage("disk full");
      assertThat(Files.exists(dir.resolve("backup.zip"))).isFalse();
    }
  }

  @Nested
  class JsonValue {

    private final ResultSet result = mock(ResultSet.class);

    private Object value(String family) throws Exception {
      return PortableBackupExporter.jsonValue(
          result,
          1,
          new DatabaseVendorOperations.ColumnMetadata("C", family, Types.OTHER, false, false));
    }

    @Test
    void convertsEachFamilyToItsPortableForm() throws Exception {
      when(result.getLong(1)).thenReturn(7L);
      when(result.getBigDecimal(1)).thenReturn(new BigDecimal("1E+2"));
      when(result.getBoolean(1)).thenReturn(true);
      when(result.getDouble(1)).thenReturn(1.5);
      when(result.getString(1)).thenReturn("ABC-DEF");
      when(result.getBytes(1)).thenReturn(new byte[] {1, 2});
      when(result.getObject(1, LocalDate.class)).thenReturn(LocalDate.of(2026, 1, 2));
      when(result.getObject(1, LocalTime.class)).thenReturn(LocalTime.of(3, 4, 5));
      when(result.getObject(1, LocalDateTime.class))
          .thenReturn(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
      when(result.getObject(1, OffsetDateTime.class))
          .thenReturn(OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.ofHours(-7)));

      assertThat(value("INTEGER")).isEqualTo(7L);
      assertThat(value("DECIMAL")).isEqualTo("100");
      assertThat(value("BOOLEAN")).isEqualTo(true);
      assertThat(value("FLOAT")).isEqualTo(1.5);
      assertThat(value("UUID")).isEqualTo("abc-def");
      assertThat(value("BINARY")).isEqualTo("AQI=");
      assertThat(value("DATE")).isEqualTo("2026-01-02");
      assertThat(value("TIME")).isEqualTo("03:04:05");
      assertThat(value("TIMESTAMP")).isEqualTo("2026-01-02T03:04:05");
      assertThat(value("TIMESTAMP_TZ")).isEqualTo("2026-01-02T03:04:05-07:00");
      assertThat(value("STRING")).isEqualTo("ABC-DEF");
    }

    @Test
    void returnsNullForSqlNullInEveryFamily() throws Exception {
      when(result.wasNull()).thenReturn(true);

      for (String family :
          List.of(
              "INTEGER",
              "DECIMAL",
              "BOOLEAN",
              "FLOAT",
              "UUID",
              "BINARY",
              "DATE",
              "TIME",
              "TIMESTAMP",
              "TIMESTAMP_TZ",
              "STRING")) {
        assertThat(value(family)).as(family).isNull();
      }
    }
  }
}
