package com.bookie.service;

import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import com.bookie.util.AccountNumbers;
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
  private final ParseSessionContext parseSessionContext;

  @Tool(
      description =
          """
          Use this to identify the payer when you have account numbers from the email \
          (e.g. utility account number, customer account number). Call this before any other \
          payer lookup. Pass the raw account numbers — masked characters like leading asterisks \
          are stripped automatically. Returns the exact payer name if matched; empty if not found. \
          If empty, call findPayerByAlias next.""")
  public List<String> findPayerByAccountNumber(List<String> accountNumbers) {
    List<String> normalized = AccountNumbers.normalize(accountNumbers);
    if (normalized.isEmpty()) {
      return List.of();
    }
    List<String> result =
        payerRepository.findByAccountIn(normalized).stream().map(Payer::getName).toList();
    log.debug("findPayerByAccountNumber({}) -> {}", normalized, result);
    return result;
  }

  @Tool(
      description =
          """
          Use this when findPayerByAccountNumber returns empty and the email mentions a company \
          abbreviation or short name (e.g. "ACWD", "PG&E"). Pass the abbreviations or short \
          names exactly as they appear in the email. Returns the canonical payer name if the \
          abbreviation is a registered alias; empty if not found. \
          If empty, call getPayerHints next.""")
  public List<String> findPayerByAlias(List<String> aliases) {
    List<String> result =
        aliases.stream()
            .flatMap(a -> payerRepository.findByAliasIgnoreCase(a).stream())
            .map(Payer::getName)
            .distinct()
            .toList();
    if (result.isEmpty()) {
      aliases.forEach(parseSessionContext::addUnrecognizedAlias);
    }
    log.debug("findPayerByAlias({}) -> {}", aliases, result);
    return result;
  }

  @Tool(
      description =
          """
          Use this to identify the payer when findPayerByAccountNumber and findPayerByAlias \
          both return empty. Pass all stable identifiers from the email (account numbers, \
          reference codes, invoice numbers). Returns payer names ranked by how often those \
          identifiers have appeared on past confirmed expenses. Use the top-ranked name if \
          results are returned.""")
  public List<String> getPayerHints(List<String> keywords) {
    return propertyHistoryService.getPayerHints(keywords);
  }

  @Tool(
      description =
          """
          Use this to identify the payer when findPayerByAccountNumber, findPayerByAlias, and \
          getPayerHints all return empty. Returns all known payer names. Find the closest match to the payer \
          name in the email and use that exact name — never use an abbreviated or alternate \
          name from the email (e.g. use "Pacific Gas and Electric Company", not "PG&E").""")
  public List<String> getKnownPayers() {
    return payerRepository.findAll().stream().map(Payer::getName).toList();
  }

  @Tool(
      description =
          """
          Use this to identify the expense category when you have stable identifiers from \
          the email. Call this before getCategoryForPayer. Pass account numbers, invoice \
          numbers, and reference codes — do not pass payer names or company names. Returns \
          categories ranked by how often those identifiers have appeared on past confirmed \
          expenses. Use the top-ranked enum key exactly if results are returned \
          (e.g. UTILITIES, REPAIRS) — never a description or label.""")
  public List<String> getCategoryHints(List<String> keywords) {
    return propertyHistoryService.getCategoryHints(keywords);
  }

  @Tool(
      description =
          """
          Use this to identify the expense category when getCategoryHints returns empty. \
          Pass the identified payer name in a single-element list. Returns categories ranked \
          by how often this payer has been assigned each category in past confirmed expenses. \
          Use the top-ranked enum key exactly if results are returned.""")
  public List<String> getCategoryForPayer(List<String> payerNames) {
    if (payerNames == null || payerNames.isEmpty()) {
      return List.of();
    }
    return propertyHistoryService.getCategoryForPayer(payerNames.get(0));
  }

  @Tool(
      description =
          """
          Use this to identify the expense category when both getCategoryHints and \
          getCategoryForPayer return empty. Returns all valid expense categories with their \
          Schedule E line numbers. Pick the best match and use the exact enum key.""")
  public List<String> getExpenseCategories() {
    return Arrays.stream(ExpenseCategory.values())
        .map(c -> "%s: %s (Schedule E line %d)".formatted(c.name(), c.label, c.scheduleELine))
        .toList();
  }

  @Tool(
      description =
          """
          Use this whenever the email contains account numbers (utility, service, or customer \
          accounts) to find which rental property that account belongs to. Each property in \
          the system has known account numbers configured — this is the only way to link an \
          email to a property when the property name is not mentioned in the email. Call this \
          before any other property tool, passing all account numbers from the email. Masked \
          characters like leading asterisks are stripped automatically. Returns the exact \
          property name if matched; empty if not found.""")
  public List<String> findPropertyByAccount(List<String> accountNumbers) {
    List<String> normalized = AccountNumbers.normalize(accountNumbers);
    if (normalized.isEmpty()) {
      return List.of();
    }
    List<String> result =
        propertyRepository.findByAccountIn(normalized).stream().map(Property::getName).toList();
    log.debug("findPropertyByAccount({}) -> {}", normalized, result);
    return result;
  }

  @Tool(
      description =
          """
          Call this after resolving the payer name, when findPropertyByAccount returned empty. \
          Call this before resolving category fields and before calling getKnownProperties — \
          do not skip this step. Pass the resolved payer name and all stable identifiers from \
          the email (account numbers, reference codes, invoice numbers, service addresses). \
          Returns property names ranked by how often this payer or those identifiers have \
          appeared on past confirmed expenses. Use the top-ranked name exactly if results \
          are returned.""")
  public List<String> getPropertyHints(String payerName, List<String> keywords) {
    List<String> hints = propertyHistoryService.getPropertyHints(payerName, keywords);
    log.debug("getPropertyHints(payer={}, keywords={}) -> {}", payerName, keywords, hints);
    return hints;
  }

  @Tool(
      description =
          """
          Use this when findPropertyByAccount and getPropertyHints have both returned empty — \
          this is a required step, not optional. Returns all known rental properties. Each entry \
          is formatted as "NAME | address: ADDRESS" — set propertyName to the NAME part only \
          (the text before " | "), exactly as shown, never the full string. If only one property \
          is returned, use that name — it is almost certainly correct. If multiple, pick the one \
          whose name or address best matches any location hint in the email. Leave propertyName \
          blank only if this tool itself returns empty.""")
  public List<String> getKnownProperties() {
    return propertyRepository.findAll().stream().map(this::formatProperty).toList();
  }

  private String formatProperty(Property p) {
    return StringUtils.hasText(p.getAddress())
        ? "%s | address: %s".formatted(p.getName(), p.getAddress())
        : p.getName();
  }
}
