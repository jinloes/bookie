package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.repository.PayerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuggestionValidatorTest {

  @Mock private PayerRepository payerRepository;

  @InjectMocks private SuggestionValidator validator;

  @Nested
  class Validate {

    @Test
    void expenseWithUnknownProperty_dropsPropertyName() {
      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.EXPENSE)
              .amount(100.0)
              .description("desc")
              .date("2026-06-01")
              .category("UTILITIES")
              .propertyName("Imaginary Property")
              .payerName("Vendor")
              .build();

      EmailSuggestion result =
          validator.validate(
              suggestion,
              "Vendor",
              List.of(
                  Property.builder()
                      .id(1L)
                      .name("Real Property")
                      .address("1 Main St")
                      .type(PropertyType.SINGLE_FAMILY)
                      .build()));

      assertThat(result.propertyName()).isNull();
    }

    @Test
    void expenseWithUnknownCanonicalPayer_fallsBackToRawParsedPayer() {
      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.EXPENSE)
              .amount(100.0)
              .description("desc")
              .date("2026-06-01")
              .category("UTILITIES")
              .propertyName("Real Property")
              .payerName("Hallucinated Canonical Payer")
              .build();
      when(payerRepository.findByNameIgnoreCase("Hallucinated Canonical Payer"))
          .thenReturn(Optional.empty());

      EmailSuggestion result =
          validator.validate(
              suggestion,
              "Vendor Raw Name",
              List.of(
                  Property.builder()
                      .id(1L)
                      .name("Real Property")
                      .address("1 Main St")
                      .type(PropertyType.SINGLE_FAMILY)
                      .build()));

      assertThat(result.payerName()).isEqualTo("Vendor Raw Name");
    }

    @Test
    void expenseWithKnownCanonicalPayer_keepsCanonicalNameFromDb() {
      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.EXPENSE)
              .amount(100.0)
              .description("desc")
              .date("2026-06-01")
              .category("utilities")
              .propertyName("Real Property")
              .payerName("acwd")
              .build();
      when(payerRepository.findByNameIgnoreCase("acwd"))
          .thenReturn(
              Optional.of(Payer.builder().id(1L).name("ACWD").type(PayerType.COMPANY).build()));

      EmailSuggestion result =
          validator.validate(
              suggestion,
              "Raw Vendor",
              List.of(
                  Property.builder()
                      .id(1L)
                      .name("Real Property")
                      .address("1 Main St")
                      .type(PropertyType.SINGLE_FAMILY)
                      .build()));

      assertThat(result.payerName()).isEqualTo("ACWD");
      assertThat(result.category()).isEqualTo("UTILITIES");
    }

    @Test
    void incomeAlwaysDropsCategoryAndUsesRawPayerName() {
      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.INCOME)
              .amount(2500.0)
              .description("Rent")
              .date("2026-06-01")
              .category("UTILITIES")
              .payerName("Wrong Canonical")
              .build();

      EmailSuggestion result = validator.validate(suggestion, "Todd Freeman", List.of());

      assertThat(result.category()).isNull();
      assertThat(result.payerName()).isEqualTo("Todd Freeman");
    }

    @Test
    void negativeAmount_isDropped() {
      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.EXPENSE)
              .amount(-5.0)
              .description("desc")
              .date("2026-06-01")
              .category("UTILITIES")
              .payerName("Vendor")
              .build();

      EmailSuggestion result = validator.validate(suggestion, "Vendor", List.of());

      assertThat(result.amount()).isNull();
    }
  }
}
