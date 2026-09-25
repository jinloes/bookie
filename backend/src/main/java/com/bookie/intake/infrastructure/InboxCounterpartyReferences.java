package com.bookie.intake.infrastructure;

import com.bookie.catalog.counterparty.application.CounterpartyIntakeReferences;
import com.bookie.intake.application.InboxItemStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboxCounterpartyReferences implements CounterpartyIntakeReferences {

  private final InboxItemStore inboxItemStore;

  @Override
  public void detachCounterparty(Long counterpartyId) {
    inboxItemStore.detachCounterparty(counterpartyId);
  }

  @Override
  public void requireNoReferences(Long counterpartyId) {
    if (inboxItemStore.countCounterpartyReferences(counterpartyId) != 0) {
      throw new IllegalStateException(
          "Intake records still reference counterparty: " + counterpartyId);
    }
  }
}
