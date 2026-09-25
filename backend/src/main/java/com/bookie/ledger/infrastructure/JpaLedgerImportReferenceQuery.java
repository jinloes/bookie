package com.bookie.ledger.infrastructure;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.TransactionImportReference;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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
  private final EntityManager entityManager;

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

  @Override
  @Transactional(readOnly = true)
  public boolean existsActive(String origin, String externalId) {
    return !entityManager
        .createQuery(
            """
            SELECT reference.id
            FROM TransactionImportReference reference
            WHERE reference.origin = :origin
              AND reference.externalId = :externalId
              AND reference.transaction.deletedAt IS NULL
            """,
            Long.class)
        .setParameter("origin", origin)
        .setParameter("externalId", externalId)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  @Transactional(readOnly = true)
  public List<LinkedTransaction> findActiveLinks(Collection<String> externalIds) {
    if (CollectionUtils.isEmpty(externalIds)) {
      return List.of();
    }
    Set<LinkedTransaction> links = new LinkedHashSet<>();
    links.addAll(
        links(
            """
            SELECT attachment.externalId, transaction.id, transaction.version,
                   transaction.direction
            FROM FinancialTransaction transaction
            JOIN transaction.attachments attachment
            WHERE attachment.externalId IN :externalIds
              AND transaction.deletedAt IS NULL
            ORDER BY transaction.id
            """,
            externalIds));
    links.addAll(
        links(
            """
            SELECT reference.externalId, transaction.id, transaction.version,
                   transaction.direction
            FROM FinancialTransaction transaction
            JOIN transaction.importReferences reference
            WHERE reference.externalId IN :externalIds
              AND transaction.deletedAt IS NULL
            ORDER BY transaction.id
            """,
            externalIds));
    return new ArrayList<>(links);
  }

  private List<LinkedTransaction> links(String query, Collection<String> externalIds) {
    return entityManager
        .createQuery(query, Object[].class)
        .setParameter("externalIds", externalIds)
        .getResultList()
        .stream()
        .map(
            row ->
                new LinkedTransaction(
                    (String) row[0], (Long) row[1], (Long) row[2], (TransactionDirection) row[3]))
        .toList();
  }
}
