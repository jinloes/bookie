package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreJournalStoreTest {

  @TempDir Path directory;

  private RestorePaths paths;
  private RestoreJournalStore store;

  @BeforeEach
  void setUp() {
    paths = new RestorePaths(directory);
    store = new RestoreJournalStore(paths);
  }

  @Nested
  class Persistence {

    @Test
    void writesFsyncedActiveAndAuditCopies() throws Exception {
      RestoreJournal written = store.write(journal(paths, RestoreState.VALIDATED));

      assertThat(paths.activeJournalFile()).isRegularFile();
      assertThat(Path.of(written.auditJournalFile())).isRegularFile();
      assertThat(written.journalChecksum()).hasSize(64);
      assertThat(store.read()).contains(written);
    }

    @Test
    void rejectsTampering() throws Exception {
      store.write(journal(paths, RestoreState.VALIDATED));
      Path active = paths.activeJournalFile();
      Files.writeString(active, Files.readString(active).replace("\"VALIDATED\"", "\"FAILED\""));

      assertThatThrownBy(store::read)
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("checksum mismatch");
    }

    @Test
    void rejectsPathsOutsideTheDataDirectory() {
      RestoreJournal original = journal(paths, RestoreState.VALIDATED);
      RestoreJournal escaping =
          new RestoreJournal(
              original.journalVersion(),
              original.restoreId(),
              original.state(),
              original.sourceFileId(),
              original.sourceName(),
              original.sourceBytes(),
              original.sourceChecksum(),
              original.dataDirectory(),
              "/tmp/outside-live.mv.db",
              original.shadowFile(),
              original.rollbackFile(),
              original.failedFile(),
              original.manifestFile(),
              original.auditJournalFile(),
              original.liveDatabaseHash(),
              original.shadowDatabaseHash(),
              original.manifestChecksum(),
              original.createdAt(),
              original.updatedAt(),
              original.message(),
              "");

      assertThatThrownBy(() -> store.write(escaping))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("escapes");
    }
  }

  static RestoreJournal journal(RestorePaths paths, RestoreState state) {
    RestorePaths.RestoreAttemptPaths attempt = paths.attempt("restore-123");
    String now = Instant.now().toString();
    return new RestoreJournal(
        RestoreJournal.CURRENT_VERSION,
        "restore-123",
        state,
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
        "live-hash",
        "shadow-hash",
        "manifest-hash",
        now,
        now,
        "test",
        "");
  }
}
