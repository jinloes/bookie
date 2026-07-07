package com.bookie.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailParseQueueServiceTest {

  @Mock private OutlookService outlookService;
  @Mock private EmailParserService emailParserService;
  @Mock private PropertyHistoryService propertyHistoryService;
  @Mock private ParseQueueSupport parseQueueSupport;

  @InjectMocks private EmailParseQueueService service;

  @Nested
  class ProcessEmail {

    @Test
    void fetchesMessageParsesAndStoresKeywords() throws Exception {
      OutlookService.MessageContent msg =
          new OutlookService.MessageContent("Subject", "Body", "2026-03-17");
      when(outlookService.fetchMessageBody("msg-1")).thenReturn(msg);

      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.EXPENSE)
              .keywords(List.of("acc-123"))
              .build();
      when(emailParserService.suggestFromEmail("Subject", "Body", "2026-03-17"))
          .thenReturn(suggestion);

      doAnswer(
              inv -> {
                @SuppressWarnings("unchecked")
                Callable<EmailSuggestion> task = inv.getArgument(2);
                task.call();
                return null;
              })
          .when(parseQueueSupport)
          .run(any(), any(), any());

      service.processEmail(10L, "msg-1");

      verify(parseQueueSupport).run(eq(10L), eq(ExpenseSource.OUTLOOK_EMAIL), any());
      verify(propertyHistoryService).storeKeywords("msg-1", List.of("acc-123"));
    }
  }
}
