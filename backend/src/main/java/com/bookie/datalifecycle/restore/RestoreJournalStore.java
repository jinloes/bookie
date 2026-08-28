package com.bookie.datalifecycle.restore;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
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
import java.util.Optional;

public class RestoreJournalStore {

  private final RestorePaths paths;
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  public RestoreJournalStore(RestorePaths paths) {
    this.paths = paths;
  }

  public Optional<RestoreJournal> read() throws IOException {
    Path journalFile = paths.activeJournalFile();
    if (!Files.exists(journalFile)) {
      return Optional.empty();
    }
    if (!Files.isRegularFile(journalFile) || Files.isSymbolicLink(journalFile)) {
      throw new IOException("Restore intent journal is not a regular file: " + journalFile);
    }
    RestoreJournal journal = objectMapper.readValue(journalFile.toFile(), RestoreJournal.class);
    verify(journal);
    validateContainedPaths(journal);
    return Optional.of(journal);
  }

  public RestoreJournal write(RestoreJournal journal) throws IOException {
    RestoreJournal sealed = seal(journal);
    validateContainedPaths(sealed);
    Path auditPath = Path.of(sealed.auditJournalFile());
    writeAtomically(auditPath, sealed);
    writeAtomically(paths.activeJournalFile(), sealed);
    return sealed;
  }

  public RestoreJournal seal(RestoreJournal journal) {
    RestoreJournal unsealed = withChecksum(journal, "");
    return withChecksum(unsealed, MigrationIntegrityManifestCodec.sha256(canonicalBytes(unsealed)));
  }

  public void verify(RestoreJournal journal) {
    if (journal.journalVersion() != RestoreJournal.CURRENT_VERSION) {
      throw new MigrationIntegrityException(
          "Unsupported restore journal version: " + journal.journalVersion());
    }
    String expected = journal.journalChecksum();
    String observed =
        MigrationIntegrityManifestCodec.sha256(canonicalBytes(withChecksum(journal, "")));
    if (expected == null
        || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), observed.getBytes(StandardCharsets.UTF_8))) {
      throw new MigrationIntegrityException(
          "Restore journal checksum mismatch: expected " + expected + ", observed " + observed);
    }
  }

  private void validateContainedPaths(RestoreJournal journal) throws IOException {
    Path dataDirectory = paths.dataDirectory();
    if (!Path.of(journal.dataDirectory()).toAbsolutePath().normalize().equals(dataDirectory)) {
      throw new MigrationIntegrityException(
          "Restore journal data directory does not match configured data directory");
    }
    for (String value :
        new String[] {
          journal.liveFile(),
          journal.shadowFile(),
          journal.rollbackFile(),
          journal.failedFile(),
          journal.manifestFile(),
          journal.auditJournalFile()
        }) {
      Path candidate = Path.of(value).toAbsolutePath().normalize();
      if (!candidate.startsWith(dataDirectory)) {
        throw new MigrationIntegrityException(
            "Restore journal path escapes the configured data directory: " + candidate);
      }
      if (Files.isSymbolicLink(candidate)) {
        throw new MigrationIntegrityException(
            "Restore journal path must not be a symbolic link: " + candidate);
      }
      Path existing = candidate;
      while (existing != null && !Files.exists(existing)) {
        existing = existing.getParent();
      }
      if (existing == null
          || !existing
              .toRealPath()
              .resolve(existing.relativize(candidate))
              .normalize()
              .startsWith(dataDirectory)) {
        throw new MigrationIntegrityException(
            "Restore journal path resolves outside the configured data directory: " + candidate);
      }
    }
  }

  private byte[] canonicalBytes(RestoreJournal journal) {
    try {
      return objectMapper.writeValueAsBytes(journal);
    } catch (JsonProcessingException e) {
      throw new MigrationIntegrityException("Could not serialize restore journal", e);
    }
  }

  private RestoreJournal withChecksum(RestoreJournal journal, String checksum) {
    return new RestoreJournal(
        journal.journalVersion(),
        journal.restoreId(),
        journal.state(),
        journal.sourceFileId(),
        journal.sourceName(),
        journal.sourceBytes(),
        journal.sourceChecksum(),
        journal.dataDirectory(),
        journal.liveFile(),
        journal.shadowFile(),
        journal.rollbackFile(),
        journal.failedFile(),
        journal.manifestFile(),
        journal.auditJournalFile(),
        journal.liveDatabaseHash(),
        journal.shadowDatabaseHash(),
        journal.manifestChecksum(),
        journal.createdAt(),
        journal.updatedAt(),
        journal.message(),
        checksum);
  }

  private void writeAtomically(Path target, RestoreJournal journal) throws IOException {
    Files.createDirectories(target.getParent());
    Path temporary =
        Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
    try {
      Files.write(
          temporary,
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(journal),
          StandardOpenOption.TRUNCATE_EXISTING);
      forceFile(temporary);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        throw new IOException("Atomic restore-journal updates are not supported for " + target, e);
      }
      forceDirectory(target.getParent());
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void forceFile(Path file) throws IOException {
    try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
  }

  private static void forceDirectory(Path directory) {
    try (var channel = java.nio.channels.FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (IOException | UnsupportedOperationException ignored) {
      // Some platforms do not permit opening directories; the file itself was already fsynced.
    }
  }
}
