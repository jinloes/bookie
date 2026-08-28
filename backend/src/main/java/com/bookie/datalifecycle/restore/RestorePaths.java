package com.bookie.datalifecycle.restore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record RestorePaths(Path dataDirectory) {

  private static final String DATABASE_BASENAME = "bookiedb";

  public RestorePaths {
    dataDirectory = canonicalize(dataDirectory);
  }

  public Path liveDatabaseFile() {
    return dataDirectory.resolve(DATABASE_BASENAME + ".mv.db");
  }

  public Path liveLockFile() {
    return dataDirectory.resolve(DATABASE_BASENAME + ".lock.db");
  }

  public Path activeJournalFile() {
    return dataDirectory.resolve("restore-intent.json");
  }

  public RestoreAttemptPaths attempt(String restoreId) {
    Path stagingDirectory = dataDirectory.resolve("restore-staging").resolve(restoreId);
    return new RestoreAttemptPaths(
        stagingDirectory,
        stagingDirectory.resolve("source.sql"),
        stagingDirectory.resolve("bookiedb-shadow"),
        stagingDirectory.resolve("bookiedb-shadow.mv.db"),
        stagingDirectory.resolve("migration-integrity-manifest.json"),
        stagingDirectory.resolve("restore-journal.json"),
        dataDirectory.resolve(DATABASE_BASENAME + ".rollback-" + restoreId + ".mv.db"),
        dataDirectory.resolve(DATABASE_BASENAME + ".failed-" + restoreId + ".mv.db"));
  }

  public record RestoreAttemptPaths(
      Path stagingDirectory,
      Path sourceScript,
      Path shadowDatabaseBase,
      Path shadowDatabaseFile,
      Path manifestFile,
      Path auditJournalFile,
      Path rollbackFile,
      Path failedFile) {}

  private static Path canonicalize(Path path) {
    Path absolute = path.toAbsolutePath().normalize();
    Path existing = absolute;
    while (existing != null && !Files.exists(existing)) {
      existing = existing.getParent();
    }
    if (existing == null) {
      throw new IllegalArgumentException(
          "Restore data directory has no existing ancestor: " + path);
    }
    try {
      Path canonicalAncestor = existing.toRealPath();
      return canonicalAncestor.resolve(existing.relativize(absolute)).normalize();
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not resolve restore data directory: " + path, e);
    }
  }
}
