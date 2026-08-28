package com.bookie.datalifecycle.restore;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifest;
import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import com.bookie.datalifecycle.migration.MigrationIntegrityVerifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostRestoreValidator implements ApplicationListener<ApplicationStartedEvent> {

  private final DataSource dataSource;
  private final MigrationIntegrityVerifier integrityVerifier;
  private final RestoreJournalStore journalStore;
  private final MigrationIntegrityManifestCodec manifestCodec =
      new MigrationIntegrityManifestCodec();

  @Override
  public void onApplicationEvent(ApplicationStartedEvent event) {
    RestoreJournal journal;
    try {
      journal = journalStore.read().orElse(null);
      if (journal == null || journal.state() != RestoreState.SHADOW_ACTIVATED) {
        return;
      }
      MigrationIntegrityManifest staged = manifestCodec.read(Path.of(journal.manifestFile()));
      if (!MessageDigest.isEqual(
          journal.manifestChecksum().getBytes(StandardCharsets.UTF_8),
          staged.contentChecksum().getBytes(StandardCharsets.UTF_8))) {
        throw new MigrationIntegrityException(
            "Activated restore manifest does not match the staged journal");
      }
      MigrationIntegrityManifest active =
          integrityVerifier.capture(
              dataSource, staged.applicationVersion(), journal.shadowDatabaseHash());
      integrityVerifier.assertHealthy(active);
      integrityVerifier.reconcile(staged, active);
      journalStore.write(
          journal.withState(
              RestoreState.POST_START_VALIDATED,
              "Activated database passed post-start health and integrity validation"));
    } catch (Exception e) {
      markRollbackRequired(journalStore, e);
      throw new MigrationIntegrityException(
          "Activated restore failed post-start validation; rollback scheduled for next restart", e);
    }
  }

  private static void markRollbackRequired(
      RestoreJournalStore journalStore, Exception validationFailure) {
    try {
      journalStore
          .read()
          .filter(journal -> journal.state() == RestoreState.SHADOW_ACTIVATED)
          .ifPresent(
              journal -> {
                try {
                  journalStore.write(
                      journal.withState(
                          RestoreState.ROLLBACK_REQUIRED,
                          "Post-start validation failed: "
                              + validationFailure.getClass().getSimpleName()));
                } catch (Exception journalFailure) {
                  validationFailure.addSuppressed(journalFailure);
                }
              });
    } catch (Exception journalFailure) {
      validationFailure.addSuppressed(journalFailure);
    }
  }
}
