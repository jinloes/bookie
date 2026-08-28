package com.bookie.ledger.infrastructure;

import com.bookie.ledger.application.LedgerTransactionStore;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionMap;
import com.bookie.model.TransactionDirection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaLedgerTransactionStore implements LedgerTransactionStore {

  private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));

  private final FinancialTransactionRepository transactionRepository;
  private final LegacyTransactionMapRepository mapRepository;

  @Override
  public FinancialTransaction save(FinancialTransaction transaction) {
    return transactionRepository.saveAndFlush(transaction);
  }

  @Override
  public LegacyTransactionMap saveMap(LegacyTransactionMap legacyTransactionMap) {
    return mapRepository.saveAndFlush(legacyTransactionMap);
  }

  @Override
  public Optional<FinancialTransaction> findActiveById(Long id) {
    return transactionRepository.findByIdAndDeletedAtIsNull(id);
  }

  @Override
  public Optional<FinancialTransaction> findAnyById(Long id) {
    return transactionRepository.findById(id);
  }

  @Override
  public Optional<FinancialTransaction> findActiveByLegacyKey(LegacyTransactionKey key) {
    return mapRepository
        .findById(key)
        .map(LegacyTransactionMap::getTransaction)
        .filter(transaction -> transaction.getDeletedAt() == null);
  }

  @Override
  public Optional<LegacyTransactionMap> findMap(LegacyTransactionKey key) {
    return mapRepository.findById(key);
  }

  @Override
  public Optional<LegacyTransactionMap> findMapByTransactionId(Long transactionId) {
    return mapRepository.findByTransactionId(transactionId);
  }

  @Override
  public Optional<FinancialTransaction> findBySourceIdentity(String origin, String externalId) {
    return transactionRepository.findBySourceIdentity(origin, externalId);
  }

  @Override
  public List<FinancialTransaction> findAllActive() {
    return transactionRepository.findAllByDeletedAtIsNull(NEWEST_FIRST);
  }

  @Override
  public List<FinancialTransaction> findAllActiveByDirection(TransactionDirection direction) {
    return transactionRepository.findAllByDirectionAndDeletedAtIsNull(direction, NEWEST_FIRST);
  }

  @Override
  public List<FinancialTransaction> findAllActiveByActivityId(Long activityId) {
    return transactionRepository.findAllByActivityIdAndDeletedAtIsNull(activityId);
  }

  @Override
  public long countActiveByActivityId(Long activityId) {
    return transactionRepository.countByActivityIdAndDeletedAtIsNull(activityId);
  }

  @Override
  public long countActiveByCounterpartyId(Long counterpartyId) {
    return transactionRepository.countByCounterpartyIdAndDeletedAtIsNull(counterpartyId);
  }
}
