package com.bookie.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.infrastructure.InboxCounterpartyReferences;
import com.bookie.ledger.application.LedgerLifecycle;
import com.bookie.ledger.infrastructure.LedgerCounterpartyReferences;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CounterpartyReferenceAdaptersTest {

  @Mock private InboxItemStore inboxItemStore;
  @Mock private LedgerLifecycle ledgerLifecycle;

  @InjectMocks private LedgerCounterpartyReferences ledgerReferences;
  @InjectMocks private InboxCounterpartyReferences intakeReferences;

  @Nested
  class Ledger {

    @Test
    void detachesCounterpartyFromUnifiedTransactionsOnly() {
      ledgerReferences.detachCounterparty(8L);

      verify(ledgerLifecycle).detachCounterparty(8L);
      verifyNoInteractions(inboxItemStore);
    }

    @Test
    void failsClosedWhileAnyTransactionReferenceRemains() {
      when(ledgerLifecycle.countCounterpartyReferences(8L)).thenReturn(1L);

      assertThatThrownBy(() -> ledgerReferences.requireNoReferences(8L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("8");
    }

    @Test
    void passesOnceNoTransactionReferencesRemain() {
      when(ledgerLifecycle.countCounterpartyReferences(8L)).thenReturn(0L);

      assertThatCode(() -> ledgerReferences.requireNoReferences(8L)).doesNotThrowAnyException();
    }
  }

  @Nested
  class Intake {

    @Test
    void detachesCounterpartyFromInboxItemsWithoutDeletingThem() {
      intakeReferences.detachCounterparty(8L);

      verify(inboxItemStore).detachCounterparty(8L);
      verifyNoInteractions(ledgerLifecycle);
    }

    @Test
    void failsClosedWhileAnyInboxReferenceRemains() {
      when(inboxItemStore.countCounterpartyReferences(8L)).thenReturn(1L);

      assertThatThrownBy(() -> intakeReferences.requireNoReferences(8L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("8");
    }

    @Test
    void passesOnceNoInboxReferencesRemain() {
      when(inboxItemStore.countCounterpartyReferences(8L)).thenReturn(0L);

      assertThatCode(() -> intakeReferences.requireNoReferences(8L)).doesNotThrowAnyException();
    }
  }
}
