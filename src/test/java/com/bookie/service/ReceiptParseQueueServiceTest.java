package com.bookie.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import java.io.ByteArrayInputStream;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptParseQueueServiceTest {

  @Mock private ReceiptService receiptService;
  @Mock private PdfExtractorService pdfExtractorService;
  @Mock private EmailParserService emailParserService;
  @Mock private ParseQueueSupport parseQueueSupport;

  @InjectMocks private ReceiptParseQueueService service;

  @Nested
  class ProcessReceipt {

    @Test
    void extractsTextAndParsesWithFixedSubject() throws Exception {
      when(receiptService.getReceiptContent("item-1"))
          .thenReturn(new ByteArrayInputStream("pdf-bytes".getBytes()));
      when(pdfExtractorService.extractText(any())).thenReturn("extracted text");

      EmailSuggestion suggestion = EmailSuggestion.builder().emailType(EmailType.EXPENSE).build();
      when(emailParserService.suggestFromEmail("Vendor Receipt / Invoice", "extracted text", null))
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

      service.processReceipt(10L, "item-1");

      verify(parseQueueSupport).run(eq(10L), eq(ExpenseSource.RECEIPT), any());
      verify(emailParserService)
          .suggestFromEmail("Vendor Receipt / Invoice", "extracted text", null);
    }

    @Test
    void nullStream_passesEmptyBytesToExtractor() throws Exception {
      when(receiptService.getReceiptContent("item-2")).thenReturn(null);
      when(pdfExtractorService.extractText(new byte[0])).thenReturn("");

      EmailSuggestion suggestion = EmailSuggestion.builder().emailType(EmailType.EXPENSE).build();
      when(emailParserService.suggestFromEmail(any(), any(), any())).thenReturn(suggestion);

      doAnswer(
              inv -> {
                @SuppressWarnings("unchecked")
                Callable<EmailSuggestion> task = inv.getArgument(2);
                task.call();
                return null;
              })
          .when(parseQueueSupport)
          .run(any(), any(), any());

      service.processReceipt(5L, "item-2");

      verify(pdfExtractorService).extractText(new byte[0]);
    }
  }
}
