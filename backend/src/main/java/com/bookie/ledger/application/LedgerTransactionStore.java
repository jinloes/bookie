package com.bookie.ledger.application;

import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionMap;
import com.bookie.model.TransactionDirection;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionStore {

  FinancialTransaction save(FinancialTransaction transaction);

  LegacyTransactionMap saveMap(LegacyTransactionMap legacyTransactionMap);

  Optional<FinancialTransaction> findActiveById(Long id);

  Optional<FinancialTransaction> findAnyById(Long id);

  Optional<FinancialTransaction> findActiveByLegacyKey(LegacyTransactionKey key);

  Optional<LegacyTransactionMap> findMap(LegacyTransactionKey key);

  Optional<LegacyTransactionMap> findMapByTransactionId(Long transactionId);

  Optional<FinancialTransaction> findBySourceIdentity(String origin, String externalId);

  List<FinancialTransaction> findAllActive();

  List<FinancialTransaction> findAllActiveByDirection(TransactionDirection direction);

  List<FinancialTransaction> findAllActiveByActivityId(Long activityId);

  long countActiveByActivityId(Long activityId);

  long countActiveByCounterpartyId(Long counterpartyId);
}
