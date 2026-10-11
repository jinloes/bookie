package com.bookie.datalifecycle.portable;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.Builder;
import org.springframework.stereotype.Component;

/**
 * The portable backup format: a zip of {@code manifest.json} and one {@code tables/<table>.jsonl}
 * per Bookie table, one JSON object per row keyed by lower-case column name. Values are
 * vendor-neutral: integers and floats as numbers, decimals as plain strings, booleans, base64
 * binary, and ISO-8601 temporals.
 */
@Component
public class PortableBackupArchive {

  public static final int FORMAT_VERSION = 1;
  static final String MANIFEST_ENTRY = "manifest.json";
  static final String TABLE_PREFIX = "tables/";
  static final String TABLE_SUFFIX = ".jsonl";

  private static final TypeReference<LinkedHashMap<String, Object>> ROW_TYPE =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private final MigrationIntegrityManifestCodec codec = new MigrationIntegrityManifestCodec();

  /** The archive's {@code manifest.json}. */
  @Builder
  public record Manifest(
      int formatVersion,
      String flywayVersion,
      String vendor,
      String createdAt,
      List<TableEntry> tables,
      MigrationIntegrityManifest integrityManifest) {}

  /** One table's file: lower-case table name, row count and SHA-256 of its JSONL bytes. */
  public record TableEntry(String name, long rows, String sha256) {}

  /** A verified archive's manifest and rows, keyed by upper-case logical table name. */
  public record Contents(Manifest manifest, Map<String, List<Map<String, Object>>> rows) {}

  /** Writes rows keyed by upper-case logical table name; returns the manifest that was written. */
  public Manifest write(
      Path target, Manifest header, Map<String, List<Map<String, Object>>> rowsByTable)
      throws IOException {
    Map<String, byte[]> files = new TreeMap<>();
    List<TableEntry> entries = new ArrayList<>();
    for (Map.Entry<String, List<Map<String, Object>>> table : rowsByTable.entrySet()) {
      String name = table.getKey().toLowerCase(Locale.ROOT);
      byte[] bytes = encodeRows(table.getValue());
      files.put(name, bytes);
      entries.add(
          new TableEntry(
              name, table.getValue().size(), MigrationIntegrityManifestCodec.sha256(bytes)));
    }
    Manifest manifest =
        new Manifest(
            FORMAT_VERSION,
            header.flywayVersion(),
            header.vendor(),
            header.createdAt(),
            List.copyOf(entries),
            header.integrityManifest());
    try (OutputStream file = Files.newOutputStream(target);
        ZipOutputStream zip = new ZipOutputStream(file)) {
      zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
      zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
      zip.closeEntry();
      for (Map.Entry<String, byte[]> table : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(TABLE_PREFIX + table.getKey() + TABLE_SUFFIX));
        zip.write(table.getValue());
        zip.closeEntry();
      }
    }
    return manifest;
  }

  /**
   * Reads and fully verifies an archive: format version, the sealed integrity manifest, and each
   * table file's row count and SHA-256. Nothing is returned unless every check passes.
   */
  public Contents read(Path source) throws IOException {
    byte[] manifestBytes = null;
    Map<String, byte[]> files = new TreeMap<>();
    try (InputStream file = Files.newInputStream(source);
        ZipInputStream zip = new ZipInputStream(file)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        byte[] bytes = zip.readAllBytes();
        String name = entry.getName();
        if (name.equals(MANIFEST_ENTRY)) {
          manifestBytes = bytes;
        } else if (name.startsWith(TABLE_PREFIX) && name.endsWith(TABLE_SUFFIX)) {
          files.put(
              name.substring(TABLE_PREFIX.length(), name.length() - TABLE_SUFFIX.length()), bytes);
        } else {
          throw new MigrationIntegrityException("Unexpected entry in backup archive: " + name);
        }
      }
    }
    if (manifestBytes == null) {
      throw new MigrationIntegrityException("Backup archive has no " + MANIFEST_ENTRY);
    }
    Manifest manifest = objectMapper.readValue(manifestBytes, Manifest.class);
    if (manifest.formatVersion() != FORMAT_VERSION) {
      throw new MigrationIntegrityException(
          "Unsupported backup format version: " + manifest.formatVersion());
    }
    if (manifest.integrityManifest() == null || manifest.tables() == null) {
      throw new MigrationIntegrityException("Backup archive manifest is incomplete");
    }
    codec.verify(manifest.integrityManifest());
    Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
    for (TableEntry table : manifest.tables()) {
      byte[] bytes = files.remove(table.name());
      if (bytes == null) {
        throw new MigrationIntegrityException("Backup archive is missing table " + table.name());
      }
      String observed = MigrationIntegrityManifestCodec.sha256(bytes);
      if (!observed.equals(table.sha256())) {
        throw new MigrationIntegrityException(
            "Backup archive hash mismatch for table " + table.name());
      }
      List<Map<String, Object>> decoded = decodeRows(bytes);
      if (decoded.size() != table.rows()) {
        throw new MigrationIntegrityException(
            "Backup archive row count mismatch for table " + table.name());
      }
      rows.put(table.name().toUpperCase(Locale.ROOT), decoded);
    }
    if (!files.isEmpty()) {
      throw new MigrationIntegrityException(
          "Backup archive has tables missing from its manifest: " + files.keySet());
    }
    return new Contents(manifest, rows);
  }

  private byte[] encodeRows(List<Map<String, Object>> rows) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Map<String, Object> row : rows) {
      out.write(objectMapper.writeValueAsBytes(row));
      out.write('\n');
    }
    return out.toByteArray();
  }

  private List<Map<String, Object>> decodeRows(byte[] bytes) throws IOException {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
      if (!line.isEmpty()) {
        rows.add(objectMapper.readValue(line, ROW_TYPE));
      }
    }
    return rows;
  }
}
