package com.bookie.intake.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.compatibility.intake.LegacyInboxSnapshots;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.LegacyInboxMap;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyInboxReadSelectorTest {

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 24, 12, 0);

  private final LegacyInboxMapStore mapStore = mock(LegacyInboxMapStore.class);

  @Test
  void legacyModeReturnsLegacyRowsWithoutReadingDurableState() {
    PendingExpense pending = pending(1L, "legacy-one");
    LegacyInboxReadSelector selector = new LegacyInboxReadSelector(mapStore, "LEGACY");

    List<PendingExpense> result =
        selector.select(
            LegacyPendingTable.PENDING_EXPENSES,
            List.of(pending),
            PendingExpense::getId,
            LegacyInboxSnapshots::from);

    assertThat(result).containsExactly(pending);
    verifyNoInteractions(mapStore);
  }

  @Test
  void compareModeFailsClosedWhenAnyDurableFieldDiffers() {
    PendingExpense pending = pending(1L, "legacy-one");
    LegacyInboxMap mapping = mapping(pending);
    mapping.getInboxItem().setLegacySourceId("different");
    when(mapStore.findActiveByTable(LegacyPendingTable.PENDING_EXPENSES))
        .thenReturn(List.of(mapping));
    LegacyInboxReadSelector selector = new LegacyInboxReadSelector(mapStore, "COMPARE");

    assertThatThrownBy(
            () ->
                selector.select(
                    LegacyPendingTable.PENDING_EXPENSES,
                    List.of(pending),
                    PendingExpense::getId,
                    LegacyInboxSnapshots::from))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fields differ");
  }

  @Test
  void unifiedModeUsesDurableOrderOnlyAfterExactParity() {
    PendingExpense older = pending(1L, "legacy-one");
    PendingExpense newer = pending(2L, "legacy-two");
    when(mapStore.findActiveByTable(LegacyPendingTable.PENDING_EXPENSES))
        .thenReturn(List.of(mapping(newer), mapping(older)));
    LegacyInboxReadSelector selector = new LegacyInboxReadSelector(mapStore, "unified");

    List<PendingExpense> result =
        selector.select(
            LegacyPendingTable.PENDING_EXPENSES,
            List.of(older, newer),
            PendingExpense::getId,
            LegacyInboxSnapshots::from);

    assertThat(result).containsExactly(newer, older);
  }

  private PendingExpense pending(Long id, String sourceId) {
    return PendingExpense.builder()
        .id(id)
        .sourceType(ExpenseSource.OUTLOOK_EMAIL)
        .sourceId(sourceId)
        .status(PendingExpenseStatus.READY)
        .classificationAmbiguous(false)
        .createdAt(CREATED_AT)
        .build();
  }

  private LegacyInboxMap mapping(PendingExpense pending) {
    InboxItem item =
        InboxItem.builder()
            .id(pending.getId() + 100)
            .origin(ExpenseSource.OUTLOOK_EMAIL)
            .legacySourceType(ExpenseSource.OUTLOOK_EMAIL.name())
            .legacySourceId(pending.getSourceId())
            .state(InboxState.READY)
            .externalSyncState(ExternalSyncState.NOT_REQUIRED)
            .rawStatus(PendingExpenseStatus.READY.name())
            .classificationAmbiguous(false)
            .createdAt(CREATED_AT)
            .updatedAt(CREATED_AT)
            .build();
    return LegacyInboxMap.builder()
        .key(new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, pending.getId()))
        .inboxItem(item)
        .mappedAt(CREATED_AT)
        .build();
  }
}
