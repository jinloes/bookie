package com.bookie.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.infrastructure.InboxPropertyReferences;
import com.bookie.ledger.application.LedgerLifecycle;
import com.bookie.ledger.infrastructure.LedgerPropertyReferences;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyReferenceAdaptersTest {

  @Mock private InboxItemStore inboxItemStore;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private CategoryCatalog categoryCatalog;
  @Mock private LedgerLifecycle ledgerLifecycle;

  @InjectMocks private LedgerPropertyReferences ledgerReferences;
  @InjectMocks private InboxPropertyReferences intakeReferences;

  private void stubFallbacks() {
    when(activityCatalog.getNeedsClassification())
        .thenReturn(FinancialActivity.builder().id(99L).build());
    when(categoryCatalog.findByKey("OTHER_EXPENSE"))
        .thenReturn(Optional.of(LegacyCategoryView.builder().id(20L).build()));
    when(categoryCatalog.findByKey("OTHER_INCOME"))
        .thenReturn(Optional.of(LegacyCategoryView.builder().id(21L).build()));
  }

  @Nested
  class Ledger {

    @Test
    void reassignsBothDirectionsToTheirRequiredFallbackCategories() {
      stubFallbacks();

      ledgerReferences.reassignActivity(10L);

      verify(ledgerLifecycle).reassignActivity(10L, 99L, 21L, 20L);
      verifyNoInteractions(inboxItemStore);
    }

    @Test
    void failsWhenAFallbackCategoryIsMissing() {
      when(activityCatalog.getNeedsClassification())
          .thenReturn(FinancialActivity.builder().id(99L).build());
      when(categoryCatalog.findByKey("OTHER_INCOME")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> ledgerReferences.reassignActivity(10L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("OTHER_INCOME");
      verifyNoInteractions(ledgerLifecycle);
    }

    @Test
    void propertyDetachIsCoveredByTheActivityReassignment() {
      ledgerReferences.detachProperty(7L);

      verifyNoInteractions(ledgerLifecycle, inboxItemStore);
    }

    @Test
    void failsClosedWhileTheRetiredActivityIsStillReferenced() {
      when(ledgerLifecycle.countActivityReferences(10L)).thenReturn(1L);

      assertThatThrownBy(() -> ledgerReferences.requireNoReferences(7L, Optional.of(10L)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("7");
    }

    @Test
    void aPropertyWithoutARentalActivityHasNoLedgerReferences() {
      assertThatCode(() -> ledgerReferences.requireNoReferences(7L, Optional.empty()))
          .doesNotThrowAnyException();
      verifyNoInteractions(ledgerLifecycle);
    }
  }

  @Nested
  class Intake {

    @Test
    void reassignsInboxItemsToTheFallbackActivityAndDirectionCategories() {
      stubFallbacks();

      intakeReferences.reassignActivity(10L);

      verify(inboxItemStore).reassignActivity(10L, 99L, 21L, 20L);
      verifyNoInteractions(ledgerLifecycle);
    }

    @Test
    void detachesPropertyWithoutDeletingInboxItems() {
      intakeReferences.detachProperty(7L);

      verify(inboxItemStore).detachProperty(7L);
    }

    @Test
    void failsClosedWhileAnInboxPropertyReferenceRemains() {
      when(inboxItemStore.countPropertyReferences(7L)).thenReturn(1L);

      assertThatThrownBy(() -> intakeReferences.requireNoReferences(7L, Optional.empty()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("7");
    }

    @Test
    void failsClosedWhileAnInboxActivityReferenceRemains() {
      when(inboxItemStore.countPropertyReferences(7L)).thenReturn(0L);
      when(inboxItemStore.countActivityReferences(10L)).thenReturn(1L);

      assertThatThrownBy(() -> intakeReferences.requireNoReferences(7L, Optional.of(10L)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("7");
    }

    @Test
    void passesOnceNoInboxReferencesRemain() {
      when(inboxItemStore.countPropertyReferences(7L)).thenReturn(0L);
      when(inboxItemStore.countActivityReferences(10L)).thenReturn(0L);

      assertThatCode(() -> intakeReferences.requireNoReferences(7L, Optional.of(10L)))
          .doesNotThrowAnyException();
    }
  }
}
