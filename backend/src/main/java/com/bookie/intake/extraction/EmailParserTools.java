package com.bookie.intake.extraction;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.HistoryHint;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.util.AccountNumbers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailParserTools {

  private final CounterpartyCatalog counterpartyCatalog;
  private final PropertyCatalog propertyCatalog;
  private final ClassificationHistory classificationHistory;

  public record AliasLookupResult(List<String> matches, List<String> unrecognizedAliases) {}

  public List<String> findPayerByAccountNumber(List<String> accountNumbers) {
    List<String> normalized = AccountNumbers.normalize(accountNumbers);
    if (normalized.isEmpty()) {
      return List.of();
    }
    List<String> result =
        counterpartyCatalog.findByAccounts(normalized).stream().map(Counterparty::getName).toList();
    log.debug("findPayerByAccountNumber({}) -> {}", normalized, result);
    return result;
  }

  /**
   * Looks up the canonical payer name by alias. When no alias matches, returns the input aliases as
   * unrecognized so they can be offered/auto-saved when the item is confirmed.
   */
  public AliasLookupResult findPayerByAlias(List<String> aliases) {
    List<String> safeAliases = List.copyOf(CollectionUtils.emptyIfNull(aliases));
    if (safeAliases.isEmpty()) {
      return new AliasLookupResult(List.of(), List.of());
    }
    List<String> result =
        safeAliases.stream()
            .flatMap(alias -> counterpartyCatalog.findByAlias(alias).stream())
            .map(Counterparty::getName)
            .distinct()
            .toList();
    List<String> unrecognized = result.isEmpty() ? safeAliases : List.of();
    log.debug("findPayerByAlias({}) -> {}", safeAliases, result);
    return new AliasLookupResult(result, unrecognized);
  }

  public List<HistoryHint> getPayerHints(List<String> keywords) {
    List<HistoryHint> result = classificationHistory.getPayerHints(keywords);
    log.debug("getPayerHints({}) -> {}", keywords, result);
    return result;
  }

  public List<String> findPropertyByAccount(List<String> accountNumbers) {
    List<String> normalized = AccountNumbers.normalize(accountNumbers);
    if (normalized.isEmpty()) {
      return List.of();
    }
    List<String> result =
        propertyCatalog.findByAccounts(normalized).stream().map(Property::getName).toList();
    log.debug("findPropertyByAccount({}) -> {}", normalized, result);
    return result;
  }

  public List<HistoryHint> getPropertyHints(String payerName, List<String> keywords) {
    List<HistoryHint> hints = classificationHistory.getPropertyHints(payerName, keywords);
    log.debug("getPropertyHints(payer={}, keywords={}) -> {}", payerName, keywords, hints);
    return hints;
  }
}
