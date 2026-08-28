package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.bookie.integrations.llm.LlmGateway;
import com.bookie.integrations.llm.LlmTextRequest;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TransactionDirection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

  @Mock private LlmGateway llmGateway;
  @Mock private CounterpartyCatalog counterpartyCatalog;
  @Mock private AutomatedIntakeClassificationService classificationService;

  private AgentService service;
  private FinancialActivity needsClassification;
  private FinancialCategory otherExpense;

  @BeforeEach
  void setUp() {
    service =
        new AgentService(
            llmGateway, new ObjectMapper(), counterpartyCatalog, classificationService);
    ReflectionTestUtils.setField(service, "agentModel", "test-agent-model");

    needsClassification =
        activity(
            90L,
            "Needs classification",
            TaxTreatment.NONE,
            ActivityCatalog.NEEDS_CLASSIFICATION_KEY,
            null);
    otherExpense = category(91L, "OTHER_EXPENSE", TransactionDirection.EXPENSE);
    lenient()
        .when(
            classificationService.resolveFromFreeform(
                any(TransactionDirection.class),
                nullable(String.class),
                nullable(String.class),
                any(LocalDate.class)))
        .thenReturn(
            new AutomatedIntakeClassificationService.Resolution(
                needsClassification, otherExpense, true));
    lenient().when(counterpartyCatalog.findAll()).thenReturn(List.of());
  }

  @Nested
  class Proposals {

    @Test
    void proposesIncomeAgainstResolvedActivityWithoutSavingIt() {
      FinancialActivity tutoring = activity(10L, "Tutoring", TaxTreatment.SCHEDULE_C, null, null);
      FinancialCategory tutoringIncome = category(11L, "OTHER_INCOME", TransactionDirection.INCOME);
      stubClassification(
          TransactionDirection.INCOME,
          new AutomatedIntakeClassificationService.Resolution(tutoring, tutoringIncome, false));
      stubExtraction(
          """
          {"direction":"INCOME","amount":320.00,"description":"Tutoring sessions",\
          "date":"2026-08-20","counterpartyName":"Demo Learner",\
          "keywords":["tutor-demo-001"],"accountNumbers":[],\
          "needsMoreInfo":false,"followUpQuestion":""}
          """);

      AgentService.AgentResponse response =
          service.processMessage("Demo Learner paid $320 for tutoring");

      assertThat(response.proposedTransaction()).isNotNull();
      assertThat(response.proposedExpense()).isNull();
      assertThat(response.proposedTransaction().direction()).isEqualTo(TransactionDirection.INCOME);
      assertThat(response.proposedTransaction().activityId()).isEqualTo(10L);
      assertThat(response.proposedTransaction().ownerName())
          .isEqualTo("Synthetic Household Member");
      assertThat(response.proposedTransaction().categoryKey()).isEqualTo("OTHER_INCOME");
      assertThat(response.proposedTransaction().counterpartyName()).isEqualTo("Demo Learner");
      assertThat(response.message()).contains("review the details");
    }

    @Test
    void legacyExpenseEndpointReturnsEditableCompatibilityProposal() {
      FinancialActivity teaching = activity(20L, "Teaching", TaxTreatment.W2, null, null);
      FinancialCategory educatorExpenses =
          category(21L, "EDUCATOR_EXPENSES", TransactionDirection.EXPENSE);
      stubClassification(
          TransactionDirection.EXPENSE,
          new AutomatedIntakeClassificationService.Resolution(teaching, educatorExpenses, false));
      stubExtraction(
          """
          {"direction":"INCOME","amount":78.45,"description":"Classroom supplies",\
          "date":"2026-08-22","counterpartyName":"Demo Classroom Supply",\
          "keywords":["edu-demo-001"],"accountNumbers":[],\
          "needsMoreInfo":false,"followUpQuestion":""}
          """);

      AgentService.AgentResponse response =
          service.processExpenseMessage("I spent $78.45 on classroom supplies");

      assertThat(response.proposedTransaction().direction())
          .isEqualTo(TransactionDirection.EXPENSE);
      assertThat(response.proposedExpense()).isSameAs(response.proposedTransaction());
      assertThat(response.proposedExpense().activityName()).isEqualTo("Teaching");
      assertThat(response.proposedExpense().categoryKey()).isEqualTo("EDUCATOR_EXPENSES");
      assertThat(response.proposedExpense().amount()).isEqualByComparingTo("78.45");
    }

    @Test
    void preservesDepositedPaycheckAmountWithoutReconstructingGrossPay() {
      FinancialActivity teaching = activity(30L, "Teaching", TaxTreatment.W2, null, null);
      FinancialCategory wages = category(31L, "WAGES", TransactionDirection.INCOME);
      stubClassification(
          TransactionDirection.INCOME,
          new AutomatedIntakeClassificationService.Resolution(teaching, wages, false));
      stubExtraction(
          """
          {"direction":"INCOME","amount":2418.73,\
          "description":"Net paycheck deposited after deductions",\
          "date":"2026-08-15","counterpartyName":"Synthetic District",\
          "keywords":["pay-demo-001"],"accountNumbers":[],\
          "needsMoreInfo":false,"followUpQuestion":""}
          """);

      AgentService.AgentResponse response =
          service.processMessage("Net deposited amount was $2,418.73");

      assertThat(response.proposedTransaction().amount()).isEqualByComparingTo("2418.73");
      assertThat(response.proposedTransaction().description())
          .isEqualTo("Net paycheck deposited after deductions");
    }

    @Test
    void derivesPropertyFromResolvedRentalActivity() {
      Property property =
          Property.builder()
              .id(3L)
              .name("Synthetic Rental")
              .address("100 Demo Street")
              .type(PropertyType.SINGLE_FAMILY)
              .build();
      FinancialActivity rental =
          activity(40L, "Synthetic Rental", TaxTreatment.SCHEDULE_E, null, property);
      FinancialCategory repairs = category(41L, "REPAIRS", TransactionDirection.EXPENSE);
      stubClassification(
          TransactionDirection.EXPENSE,
          new AutomatedIntakeClassificationService.Resolution(rental, repairs, false));
      stubExtraction(
          """
          {"direction":"EXPENSE","amount":250,"description":"Plumbing repair",\
          "date":"2026-01-05","counterpartyName":"Demo Plumbing",\
          "keywords":[],"accountNumbers":[],"needsMoreInfo":false,"followUpQuestion":""}
          """);

      AgentService.AgentResponse response = service.processMessage("Synthetic rental plumbing");

      assertThat(response.proposedTransaction().propertyId()).isEqualTo(3L);
      assertThat(response.proposedTransaction().propertyName()).isEqualTo("Synthetic Rental");
    }

    @Test
    void resolvesCounterpartyIdFromKnownAlias() {
      Counterparty supplier =
          Counterparty.builder()
              .id(8L)
              .name("Demo Classroom Supply Incorporated")
              .type(CounterpartyType.COMPANY)
              .aliases(List.of("Demo Classroom Supply"))
              .build();
      when(counterpartyCatalog.findAll()).thenReturn(List.of(supplier));
      stubExtraction(
          """
          {"direction":"EXPENSE","amount":45,"description":"Supplies",\
          "date":"2026-01-01","counterpartyName":"Demo Classroom Supply",\
          "keywords":[],"accountNumbers":[],"needsMoreInfo":false,"followUpQuestion":""}
          """);

      AgentService.AgentResponse response =
          service.processMessage("Supplies from Demo Classroom Supply");

      assertThat(response.proposedTransaction().payerId()).isEqualTo(8L);
    }

    @Test
    void leavesAmbiguousClassificationVisibleForReview() {
      stubExtraction(
          """
          {"direction":"EXPENSE","amount":64.20,"description":"Unclear purchase",\
          "date":"2026-08-25","counterpartyName":"Synthetic Marketplace",\
          "keywords":["amb-demo-001"],"accountNumbers":[],\
          "needsMoreInfo":false,"followUpQuestion":""}
          """);

      AgentService.AgentResponse response = service.processMessage("A $64.20 purchase");

      assertThat(response.proposedTransaction().activityId()).isEqualTo(90L);
      assertThat(response.proposedTransaction().classificationAmbiguous()).isTrue();
      assertThat(response.proposedTransaction().categoryKey()).isEqualTo("OTHER_EXPENSE");
    }
  }

  @Nested
  class ClarificationAndPrompt {

    @Test
    void asksFollowUpQuestionWhenAmountIsMissing() {
      stubExtraction(
          """
          {"direction":"EXPENSE","amount":0,"description":"","date":"2026-01-01",\
          "counterpartyName":"","keywords":[],"accountNumbers":[],\
          "needsMoreInfo":true,"followUpQuestion":"How much did you pay?"}
          """);

      AgentService.AgentResponse response = service.processMessage("I paid for supplies");

      assertThat(response.message()).isEqualTo("How much did you pay?");
      assertThat(response.proposedTransaction()).isNull();
      assertThat(response.proposedExpense()).isNull();
    }

    @Test
    void usesDirectionSpecificDefaultQuestionForZeroAmount() {
      stubExtraction(
          """
          {"direction":"INCOME","amount":0,"description":"Payment","date":"",\
          "counterpartyName":"","keywords":[],"accountNumbers":[],\
          "needsMoreInfo":false,"followUpQuestion":""}
          """);

      AgentService.AgentResponse response = service.processMessage("I got paid");

      assertThat(response.message()).isEqualTo("What was the dollar amount for this income?");
      assertThat(response.proposedTransaction()).isNull();
    }

    @Test
    void fallsBackToTodayWhenDateCannotBeParsed() {
      stubExtraction(
          """
          {"direction":"EXPENSE","amount":40,"description":"Supplies",\
          "date":"not-a-date","counterpartyName":"","keywords":[],"accountNumbers":[],\
          "needsMoreInfo":false,"followUpQuestion":""}
          """);

      AgentService.AgentResponse response = service.processMessage("Supplies today");

      assertThat(response.proposedTransaction().date()).isEqualTo(LocalDate.now());
    }

    @Test
    void returnsClarifyingMessageWhenModelResponseIsInvalid() {
      when(llmGateway.completeText(any(LlmTextRequest.class))).thenReturn("not json");

      AgentService.AgentResponse response = service.processMessage("Hello");

      assertThat(response.proposedTransaction()).isNull();
      assertThat(response.message()).contains("couldn't understand");
    }

    @Test
    void promptRequestsNeutralExtractionAndIncludesUserMessage() {
      ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
      when(llmGateway.completeText(requestCaptor.capture()))
          .thenReturn(
              """
              {"direction":"EXPENSE","amount":0,"description":"","date":"",\
              "counterpartyName":"","keywords":[],"accountNumbers":[],\
              "needsMoreInfo":true,"followUpQuestion":"?"}
              """);

      service.processMessage("Record my synthetic purchase");

      LlmTextRequest captured = requestCaptor.getValue();
      assertThat(captured.userPrompt()).isEqualTo("Record my synthetic purchase");
      assertThat(captured.model()).isEqualTo("test-agent-model");
      assertThat(captured.systemPrompt())
          .contains("Extract a proposed household cashflow record")
          .contains("Do not output an activity, owner, property, category, or tax treatment");
    }
  }

  private void stubExtraction(String json) {
    when(llmGateway.completeText(any(LlmTextRequest.class))).thenReturn(json);
  }

  private void stubClassification(
      TransactionDirection direction, AutomatedIntakeClassificationService.Resolution resolution) {
    when(classificationService.resolveFromFreeform(
            eq(direction), anyString(), nullable(String.class), any(LocalDate.class)))
        .thenReturn(resolution);
  }

  private FinancialActivity activity(
      long id, String name, TaxTreatment taxTreatment, String systemKey, Property property) {
    return FinancialActivity.builder()
        .id(id)
        .name(name)
        .taxTreatment(taxTreatment)
        .owner(
            HouseholdMember.builder()
                .id(1L)
                .name("Synthetic Household Member")
                .active(true)
                .build())
        .property(property)
        .active(true)
        .systemKey(systemKey)
        .build();
  }

  private FinancialCategory category(long id, String key, TransactionDirection direction) {
    return FinancialCategory.builder()
        .id(id)
        .key(key)
        .direction(direction)
        .taxTreatment(TaxTreatment.NONE)
        .active(true)
        .build();
  }
}
