package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private ChatClient chatClient;

  private EmailParserService service;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    service = new EmailParserService(chatClient, objectMapper);
  }

  @Nested
  class SuggestFromEmail {

    @Test
    void expense_mapsAllFieldsToSuggestion() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":125.50,"description":"Plumber repair",\
          "date":"2025-03-01","category":"REPAIRS","propertyName":"Main St",\
          "payerName":"Bob's Plumbing","keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

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
      stubContent(
          """
          {"emailType":"INCOME","amount":1500.0,\
          "description":"Tenant Name - Rent Payment Mar 2025",\
          "date":"2025-03-01","propertyName":"Main St",\
          "category":null,"payerName":null,"keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.emailType()).isEqualTo(EmailType.INCOME);
      assertThat(result.amount()).isEqualTo(1500.0);
      assertThat(result.category()).isNull();
      assertThat(result.payerName()).isNull();
      assertThat(result.propertyName()).isEqualTo("Main St");
    }

    @Test
    void nullEmailType_defaultsToExpense() {
      stubContent(
          """
          {"emailType":null,"amount":50.0,"description":"Water bill",\
          "date":"2025-03-15","category":"UTILITIES","propertyName":null,\
          "payerName":null,"keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.emailType()).isEqualTo(EmailType.EXPENSE);
    }

    @Test
    void keywords_carriedThroughToSuggestion() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":50.0,"description":"Electric bill",\
          "date":"2025-03-15","category":"UTILITIES","propertyName":"456 Oak Ave",\
          "payerName":"National Grid","keywords":["acc-7891","inv-001"],\
          "accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.keywords()).containsExactly("acc-7891", "inv-001");
    }

    @Test
    void accountNumbers_carriedThroughToSuggestion() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":157.64,\
          "description":"ACWD - Water Service Jan 2026","date":"2026-01-12",\
          "category":"UTILITIES","propertyName":"Main St",\
          "payerName":"Alameda County Water District","keywords":[],\
          "accountNumbers":["41091091","98647584065711091"]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.accountNumbers()).containsExactly("41091091", "98647584065711091");
    }

    @Test
    void validResult_sourceTypeAndSourceIdAreNull() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":50.0,"description":"Water bill",\
          "date":"2025-03-15","category":"UTILITIES","propertyName":null,\
          "payerName":null,"keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.sourceType()).isNull();
      assertThat(result.sourceId()).isNull();
    }

    @Test
    void emptyResponse_throwsIllegalStateException() {
      when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
          .thenReturn("");

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Email parser returned empty response");
    }

    @Test
    void invalidJson_throwsIllegalStateException() {
      when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
          .thenReturn("not-json");

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Email parser returned invalid JSON");
    }

    @Test
    void clientThrows_propagatesException() {
      when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
          .thenThrow(new RuntimeException("AI service unavailable"));

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("AI service unavailable");
    }

    private void stubContent(String json) {
      when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
          .thenReturn(json);
    }
  }
}
