package com.bookie.service;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.Property;
import com.bookie.repository.PayerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Enforces grounding constraints so suggestions cannot return unverifiable canonical fields. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuggestionValidator {

  private final PayerRepository payerRepository;

  public EmailSuggestion validate(
      EmailSuggestion suggestion, String rawParsedPayerName, List<Property> knownProperties) {
    EmailType emailType =
        suggestion.emailType() != null ? suggestion.emailType() : EmailType.EXPENSE;
    return EmailSuggestion.builder()
        .emailType(emailType)
        .amount(validateAmount(suggestion.amount()))
        .description(suggestion.description())
        .date(suggestion.date())
        .category(validateCategory(emailType, suggestion.category()))
        .propertyName(validatePropertyName(suggestion.propertyName(), knownProperties))
        .payerName(validatePayerName(emailType, suggestion.payerName(), rawParsedPayerName))
        .keywords(suggestion.keywords())
        .accountNumbers(suggestion.accountNumbers())
        .build();
  }

  private Double validateAmount(Double amount) {
    if (amount != null && amount < 0) {
      log.warn("Dropping negative amount from suggestion: {}", amount);
      return null;
    }
    return amount;
  }

  private String validateCategory(EmailType emailType, String category) {
    if (emailType == EmailType.INCOME || StringUtils.isBlank(category)) {
      return null;
    }
    try {
      return ExpenseCategory.valueOf(category.trim().toUpperCase()).name();
    } catch (IllegalArgumentException e) {
      log.warn("Dropping unrecognized category from suggestion: '{}'", category);
      return null;
    }
  }

  private String validatePropertyName(String propertyName, List<Property> knownProperties) {
    String normalized = StringUtils.trimToNull(propertyName);
    if (normalized == null) {
      return null;
    }
    return CollectionUtils.emptyIfNull(knownProperties).stream()
        .filter(property -> StringUtils.equalsIgnoreCase(property.getName(), normalized))
        .map(Property::getName)
        .findFirst()
        .orElseGet(
            () -> {
              log.warn("Dropping unrecognized property from suggestion: '{}'", propertyName);
              return null;
            });
  }

  private String validatePayerName(
      EmailType emailType, String suggestedPayerName, String rawParsedPayerName) {
    String raw = StringUtils.trimToNull(rawParsedPayerName);
    String resolved = StringUtils.trimToNull(suggestedPayerName);
    if (emailType == EmailType.INCOME) {
      return raw != null ? raw : resolved;
    }
    if (resolved != null) {
      var canonical = payerRepository.findByNameIgnoreCase(resolved).map(p -> p.getName());
      if (canonical.isPresent()) {
        return canonical.get();
      }
      log.warn(
          "Dropping unrecognized canonical payer '{}', falling back to raw parsed payer '{}'",
          resolved,
          raw);
    }
    return raw;
  }
}
