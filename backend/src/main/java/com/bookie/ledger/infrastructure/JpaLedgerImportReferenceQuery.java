package com.bookie.ledger.infrastructure;

import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.TransactionImportReference;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Component
@RequiredArgsConstructor
class JpaLedgerImportReferenceQuery implements LedgerImportReferenceQuery {

  private final TransactionImportReferenceRepository importReferenceRepository;

  @Override
  @Transactional(readOnly = true)
  public Set<String> findSavedParentExternalIds(
      String origin, Collection<String> parentExternalIds) {
    if (CollectionUtils.isEmpty(parentExternalIds)) {
      return Set.of();
    }
    return importReferenceRepository
        .findByOriginAndParentExternalIdInAndTransactionDeletedAtIsNull(origin, parentExternalIds)
        .stream()
        .map(TransactionImportReference::getParentExternalId)
        .collect(Collectors.toUnmodifiableSet());
  }
}
