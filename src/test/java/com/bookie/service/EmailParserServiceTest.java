package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.Property;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

@ExtendWith(MockitoExtension.class)
class EmailParserServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient chatClient;

  @Mock private PropertyRepository propertyRepository;
  @Mock private PayerRepository payerRepository;
  @Mock private EmailParserTools tools;

  private EmailParserService service;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    service =
        new EmailParserService(
            chatClient, objectMapper, propertyRepository, payerRepository, tools);
    // Default: all resolution lookups return empty so field-mapping tests focus on LLM output.
    // lenient() suppresses UnnecessaryStubbingException for tests that throw before resolution
    // runs.
    lenient().when(propertyRepository.findAll()).thenReturn(List.of());
    lenient().when(payerRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    lenient().when(tools.findPropertyByAccount(anyList())).thenReturn(List.of());
    lenient().when(tools.getPropertyHints(any(), anyList())).thenReturn(List.of());
    lenient().when(tools.findPayerByAccountNumber(anyList())).thenReturn(List.of());
    lenient().when(tools.findPayerByAlias(anyList())).thenReturn(List.of());
    lenient().when(tools.getPayerHints(anyList())).thenReturn(List.of());
    lenient().when(tools.getCategoryHints(anyList())).thenReturn(List.of());
    lenient().when(tools.getCategoryForPayer(anyList())).thenReturn(List.of());
  }

  @Nested
  class SuggestFromEmail {

    @Test
    void expense_mapsRawFieldsToSuggestion() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":125.50,"description":"Plumber repair",\
          "date":"2025-03-01","category":"REPAIRS","propertyName":"",\
          "payerName":"Bob's Plumbing","keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.emailType()).isEqualTo(EmailType.EXPENSE);
      assertThat(result.amount()).isEqualTo(125.50);
      assertThat(result.description()).isEqualTo("Plumber repair");
      assertThat(result.date()).isEqualTo("2025-03-01");
      assertThat(result.category()).isEqualTo("REPAIRS");
      assertThat(result.payerName()).isEqualTo("Bob's Plumbing");
    }

    @Test
    void income_nullsCategoryAndResolvesPayerAsRawTenantName() {
      stubContent(
          """
          {"emailType":"INCOME","amount":1500.0,\
          "description":"Tenant Name - Rent Payment Mar 2025",\
          "date":"2025-03-01","propertyName":"",\
          "category":null,"payerName":"Jane Smith","keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.emailType()).isEqualTo(EmailType.INCOME);
      assertThat(result.amount()).isEqualTo(1500.0);
      assertThat(result.category()).isNull();
      // For INCOME the raw tenant name is used without DB lookup.
      assertThat(result.payerName()).isEqualTo("Jane Smith");
    }

    @Test
    void nullEmailType_defaultsToExpense() {
      stubContent(
          """
          {"emailType":null,"amount":50.0,"description":"Water bill",\
          "date":"2025-03-15","category":"UTILITIES","propertyName":"",\
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
          "date":"2025-03-15","category":"UTILITIES","propertyName":"",\
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
          "category":"UTILITIES","propertyName":"",\
          "payerName":"Alameda County Water District","keywords":[],\
          "accountNumbers":["41091091","98647584065711091"]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.accountNumbers()).containsExactly("41091091", "98647584065711091");
    }

    @Test
    void unrecognizedCategory_isDiscarded() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":250.0,"description":"HOA Assessment Mar 2026",\
          "date":"2026-03-01","category":"HOA","propertyName":"",\
          "payerName":"Bridgepointe HOA","keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-01");

      assertThat(result.category()).isNull();
    }

    @Test
    void validCategory_isPreserved() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":250.0,"description":"HOA Assessment Mar 2026",\
          "date":"2026-03-01","category":"MANAGEMENT_FEES","propertyName":"",\
          "payerName":"Bridgepointe HOA","keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-01");

      assertThat(result.category()).isEqualTo("MANAGEMENT_FEES");
    }

    @Test
    void emptyResponse_throwsIllegalStateException() {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn("");

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Email parser returned empty response");
    }

    @Test
    void invalidJson_throwsIllegalStateException() {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn("not-json");

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Email parser returned invalid JSON");
    }

    @Test
    void clientThrows_propagatesException() {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenThrow(new RuntimeException("AI service unavailable"));

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("AI service unavailable");
    }

    @Test
    void longBody_isTruncatedBeforeSendingToLlm() {
      String longBody = "x".repeat(7_000);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
      when(chatClient
              .prompt()
              .system(any(String.class))
              .messages(messagesCaptor.capture())
              .call()
              .content())
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2025-03-01",\
              "category":"SUPPLIES","propertyName":"","payerName":"Vendor",\
              "keywords":[],"accountNumbers":[]}
              """);

      service.suggestFromEmail("subj", longBody, "2026-03-17");

      List<Message> capturedMessages = messagesCaptor.getValue();
      String userMessageText = capturedMessages.get(0).getText();
      // The raw body is 7000 chars; after truncation the body portion is exactly 6000 chars
      // plus the "…[truncated]" suffix, so the full user message must be well under 7000 body
      // chars.
      assertThat(userMessageText).contains("…[truncated]");
      assertThat(userMessageText).doesNotContain("x".repeat(6_001));
    }

    private void stubContent(String json) {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn(json);
    }
  }

  @Nested
  class ResolveProperty {

    @Test
    void byAccountNumber_returnsMatchedProperty() {
      stubExpenseJson(
          """
          "accountNumbers":["41091091"],"keywords":[],"payerName":"ACWD"
          """);
      when(tools.findPropertyByAccount(List.of("41091091"))).thenReturn(List.of("Wild Indigo"));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.propertyName()).isEqualTo("Wild Indigo");
    }

    @Test
    void byHistoryHints_returnsTopHint() {
      stubExpenseJson(
          """
          "accountNumbers":[],"keywords":["inv-001"],"payerName":"Bob's Plumbing"
          """);
      when(tools.getPropertyHints(anyString(), anyList()))
          .thenReturn(List.of("Bob's Plumbing → Wild Indigo (4 times)"));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.propertyName()).isEqualTo("Wild Indigo");
    }

    @Test
    void byAddressMatch_standardFormat() {
      stubExpenseJson(
          """
          "accountNumbers":[],"keywords":[],"payerName":"Amazon.com"
          """);
      String body = "Ship to\n41784 Wild Indigo Ter\nFremont, CA 94538";
      Property p =
          Property.builder()
              .id(1L)
              .name("Wild Indigo")
              .address("41784 Wild Indigo Ter, Fremont, CA 94538")
              .build();
      when(propertyRepository.findAll()).thenReturn(List.of(p));

      assertThat(service.suggestFromEmail("subj", body, "2026-03-17").propertyName())
          .isEqualTo("Wild Indigo");
    }

    @Test
    void byAddressMatch_periodAndCitySuffix_stillMatches() {
      // Stored address has no comma between street and city: "41784 Wild Indigo Ter. Fremont"
      // Old comma-split logic produced streetPart="41784 Wild Indigo Ter. Fremont" which didn't
      // match "41784 WILD INDIGO TER" in the email. Street-key matching uses "41784 Wild" only.
      stubExpenseJson(
          """
          "accountNumbers":[],"keywords":[],"payerName":"Amazon.com"
          """);
      String body = "Ship to\n41784 WILD INDIGO TER\nFREMONT, CA 94538-3284";
      Property p =
          Property.builder()
              .id(1L)
              .name("Wild Indigo")
              .address("41784 Wild Indigo Ter. Fremont, CA 94538")
              .build();
      when(propertyRepository.findAll()).thenReturn(List.of(p));

      assertThat(service.suggestFromEmail("subj", body, "2026-03-17").propertyName())
          .isEqualTo("Wild Indigo");
    }

    @Test
    void singlePropertyFallback_usedWhenAllLookupsEmpty() {
      stubExpenseJson(
          """
          "accountNumbers":[],"keywords":[],"payerName":"Unknown"
          """);
      Property p =
          Property.builder().id(1L).name("Only Property").address("100 Main St, City, CA").build();
      when(propertyRepository.findAll()).thenReturn(List.of(p));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.propertyName()).isEqualTo("Only Property");
    }

    @Test
    void multipleProperties_noMatch_returnsNull() {
      stubExpenseJson(
          """
          "accountNumbers":[],"keywords":[],"payerName":"Unknown"
          """);
      Property p1 =
          Property.builder().id(1L).name("Property A").address("100 Oak St, City, CA").build();
      Property p2 =
          Property.builder().id(2L).name("Property B").address("200 Elm St, City, CA").build();
      when(propertyRepository.findAll()).thenReturn(List.of(p1, p2));

      EmailSuggestion result = service.suggestFromEmail("subj", "unrelated body", "2026-03-17");

      assertThat(result.propertyName()).isNull();
    }

    private void stubExpenseJson(String fields) {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test",\
              "date":"2026-03-01","category":"SUPPLIES","propertyName":"",\
              %s}
              """
                  .formatted(fields));
    }
  }

  @Nested
  class ResolvePayer {

    @Test
    void byAccountNumber_returnsCanonicalPayerName() {
      stubExpenseJson("41091091");
      when(tools.findPayerByAccountNumber(List.of("41091091")))
          .thenReturn(List.of("Alameda County Water District"));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.payerName()).isEqualTo("Alameda County Water District");
    }

    @Test
    void byExactNameMatch_returnsStoredCanonicalName() {
      stubExpenseJsonNoAccount("amazon.com");
      Payer stored = Payer.builder().id(1L).name("Amazon.com").type(PayerType.COMPANY).build();
      when(payerRepository.findByNameIgnoreCase("amazon.com")).thenReturn(Optional.of(stored));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.payerName()).isEqualTo("Amazon.com");
    }

    @Test
    void byAlias_returnsCanonicalPayerName() {
      stubExpenseJsonNoAccount("ACWD");
      when(tools.findPayerByAlias(List.of("ACWD")))
          .thenReturn(List.of("Alameda County Water District"));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.payerName()).isEqualTo("Alameda County Water District");
    }

    @Test
    void byKeywordHints_returnsTopHint() {
      stubExpenseJsonWithKeywords("UnknownVendor", "inv-001");
      when(tools.getPayerHints(List.of("inv-001")))
          .thenReturn(List.of("Keyword 'inv-001' → Bob's Plumbing (3 times)"));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.payerName()).isEqualTo("Bob's Plumbing");
    }

    @Test
    void rawFallback_usedWhenAllLookupsEmpty() {
      stubExpenseJsonNoAccount("Amazon.com");

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.payerName()).isEqualTo("Amazon.com");
    }

    @Test
    void nullPayerName_returnsNull() {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"OTHER","propertyName":"","payerName":null,"keywords":[],"accountNumbers":[]}
              """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.payerName()).isNull();
    }

    private void stubExpenseJson(String accountNumber) {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"UTILITIES","propertyName":"","payerName":"ACWD",\
              "keywords":[],"accountNumbers":["%s"]}
              """
                  .formatted(accountNumber));
    }

    private void stubExpenseJsonNoAccount(String payerName) {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"SUPPLIES","propertyName":"","payerName":"%s",\
              "keywords":[],"accountNumbers":[]}
              """
                  .formatted(payerName));
    }

    private void stubExpenseJsonWithKeywords(String payerName, String keyword) {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"SUPPLIES","propertyName":"","payerName":"%s",\
              "keywords":["%s"],"accountNumbers":[]}
              """
                  .formatted(payerName, keyword));
    }
  }

  @Nested
  class ResolveCategory {

    @Test
    void byKeywordHints_overridesLlmGuess() {
      stubExpense("SUPPLIES", "inv-001", "Bob");
      when(tools.getCategoryHints(List.of("inv-001")))
          .thenReturn(List.of("Keyword 'inv-001' → REPAIRS (5 times)"));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.category()).isEqualTo("REPAIRS");
    }

    @Test
    void byPayerHints_overridesLlmGuess() {
      stubExpense("SUPPLIES", "", "Bridgepointe HOA");
      when(tools.getCategoryForPayer(List.of("Bridgepointe HOA")))
          .thenReturn(List.of("MANAGEMENT_FEES (7 times)"));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.category()).isEqualTo("MANAGEMENT_FEES");
    }

    @Test
    void llmFallback_usedWhenNoHistory() {
      stubExpense("UTILITIES", "", "PG&E");

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.category()).isEqualTo("UTILITIES");
    }

    @Test
    void income_categoryIsAlwaysNull() {
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn(
              """
              {"emailType":"INCOME","amount":1500.0,"description":"Rent","date":"2026-03-01",\
              "category":"","propertyName":"","payerName":"Jane Smith",\
              "keywords":[],"accountNumbers":[]}
              """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.category()).isNull();
    }

    private void stubExpense(String category, String keyword, String payerName) {
      String kw = keyword.isEmpty() ? "[]" : "[\"" + keyword + "\"]";
      when(chatClient.prompt().system(any(String.class)).messages(anyList()).call().content())
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"%s","propertyName":"","payerName":"%s",\
              "keywords":%s,"accountNumbers":[]}
              """
                  .formatted(category, payerName, kw));
    }
  }
}
