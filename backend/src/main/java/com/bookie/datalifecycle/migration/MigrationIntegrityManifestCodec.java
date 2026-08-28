package com.bookie.datalifecycle.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class MigrationIntegrityManifestCodec {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  public MigrationIntegrityManifest seal(MigrationIntegrityManifest manifest) {
    MigrationIntegrityManifest unsealed = withChecksum(manifest, "");
    return withChecksum(unsealed, sha256(canonicalBytes(unsealed)));
  }

  public void verify(MigrationIntegrityManifest manifest) {
    if (manifest.manifestVersion() != MigrationIntegrityManifest.CURRENT_VERSION) {
      throw new MigrationIntegrityException(
          "Unsupported migration manifest version: " + manifest.manifestVersion());
    }
    String expected = manifest.contentChecksum();
    String observed = sha256(canonicalBytes(withChecksum(manifest, "")));
    if (expected == null || !MessageDigest.isEqual(expected.getBytes(), observed.getBytes())) {
      throw new MigrationIntegrityException(
          "Migration integrity manifest checksum mismatch: expected "
              + expected
              + ", observed "
              + observed);
    }
  }

  public void write(Path target, MigrationIntegrityManifest manifest) throws IOException {
    MigrationIntegrityManifest sealed = seal(manifest);
    Path absoluteTarget = target.toAbsolutePath().normalize();
    Files.createDirectories(absoluteTarget.getParent());
    Path temporary =
        Files.createTempFile(
            absoluteTarget.getParent(), absoluteTarget.getFileName().toString(), ".tmp");
    try {
      Files.write(
          temporary,
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(sealed),
          StandardOpenOption.TRUNCATE_EXISTING);
      try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      atomicMove(temporary, absoluteTarget);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public MigrationIntegrityManifest read(Path source) throws IOException {
    MigrationIntegrityManifest manifest =
        objectMapper.readValue(source.toFile(), MigrationIntegrityManifest.class);
    verify(manifest);
    return manifest;
  }

  public byte[] canonicalBytes(MigrationIntegrityManifest manifest) {
    try {
      return objectMapper.writeValueAsBytes(manifest);
    } catch (JsonProcessingException e) {
      throw new MigrationIntegrityException("Could not serialize migration manifest", e);
    }
  }

  public static String sha256(Path path) throws IOException {
    MessageDigest digest = sha256Digest();
    try (var input = Files.newInputStream(path)) {
      byte[] buffer = new byte[1024 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  public static String sha256(byte[] value) {
    return HexFormat.of().formatHex(sha256Digest().digest(value));
  }

  public static String sha256(String value) {
    return sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private MigrationIntegrityManifest withChecksum(
      MigrationIntegrityManifest manifest, String checksum) {
    return new MigrationIntegrityManifest(
        manifest.manifestVersion(),
        manifest.schemaVersion(),
        manifest.applicationVersion(),
        manifest.sourceDatabaseHash(),
        manifest.tableCounts(),
        manifest.tableColumns(),
        manifest.tableIdentityColumns(),
        manifest.financialAggregates(),
        manifest.referenceCounts(),
        manifest.uniquenessChecks(),
        manifest.orphanChecks(),
        manifest.rowHashes(),
        manifest.preservedColumnHashes(),
        checksum);
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static void atomicMove(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target);
    }
  }
}
