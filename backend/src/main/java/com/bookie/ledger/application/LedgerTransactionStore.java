package com.bookie.ledger.application;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.ledger.domain.FinancialTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionStore {

  FinancialTransaction save(FinancialTransaction transaction);

  Optional<FinancialTransaction> findActiveById(Long id);

  Optional<FinancialTransaction> findAnyById(Long id);

  Optional<FinancialTransaction> findBySourceIdentity(String origin, String externalId);

  List<FinancialTransaction> findAllActive();

  List<FinancialTransaction> findAllActiveByDirection(TransactionDirection direction);

  List<FinancialTransaction> findActiveFiltered(
      TransactionDirection direction, LocalDate from, LocalDate to);

  List<FinancialTransaction> findAllActiveByActivityId(Long activityId);

  List<FinancialTransaction> findAllActiveByCounterpartyId(Long counterpartyId);

  long countActiveByActivityId(Long activityId);

  long countActiveByCounterpartyId(Long counterpartyId);
}
