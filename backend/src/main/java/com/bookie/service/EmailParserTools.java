package com.bookie.service;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.model.HistoryHint;
import com.bookie.model.TransactionDirection;
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
  private final ParseSessionContext parseSessionContext;

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
   * Looks up the canonical payer name by alias. If the alias is not found, records it in the
   * session context so it can be auto-saved when the expense is confirmed.
   */
  public List<String> findPayerByAlias(List<String> aliases) {
    List<String> result =
        aliases.stream()
            .flatMap(alias -> counterpartyCatalog.findByAlias(alias).stream())
            .map(Counterparty::getName)
            .distinct()
            .toList();
    if (result.isEmpty()) {
      aliases.forEach(parseSessionContext::addUnrecognizedAlias);
    }
    log.debug("findPayerByAlias({}) -> {}", aliases, result);
    return result;
  }

  public List<HistoryHint> getPayerHints(List<String> keywords) {
    List<HistoryHint> result = classificationHistory.getPayerHints(keywords);
    log.debug("getPayerHints({}) -> {}", keywords, result);
    return result;
  }

  public List<HistoryHint> getCategoryHints(List<String> keywords) {
    return classificationHistory.getCategoryHints(keywords);
  }

  public List<HistoryHint> getCategoryForPayer(List<String> payerNames) {
    if (CollectionUtils.isEmpty(payerNames)) {
      return List.of();
    }
    return classificationHistory.getCategoryForPayer(payerNames.get(0));
  }

  public List<HistoryHint> getActivityHints(List<String> keywords) {
    List<HistoryHint> hints = classificationHistory.getActivityHints(keywords);
    log.debug("getActivityHints({}) -> {}", keywords, hints);
    return hints;
  }

  public List<HistoryHint> getFinancialCategoryHints(
      Long activityId, TransactionDirection direction, List<String> keywords) {
    List<HistoryHint> hints =
        classificationHistory.getFinancialCategoryHints(activityId, direction, keywords);
    log.debug(
        "getFinancialCategoryHints(activityId={}, direction={}, keywords={}) -> {}",
        activityId,
        direction,
        keywords,
        hints);
    return hints;
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
