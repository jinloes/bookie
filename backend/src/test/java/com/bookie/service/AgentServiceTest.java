package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
  @Mock private PropertyRepository propertyRepository;
  @Mock private PayerRepository payerRepository;

  private AgentService service;

  @BeforeEach
  void setUp() {
    service = new AgentService(llmGateway, new ObjectMapper(), propertyRepository, payerRepository);
    ReflectionTestUtils.setField(service, "agentModel", "test-agent-model");
  }

  private static Property property(Long id, String name, String address) {
    return Property.builder()
        .id(id)
        .name(name)
        .address(address)
        .type(PropertyType.SINGLE_FAMILY)
        .build();
  }

  @Nested
  class ProcessExpenseMessage {

    @Test
    void neverCreatesAnExpenseDirectlyOnlyProposesOne() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":250,"description":"Plumbing repairs","date":"2026-01-05","category":"REPAIRS","propertyName":"Oak Street","payerName":"Joe's Plumbing","needsMoreInfo":false,"followUpQuestion":""}
              """);
      when(propertyRepository.findAll()).thenReturn(List.of());
      when(payerRepository.findAll()).thenReturn(List.of());

      AgentService.AgentResponse response =
          service.processExpenseMessage("I paid $250 for plumbing at Oak Street");

      assertThat(response.proposedExpense()).isNotNull();
      assertThat(response.proposedExpense().amount()).isEqualByComparingTo(BigDecimal.valueOf(250));
      assertThat(response.proposedExpense().category()).isEqualTo(ExpenseCategory.REPAIRS);
      assertThat(response.message()).contains("review the details");
    }

    @Test
    void resolvesPropertyIdWhenNameMatchesExistingProperty() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":100,"description":"Landscaping","date":"2026-02-01","category":"CLEANING_AND_MAINTENANCE","propertyName":"Main St Duplex","payerName":"","needsMoreInfo":false,"followUpQuestion":""}
              """);
      when(propertyRepository.findAll())
          .thenReturn(List.of(property(3L, "Main St Duplex", "123 Main St")));

      AgentService.AgentResponse response =
          service.processExpenseMessage("Landscaping at Main St Duplex");

      assertThat(response.proposedExpense().propertyId()).isEqualTo(3L);
    }

    @Test
    void resolvesPayerIdWhenNameMatchesExistingPayerAlias() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":75,"description":"Cleaning supplies","date":"2026-01-01","category":"SUPPLIES","propertyName":"","payerName":"Home Depot","needsMoreInfo":false,"followUpQuestion":""}
              """);
      Payer homeDepot =
          Payer.builder()
              .id(8L)
              .name("The Home Depot")
              .type(PayerType.COMPANY)
              .aliases(List.of("Home Depot"))
              .build();
      when(payerRepository.findAll()).thenReturn(List.of(homeDepot));

      AgentService.AgentResponse response =
          service.processExpenseMessage("Cleaning supplies from Home Depot");

      assertThat(response.proposedExpense().payerId()).isEqualTo(8L);
    }

    @Test
    void leavesPropertyAndPayerUnresolvedWhenNoMatchFound() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":50,"description":"Supplies","date":"2026-01-01","category":"SUPPLIES","propertyName":"Nonexistent Place","payerName":"Unknown Vendor","needsMoreInfo":false,"followUpQuestion":""}
              """);
      when(propertyRepository.findAll()).thenReturn(List.of());
      when(payerRepository.findAll()).thenReturn(List.of());

      AgentService.AgentResponse response =
          service.processExpenseMessage("Supplies for Nonexistent Place");

      assertThat(response.proposedExpense().propertyId()).isNull();
      assertThat(response.proposedExpense().propertyName()).isEqualTo("Nonexistent Place");
      assertThat(response.proposedExpense().payerId()).isNull();
      assertThat(response.proposedExpense().payerName()).isEqualTo("Unknown Vendor");
    }

    @Test
    void asksFollowUpQuestionWhenAmountIsMissing() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":0,"description":"","date":"2026-01-01","category":"","propertyName":"","payerName":"","needsMoreInfo":true,"followUpQuestion":"How much did you pay?"}
              """);

      AgentService.AgentResponse response = service.processExpenseMessage("I paid for plumbing");

      assertThat(response.message()).isEqualTo("How much did you pay?");
      assertThat(response.proposedExpense()).isNull();
    }

    @Test
    void usesDefaultFollowUpQuestionWhenModelDoesNotProvideOne() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":0,"description":"","date":"","category":"","propertyName":"","payerName":"","needsMoreInfo":true,"followUpQuestion":""}
              """);

      AgentService.AgentResponse response = service.processExpenseMessage("Something happened");

      assertThat(response.message()).isEqualTo("What was the dollar amount for this expense?");
      assertThat(response.proposedExpense()).isNull();
    }

    @Test
    void treatsZeroAmountAsNeedingMoreInfoEvenIfModelDidNotFlagIt() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":0,"description":"Repairs","date":"2026-01-01","category":"REPAIRS","propertyName":"","payerName":"","needsMoreInfo":false,"followUpQuestion":""}
              """);

      AgentService.AgentResponse response = service.processExpenseMessage("Repairs at some point");

      assertThat(response.proposedExpense()).isNull();
      assertThat(response.message()).isEqualTo("What was the dollar amount for this expense?");
    }

    @Test
    void fallsBackToTodayWhenDateCannotBeParsed() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":40,"description":"Repairs","date":"not-a-date","category":"REPAIRS","propertyName":"","payerName":"","needsMoreInfo":false,"followUpQuestion":""}
              """);

      AgentService.AgentResponse response = service.processExpenseMessage("Repairs today");

      assertThat(response.proposedExpense().date()).isEqualTo(LocalDate.now());
    }

    @Test
    void returnsNullCategoryWhenCategoryIsBlankOrInvalid() {
      when(llmGateway.completeText(any(LlmTextRequest.class)))
          .thenReturn(
              """
              {"amount":40,"description":"Misc","date":"2026-01-01","category":"NOT_A_REAL_CATEGORY","propertyName":"","payerName":"","needsMoreInfo":false,"followUpQuestion":""}
              """);

      AgentService.AgentResponse response = service.processExpenseMessage("Misc expense");

      assertThat(response.proposedExpense().category()).isNull();
    }

    @Test
    void returnsClarifyingMessageWhenModelReturnsInvalidJson() {
      when(llmGateway.completeText(any(LlmTextRequest.class))).thenReturn("not json at all");

      AgentService.AgentResponse response = service.processExpenseMessage("Hello");

      assertThat(response.proposedExpense()).isNull();
      assertThat(response.message()).contains("couldn't understand");
    }

    @Test
    void returnsClarifyingMessageWhenModelReturnsEmptyResponse() {
      when(llmGateway.completeText(any(LlmTextRequest.class))).thenReturn("");

      AgentService.AgentResponse response = service.processExpenseMessage("Hello");

      assertThat(response.proposedExpense()).isNull();
      assertThat(response.message()).contains("couldn't understand");
    }

    @Test
    void includesUserMessageInPrompt() {
      ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
      when(llmGateway.completeText(requestCaptor.capture()))
          .thenReturn(
              """
              {"amount":0,"description":"","date":"","category":"","propertyName":"","payerName":"","needsMoreInfo":true,"followUpQuestion":"?"}
              """);

      service.processExpenseMessage("Record my Home Depot expense");

      LlmTextRequest captured = requestCaptor.getValue();
      assertThat(captured.userPrompt()).isEqualTo("Record my Home Depot expense");
      assertThat(captured.model()).isEqualTo("test-agent-model");
      assertThat(captured.systemPrompt()).contains("Extract the following fields");
    }
  }
}
