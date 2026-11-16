package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.bookie.intake.application.JobExecutionException;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.List;
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

  @Mock private InboxItemCreationService inboxItemCreationService;

  @InjectMocks private ParseQueueSupport support;

  @Nested
  class Run {

    @Test
    void success_marksReadyWithUnrecognizedAliases() {
      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.EXPENSE)
              .unrecognizedAliases(List.of("alias1"))
              .build();

      support.run(10L, ExpenseSource.RECEIPT, () -> suggestion);

      verify(inboxItemCreationService).markReady(10L, suggestion, List.of("alias1"));
    }

    @Test
    void failure_marksFailedAndWrapsUntypedExceptionAsRetryable() {
      assertThatThrownBy(() ->
              support.run(
                  10L,
                  ExpenseSource.RECEIPT,
                  () -> {
                    throw new RuntimeException("boom");
                  }))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(failure ->
              assertThat(((JobExecutionException) failure).getKind())
                  .isEqualTo(JobExecutionException.FailureKind.RETRYABLE));

      verify(inboxItemCreationService).markFailed(10L, "boom");
    }

    @Test
    void preservesJobFailureClassification() {
      JobExecutionException failure = JobExecutionException.manualReview("Review required", null);

      assertThatThrownBy(() ->
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

      assertThatThrownBy(() ->
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

      assertThatThrownBy(() ->
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
