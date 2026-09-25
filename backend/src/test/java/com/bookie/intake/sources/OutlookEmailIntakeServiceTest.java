package com.bookie.intake.sources;

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
import com.bookie.intake.sources.InboxItemCreationService.FindOrCreateResult;
import com.bookie.intake.sources.InboxItemCreationService.IntakeSource;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OutlookEmailIntakeServiceTest {

  @Mock private OutlookService outlookService;
  @Mock private InboxItemCreationService inboxItemCreationService;
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
    void createsAndQueuesOneInboxItemPerAttachment() {
      when(outlookService.discoverIntakeTargets("message")).thenReturn(targets);
      for (int index = 0; index < targets.size(); index++) {
        OutlookService.IntakeTarget target = targets.get(index);
        when(inboxItemCreationService.findOrCreate(source(target, 42L)))
            .thenReturn(
                new FindOrCreateResult(item((long) index + 1, InboxState.QUEUED, 42L), false));
      }

      OutlookEmailIntakeService.QueueResult result = service.queue("message", 42L);

      assertThat(result.ids()).containsExactly(1L, 2L, 3L);
      assertThat(result.first().getId()).isEqualTo(1L);
      // findOrCreate publishes each parse kick (InboxItemQueued) inside its own transaction.
      assertThat(result.queuedCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"QUEUED", "PROCESSING", "READY"})
    void repeatImportReturnsActiveItemsWithoutDuplicateJobs(InboxState state) {
      when(outlookService.discoverIntakeTargets("message")).thenReturn(targets);
      for (int index = 0; index < targets.size(); index++) {
        when(inboxItemStore.findBySourceIdentity(
                ExpenseSource.OUTLOOK_EMAIL, targets.get(index).sourceId()))
            .thenReturn(Optional.of(item((long) index + 1, state, null)));
      }

      OutlookEmailIntakeService.QueueResult result = service.queue("message", null);

      assertThat(result.ids()).containsExactly(1L, 2L, 3L);
      assertThat(result.queuedCount()).isZero();
      verify(inboxItemCreationService, never()).findOrCreate(any());
    }

    @Test
    void requeuesAFailedItemThroughFindOrCreate() {
      OutlookService.IntakeTarget target = targets.getFirst();
      when(outlookService.discoverIntakeTargets("message")).thenReturn(List.of(target));
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.OUTLOOK_EMAIL, target.sourceId()))
          .thenReturn(Optional.of(item(1L, InboxState.FAILED, null)));
      when(inboxItemCreationService.findOrCreate(source(target, null)))
          .thenReturn(new FindOrCreateResult(item(1L, InboxState.QUEUED, null), false));

      assertThat(service.queue("message", null).queuedCount()).isOne();
    }

    @Test
    void includesAConcurrentlyCreatedItemWithoutQueueingItAgain() {
      OutlookService.IntakeTarget target = targets.getFirst();
      when(outlookService.discoverIntakeTargets("message")).thenReturn(List.of(target));
      when(inboxItemCreationService.findOrCreate(source(target, null)))
          .thenReturn(new FindOrCreateResult(item(1L, InboxState.QUEUED, null), true));

      OutlookEmailIntakeService.QueueResult result = service.queue("message", null);

      assertThat(result.ids()).containsExactly(1L);
      assertThat(result.queuedCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"SAVED", "DISMISSED"})
    void completedAttachmentIsNotRecreated(InboxState state) {
      OutlookService.IntakeTarget target = targets.getFirst();
      when(outlookService.discoverIntakeTargets("message")).thenReturn(List.of(target));
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.OUTLOOK_EMAIL, target.sourceId()))
          .thenReturn(Optional.of(item(1L, state, null)));

      OutlookEmailIntakeService.QueueResult result = service.queue("message", null);

      assertThat(result.items()).isEmpty();
      assertThat(result.queuedCount()).isZero();
      assertThatThrownBy(result::first)
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
      verifyNoInteractions(inboxItemCreationService);
    }

    @Test
    void skipsAnAttachmentAlreadySavedToTheLedger() {
      OutlookService.IntakeTarget target = targets.getFirst();
      when(outlookService.discoverIntakeTargets("message")).thenReturn(List.of(target));
      when(inboxItemCreationService.findOrCreate(source(target, null)))
          .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "already saved"));

      assertThat(service.queue("message", null).items()).isEmpty();
    }

    @Test
    void propagatesNonConflictFailures() {
      OutlookService.IntakeTarget target = targets.getFirst();
      when(outlookService.discoverIntakeTargets("message")).thenReturn(List.of(target));
      when(inboxItemCreationService.findOrCreate(source(target, null)))
          .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad activity"));

      assertThatThrownBy(() -> service.queue("message", null))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("bad activity");
    }

    @Test
    void discoveryFailureCreatesNothing() {
      when(outlookService.discoverIntakeTargets("message"))
          .thenThrow(new IllegalStateException("Graph failed"));

      assertThatThrownBy(() -> service.queue("message", null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Graph failed");

      verifyNoInteractions(inboxItemCreationService, inboxItemStore);
    }
  }

  private OutlookService.IntakeTarget target(
      String sourceId, String attachmentId, String attachmentName) {
    return new OutlookService.IntakeTarget(
        sourceId, "message", attachmentId, attachmentName, "Receipts - " + attachmentName);
  }

  private IntakeSource source(OutlookService.IntakeTarget target, Long configuredActivityId) {
    return new IntakeSource(
        target.sourceId(),
        ExpenseSource.OUTLOOK_EMAIL,
        target.subject(),
        configuredActivityId,
        target.outlookMessageId(),
        target.attachmentId(),
        target.attachmentName());
  }

  private InboxItem item(Long id, InboxState state, Long configuredActivityId) {
    return InboxItem.builder()
        .id(id)
        .origin(ExpenseSource.OUTLOOK_EMAIL)
        .state(state)
        .configuredActivityId(configuredActivityId)
        .build();
  }
}
