package com.bookie.compatibility.catalog;

import com.bookie.catalog.counterparty.application.CounterpartyIntakeReferences;
import com.bookie.repository.PendingIncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyCounterpartyIntakeReferences implements CounterpartyIntakeReferences {

  private final PendingIncomeRepository pendingIncomeRepository;

  @Override
  public void detachCounterparty(Long counterpartyId) {
    pendingIncomeRepository.clearPayerById(counterpartyId);
  }

  @Override
  public void requireNoReferences(Long counterpartyId) {
    if (pendingIncomeRepository.countByPayerId(counterpartyId) != 0) {
      throw new IllegalStateException(
          "Intake records still reference counterparty: " + counterpartyId);
    }
  }
}
