package com.bookie.datalifecycle.restore;

import java.time.Instant;

public record RestoreJournal(
    int journalVersion,
    String restoreId,
    RestoreState state,
    String sourceFileId,
    String sourceName,
    long sourceBytes,
    String sourceChecksum,
    String dataDirectory,
    String liveFile,
    String shadowFile,
    String rollbackFile,
    String failedFile,
    String manifestFile,
    String auditJournalFile,
    String liveDatabaseHash,
    String shadowDatabaseHash,
    String manifestChecksum,
    String createdAt,
    String updatedAt,
    String message,
    String journalChecksum) {

  public static final int CURRENT_VERSION = 1;

  public RestoreJournal withState(RestoreState nextState, String nextMessage) {
    return withLiveDatabaseHash(liveDatabaseHash, nextState, nextMessage);
  }

  public RestoreJournal withLiveDatabaseHash(
      String nextLiveDatabaseHash, RestoreState nextState, String nextMessage) {
    return new RestoreJournal(
        journalVersion,
        restoreId,
        nextState,
        sourceFileId,
        sourceName,
        sourceBytes,
        sourceChecksum,
        dataDirectory,
        liveFile,
        shadowFile,
        rollbackFile,
        failedFile,
        manifestFile,
        auditJournalFile,
        nextLiveDatabaseHash,
        shadowDatabaseHash,
        manifestChecksum,
        createdAt,
        Instant.now().toString(),
        nextMessage,
        "");
  }
}
