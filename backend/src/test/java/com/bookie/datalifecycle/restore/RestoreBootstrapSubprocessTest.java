package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreBootstrapSubprocessTest {

  @Test
  void activatesThenRollsBackAnUnvalidatedCandidateAcrossProcessBoundaries(@TempDir Path directory)
      throws Exception {
    RestorePaths paths = new RestorePaths(directory);
    RestoreJournalStore store = new RestoreJournalStore(paths);
    Files.writeString(paths.liveDatabaseFile(), "live-database");
    RestorePaths.RestoreAttemptPaths attempt = paths.attempt("restore-subprocess");
    Files.createDirectories(attempt.stagingDirectory());
    Files.writeString(attempt.shadowDatabaseFile(), "shadow-database");
    String now = Instant.now().toString();
    store.write(
        new RestoreJournal(
            RestoreJournal.CURRENT_VERSION,
            "restore-subprocess",
            RestoreState.VALIDATED,
            "file-1",
            "backup.sql",
            100,
            "source-hash",
            paths.dataDirectory().toString(),
            paths.liveDatabaseFile().toString(),
            attempt.shadowDatabaseFile().toString(),
            attempt.rollbackFile().toString(),
            attempt.failedFile().toString(),
            attempt.manifestFile().toString(),
            attempt.auditJournalFile().toString(),
            MigrationIntegrityManifestCodec.sha256(paths.liveDatabaseFile()),
            MigrationIntegrityManifestCodec.sha256(attempt.shadowDatabaseFile()),
            "manifest-hash",
            now,
            now,
            "validated",
            ""));

    runBootstrapProcess(directory);

    RestoreJournal activated = store.read().orElseThrow();
    assertThat(activated.state()).isEqualTo(RestoreState.SHADOW_ACTIVATED);
    assertThat(Files.readString(paths.liveDatabaseFile())).isEqualTo("shadow-database");
    assertThat(Files.readString(attempt.rollbackFile())).isEqualTo("live-database");

    runBootstrapProcess(directory);

    RestoreJournal rolledBack = store.read().orElseThrow();
    assertThat(rolledBack.state()).isEqualTo(RestoreState.ROLLED_BACK);
    assertThat(Files.readString(paths.liveDatabaseFile())).isEqualTo("live-database");
    assertThat(Files.readString(attempt.failedFile())).isEqualTo("shadow-database");
  }

  private static void runBootstrapProcess(Path directory) throws Exception {
    Path java = Path.of(System.getProperty("java.home"), "bin", "java");
    Process process =
        new ProcessBuilder(
                java.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                RestoreBootstrapProcessMain.class.getName(),
                directory.toString())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(process.waitFor()).as(output).isZero();
  }
}

final class RestoreBootstrapProcessMain {

  private RestoreBootstrapProcessMain() {}

  public static void main(String[] args) throws Exception {
    RestorePaths paths = new RestorePaths(Path.of(args[0]));
    RestoreBootstrap.activatePendingRestore(paths, new RestoreJournalStore(paths));
  }
}
