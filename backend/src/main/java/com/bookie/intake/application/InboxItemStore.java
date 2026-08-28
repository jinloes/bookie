package com.bookie.intake.application;

import com.bookie.intake.domain.InboxItem;
import com.bookie.model.ExpenseSource;
import java.util.List;
import java.util.Optional;

public interface InboxItemStore {

  InboxItem save(InboxItem item);

  Optional<InboxItem> findById(Long id);

  Optional<InboxItem> findBySourceIdentity(ExpenseSource origin, String legacySourceId);

  List<InboxItem> findAll();
}
