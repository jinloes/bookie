package com.bookie.datalifecycle.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifest.ConstraintCheck;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest.FinancialAggregate;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationIntegrityManifestCodecTest {

  private final MigrationIntegrityManifestCodec codec = new MigrationIntegrityManifestCodec();

  @Nested
  class Seal {

    @Test
    void producesARepeatableChecksum() {
      MigrationIntegrityManifest first = codec.seal(manifest());
      MigrationIntegrityManifest second = codec.seal(manifest());

      assertThat(first.contentChecksum()).hasSize(64);
      assertThat(second.contentChecksum()).isEqualTo(first.contentChecksum());
      codec.verify(first);
    }
  }

  @Nested
  class Persistence {

    @Test
    void writesAndReadsAChecksummedManifest(@TempDir Path directory) throws Exception {
      Path target = directory.resolve("migration-manifest.json");

      codec.write(target, manifest());
      MigrationIntegrityManifest restored = codec.read(target);

      assertThat(restored.tableCounts()).containsEntry("INCOMES", 1L);
      assertThat(restored.contentChecksum()).hasSize(64);
    }

    @Test
    void rejectsATamperedManifest(@TempDir Path directory) throws Exception {
      Path target = directory.resolve("migration-manifest.json");
      codec.write(target, manifest());
      String json = Files.readString(target).replace("\"INCOMES\" : 1", "\"INCOMES\" : 2");
      Files.writeString(target, json);

      assertThatThrownBy(() -> codec.read(target))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("checksum mismatch");
    }
  }

  private MigrationIntegrityManifest manifest() {
    TreeMap<String, Long> tableCounts = new TreeMap<>();
    tableCounts.put("INCOMES", 1L);
    TreeMap<String, Long> referenceCounts = new TreeMap<>();
    referenceCounts.put("INCOMES.SOURCE_ID", 1L);
    TreeMap<String, List<String>> rowHashes = new TreeMap<>();
    rowHashes.put("INCOMES", List.of("abc"));
    return new MigrationIntegrityManifest(
        MigrationIntegrityManifest.CURRENT_VERSION,
        "10",
        "test",
        "source-hash",
        tableCounts,
        new TreeMap<>(java.util.Map.of("INCOMES", List.of("ID:BIGINT"))),
        new TreeMap<>(java.util.Map.of("INCOMES", List.of("ID"))),
        List.of(
            new FinancialAggregate("INCOMES", "INCOME", 2026, 1L, 2L, 1L, new BigDecimal("10.00"))),
        referenceCounts,
        List.of(new ConstraintCheck("INCOMES.PRIMARY_KEY", 0)),
        List.of(),
        rowHashes,
        new TreeMap<>(),
        "");
  }
}
