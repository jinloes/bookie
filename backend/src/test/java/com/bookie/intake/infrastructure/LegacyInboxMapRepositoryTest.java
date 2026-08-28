package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.LegacyInboxMap;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import com.bookie.model.ExpenseSource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class LegacyInboxMapRepositoryTest {

  @Autowired private InboxItemRepository inboxItemRepository;
  @Autowired private LegacyInboxMapRepository legacyInboxMapRepository;

  @Test
  void activeReadUsesOnlyNewestLegacyIncarnationOfAnInboxItem() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 25, 13, 0);
    InboxItem item =
        inboxItemRepository.saveAndFlush(
            InboxItem.builder()
                .origin(ExpenseSource.OUTLOOK_EMAIL)
                .legacySourceType(ExpenseSource.OUTLOOK_EMAIL.name())
                .legacySourceId("message")
                .state(InboxState.READY)
                .externalSyncState(ExternalSyncState.NOT_REQUIRED)
                .rawStatus("READY")
                .classificationAmbiguous(false)
                .createdAt(now)
                .updatedAt(now)
                .build());
    LegacyInboxMap older = mapping(item, 1L, now.minusMinutes(1));
    LegacyInboxMap current = mapping(item, 2L, now);
    legacyInboxMapRepository.saveAllAndFlush(List.of(older, current));

    List<LegacyInboxMap> active =
        legacyInboxMapRepository.findActiveByTable(LegacyPendingTable.PENDING_EXPENSES);

    assertThat(active).extracting(mapping -> mapping.getKey().getId()).containsExactly(2L);
  }

  private LegacyInboxMap mapping(InboxItem item, Long legacyId, LocalDateTime mappedAt) {
    return LegacyInboxMap.builder()
        .key(new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, legacyId))
        .inboxItem(item)
        .mappedAt(mappedAt)
        .build();
  }
}
