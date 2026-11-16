package com.bookie.intake.infrastructure;

import com.bookie.catalog.counterparty.application.CounterpartyDeleting;
import com.bookie.intake.application.InboxItemStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboxCounterpartyReferences {

  private final InboxItemStore inboxItemStore;

  @EventListener
  public void onCounterpartyDeleting(CounterpartyDeleting event) {
    detachCounterparty(event.counterpartyId());
    requireNoReferences(event.counterpartyId());
  }

  public void detachCounterparty(Long counterpartyId) {
    inboxItemStore.detachCounterparty(counterpartyId);
  }

  public void requireNoReferences(Long counterpartyId) {
    if (inboxItemStore.countCounterpartyReferences(counterpartyId) != 0) {
      throw new IllegalStateException(
          "Intake records still reference counterparty: " + counterpartyId);
    }
  }
}
