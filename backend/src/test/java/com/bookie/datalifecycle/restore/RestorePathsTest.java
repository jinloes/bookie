package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestorePathsTest {

  @Test
  void keepsEveryRestoreArtifactUnderTheConfiguredDataDirectory(@TempDir Path directory)
      throws Exception {
    RestorePaths paths = new RestorePaths(directory);
    RestorePaths.RestoreAttemptPaths attempt = paths.attempt("restore-123");
    Path canonicalDirectory = directory.toRealPath();

    assertThat(paths.liveDatabaseFile()).isEqualTo(canonicalDirectory.resolve("bookiedb.mv.db"));
    assertThat(paths.liveLockFile()).isEqualTo(canonicalDirectory.resolve("bookiedb.lock.db"));
    assertThat(paths.activeJournalFile())
        .isEqualTo(canonicalDirectory.resolve("restore-intent.json"));
    assertThat(
            java.util.List.of(
                attempt.stagingDirectory(),
                attempt.sourceScript(),
                attempt.shadowDatabaseFile(),
                attempt.manifestFile(),
                attempt.auditJournalFile(),
                attempt.rollbackFile(),
                attempt.failedFile()))
        .allMatch(path -> path.toAbsolutePath().normalize().startsWith(canonicalDirectory));
  }
}
