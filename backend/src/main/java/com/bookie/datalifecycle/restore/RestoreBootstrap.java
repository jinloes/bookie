package com.bookie.datalifecycle.restore;

import com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class RestoreBootstrap {

  private RestoreBootstrap() {}

  static void activatePendingRestore(RestorePaths paths, RestoreJournalStore journalStore)
      throws IOException {
    Optional<RestoreJournal> current = journalStore.read();
    if (current.isEmpty()) {
      return;
    }
    RestoreJournal journal = current.get();
    switch (journal.state()) {
      case VALIDATED -> activateValidated(paths, journalStore, journal);
      case LIVE_RETAINED -> activateShadow(journalStore, journal);
      case ROLLBACK_REQUIRED, SHADOW_ACTIVATED -> rollBack(journalStore, journal);
      case IDLE, POST_START_VALIDATED, ROLLED_BACK, FAILED -> {
        // No pre-datasource transition is required.
      }
    }
  }

  private static void activateValidated(
      RestorePaths paths, RestoreJournalStore journalStore, RestoreJournal journal)
      throws IOException {
    if (Files.exists(paths.liveLockFile())) {
      throw new IOException(
          "H2 lock file exists before restore activation; another process may hold the database: "
              + paths.liveLockFile());
    }
    Path live = Path.of(journal.liveFile());
    Path shadow = Path.of(journal.shadowFile());
    Path rollback = Path.of(journal.rollbackFile());

    if (hasHash(live, journal.shadowDatabaseHash())
        && isRegularFile(rollback)
        && !Files.exists(shadow)) {
      String retainedHash = MigrationIntegrityManifestCodec.sha256(rollback);
      journalStore.write(
          journal.withLiveDatabaseHash(
              retainedHash,
              RestoreState.SHADOW_ACTIVATED,
              "Recovered activation completed before the journal update"
                  + liveChangeNote(journal, retainedHash)));
      return;
    }
    if (!Files.exists(live)
        && isRegularFile(rollback)
        && hasHash(shadow, journal.shadowDatabaseHash())) {
      String retainedHash = MigrationIntegrityManifestCodec.sha256(rollback);
      activateShadow(
          journalStore,
          journal.withLiveDatabaseHash(
              retainedHash,
              RestoreState.LIVE_RETAINED,
              "Recovered retained live database after interrupted journal update"
                  + liveChangeNote(journal, retainedHash)));
      return;
    }
    requireRegularFile(live, "live database");
    requireHash(shadow, journal.shadowDatabaseHash(), "shadow database");
    if (Files.exists(rollback)) {
      throw new IOException("Rollback target already exists: " + rollback);
    }

    String retainedHash = MigrationIntegrityManifestCodec.sha256(live);
    atomicMove(live, rollback);
    RestoreJournal retained =
        journalStore.write(
            journal.withLiveDatabaseHash(
                retainedHash,
                RestoreState.LIVE_RETAINED,
                "Previous live database retained; shadow activation pending"
                    + liveChangeNote(journal, retainedHash)));
    activateShadow(journalStore, retained);
  }

  private static void activateShadow(RestoreJournalStore journalStore, RestoreJournal journal)
      throws IOException {
    Path live = Path.of(journal.liveFile());
    Path shadow = Path.of(journal.shadowFile());
    Path rollback = Path.of(journal.rollbackFile());
    requireHash(rollback, journal.liveDatabaseHash(), "retained live database");

    if (hasHash(live, journal.shadowDatabaseHash()) && !Files.exists(shadow)) {
      journalStore.write(
          journal.withState(
              RestoreState.SHADOW_ACTIVATED,
              "Recovered shadow activation completed before the journal update"
                  + retainedChangeNote(journal)));
      return;
    }
    if (Files.exists(live)) {
      throw new IOException("Live database unexpectedly exists before shadow activation: " + live);
    }
    requireHash(shadow, journal.shadowDatabaseHash(), "shadow database");
    atomicMove(shadow, live);
    journalStore.write(
        journal.withState(
            RestoreState.SHADOW_ACTIVATED,
            "Shadow database activated; post-start validation pending"
                + retainedChangeNote(journal)));
  }

  private static String liveChangeNote(RestoreJournal journal, String retainedHash) {
    return retainedHash.equals(journal.liveDatabaseHash())
        ? ""
        : " (live file changed after staging; exact shutdown copy retained)";
  }

  private static String retainedChangeNote(RestoreJournal journal) {
    return journal.message().contains("exact shutdown copy retained")
        ? " (live file changed after staging; exact shutdown copy retained)"
        : "";
  }

  private static void rollBack(RestoreJournalStore journalStore, RestoreJournal journal)
      throws IOException {
    Path live = Path.of(journal.liveFile());
    Path rollback = Path.of(journal.rollbackFile());
    Path failed = Path.of(journal.failedFile());
    if (hasHash(live, journal.liveDatabaseHash())
        && !Files.exists(rollback)
        && hasHash(failed, journal.shadowDatabaseHash())) {
      journalStore.write(
          journal.withState(
              RestoreState.ROLLED_BACK, "Recovered rollback completed before the journal update"));
      return;
    }
    requireHash(rollback, journal.liveDatabaseHash(), "retained live database");

    if (Files.exists(live)) {
      requireHash(live, journal.shadowDatabaseHash(), "failed candidate database");
      if (Files.exists(failed)) {
        throw new IOException("Failed-candidate retention path already exists: " + failed);
      }
      atomicMove(live, failed);
    }
    atomicMove(rollback, live);
    journalStore.write(
        journal.withState(
            RestoreState.ROLLED_BACK,
            "Retained live database restored; failed candidate preserved"));
  }

  private static boolean hasHash(Path path, String expected) throws IOException {
    return !Files.isSymbolicLink(path)
        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
        && MigrationIntegrityManifestCodec.sha256(path).equals(expected);
  }

  private static boolean isRegularFile(Path path) {
    return !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
  }

  private static void requireRegularFile(Path path, String description) throws IOException {
    if (!isRegularFile(path)) {
      throw new IOException("Expected " + description + " is missing: " + path);
    }
  }

  private static void requireHash(Path path, String expected, String description)
      throws IOException {
    requireRegularFile(path, description);
    String observed = MigrationIntegrityManifestCodec.sha256(path);
    if (!observed.equals(expected)) {
      throw new IOException(
          "Hash mismatch for " + description + ": expected " + expected + ", observed " + observed);
    }
  }

  private static void atomicMove(Path source, Path target) throws IOException {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    forceDirectory(target.getParent());
  }

  private static void forceDirectory(Path directory) {
    try (var channel = java.nio.channels.FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (IOException | UnsupportedOperationException ignored) {
      // Atomic rename is still the safety boundary on platforms that cannot fsync a directory.
    }
  }
}
