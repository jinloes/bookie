package com.bookie.intake.application;

import com.bookie.intake.domain.InboxItem;

/** Inbox write operations exposed through the unified inbox API. */
public interface InboxCommands {

  /**
   * Saves the inbox item as one ledger transaction. The ledger row, SAVED inbox state, and any
   * external-sync job commit together; external effects run only after that commit.
   */
  InboxItem save(Long inboxItemId, SaveInboxItemCommand command);

  void dismiss(Long inboxItemId);

  InboxItem reparse(Long inboxItemId);

  VenmoImportResult importVenmo(VenmoImportCommand command);
}
