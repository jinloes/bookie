package com.bookie.service;

import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailParserTools {

  private static final List<String> EXPENSE_CATEGORY_STRINGS =
      Arrays.stream(ExpenseCategory.values())
          .map(c -> "%s (Schedule E line %d)".formatted(c.name(), c.scheduleELine))
          .toList();

  private final PayerRepository payerRepository;
  private final PropertyRepository propertyRepository;
  private final PropertyHistoryService propertyHistoryService;

  @Tool(
      description =
          "Returns the valid expense categories. Call this to pick the most appropriate category for the expense.")
  public List<String> getExpenseCategories() {
    return EXPENSE_CATEGORY_STRINGS;
  }

  @Tool(
      description =
          "Returns the list of known payers (people or companies expenses are paid to). "
              + "Call this to check whether a payer name found in the email matches an existing payer.")
  public List<String> getKnownPayers() {
    return payerRepository.findAll().stream().map(Payer::getName).toList();
  }

  @Tool(
      description =
          "Returns the list of known rental properties with their addresses. "
              + "Call this to check whether a service address or property name found in the email matches a known property. "
              + "Format: 'Property Name (address)' or just 'Property Name' if no address is set.")
  public List<String> getKnownProperties() {
    return propertyRepository.findAll().stream()
        .map(
            p ->
                p.getAddress() != null && !p.getAddress().isBlank()
                    ? "%s (%s)".formatted(p.getName(), p.getAddress())
                    : p.getName())
        .toList();
  }

  @Tool(
      description =
          "Returns property suggestions ranked by how often a payer or keyword has been linked to "
              + "each property in past confirmed expenses. Call this with the identified payer name "
              + "and any stable identifiers found in the email (account numbers, reference codes, "
              + "invoice numbers, service addresses) to improve property matching accuracy. "
              + "A payer may appear multiple times if they service multiple properties.")
  public List<String> getPropertyHints(String payerName, List<String> keywords) {
    return propertyHistoryService.getPropertyHints(payerName, keywords);
  }

  @Tool(
      description =
          "Returns payer suggestions based on stable email identifiers such as account numbers, "
              + "reference codes, or invoice numbers found in past confirmed expenses. Call this "
              + "before getKnownPayers when the email contains identifiers that may match a known "
              + "payer indirectly (e.g. account number on a utility bill).")
  public List<String> getPayerHints(List<String> keywords) {
    return propertyHistoryService.getPayerHints(keywords);
  }
}
