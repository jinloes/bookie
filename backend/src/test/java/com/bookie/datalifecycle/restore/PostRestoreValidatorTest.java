package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationStartedEvent;

@ExtendWith(MockitoExtension.class)
class PostRestoreValidatorTest {

  @Mock private DataSource dataSource;
  @Mock private MigrationIntegrityVerifier integrityVerifier;
  @Mock private RestoreJournalStore journalStore;
  @Mock private ApplicationStartedEvent event;

  private PostRestoreValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PostRestoreValidator(dataSource, integrityVerifier, journalStore);
  }

  @Nested
  class ApplicationStarted {

    @Test
    void ignoresStartupWithoutAnActivatedRestore() throws Exception {
      when(journalStore.read()).thenReturn(Optional.empty());

      validator.onApplicationEvent(event);

      verify(journalStore).read();
    }

    @Test
    void marksActivatedRestoreValidatedAfterSemanticReconciliation(@TempDir Path directory)
        throws Exception {
      MigrationIntegrityManifest manifest = manifest();
      Path manifestFile = directory.resolve("manifest.json");
      new MigrationIntegrityManifestCodec().write(manifestFile, manifest);
      RestoreJournal journal = journal(directory, manifestFile, manifest.contentChecksum());
      when(journalStore.read()).thenReturn(Optional.of(journal));
      when(integrityVerifier.capture(dataSource, "test", journal.shadowDatabaseHash()))
          .thenReturn(manifest);
      when(journalStore.write(any(RestoreJournal.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      validator.onApplicationEvent(event);

      verify(integrityVerifier).assertHealthy(manifest);
      verify(integrityVerifier).reconcile(manifest, manifest);
      ArgumentCaptor<RestoreJournal> saved = ArgumentCaptor.forClass(RestoreJournal.class);
      verify(journalStore).write(saved.capture());
      assertThat(saved.getValue().state()).isEqualTo(RestoreState.POST_START_VALIDATED);
    }

    @Test
    void schedulesRollbackWhenPostStartReconciliationFails(@TempDir Path directory)
        throws Exception {
      MigrationIntegrityManifest manifest = manifest();
      Path manifestFile = directory.resolve("manifest.json");
      new MigrationIntegrityManifestCodec().write(manifestFile, manifest);
      RestoreJournal journal = journal(directory, manifestFile, manifest.contentChecksum());
      when(journalStore.read()).thenReturn(Optional.of(journal));
      when(integrityVerifier.capture(dataSource, "test", journal.shadowDatabaseHash()))
          .thenThrow(new MigrationIntegrityException("synthetic mismatch"));
      when(journalStore.write(any(RestoreJournal.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      assertThatThrownBy(() -> validator.onApplicationEvent(event))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("rollback scheduled");

      ArgumentCaptor<RestoreJournal> saved = ArgumentCaptor.forClass(RestoreJournal.class);
      verify(journalStore).write(saved.capture());
      assertThat(saved.getValue().state()).isEqualTo(RestoreState.ROLLBACK_REQUIRED);
    }

    @Test
    void schedulesRollbackWhenTheManifestDoesNotMatchTheJournal(@TempDir Path directory)
        throws Exception {
      MigrationIntegrityManifest manifest = manifest();
      Path manifestFile = directory.resolve("manifest.json");
      new MigrationIntegrityManifestCodec().write(manifestFile, manifest);
      RestoreJournal journal = journal(directory, manifestFile, "different-checksum");
      when(journalStore.read()).thenReturn(Optional.of(journal));
      when(journalStore.write(any(RestoreJournal.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      assertThatThrownBy(() -> validator.onApplicationEvent(event))
          .isInstanceOf(MigrationIntegrityException.class)
          .hasMessageContaining("rollback scheduled");

      ArgumentCaptor<RestoreJournal> saved = ArgumentCaptor.forClass(RestoreJournal.class);
      verify(journalStore).write(saved.capture());
      assertThat(saved.getValue().state()).isEqualTo(RestoreState.ROLLBACK_REQUIRED);
    }
  }

  private MigrationIntegrityManifest manifest() {
    MigrationIntegrityManifest unsealed =
        new MigrationIntegrityManifest(
            MigrationIntegrityManifest.CURRENT_VERSION,
            "10",
            "test",
            "source-hash",
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            List.of(),
            new TreeMap<>(),
            List.of(),
            List.of(),
            new TreeMap<>(),
            new TreeMap<>(),
            "");
    return new MigrationIntegrityManifestCodec().seal(unsealed);
  }

  private RestoreJournal journal(Path directory, Path manifestFile, String manifestChecksum) {
    String now = Instant.now().toString();
    return new RestoreJournal(
        RestoreJournal.CURRENT_VERSION,
        "restore-123",
        RestoreState.SHADOW_ACTIVATED,
        "file-1",
        "backup.sql",
        100,
        "source-hash",
        directory.toString(),
        directory.resolve("bookiedb.mv.db").toString(),
        directory.resolve("shadow.mv.db").toString(),
        directory.resolve("rollback.mv.db").toString(),
        directory.resolve("failed.mv.db").toString(),
        manifestFile.toString(),
        directory.resolve("audit.json").toString(),
        "live-hash",
        "shadow-hash",
        manifestChecksum,
        now,
        now,
        "activated",
        "");
  }
}
