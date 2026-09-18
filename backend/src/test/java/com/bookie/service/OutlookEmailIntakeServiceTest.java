package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutlookEmailIntakeServiceTest {

  @Mock private OutlookService outlookService;
  @Mock private PendingExpenseService pendingExpenseService;
  @Mock private EmailParseQueueService emailParseQueueService;
  @Mock private InboxItemStore inboxItemStore;

  @InjectMocks private OutlookEmailIntakeService service;

  private List<OutlookService.IntakeTarget> targets;

  @BeforeEach
  void setUp() {
    targets =
        List.of(
            target("source-1", "attachment-1", "one.pdf"),
            target("source-2", "attachment-2", "two.pdf"),
            target("source-3", "attachment-3", "three.png"));
  }

  @Nested
  class Queue {

    @Test
    void createsAndQueuesOnePendingItemPerAttachment() {
      when(outlookService.discoverIntakeTargets("message")).thenReturn(targets);
      for (int index = 0; index < targets.size(); index++) {
        OutlookService.IntakeTarget target = targets.get(index);
        PendingExpense pending = pending((long) index + 1, target.sourceId());
        when(pendingExpenseService.findOrCreate(
                target.sourceId(),
                ExpenseSource.OUTLOOK_EMAIL,
                target.subject(),
                42L,
                "message",
                target.attachmentId(),
                target.attachmentName()))
            .thenReturn(new PendingExpenseService.FindOrCreateResult(pending, false));
      }

      OutlookEmailIntakeService.QueueResult result = service.queue("message", 42L);

      assertThat(result.ids()).containsExactly(1L, 2L, 3L);
      assertThat(result.queuedCount()).isEqualTo(3);
      verify(emailParseQueueService).processEmail(1L, "message", 42L);
      verify(emailParseQueueService).processEmail(2L, "message", 42L);
      verify(emailParseQueueService).processEmail(3L, "message", 42L);
    }

    @Test
    void repeatImportReturnsExistingItemsWithoutDuplicateJobs() {
      when(outlookService.discoverIntakeTargets("message")).thenReturn(targets);
      for (int index = 0; index < targets.size(); index++) {
        OutlookService.IntakeTarget target = targets.get(index);
        PendingExpense pending = pending((long) index + 1, target.sourceId());
        when(pendingExpenseService.findBySourceId(target.sourceId()))
            .thenReturn(Optional.of(pending));
      }

      OutlookEmailIntakeService.QueueResult result = service.queue("message", null);

      assertThat(result.ids()).containsExactly(1L, 2L, 3L);
      assertThat(result.queuedCount()).isZero();
      verify(pendingExpenseService, never())
          .findOrCreate(any(), any(), any(), any(), any(), any(), any());
      verifyNoInteractions(emailParseQueueService);
    }

    @Test
    void completedAttachmentIsNotRecreated() {
      OutlookService.IntakeTarget target = targets.getFirst();
      when(outlookService.discoverIntakeTargets("message")).thenReturn(List.of(target));
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.OUTLOOK_EMAIL, target.sourceId()))
          .thenReturn(
              Optional.of(
                  InboxItem.builder()
                      .origin(ExpenseSource.OUTLOOK_EMAIL)
                      .legacySourceId(target.sourceId())
                      .state(InboxState.SAVED)
                      .build()));

      OutlookEmailIntakeService.QueueResult result = service.queue("message", null);

      assertThat(result.pendingItems()).isEmpty();
      assertThat(result.queuedCount()).isZero();
      verifyNoInteractions(emailParseQueueService);
    }

    @Test
    void discoveryFailureCreatesNothing() {
      when(outlookService.discoverIntakeTargets("message"))
          .thenThrow(new IllegalStateException("Graph failed"));

      assertThatThrownBy(() -> service.queue("message", null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Graph failed");

      verifyNoInteractions(pendingExpenseService, emailParseQueueService, inboxItemStore);
    }
  }

  private OutlookService.IntakeTarget target(
      String sourceId, String attachmentId, String attachmentName) {
    return new OutlookService.IntakeTarget(
        sourceId, "message", attachmentId, attachmentName, "Receipts - " + attachmentName);
  }

  private PendingExpense pending(Long id, String sourceId) {
    return PendingExpense.builder()
        .id(id)
        .sourceId(sourceId)
        .sourceType(ExpenseSource.OUTLOOK_EMAIL)
        .outlookMessageId("message")
        .configuredActivityId(42L)
        .status(PendingExpenseStatus.PROCESSING)
        .build();
  }
}
