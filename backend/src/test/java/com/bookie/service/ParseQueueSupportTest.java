package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
      support.run(
          10L,
          ExpenseSource.RECEIPT,
          () -> {
            throw new RuntimeException("boom");
          });

      verify(parseSessionContext, times(2)).clear();
      verify(pendingExpenseService).markFailed(10L, "boom");
      verify(sseService).emit("pending-updated", Map.of("id", 10L, "status", "FAILED"));
    }
  }
}
