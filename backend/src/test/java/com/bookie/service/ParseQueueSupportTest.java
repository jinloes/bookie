package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.JobExecutionException;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ParseQueueSupportTest {

  @Mock private ParseSessionContext parseSessionContext;
  @Mock private PendingExpenseService pendingExpenseService;
  @Mock private SseService sseService;

  @InjectMocks private ParseQueueSupport support;

  @Nested
  class Run {

    @Test
    void success_marksReadyAndEmitsSse() {
      EmailSuggestion suggestion = EmailSuggestion.builder().emailType(EmailType.EXPENSE).build();
      when(parseSessionContext.getUnrecognizedAliases()).thenReturn(List.of("alias1"));

      support.run(10L, ExpenseSource.RECEIPT, () -> suggestion);

      verify(parseSessionContext, times(2)).clear();
      verify(pendingExpenseService).markReady(10L, suggestion, List.of("alias1"));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
      verify(sseService).emit(eq("pending-updated"), captor.capture());
      assertThat(captor.getValue())
          .containsEntry("id", 10L)
          .containsEntry("status", "READY")
          .containsEntry("emailType", "EXPENSE")
          .containsEntry("sourceType", "RECEIPT");
    }

    @Test
    void nullEmailType_defaultsToExpenseInSse() {
      EmailSuggestion suggestion = EmailSuggestion.builder().build();
      when(parseSessionContext.getUnrecognizedAliases()).thenReturn(List.of());

      support.run(5L, ExpenseSource.OUTLOOK_EMAIL, () -> suggestion);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
      verify(sseService).emit(eq("pending-updated"), captor.capture());
      assertThat(captor.getValue()).containsEntry("emailType", "EXPENSE");
    }

    @Test
    void failure_marksFailedAndEmitsSse() {
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
      verify(pendingExpenseService).markFailed(10L, "boom");
      verify(sseService).emit("pending-updated", Map.of("id", 10L, "status", "FAILED"));
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
