package com.bookie.service;

import com.bookie.model.ExpenseSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Runs email parsing asynchronously so the HTTP request returns immediately. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailParseQueueService {

  private final OutlookService outlookService;
  private final EmailParserService emailParserService;
  private final PropertyHistoryService propertyHistoryService;
  private final ParseQueueSupport parseQueueSupport;

  @Async
  public void processEmail(Long pendingId, String messageId) {
    parseQueueSupport.run(
        pendingId,
        ExpenseSource.OUTLOOK_EMAIL,
        () -> {
          OutlookService.MessageContent message = outlookService.fetchMessageBody(messageId);
          var suggestion =
              emailParserService.suggestFromEmail(
                  message.subject(), message.body(), message.receivedDate());
          propertyHistoryService.storeKeywords(messageId, suggestion.keywords());
          return suggestion;
        });
  }
}
