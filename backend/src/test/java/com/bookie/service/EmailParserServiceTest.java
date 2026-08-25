package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HistoryHint;
import com.bookie.model.HouseholdMember;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.Property;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailParserServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LlmGateway llmGateway;

  @Mock private PropertyRepository propertyRepository;
  @Mock private PayerRepository payerRepository;
  @Mock private EmailParserTools tools;
  @Mock private EmailParserToolDefinitions toolDefinitions;
  @Mock private SuggestionValidator suggestionValidator;
  @Mock private AutomatedIntakeClassificationService classificationService;

  private EmailParserService service;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    service =
        new EmailParserService(
            llmGateway,
            objectMapper,
            propertyRepository,
            payerRepository,
            tools,
            toolDefinitions,
            suggestionValidator,
            classificationService);
    ReflectionTestUtils.setField(service, "chatModel", "test-model");
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
    lenient().when(toolDefinitions.createTools()).thenReturn(List.of());
    lenient()
        .when(
            classificationService.resolve(
                any(TransactionDirection.class),
                nullable(Long.class),
                nullable(String.class),
                anyList(),
                nullable(String.class)))
        .thenAnswer(
            invocation -> {
              TransactionDirection direction = invocation.getArgument(0);
              FinancialActivity activity =
                  FinancialActivity.builder()
                      .id(99L)
                      .name("Needs classification")
                      .taxTreatment(TaxTreatment.NONE)
                      .owner(
                          HouseholdMember.builder()
                              .id(1L)
                              .name("Synthetic Household Member")
                              .active(true)
                              .build())
                      .active(true)
                      .systemKey(FinancialActivityService.NEEDS_CLASSIFICATION_KEY)
                      .build();
              FinancialCategory category =
                  FinancialCategory.builder()
                      .id(direction == TransactionDirection.INCOME ? 101L : 102L)
                      .key(
                          direction == TransactionDirection.INCOME
                              ? "OTHER_INCOME"
                              : "OTHER_EXPENSE")
                      .direction(direction)
                      .taxTreatment(TaxTreatment.NONE)
                      .active(true)
                      .build();
              return new AutomatedIntakeClassificationService.Resolution(activity, category, true);
            });
    lenient()
        .when(suggestionValidator.validate(any(EmailSuggestion.class), any(), anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));
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
      assertThat(result.category()).isEqualTo("OTHER_EXPENSE");
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
      assertThat(result.category()).isEqualTo("OTHER_INCOME");
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

      assertThat(result.category()).isEqualTo("OTHER_EXPENSE");
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

      assertThat(result.category()).isEqualTo("OTHER_EXPENSE");
    }

    @Test
    void emptyResponse_throwsIllegalStateException() {
      when(llmGateway.completeText(any(LlmTextRequest.class))).thenReturn("");

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Email parser returned empty response");
    }

    @Test
    void invalidJson_throwsIllegalStateException() {
      when(llmGateway.completeText(any(LlmTextRequest.class))).thenReturn("not-json");

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Email parser returned invalid JSON");
    }

    @Test
    void clientThrows_propagatesException() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenThrow(new RuntimeException("AI service unavailable"));

      assertThatThrownBy(() -> service.suggestFromEmail("subj", "body", "2026-03-17"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("AI service unavailable");
    }

    @Test
    void longBody_isTruncatedBeforeSendingToLlm() {
      String longBody = "x".repeat(7_000);

      ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
      when(llmGateway.completeText(requestCaptor.capture()))
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2025-03-01",\
              "category":"SUPPLIES","propertyName":"","payerName":"Vendor",\
              "keywords":[],"accountNumbers":[]}
              """);

      service.suggestFromEmail("subj", longBody, "2026-03-17");

      String userMessageText = requestCaptor.getValue().userPrompt();
      // The raw body is 7000 chars; after truncation the body portion is exactly 6000 chars
      // plus the "…[truncated]" suffix, so the full user message must be well under 7000 body
      // chars.
      assertThat(userMessageText).contains("…[truncated]");
      assertThat(userMessageText).doesNotContain("x".repeat(6_001));
    }

    @Test
    void blankDate_fallsBackToReceivedDate() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"",\
          "category":"SUPPLIES","propertyName":"","payerName":"Vendor",\
          "keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.date()).isEqualTo("2026-03-17");
    }

    @Test
    void blankDateAndNoReceivedDate_returnsNullDate() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"",\
          "category":"SUPPLIES","propertyName":"","payerName":"Vendor",\
          "keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", null);

      assertThat(result.date()).isNull();
    }

    @Test
    void invalidDate_fallsBackToReceivedDate() {
      stubContent(
          """
          {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"not-a-date",\
          "category":"SUPPLIES","propertyName":"","payerName":"Vendor",\
          "keywords":[],"accountNumbers":[]}
          """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.date()).isEqualTo("2026-03-17");
    }

    @Test
    void emailParserToolsEnabled_addsToolsToLlmRequest() {
      ReflectionTestUtils.setField(service, "emailParserToolsEnabled", true);
      LlmToolDefinition tool =
          LlmToolDefinition.builder()
              .name("findPayerByAccountNumber")
              .description("Use this when testing tool wiring.")
              .parameters(java.util.Map.of("type", "object"))
              .handler(args -> java.util.Map.of())
              .build();
      when(toolDefinitions.createTools()).thenReturn(List.of(tool));

      ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
      when(llmGateway.completeText(requestCaptor.capture()))
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2025-03-01",\
              "category":"SUPPLIES","propertyName":"","payerName":"Vendor",\
              "keywords":[],"accountNumbers":[]}
              """);

      service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(requestCaptor.getValue().tools()).hasSize(1);
      assertThat(requestCaptor.getValue().tools().get(0).name())
          .isEqualTo("findPayerByAccountNumber");
    }

    @Test
    void promptExtractsNeutralFactsAndUsesDepositedPaycheckAmount() {
      ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
      when(llmGateway.completeText(requestCaptor.capture()))
          .thenReturn(
              """
              {"direction":"INCOME","amount":2418.73,\
              "description":"Net paycheck deposited after deductions","date":"2026-08-15",\
              "counterpartyName":"North Valley Unified School District",\
              "keywords":["pay-demo-001"],"accountNumbers":[]}
              """);

      EmailSuggestion result =
          service.suggestFromEmail("Synthetic pay advice", "Body", "2026-08-15", 42L);

      assertThat(result.amount()).isEqualTo(2418.73);
      assertThat(result.emailType()).isEqualTo(EmailType.INCOME);
      assertThat(requestCaptor.getValue().systemPrompt())
          .contains("extract only the deposited/net amount shown")
          .contains("Do not output an activity, owner, property, category, or tax treatment")
          .doesNotContain("\"category\":")
          .doesNotContain("\"propertyName\":");
      verify(classificationService)
          .resolve(
              TransactionDirection.INCOME,
              42L,
              null,
              List.of("pay-demo-001"),
              "North Valley Unified School District");
      verify(tools, org.mockito.Mockito.never()).getPropertyHints(any(), any());
    }

    private void stubContent(String json) {
      when(llmGateway.completeText(any(LlmTextRequest.class))).thenReturn(json);
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
          .thenReturn(List.of(new HistoryHint("Wild Indigo", 4, "payer-history")));

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
      when(llmGateway.completeText(any(LlmTextRequest.class)))
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
          .thenReturn(List.of(new HistoryHint("Bob's Plumbing", 3, "keyword-history")));

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
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"OTHER","propertyName":"","payerName":null,"keywords":[],"accountNumbers":[]}
              """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.payerName()).isNull();
    }

    private void stubExpenseJson(String accountNumber) {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"UTILITIES","propertyName":"","payerName":"ACWD",\
              "keywords":[],"accountNumbers":["%s"]}
              """
                  .formatted(accountNumber));
    }

    private void stubExpenseJsonNoAccount(String payerName) {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"SUPPLIES","propertyName":"","payerName":"%s",\
              "keywords":[],"accountNumbers":[]}
              """
                  .formatted(payerName));
    }

    private void stubExpenseJsonWithKeywords(String payerName, String keyword) {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
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
    void deterministicResolverOverridesModelGuess() {
      stubExpense("SUPPLIES", "inv-001", "Bob");
      when(classificationService.resolve(
              TransactionDirection.EXPENSE, null, null, List.of("inv-001"), "Bob"))
          .thenReturn(
              new AutomatedIntakeClassificationService.Resolution(
                  classificationActivity(),
                  classificationCategory("REPAIRS", TransactionDirection.EXPENSE),
                  false));

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.category()).isEqualTo("REPAIRS");
      assertThat(result.classificationAmbiguous()).isFalse();
    }

    @Test
    void modelCategoryIsIgnoredWhenResolverUsesFallback() {
      stubExpense("UTILITIES", "", "Synthetic Utility");

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.category()).isEqualTo("OTHER_EXPENSE");
      assertThat(result.classificationAmbiguous()).isTrue();
    }

    @Test
    void incomeUsesResolvedIncomeCategory() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"emailType":"INCOME","amount":1500.0,"description":"Rent","date":"2026-03-01",\
              "category":"","propertyName":"","payerName":"Jane Smith",\
              "keywords":[],"accountNumbers":[]}
              """);

      EmailSuggestion result = service.suggestFromEmail("subj", "body", "2026-03-17");

      assertThat(result.category()).isEqualTo("OTHER_INCOME");
    }

    private void stubExpense(String category, String keyword, String payerName) {
      String kw = keyword.isEmpty() ? "[]" : "[\"" + keyword + "\"]";
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"emailType":"EXPENSE","amount":50.0,"description":"Test","date":"2026-03-01",\
              "category":"%s","propertyName":"","payerName":"%s",\
              "keywords":%s,"accountNumbers":[]}
              """
                  .formatted(category, payerName, kw));
    }

    private FinancialActivity classificationActivity() {
      return FinancialActivity.builder()
          .id(88L)
          .name("Synthetic activity")
          .taxTreatment(TaxTreatment.SCHEDULE_E)
          .owner(
              HouseholdMember.builder()
                  .id(1L)
                  .name("Synthetic Household Member")
                  .active(true)
                  .build())
          .active(true)
          .build();
    }

    private FinancialCategory classificationCategory(String key, TransactionDirection direction) {
      return FinancialCategory.builder()
          .id(89L)
          .key(key)
          .direction(direction)
          .taxTreatment(TaxTreatment.SCHEDULE_E)
          .active(true)
          .build();
    }
  }
}
