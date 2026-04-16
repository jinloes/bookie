package com.bookie.service;

import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
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

  private final PayerRepository payerRepository;
  private final PropertyRepository propertyRepository;
  private final PropertyHistoryService propertyHistoryService;
  private final ParseSessionContext parseSessionContext;

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

  /**
   * Looks up the canonical payer name by alias. If the alias is not found, records it in the
   * session context so it can be auto-saved when the expense is confirmed.
   */
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

  public List<String> getPayerHints(List<String> keywords) {
    List<String> result = propertyHistoryService.getPayerHints(keywords);
    log.debug("getPayerHints({}) -> {}", keywords, result);
    return result;
  }

  public List<String> getCategoryHints(List<String> keywords) {
    return propertyHistoryService.getCategoryHints(keywords);
  }

  public List<String> getCategoryForPayer(List<String> payerNames) {
    if (CollectionUtils.isEmpty(payerNames)) {
      return List.of();
    }
    return propertyHistoryService.getCategoryForPayer(payerNames.get(0));
  }

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

  public List<String> getPropertyHints(String payerName, List<String> keywords) {
    List<String> hints = propertyHistoryService.getPropertyHints(payerName, keywords);
    log.debug("getPropertyHints(payer={}, keywords={}) -> {}", payerName, keywords, hints);
    return hints;
  }
}
