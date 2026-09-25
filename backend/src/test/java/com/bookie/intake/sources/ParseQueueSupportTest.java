package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.InboxEventStream;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.application.JobExecutionException;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.intake.extraction.ParseSessionContext;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ParseQueueSupportTest {

  @Mock private ParseSessionContext parseSessionContext;
  @Mock private InboxItemCreationService inboxItemCreationService;
  @Mock private InboxItemStore inboxItemStore;
  @Mock private InboxEventStream inboxEventStream;

  @InjectMocks private ParseQueueSupport support;

  @Nested
  class Run {

    @Test
    void success_marksReadyAndPublishesTheInboxItem() {
      EmailSuggestion suggestion = EmailSuggestion.builder().emailType(EmailType.EXPENSE).build();
      InboxItem item = InboxItem.builder().id(110L).state(InboxState.READY).build();
      when(parseSessionContext.getUnrecognizedAliases()).thenReturn(List.of("alias1"));
      when(inboxItemStore.findById(10L)).thenReturn(Optional.of(item));

      support.run(10L, ExpenseSource.RECEIPT, () -> suggestion);

      verify(parseSessionContext, times(2)).clear();
      verify(inboxItemCreationService).markReady(10L, suggestion, List.of("alias1"));
      verify(inboxEventStream).inboxUpdated(item);
    }

    @Test
    void success_skipsNotificationWhenTheInboxItemIsGone() {
      EmailSuggestion suggestion = EmailSuggestion.builder().build();
      when(parseSessionContext.getUnrecognizedAliases()).thenReturn(List.of());
      when(inboxItemStore.findById(5L)).thenReturn(Optional.empty());

      support.run(5L, ExpenseSource.OUTLOOK_EMAIL, () -> suggestion);

      verify(inboxItemCreationService).markReady(5L, suggestion, List.of());
      verifyNoInteractions(inboxEventStream);
    }

    @Test
    void failure_marksFailedAndPublishesTheInboxItem() {
      InboxItem item = InboxItem.builder().id(110L).state(InboxState.FAILED).build();
      when(inboxItemStore.findById(10L)).thenReturn(Optional.of(item));

      assertThatThrownBy(
              () ->
                  support.run(
                      10L,
                      ExpenseSource.RECEIPT,
                      () -> {
                        throw new RuntimeException("boom");
                      }))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.RETRYABLE));

      verify(parseSessionContext, times(2)).clear();
      verify(inboxItemCreationService).markFailed(10L, "boom");
      verify(inboxEventStream).inboxUpdated(item);
    }

    @Test
    void preservesJobFailureClassification() {
      JobExecutionException failure = JobExecutionException.manualReview("Review required", null);

      assertThatThrownBy(
              () ->
                  support.run(
                      11L,
                      ExpenseSource.OUTLOOK_EMAIL,
                      () -> {
                        throw failure;
                      }))
          .isSameAs(failure);
    }

    @Test
    void preservesTypedIntegrationFailureForDispatcherClassification() {
      IntegrationException failure =
          IntegrationException.builder()
              .kind(IntegrationFailureKind.RECONNECT_REQUIRED)
              .message("Reconnect Outlook")
              .build();

      assertThatThrownBy(
              () ->
                  support.run(
                      12L,
                      ExpenseSource.OUTLOOK_EMAIL,
                      () -> {
                        throw failure;
                      }))
          .isSameAs(failure);
    }

    @Test
    void preservesResponseFailureForDispatcherClassification() {
      ResponseStatusException failure =
          new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Reconnect Outlook");

      assertThatThrownBy(
              () ->
                  support.run(
                      13L,
                      ExpenseSource.OUTLOOK_EMAIL,
                      () -> {
                        throw failure;
                      }))
          .isSameAs(failure);
    }
  }
}
