package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bookie.model.ExpenseSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class EmailParserServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient.Builder builder;

  @Mock private EmailParserTools tools;

  private EmailParserService service;

  @BeforeEach
  void setUp() {
    service = new EmailParserService(builder, tools);
  }

  @Nested
  class SuggestExpenseFromEmail {

    @Test
    void validResult_mapsAllFieldsToSuggestion() {
      stubEntity(
          new EmailParserService.EmailParseResult(
              125.50, "Plumber repair", "2025-03-01", "REPAIRS", "Main St", "Bob's Plumbing"));

      ExpenseSuggestion result = service.suggestExpenseFromEmail("Invoice #123", "body text");

      assertThat(result.amount()).isEqualTo(125.50);
      assertThat(result.description()).isEqualTo("Plumber repair");
      assertThat(result.date()).isEqualTo("2025-03-01");
      assertThat(result.category()).isEqualTo("REPAIRS");
      assertThat(result.propertyName()).isEqualTo("Main St");
      assertThat(result.payerName()).isEqualTo("Bob's Plumbing");
    }

    @Test
    void validResult_sourceTypeAndSourceIdAreNull() {
      stubEntity(
          new EmailParserService.EmailParseResult(
              50.0, "Water bill", "2025-03-15", "UTILITIES", null, null));

      ExpenseSuggestion result = service.suggestExpenseFromEmail("Utility Bill", "body text");

      assertThat(result.sourceType()).isNull();
      assertThat(result.sourceId()).isNull();
    }

    @Test
    void nullResult_throwsIllegalStateException() {
      stubEntity(null);

      assertThatThrownBy(() -> service.suggestExpenseFromEmail("Invoice", "body"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Email parser returned null result");
    }

    @Test
    void clientThrows_propagatesException() {
      when(builder
              .build()
              .prompt()
              .system(anyString())
              .user(anyString())
              .tools(any())
              .call()
              .entity(EmailParserService.EmailParseResult.class))
          .thenThrow(new RuntimeException("AI service unavailable"));

      assertThatThrownBy(() -> service.suggestExpenseFromEmail("Invoice", "body"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("AI service unavailable");
    }

    private void stubEntity(EmailParserService.EmailParseResult result) {
      when(builder
              .build()
              .prompt()
              .system(anyString())
              .user(anyString())
              .tools(any())
              .call()
              .entity(EmailParserService.EmailParseResult.class))
          .thenReturn(result);
    }
  }
}
