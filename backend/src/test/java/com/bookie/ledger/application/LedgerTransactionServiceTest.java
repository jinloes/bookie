package com.bookie.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionMap;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LedgerTransactionServiceTest {

  @Mock private LedgerTransactionStore transactionStore;
  @Mock private LedgerReferenceResolver referenceResolver;
  @Mock private LegacyTransactionWriter legacyTransactionWriter;

  @InjectMocks private LedgerTransactionService transactionService;

  private FinancialActivity activity;
  private NeutralCategory category;
  private LegacyTransactionSnapshot snapshot;
  private LedgerTransactionInput input;

  @BeforeEach
  void setUp() {
    activity =
        FinancialActivity.builder()
            .id(4L)
            .name("Consulting")
            .activityType(ActivityType.SELF_EMPLOYMENT)
            .taxTreatment(TaxTreatment.SCHEDULE_C)
            .active(true)
            .build();
    category =
        NeutralCategory.builder()
            .id(7L)
            .key("SERVICE_INCOME")
            .label("Service income")
            .direction(TransactionDirection.INCOME)
            .active(true)
            .system(true)
            .build();
    snapshot =
        LegacyTransactionSnapshot.builder()
            .key(new LegacyTransactionKey(LegacyTransactionTable.INCOMES, 11L))
            .amount(new BigDecimal("100.00"))
            .direction(TransactionDirection.INCOME)
            .date(LocalDate.of(2026, 8, 1))
            .description("Consulting income")
            .activityId(4L)
            .legacyCategoryId(17L)
            .legacyCounterpartyId(9L)
            .origin("VENMO")
            .externalId("source-001")
            .sourceLabel("Synthetic client")
            .attachmentStorageProvider("ONEDRIVE")
            .attachmentExternalId("file-001")
            .attachmentFileName("statement.pdf")
            .build();
    input =
        LedgerTransactionInput.builder()
            .amount(new BigDecimal("100.00"))
            .direction(TransactionDirection.INCOME)
            .date(LocalDate.of(2026, 8, 1))
            .description("Consulting income")
            .activityId(4L)
            .neutralCategoryId(7L)
            .counterpartyId(20L)
            .origin("VENMO")
            .externalId("source-001")
            .sourceLabel("Synthetic client")
            .attachmentStorageProvider("ONEDRIVE")
            .attachmentExternalId("file-001")
            .attachmentFileName("statement.pdf")
            .build();
  }

  @Nested
  class SynchronizeLegacy {

    @Test
    void createsOneTargetMappingWithNormalizedReferences() {
      when(referenceResolver.resolveFromLegacy(4L, 17L, 9L))
          .thenReturn(new ResolvedLedgerReferences(activity, category, 20L));
      when(transactionStore.findMap(snapshot.getKey())).thenReturn(Optional.empty());
      when(transactionStore.findBySourceIdentity("VENMO", "source-001"))
          .thenReturn(Optional.empty());
      when(transactionStore.save(any()))
          .thenAnswer(
              invocation -> {
                FinancialTransaction transaction = invocation.getArgument(0);
                transaction.setId(31L);
                transaction.setVersion(0L);
                return transaction;
              });

      FinancialTransaction result = transactionService.synchronizeLegacy(snapshot);

      assertThat(result.getAmount()).isEqualByComparingTo("100.00");
      assertThat(result.getDirection()).isEqualTo(TransactionDirection.INCOME);
      assertThat(result.getActivity()).isEqualTo(activity);
      assertThat(result.getNeutralCategory()).isEqualTo(category);
      assertThat(result.getCounterpartyId()).isEqualTo(20L);
      assertThat(result.getImportReferences())
          .singleElement()
          .satisfies(
              reference -> {
                assertThat(reference.getOrigin()).isEqualTo("VENMO");
                assertThat(reference.getExternalId()).isEqualTo("source-001");
                assertThat(reference.getSourceLabel()).isEqualTo("Synthetic client");
              });
      assertThat(result.getAttachments())
          .singleElement()
          .satisfies(
              attachment -> {
                assertThat(attachment.getExternalId()).isEqualTo("file-001");
                assertThat(attachment.getFileName()).isEqualTo("statement.pdf");
              });
      ArgumentCaptor<LegacyTransactionMap> mapCaptor =
          ArgumentCaptor.forClass(LegacyTransactionMap.class);
      verify(transactionStore).saveMap(mapCaptor.capture());
      assertThat(mapCaptor.getValue().getKey()).isEqualTo(snapshot.getKey());
      assertThat(mapCaptor.getValue().getCanonicalHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsSourceIdentityAlreadyOwnedByAnotherTransaction() {
      when(referenceResolver.resolveFromLegacy(4L, 17L, 9L))
          .thenReturn(new ResolvedLedgerReferences(activity, category, 20L));
      when(transactionStore.findMap(snapshot.getKey())).thenReturn(Optional.empty());
      FinancialTransaction conflicting = transaction();
      conflicting.setId(99L);
      when(transactionStore.findBySourceIdentity("VENMO", "source-001"))
          .thenReturn(Optional.of(conflicting));

      assertThatThrownBy(() -> transactionService.synchronizeLegacy(snapshot))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("source identity");

      verify(transactionStore, never()).save(any());
    }

    @Test
    void refusesIncompleteLegacyRecordInsteadOfDroppingFields() {
      LegacyTransactionSnapshot incomplete =
          LegacyTransactionSnapshot.builder()
              .key(snapshot.getKey())
              .amount(BigDecimal.TEN)
              .direction(TransactionDirection.INCOME)
              .date(snapshot.getDate())
              .description(snapshot.getDescription())
              .activityId(snapshot.getActivityId())
              .build();

      assertThatThrownBy(() -> transactionService.synchronizeLegacy(incomplete))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("losslessly");
    }
  }

  @Nested
  class UnifiedCommands {

    @Test
    void createWritesLegacyThenSynchronizesThatExactRow() {
      ResolvedLegacyReferences legacyReferences =
          new ResolvedLegacyReferences(activity, category, 17L, 9L);
      when(referenceResolver.resolveForUnified(4L, 7L, 20L, input.getDate()))
          .thenReturn(legacyReferences);
      when(legacyTransactionWriter.create(any(), eq(legacyReferences))).thenReturn(snapshot);
      when(referenceResolver.resolveFromLegacy(4L, 17L, 9L))
          .thenReturn(new ResolvedLedgerReferences(activity, category, 20L));
      when(transactionStore.findMap(snapshot.getKey())).thenReturn(Optional.empty());
      when(transactionStore.findBySourceIdentity("VENMO", "source-001"))
          .thenReturn(Optional.empty());
      when(transactionStore.save(any()))
          .thenAnswer(
              invocation -> {
                FinancialTransaction transaction = invocation.getArgument(0);
                transaction.setId(31L);
                transaction.setVersion(0L);
                return transaction;
              });

      FinancialTransaction result = transactionService.create(input);

      assertThat(result.getId()).isEqualTo(31L);
      verify(legacyTransactionWriter).create(any(), eq(legacyReferences));
      verify(transactionStore).saveMap(any());
    }

    @Test
    void updateRejectsStaleVersionBeforeChangingLegacyData() {
      FinancialTransaction existing = transaction();
      existing.setVersion(3L);
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> transactionService.update(31L, 2L, input))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("modified");

      verify(legacyTransactionWriter, never()).update(any(), any(), any());
    }

    @Test
    void deleteTombstonesTargetOnlyAfterDeletingMappedLegacyRow() {
      FinancialTransaction existing = transaction();
      existing.setVersion(2L);
      LegacyTransactionMap map =
          LegacyTransactionMap.builder()
              .key(snapshot.getKey())
              .transaction(existing)
              .canonicalHash("0".repeat(64))
              .mappedAt(LocalDateTime.now())
              .build();
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.of(existing));
      when(transactionStore.findMapByTransactionId(31L)).thenReturn(Optional.of(map));
      when(transactionStore.save(existing)).thenReturn(existing);

      transactionService.delete(31L, 2L);

      verify(legacyTransactionWriter).delete(snapshot.getKey());
      assertThat(existing.getDeletedAt()).isNotNull();
      verify(transactionStore).saveMap(map);
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void reassignsTargetActivityAndDirectionSpecificCategoryWithoutDeletingRecord() {
      FinancialTransaction existing = transaction();
      LegacyTransactionMap map =
          LegacyTransactionMap.builder()
              .key(snapshot.getKey())
              .transaction(existing)
              .canonicalHash("0".repeat(64))
              .mappedAt(LocalDateTime.now())
              .build();
      FinancialActivity replacement =
          FinancialActivity.builder()
              .id(44L)
              .name("Needs classification")
              .activityType(ActivityType.OTHER)
              .taxTreatment(TaxTreatment.NONE)
              .active(true)
              .build();
      NeutralCategory replacementCategory =
          NeutralCategory.builder()
              .id(77L)
              .key("OTHER_INCOME")
              .label("Other income")
              .direction(TransactionDirection.INCOME)
              .active(true)
              .build();
      when(transactionStore.findAllActiveByActivityId(4L)).thenReturn(List.of(existing));
      when(referenceResolver.resolveFromLegacy(44L, 18L, null))
          .thenReturn(new ResolvedLedgerReferences(replacement, replacementCategory, null));
      when(transactionStore.save(existing)).thenReturn(existing);
      when(transactionStore.findMapByTransactionId(31L)).thenReturn(Optional.of(map));

      transactionService.reassignActivity(4L, 44L, 18L, 19L);

      assertThat(existing.getActivity()).isEqualTo(replacement);
      assertThat(existing.getNeutralCategory()).isEqualTo(replacementCategory);
      assertThat(existing.getDeletedAt()).isNull();
      verify(transactionStore).saveMap(map);
    }
  }

  private FinancialTransaction transaction() {
    return FinancialTransaction.builder()
        .id(31L)
        .amount(new BigDecimal("100.00"))
        .direction(TransactionDirection.INCOME)
        .date(LocalDate.of(2026, 8, 1))
        .description("Consulting income")
        .activity(activity)
        .neutralCategory(category)
        .counterpartyId(20L)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .version(0L)
        .build();
  }
}
