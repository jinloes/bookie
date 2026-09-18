package com.bookie.intake.compatibility;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bookie.intake.application.*;
import com.bookie.intake.domain.*;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.model.ExpenseSource;
import com.bookie.repository.ReceiptHashRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaLegacyInboxSynchronizerTest {
  @Mock InboxItemStore items;
  @Mock LegacyInboxMapStore maps;
  @Mock BackgroundJobStore jobs;
  @Mock LedgerTransactionService ledger;
  @Mock ReceiptHashRepository hashes;
  @Mock BackgroundJobService service;
  JpaLegacyInboxSynchronizer synchronizer;
  LegacyPendingKey key = new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, 1L);
  InboxItem item;
  BackgroundJob job;

  @BeforeEach
  void setup() {
    synchronizer =
        new JpaLegacyInboxSynchronizer(
            items, maps, jobs, ledger, hashes, Clock.systemUTC(), service);
    item =
        InboxItem.builder()
            .id(1L)
            .origin(ExpenseSource.OUTLOOK_EMAIL)
            .state(InboxState.READY)
            .externalSyncState(ExternalSyncState.NOT_REQUIRED)
            .build();
    job =
        BackgroundJob.builder()
            .id(2L)
            .inboxItem(item)
            .state(BackgroundJobState.TERMINAL)
            .executionId(UUID.randomUUID())
            .executionStarted(true)
            .executionAttemptBase(2)
            .executionPreviousMaxAttempts(5)
            .attempts(13)
            .maxAttempts(13)
            .build();
  }

  void mapped() {
    when(maps.findByKey(key))
        .thenReturn(Optional.of(LegacyInboxMap.builder().key(key).inboxItem(item).build()));
    when(items.save(item)).thenReturn(item);
  }

  LegacyInboxSnapshot snapshot() {
    return LegacyInboxSnapshot.builder()
        .origin(ExpenseSource.OUTLOOK_EMAIL)
        .legacySourceId("source")
        .unrecognizedAliases(List.of())
        .build();
  }

  @Nested
  class Generations {
    @Test
    void ensureTranslationRetainsExistingTerminalGenerationAndBudget() {
      mapped();
      UUID original = job.getExecutionId();
      when(jobs.findByIdempotencyKey("translate_outlook_id:1")).thenReturn(Optional.of(job));
      when(jobs.findForUpdate(2L)).thenReturn(Optional.of(job));
      synchronizer.created(key, snapshot(), false);
      assertThat(job.getExecutionId()).isEqualTo(original);
      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getAttempts()).isEqualTo(13);
      assertThat(job.isExecutionStarted()).isTrue();
    }

    @Test
    void explicitParseResetClearsBindingButLeasedGenerationCannotBeReset() {
      mapped();
      when(jobs.findByIdempotencyKey("parse_outlook:1")).thenReturn(Optional.of(job));
      when(jobs.findForUpdate(2L)).thenReturn(Optional.of(job));
      synchronizer.retryQueued(key, snapshot());
      assertThat(job.getExecutionId()).isNull();
      assertThat(job.getExecutionAttemptBase()).isZero();
      assertThat(job.getExecutionPreviousMaxAttempts()).isNull();
      assertThat(job.isExecutionStarted()).isFalse();
      assertThat(job.getAttempts()).isZero();
      assertThat(job.getMaxAttempts()).isEqualTo(11);
      UUID active = UUID.randomUUID();
      job.setExecutionId(active);
      job.setExecutionStarted(true);
      job.setState(BackgroundJobState.LEASED);
      synchronizer.retryQueued(key, snapshot());
      assertThat(job.getExecutionId()).isEqualTo(active);
      assertThat(job.isExecutionStarted()).isTrue();
      assertThat(job.getState()).isEqualTo(BackgroundJobState.LEASED);
    }

    @Test
    void staleReadyAndFailedCallbacksAreRejectedBeforeAnySnapshotWrite() {
      doThrow(new IllegalStateException("stale generation")).when(service).requireCurrentParse(key);
      assertThatThrownBy(() -> synchronizer.ready(key, snapshot())).hasMessage("stale generation");
      assertThatThrownBy(() -> synchronizer.failed(key, snapshot())).hasMessage("stale generation");
      verifyNoInteractions(items, maps, jobs);
    }
  }

  @Nested
  class ItemReuse {
    @Test
    void reusedSourceTakesCreatedAtFromTheNewLegacyRow() {
      LocalDateTime originalCreatedAt = LocalDateTime.of(2026, 9, 11, 22, 0);
      LocalDateTime recreatedAt = originalCreatedAt.plusMinutes(10);
      item.setOrigin(ExpenseSource.RECEIPT);
      item.setState(InboxState.DISMISSED);
      item.setCreatedAt(originalCreatedAt);
      when(items.findBySourceIdentity(ExpenseSource.RECEIPT, "source"))
          .thenReturn(Optional.of(item));
      when(items.save(item)).thenReturn(item);

      synchronizer.created(
          key,
          LegacyInboxSnapshot.builder()
              .origin(ExpenseSource.RECEIPT)
              .legacySourceId("source")
              .createdAt(recreatedAt)
              .unrecognizedAliases(List.of())
              .build(),
          false);

      assertThat(item.getCreatedAt()).isEqualTo(recreatedAt);
      assertThat(item.getState()).isEqualTo(InboxState.READY);
      verify(maps).save(any(LegacyInboxMap.class));
    }

    @Nested
    class OutlookArtifacts {

      @Test
      void synchronizesAndReplacesAttachmentMetadata() {
        mapped();
        when(jobs.findByIdempotencyKey("translate_outlook_id:1")).thenReturn(Optional.empty());
        LegacyInboxSnapshot first =
            LegacyInboxSnapshot.builder()
                .origin(ExpenseSource.OUTLOOK_EMAIL)
                .legacySourceId("derived-source")
                .outlookMessageId("parent-message")
                .outlookAttachmentId("attachment-1")
                .outlookAttachmentName("one.pdf")
                .unrecognizedAliases(List.of())
                .build();

        synchronizer.created(key, first, false);

        assertThat(item.getArtifacts())
            .singleElement()
            .satisfies(
                artifact -> {
                  assertThat(artifact.getType()).isEqualTo(InboxArtifactType.OUTLOOK_EMAIL);
                  assertThat(artifact.getExternalId()).isEqualTo("parent-message");
                  assertThat(artifact.getTextValue()).isEqualTo("attachment-1");
                  assertThat(artifact.getFileName()).isEqualTo("one.pdf");
                });

        LegacyInboxSnapshot replacement =
            LegacyInboxSnapshot.builder()
                .origin(ExpenseSource.OUTLOOK_EMAIL)
                .legacySourceId("derived-source")
                .outlookMessageId("parent-message")
                .outlookAttachmentId("attachment-2")
                .outlookAttachmentName("two.pdf")
                .unrecognizedAliases(List.of())
                .build();
        synchronizer.created(key, replacement, false);

        assertThat(item.getArtifacts())
            .singleElement()
            .satisfies(
                artifact -> {
                  assertThat(artifact.getTextValue()).isEqualTo("attachment-2");
                  assertThat(artifact.getFileName()).isEqualTo("two.pdf");
                });
      }
    }
  }
}
