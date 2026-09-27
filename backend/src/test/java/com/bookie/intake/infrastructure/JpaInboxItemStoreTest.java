package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.config.ClockConfiguration;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({ClockConfiguration.class, JpaInboxItemStore.class})
class JpaInboxItemStoreTest {

  @Autowired private TestEntityManager em;
  @Autowired private JpaInboxItemStore store;

  @Nested
  class FindSavedForOutlookImmutableIds {

    @Test
    void returnsOnlySavedOutlookItemsWithAMatchingImmutableIdAndTheirArtifacts() {
      persist(ExpenseSource.OUTLOOK_EMAIL, InboxState.SAVED, "saved", "immutable-1");
      persist(ExpenseSource.OUTLOOK_EMAIL, InboxState.READY, "ready", "immutable-1");
      persist(ExpenseSource.OUTLOOK_EMAIL, InboxState.SAVED, "other", "immutable-2");
      persist(ExpenseSource.RECEIPT, InboxState.SAVED, "receipt", "immutable-1");
      em.clear();

      List<InboxItem> found = store.findSavedForOutlookImmutableIds(List.of("immutable-1"));

      assertThat(found).extracting(InboxItem::getLegacySourceId).containsExactly("saved");
      assertThat(found.getFirst().getArtifacts())
          .extracting(InboxArtifact::getExternalId)
          .containsExactly("message-saved");
    }

    @Test
    void returnsEmptyWithoutQueryingForNoIds() {
      assertThat(store.findSavedForOutlookImmutableIds(List.of())).isEmpty();
    }
  }

  private void persist(
      ExpenseSource origin, InboxState state, String sourceId, String immutableId) {
    InboxItem item =
        InboxItem.builder()
            .origin(origin)
            .state(state)
            .externalSyncState(ExternalSyncState.NOT_REQUIRED)
            .rawStatus(state.name())
            .legacySourceType(origin.name())
            .legacySourceId(sourceId)
            .immutableSourceId(immutableId)
            .createdAt(LocalDateTime.of(2026, 9, 1, 12, 0))
            .build();
    item.addArtifact(
        InboxArtifact.builder()
            .type(InboxArtifactType.OUTLOOK_EMAIL)
            .externalId("message-" + sourceId)
            .build());
    em.persistAndFlush(item);
  }
}
