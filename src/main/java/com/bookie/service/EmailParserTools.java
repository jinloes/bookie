package com.bookie.service;

import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailParserTools {

  private final PayerRepository payerRepository;
  private final PropertyRepository propertyRepository;
  private final PropertyHistoryService propertyHistoryService;

  @Tool(
      description =
          """
          Returns the valid expense categories with their Schedule E line numbers.
          Call this when getCategoryForPayer returns empty to pick the best category.""")
  public List<String> getExpenseCategories() {
    return Arrays.stream(ExpenseCategory.values())
        .map(c -> "%s: %s (Schedule E line %d)".formatted(c.name(), c.label, c.scheduleELine))
        .toList();
  }

  @Tool(
      description =
          """
          Returns the list of known payers (people or companies expenses are paid to).
          Call this to find whether a payer name from the email matches an existing payer.
          Use the exact name returned when populating payerName.""")
  public List<String> getKnownPayers() {
    return payerRepository.findAll().stream().map(Payer::getName).toList();
  }

  @Tool(
      description =
          """
          Returns the expense categories this payer has been assigned in past confirmed expenses,
          ranked by frequency. Call this after identifying the payer to get the most likely
          category. Returns empty if no history exists for this payer.""")
  public List<String> getCategoryForPayer(String payerName) {
    return propertyHistoryService.getCategoryForPayer(payerName);
  }

  @Tool(
      description =
          """
          Looks up a payer directly by account number. Call this first with any account numbers,
          reference codes, or invoice numbers extracted from the email. Returns the exact
          payer name if a match is found; returns empty if no account number is registered.""")
  public List<String> findPayerByAccountNumber(List<String> accountNumbers) {
    if (accountNumbers == null || accountNumbers.isEmpty()) {
      return List.of();
    }
    List<String> normalized =
        accountNumbers.stream().map(a -> a.toLowerCase().trim()).filter(a -> !a.isBlank()).toList();
    List<String> result =
        payerRepository.findByAccountIn(normalized).stream().map(Payer::getName).toList();
    log.debug("findPayerByAccountNumber({}) -> {}", normalized, result);
    return result;
  }

  @Tool(
      description =
          """
          Returns payer suggestions based on stable email identifiers such as account numbers,
          reference codes, or invoice numbers found in past confirmed expenses. Call this
          when the email contains identifiers that may match a known payer indirectly
          (e.g. account number on a utility bill).""")
  public List<String> getPayerHints(List<String> keywords) {
    return propertyHistoryService.getPayerHints(keywords);
  }

  @Tool(
      description =
          """
          Returns the list of known rental properties with their addresses.
          Call this when getPropertyHints returns empty to find the best matching property
          by service address or name. Format: 'Property Name (address)' or 'Property Name'
          if no address. Use the exact property name returned when populating propertyName.""")
  public List<String> getKnownProperties() {
    return propertyRepository.findAll().stream().map(this::formatProperty).toList();
  }

  private String formatProperty(Property p) {
    return StringUtils.hasText(p.getAddress())
        ? "%s (%s)".formatted(p.getName(), p.getAddress())
        : p.getName();
  }

  @Tool(
      description =
          """
          Returns property suggestions ranked by how often a payer or keyword has been linked to
          each property in past confirmed expenses. Call this with the identified payer name
          and any stable identifiers found in the email (account numbers, reference codes,
          invoice numbers, service addresses) to improve property matching accuracy.
          If results are returned, prefer the top-ranked property name over address matching.""")
  public List<String> getPropertyHints(String payerName, List<String> keywords) {
    List<String> hints = propertyHistoryService.getPropertyHints(payerName, keywords);
    log.debug("getPropertyHints(payer={}, keywords={}) -> {}", payerName, keywords, hints);
    return hints;
  }
}
