package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bookie.model.EmailParseResult;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import java.util.List;
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
  class SuggestFromEmail {

    @Test
    void expense_mapsAllFieldsToSuggestion() {
      stubEntity(
          EmailParseResult.builder()
              .emailType(EmailType.EXPENSE)
              .amount(125.50)
              .description("Plumber repair")
              .date("2025-03-01")
              .category("REPAIRS")
              .propertyName("Main St")
              .payerName("Bob's Plumbing")
              .build());

      EmailSuggestion result = service.suggestFromEmail("\1", "\2", "2026-03-17");

      assertThat(result.emailType()).isEqualTo(EmailType.EXPENSE);
      assertThat(result.amount()).isEqualTo(125.50);
      assertThat(result.description()).isEqualTo("Plumber repair");
      assertThat(result.date()).isEqualTo("2025-03-01");
      assertThat(result.category()).isEqualTo("REPAIRS");
      assertThat(result.propertyName()).isEqualTo("Main St");
      assertThat(result.payerName()).isEqualTo("Bob's Plumbing");
    }

    @Test
    void income_mapsFieldsAndNullsCategoryAndPayer() {
      stubEntity(
          EmailParseResult.builder()
              .emailType(EmailType.INCOME)
              .amount(1500.0)
              .description("Tenant Name - Rent Payment Mar 2025")
              .date("2025-03-01")
              .propertyName("Main St")
              .build());

      EmailSuggestion result = service.suggestFromEmail("\1", "\2", "2026-03-17");

      assertThat(result.emailType()).isEqualTo(EmailType.INCOME);
      assertThat(result.amount()).isEqualTo(1500.0);
      assertThat(result.category()).isNull();
      assertThat(result.payerName()).isNull();
      assertThat(result.propertyName()).isEqualTo("Main St");
    }

    @Test
    void nullEmailType_defaultsToExpense() {
      stubEntity(
          EmailParseResult.builder()
              .amount(50.0)
              .description("Water bill")
              .date("2025-03-15")
              .category("UTILITIES")
              .build());

      EmailSuggestion result = service.suggestFromEmail("\1", "\2", "2026-03-17");

      assertThat(result.emailType()).isEqualTo(EmailType.EXPENSE);
    }

    @Test
    void keywords_carriedThroughToSuggestion() {
      stubEntity(
          EmailParseResult.builder()
              .emailType(EmailType.EXPENSE)
              .amount(50.0)
              .description("Electric bill")
              .date("2025-03-15")
              .category("UTILITIES")
              .propertyName("456 Oak Ave")
              .payerName("National Grid")
              .keywords(List.of("acc-7891", "inv-001"))
              .build());

      EmailSuggestion result = service.suggestFromEmail("\1", "\2", "2026-03-17");

      assertThat(result.keywords()).containsExactly("acc-7891", "inv-001");
    }

    @Test
    void accountNumbers_carriedThroughToSuggestion() {
      stubEntity(
          EmailParseResult.builder()
              .emailType(EmailType.EXPENSE)
              .amount(157.64)
              .description("ACWD - Water Service Jan 2026")
              .date("2026-01-12")
              .category("UTILITIES")
              .propertyName("Main St")
              .payerName("Alameda County Water District")
              .accountNumbers(List.of("41091091", "98647584065711091"))
              .build());

      EmailSuggestion result = service.suggestFromEmail("\1", "\2", "2026-03-17");

      assertThat(result.accountNumbers()).containsExactly("41091091", "98647584065711091");
    }

    @Test
    void validResult_sourceTypeAndSourceIdAreNull() {
      stubEntity(
          EmailParseResult.builder()
              .amount(50.0)
              .description("Water bill")
              .date("2025-03-15")
              .category("UTILITIES")
              .build());

      EmailSuggestion result = service.suggestFromEmail("\1", "\2", "2026-03-17");

      assertThat(result.sourceType()).isNull();
      assertThat(result.sourceId()).isNull();
    }

    @Test
    void nullResult_throwsIllegalStateException() {
      stubEntity(null);

      assertThatThrownBy(() -> service.suggestFromEmail("\1", "\2", "2026-03-17"))
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
              .entity(EmailParseResult.class))
          .thenThrow(new RuntimeException("AI service unavailable"));

      assertThatThrownBy(() -> service.suggestFromEmail("\1", "\2", "2026-03-17"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("AI service unavailable");
    }

    private void stubEntity(EmailParseResult result) {
      when(builder
              .build()
              .prompt()
              .system(anyString())
              .user(anyString())
              .tools(any())
              .call()
              .entity(EmailParseResult.class))
          .thenReturn(result);
    }
  }
}
