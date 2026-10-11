package com.bookie.datalifecycle.portable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import com.bookie.datalifecycle.portable.PortableBackupArchive.Manifest;
import com.bookie.datalifecycle.portable.PortableBackupArchive.TableEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortableBackupArchiveTest {

  private static final String ROWS = "{\"id\":1,\"name\":\"Rent\"}\n";

  @TempDir Path dir;

  private final PortableBackupArchive archive = new PortableBackupArchive();
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  private static MigrationIntegrityManifest integrity() {
    MigrationIntegrityManifest unsealed =
        new MigrationIntegrityManifest(
            MigrationIntegrityManifest.CURRENT_VERSION,
            "24",
            "test",
            "source-hash",
            new TreeMap<>(Map.of("CATEGORIES", 1L)),
            new TreeMap<>(Map.of("CATEGORIES", List.of("ID:INTEGER", "NAME:STRING"))),
            new TreeMap<>(Map.of("CATEGORIES", List.of("ID"))),
            List.of(),
            new TreeMap<>(),
            List.of(),
            List.of(),
            new TreeMap<>(Map.of("CATEGORIES", List.of("abc"))),
            new TreeMap<>(),
            "");
    return new MigrationIntegrityManifestCodec().seal(unsealed);
  }

  private static Manifest header() {
    return Manifest.builder()
        .flywayVersion("24")
        .vendor("h2")
        .createdAt("2026-10-03T00:00:00Z")
        .integrityManifest(integrity())
        .build();
  }

  private static Map<String, List<Map<String, Object>>> rows() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", "Rent");
    row.put("id", 1);
    return Map.of("CATEGORIES", List.of(row));
  }

  private Manifest manifest(List<TableEntry> tables, int version) {
    return new Manifest(version, "24", "h2", "2026-10-03T00:00:00Z", tables, integrity());
  }

  private static TableEntry entry(String rows, long count) {
    return new TableEntry(
        "categories",
        count,
        MigrationIntegrityManifestCodec.sha256(rows.getBytes(StandardCharsets.UTF_8)));
  }

  private Path zip(Map<String, byte[]> entries) throws IOException {
    Path target = dir.resolve("crafted.zip");
    try (OutputStream file = Files.newOutputStream(target);
        ZipOutputStream zip = new ZipOutputStream(file)) {
      for (Map.Entry<String, byte[]> e : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(e.getKey()));
        zip.write(e.getValue());
        zip.closeEntry();
      }
    }
    return target;
  }

  private Path zip(Manifest manifest, String rows) throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
    entries.put("tables/categories.jsonl", rows.getBytes(StandardCharsets.UTF_8));
    return zip(entries);
  }

  @Nested
  class Write {

    @Test
    void writesSortedRowsAndAVerifiableManifest() throws IOException {
      Path target = dir.resolve("b.zip");

      Manifest written = archive.write(target, header(), rows());

      assertThat(written.formatVersion()).isEqualTo(PortableBackupArchive.FORMAT_VERSION);
      assertThat(written.tables()).containsExactly(entry(ROWS, 1));
      PortableBackupArchive.Contents contents = archive.read(target);
      assertThat(contents.manifest()).isEqualTo(written);
      assertThat(contents.rows())
          .containsExactly(Map.entry("CATEGORIES", List.of(Map.of("id", 1, "name", "Rent"))));
    }

    @Test
    void writesAnEmptyTable() throws IOException {
      Path target = dir.resolve("b.zip");

      archive.write(target, header(), Map.of("CATEGORIES", List.of()));

      assertThat(archive.read(target).rows()).containsExactly(Map.entry("CATEGORIES", List.of()));
    }
  }

  @Nested
  class Read {

    @Test
    void skipsBlankLines() throws IOException {
      String rows = "{\"id\":1}\n\n{\"id\":2}\n";

      assertThat(
              archive
                  .read(zip(manifest(List.of(entry(rows, 2)), 1), rows))
                  .rows()
                  .get("CATEGORIES"))
          .hasSize(2);
    }

    @Test
    void rejectsAnUnexpectedEntry() throws IOException {
      Path source = zip(Map.of("other.txt", new byte[0]));

      assertThatThrownBy(() -> archive.read(source))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("Unexpected entry")
          .hasMessageContaining("other.txt");
    }

    @Test
    void rejectsATableEntryWithTheWrongSuffix() throws IOException {
      Path source = zip(Map.of("tables/categories.csv", new byte[0]));

      assertThatThrownBy(() -> archive.read(source)).hasMessageContaining("Unexpected entry");
    }

    @Test
    void rejectsAMissingManifest() throws IOException {
      Path source = zip(Map.of("tables/categories.jsonl", new byte[0]));

      assertThatThrownBy(() -> archive.read(source))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("no manifest.json");
    }

    @Test
    void rejectsAnUnsupportedVersion() throws IOException {
      Path source = zip(manifest(List.of(entry(ROWS, 1)), 2), ROWS);

      assertThatThrownBy(() -> archive.read(source))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("version: 2");
    }

    @Test
    void rejectsAManifestWithoutIntegrityData() throws IOException {
      Manifest manifest = new Manifest(1, "24", "h2", "t", List.of(entry(ROWS, 1)), null);

      assertThatThrownBy(() -> archive.read(zip(manifest, ROWS)))
          .hasMessageContaining("incomplete");
    }

    @Test
    void rejectsAManifestWithoutTables() throws IOException {
      assertThatThrownBy(() -> archive.read(zip(manifest(null, 1), ROWS)))
          .hasMessageContaining("incomplete");
    }

    @Test
    void rejectsATamperedIntegrityManifest() throws IOException {
      MigrationIntegrityManifest sealed = integrity();
      MigrationIntegrityManifest tampered =
          new MigrationIntegrityManifest(
              sealed.manifestVersion(),
              "99",
              sealed.applicationVersion(),
              sealed.sourceDatabaseHash(),
              sealed.tableCounts(),
              sealed.tableColumns(),
              sealed.tableIdentityColumns(),
              sealed.financialAggregates(),
              sealed.referenceCounts(),
              sealed.uniquenessChecks(),
              sealed.orphanChecks(),
              sealed.rowHashes(),
              sealed.preservedColumnHashes(),
              sealed.contentChecksum());
      Manifest manifest = new Manifest(1, "24", "h2", "t", List.of(entry(ROWS, 1)), tampered);

      assertThatThrownBy(() -> archive.read(zip(manifest, ROWS)))
          .isInstanceOf(MigrationIntegrityException.class);
    }

    @Test
    void rejectsAMissingTableFile() throws IOException {
      Path source =
          zip(
              Map.of(
                  "manifest.json", mapper.writeValueAsBytes(manifest(List.of(entry(ROWS, 1)), 1))));

      assertThatThrownBy(() -> archive.read(source))
          .hasMessageContaining("missing table categories");
    }

    @Test
    void rejectsATamperedTableFile() throws IOException {
      Path source = zip(manifest(List.of(entry(ROWS, 1)), 1), ROWS.replace("Rent", "Rest"));

      assertThatThrownBy(() -> archive.read(source))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("hash mismatch for table categories");
    }

    @Test
    void rejectsAWrongRowCount() throws IOException {
      Path source = zip(manifest(List.of(entry(ROWS, 2)), 1), ROWS);

      assertThatThrownBy(() -> archive.read(source))
          .hasMessageContaining("row count mismatch for table categories");
    }

    @Test
    void rejectsTablesMissingFromTheManifest() throws IOException {
      Path source = zip(manifest(List.of(), 1), ROWS);

      assertThatThrownBy(() -> archive.read(source))
          .hasMessageContaining("missing from its manifest: [categories]");
    }
  }
}
