package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreBootstrapTest {

  @TempDir Path directory;

  private RestorePaths paths;
  private RestoreJournalStore store;
  private RestoreJournal journal;

  @BeforeEach
  void setUp() throws Exception {
    paths = new RestorePaths(directory);
    store = new RestoreJournalStore(paths);
    Files.writeString(paths.liveDatabaseFile(), "live-database");
    RestorePaths.RestoreAttemptPaths attempt = paths.attempt("restore-123");
    Files.createDirectories(attempt.stagingDirectory());
    Files.writeString(attempt.shadowDatabaseFile(), "shadow-database");
    String now = Instant.now().toString();
    journal =
        new RestoreJournal(
            RestoreJournal.CURRENT_VERSION,
            "restore-123",
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
            "");
    journal = store.write(journal);
  }

  @Nested
  class Activation {

    @Test
    void atomicallyRetainsLiveAndActivatesShadow() throws Exception {
      RestoreBootstrap.activatePendingRestore(paths, store);

      RestoreJournal activated = store.read().orElseThrow();
      assertThat(activated.state()).isEqualTo(RestoreState.SHADOW_ACTIVATED);
      assertThat(Files.readString(Path.of(activated.liveFile()))).isEqualTo("shadow-database");
      assertThat(Files.readString(Path.of(activated.rollbackFile()))).isEqualTo("live-database");
      assertThat(Path.of(activated.shadowFile())).doesNotExist();
    }

    @Test
    void recoversWhenLiveMoveCompletedBeforeJournalUpdate() throws Exception {
      Files.move(
          Path.of(journal.liveFile()),
          Path.of(journal.rollbackFile()),
          StandardCopyOption.ATOMIC_MOVE);

      RestoreBootstrap.activatePendingRestore(paths, store);

      assertThat(store.read().orElseThrow().state()).isEqualTo(RestoreState.SHADOW_ACTIVATED);
      assertThat(Files.readString(Path.of(journal.liveFile()))).isEqualTo("shadow-database");
    }

    @Test
    void recoversWhenBothMovesCompletedBeforeJournalUpdate() throws Exception {
      Files.move(
          Path.of(journal.liveFile()),
          Path.of(journal.rollbackFile()),
          StandardCopyOption.ATOMIC_MOVE);
      Files.move(
          Path.of(journal.shadowFile()),
          Path.of(journal.liveFile()),
          StandardCopyOption.ATOMIC_MOVE);

      RestoreBootstrap.activatePendingRestore(paths, store);

      assertThat(store.read().orElseThrow().state()).isEqualTo(RestoreState.SHADOW_ACTIVATED);
    }

    @Test
    void resumesShadowActivationAfterTheRetainedStateWasJournaled() throws Exception {
      Files.move(
          Path.of(journal.liveFile()),
          Path.of(journal.rollbackFile()),
          StandardCopyOption.ATOMIC_MOVE);
      store.write(journal.withState(RestoreState.LIVE_RETAINED, "retained"));

      RestoreBootstrap.activatePendingRestore(paths, store);

      assertThat(store.read().orElseThrow().state()).isEqualTo(RestoreState.SHADOW_ACTIVATED);
      assertThat(Files.readString(Path.of(journal.liveFile()))).isEqualTo("shadow-database");
      assertThat(Files.readString(Path.of(journal.rollbackFile()))).isEqualTo("live-database");
    }

    @Test
    void retainsTheExactShutdownCopyWhenTheLiveFileChangedAfterStaging() throws Exception {
      Files.writeString(Path.of(journal.liveFile()), "new-write-after-staging");

      RestoreBootstrap.activatePendingRestore(paths, store);

      RestoreJournal activated = store.read().orElseThrow();
      assertThat(Files.readString(Path.of(activated.liveFile()))).isEqualTo("shadow-database");
      assertThat(Files.readString(Path.of(activated.rollbackFile())))
          .isEqualTo("new-write-after-staging");
      assertThat(activated.liveDatabaseHash())
          .isEqualTo(MigrationIntegrityManifestCodec.sha256(Path.of(activated.rollbackFile())));
      assertThat(activated.message()).contains("exact shutdown copy retained");
    }

    @Test
    void refusesActivationWhileAnH2LockFileExists() throws Exception {
      Files.writeString(paths.liveLockFile(), "lock");

      assertThatThrownBy(() -> RestoreBootstrap.activatePendingRestore(paths, store))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("lock file");
      assertThat(Files.readString(Path.of(journal.liveFile()))).isEqualTo("live-database");
    }
  }

  @Nested
  class Rollback {

    @Test
    void rollsBackAnActivatedCandidateThatWasNotValidatedBeforeTheNextStartup() throws Exception {
      RestoreBootstrap.activatePendingRestore(paths, store);

      RestoreBootstrap.activatePendingRestore(paths, store);

      RestoreJournal rolledBack = store.read().orElseThrow();
      assertThat(rolledBack.state()).isEqualTo(RestoreState.ROLLED_BACK);
      assertThat(Files.readString(Path.of(rolledBack.liveFile()))).isEqualTo("live-database");
      assertThat(Files.readString(Path.of(rolledBack.failedFile()))).isEqualTo("shadow-database");
    }

    @Test
    void restoresRetainedLiveAndPreservesFailedCandidate() throws Exception {
      RestoreBootstrap.activatePendingRestore(paths, store);
      RestoreJournal activated = store.read().orElseThrow();
      store.write(
          activated.withState(RestoreState.ROLLBACK_REQUIRED, "synthetic validation failure"));

      RestoreBootstrap.activatePendingRestore(paths, store);

      RestoreJournal rolledBack = store.read().orElseThrow();
      assertThat(rolledBack.state()).isEqualTo(RestoreState.ROLLED_BACK);
      assertThat(Files.readString(Path.of(rolledBack.liveFile()))).isEqualTo("live-database");
      assertThat(Files.readString(Path.of(rolledBack.failedFile()))).isEqualTo("shadow-database");
    }

    @Test
    void recoversWhenRollbackMovesCompletedBeforeJournalUpdate() throws Exception {
      RestoreBootstrap.activatePendingRestore(paths, store);
      RestoreJournal activated = store.read().orElseThrow();
      RestoreJournal rollbackRequired =
          store.write(
              activated.withState(RestoreState.ROLLBACK_REQUIRED, "synthetic validation failure"));
      Files.move(
          Path.of(rollbackRequired.liveFile()),
          Path.of(rollbackRequired.failedFile()),
          StandardCopyOption.ATOMIC_MOVE);
      Files.move(
          Path.of(rollbackRequired.rollbackFile()),
          Path.of(rollbackRequired.liveFile()),
          StandardCopyOption.ATOMIC_MOVE);

      RestoreBootstrap.activatePendingRestore(paths, store);

      RestoreJournal rolledBack = store.read().orElseThrow();
      assertThat(rolledBack.state()).isEqualTo(RestoreState.ROLLED_BACK);
      assertThat(Files.readString(Path.of(rolledBack.liveFile()))).isEqualTo("live-database");
      assertThat(Files.readString(Path.of(rolledBack.failedFile()))).isEqualTo("shadow-database");
    }

    @Test
    void resumesRollbackAfterTheFailedCandidateWasRetained() throws Exception {
      RestoreBootstrap.activatePendingRestore(paths, store);
      RestoreJournal activated = store.read().orElseThrow();
      RestoreJournal rollbackRequired =
          store.write(
              activated.withState(RestoreState.ROLLBACK_REQUIRED, "synthetic validation failure"));
      Files.move(
          Path.of(rollbackRequired.liveFile()),
          Path.of(rollbackRequired.failedFile()),
          StandardCopyOption.ATOMIC_MOVE);

      RestoreBootstrap.activatePendingRestore(paths, store);

      RestoreJournal rolledBack = store.read().orElseThrow();
      assertThat(rolledBack.state()).isEqualTo(RestoreState.ROLLED_BACK);
      assertThat(Files.readString(Path.of(rolledBack.liveFile()))).isEqualTo("live-database");
      assertThat(Files.readString(Path.of(rolledBack.failedFile()))).isEqualTo("shadow-database");
    }
  }
}
