package com.bookie.catalog.classification.infrastructure;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.application.*;
import com.bookie.catalog.classification.domain.EmailKeywordClassificationHistory;
import com.bookie.catalog.classification.domain.EmailKeywordPayerHistory;
import com.bookie.catalog.classification.domain.EmailKeywordPropertyHistory;
import com.bookie.catalog.classification.domain.HasOccurrences;
import com.bookie.catalog.classification.domain.ParsedEmailKeywords;
import com.bookie.catalog.classification.domain.PayerPropertyHistory;
import com.bookie.catalog.counterparty.application.CounterpartyDeleting;
import com.bookie.catalog.counterparty.application.CounterpartyStore;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.application.PropertyDeleting;
import com.bookie.catalog.property.application.PropertyStore;
import com.bookie.catalog.property.domain.Property;
import com.bookie.util.AccountNumbers;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and retrieves property, counterparty, activity, and category associations learned from
 * confirmed transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JpaClassificationHistory implements ClassificationHistory {

  private static final int SQL_SERVER_UNIQUE_CONSTRAINT_VIOLATION = 2627;
  private static final int SQL_SERVER_UNIQUE_INDEX_VIOLATION = 2601;

  private final PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  private final EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  private final EmailKeywordPayerHistoryRepository keywordPayerHistoryRepo;
  private final EmailKeywordClassificationHistoryRepository keywordClassificationHistoryRepo;
  private final ParsedEmailKeywordsRepository parsedKeywordsRepo;
  private final CounterpartyStore counterpartyStore;
  private final PropertyStore propertyStore;
  private final EntityManager entityManager;

  @Transactional
  public void storeKeywords(String sourceId, List<String> keywords) {
    if (CollectionUtils.isEmpty(keywords)) {
      return;
    }
    // Delete before insert so durable job retries are idempotent
    int deleted = parsedKeywordsRepo.deleteBySourceId(sourceId);
    List<ParsedEmailKeywords> entities =
        AccountNumbers.normalize(keywords).stream()
            .distinct()
            .map(k -> ParsedEmailKeywords.builder().sourceId(sourceId).keyword(k).build())
            .toList();
    parsedKeywordsRepo.saveAll(entities);
    log.debug(
        "storeKeywords: sourceId={} replaced {} prior rows with {} new ones",
        sourceId,
        deleted,
        entities.size());
  }

  @Override
  @Transactional
  public void record(ConfirmedClassification classification) {
    List<String> keywords = getStoredKeywords(classification.sourceId());
    recordClassification(classification.activity(), classification.neutralCategoryId(), keywords);
    if (classification.kind() == ConfirmedClassification.Kind.EXPENSE) {
      recordExpenseAssociations(classification, keywords);
    } else {
      recordIncomeAssociations(classification);
    }
    clearStoredKeywords(classification.sourceId());
  }

  private void recordExpenseAssociations(
      ConfirmedClassification classification, List<String> keywords) {
    Optional<Counterparty> counterparty =
        classification.counterparty() == null
            ? Optional.empty()
            : counterpartyStore.findById(classification.counterparty().getId());
    Optional<Property> property =
        classification.property() == null
            ? Optional.empty()
            : propertyStore.findById(classification.property().getId());
    if (property.isEmpty()) {
      return;
    }

    Property fullProperty = property.get();
    counterparty.ifPresent(value ->
        upsert(
            payerPropertyHistoryRepo.findByPayerIdAndPropertyId(
                value.getId(), fullProperty.getId()),
            () ->
                PayerPropertyHistory.builder()
                    .payer(value)
                    .property(fullProperty)
                    .occurrences(1)
                    .build(),
            payerPropertyHistoryRepo::save));

    if (!keywords.isEmpty()) {
      recordPropertyAndPayerKeywordHistory(fullProperty, counterparty, keywords);
    }
  }

  private void recordIncomeAssociations(ConfirmedClassification classification) {
    if (classification.property() == null || classification.counterparty() == null) {
      return;
    }
    propertyStore
        .findById(classification.property().getId())
        .ifPresent(property ->
            counterpartyStore
                .findById(classification.counterparty().getId())
                .ifPresent(counterparty ->
                    upsert(
                        payerPropertyHistoryRepo.findByPayerIdAndPropertyId(
                            counterparty.getId(), property.getId()),
                        () ->
                            PayerPropertyHistory.builder()
                                .payer(counterparty)
                                .property(property)
                                .occurrences(1)
                                .build(),
                        payerPropertyHistoryRepo::save)));
  }

  public List<HistoryHint> getPropertyHints(String payerName, List<String> keywords) {
    var hints = new ArrayList<HistoryHint>();

    if (StringUtils.isNotBlank(payerName)) {
      resolvePayerByNameOrAlias(payerName)
          .ifPresent(payer ->
              payerPropertyHistoryRepo
                  .findByPayerIdOrderByOccurrencesDesc(payer.getId())
                  .forEach(h ->
                      hints.add(
                          new HistoryHint(
                              h.getProperty().getName(), h.getOccurrences(), "payer-history"))));
    }

    if (!CollectionUtils.isEmpty(keywords)) {
      keywordPropertyHistoryRepo
          .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
          .forEach(h ->
              hints.add(
                  new HistoryHint(
                      h.getProperty().getName(), h.getOccurrences(), "keyword-history")));
    }

    return hints;
  }

  public List<CounterpartyKeywordHistory> getAllPayerKeywords() {
    return keywordPayerHistoryRepo.findAll().stream()
        .map(history ->
            new CounterpartyKeywordHistory(
                history.getId(),
                history.getKeyword(),
                history.getPayer() == null ? null : history.getPayer().getId(),
                history.getPayer() == null ? null : history.getPayer().getName(),
                history.getOccurrences(),
                history.getVersion()))
        .toList();
  }

  public List<PropertyKeywordHistory> getAllPropertyKeywords() {
    return keywordPropertyHistoryRepo.findAll().stream()
        .map(history ->
            new PropertyKeywordHistory(
                history.getId(),
                history.getKeyword(),
                history.getProperty() == null ? null : history.getProperty().getId(),
                history.getProperty() == null ? null : history.getProperty().getName(),
                history.getOccurrences(),
                history.getVersion()))
        .toList();
  }

  public List<HistoryHint> getPayerHints(List<String> keywords) {
    if (CollectionUtils.isEmpty(keywords)) {
      return List.of();
    }
    return keywordPayerHistoryRepo
        .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
        .stream()
        .map(h -> new HistoryHint(h.getPayer().getName(), h.getOccurrences(), "keyword-history"))
        .toList();
  }

  public List<HistoryHint> getActivityHints(List<String> keywords) {
    if (CollectionUtils.isEmpty(keywords)) {
      return List.of();
    }
    Map<Long, HistoryHint> byActivity = new LinkedHashMap<>();
    keywordClassificationHistoryRepo
        .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
        .forEach(history ->
            byActivity.merge(
                history.getActivity().getId(),
                new HistoryHint(
                    history.getActivity().getName(),
                    history.getOccurrences(),
                    "activity-keyword-history"),
                (left, right) ->
                    new HistoryHint(
                        left.value(), left.occurrences() + right.occurrences(), left.source())));
    return byActivity.values().stream()
        .sorted(Comparator.comparingInt(HistoryHint::occurrences).reversed())
        .toList();
  }

  public List<HistoryHint> getFinancialCategoryHints(
      Long activityId, TransactionDirection direction, List<String> keywords) {
    if (activityId == null || direction == null || CollectionUtils.isEmpty(keywords)) {
      return List.of();
    }
    Map<Long, HistoryHint> byCategory = new LinkedHashMap<>();
    keywordClassificationHistoryRepo
        .findByKeywordInAndActivityIdOrderByOccurrencesDesc(
            AccountNumbers.normalize(keywords), activityId)
        .stream()
        .filter(history -> history.getNeutralCategory().isActive())
        .filter(history -> history.getNeutralCategory().getDirection() == direction)
        .forEach(history ->
            byCategory.merge(
                history.getNeutralCategory().getId(),
                new HistoryHint(
                    history.getNeutralCategory().getKey(),
                    history.getOccurrences(),
                    "activity-category-keyword-history"),
                (left, right) ->
                    new HistoryHint(
                        left.value(), left.occurrences() + right.occurrences(), left.source())));
    return byCategory.values().stream()
        .sorted(Comparator.comparingInt(HistoryHint::occurrences).reversed())
        .toList();
  }

  @Override
  public Optional<Property> findMostLikelyPropertyForCounterparty(Long counterpartyId) {
    return payerPropertyHistoryRepo.findByPayerIdOrderByOccurrencesDesc(counterpartyId).stream()
        .map(PayerPropertyHistory::getProperty)
        .filter(java.util.Objects::nonNull)
        .findFirst();
  }

  @EventListener
  public void onPropertyDeleting(PropertyDeleting event) {
    removePropertyReferences(event.propertyId());
    requireNoPropertyReferences(event.propertyId());
  }

  public void removePropertyReferences(Long propertyId) {
    payerPropertyHistoryRepo.deleteByPropertyId(propertyId);
    keywordPropertyHistoryRepo.deleteByPropertyId(propertyId);
  }

  public void requireNoPropertyReferences(Long propertyId) {
    if (payerPropertyHistoryRepo.countByPropertyId(propertyId) != 0
        || keywordPropertyHistoryRepo.countByPropertyId(propertyId) != 0) {
      throw new IllegalStateException(
          "Classification history still references property: " + propertyId);
    }
  }

  @EventListener
  public void onCounterpartyDeleting(CounterpartyDeleting event) {
    removeCounterpartyReferences(event.counterpartyId());
    requireNoCounterpartyReferences(event.counterpartyId());
  }

  public void removeCounterpartyReferences(Long counterpartyId) {
    payerPropertyHistoryRepo.deleteByPayerId(counterpartyId);
    keywordPayerHistoryRepo.deleteByPayerId(counterpartyId);
  }

  public void requireNoCounterpartyReferences(Long counterpartyId) {
    if (payerPropertyHistoryRepo.countByPayerId(counterpartyId) != 0
        || keywordPayerHistoryRepo.countByPayerId(counterpartyId) != 0) {
      throw new IllegalStateException(
          "Classification history still references counterparty: " + counterpartyId);
    }
  }

  private void recordPropertyAndPayerKeywordHistory(
      Property fullProperty, Optional<Counterparty> payer, List<String> keywords) {
    Map<String, EmailKeywordPropertyHistory> existingPropByKw =
        keywordPropertyHistoryRepo
            .findByKeywordInAndPropertyId(keywords, fullProperty.getId())
            .stream()
            .collect(Collectors.toMap(EmailKeywordPropertyHistory::getKeyword, history -> history));
    batchUpsert(
        keywords,
        existingPropByKw,
        keyword ->
            EmailKeywordPropertyHistory.builder()
                .keyword(keyword)
                .property(fullProperty)
                .occurrences(1)
                .build(),
        keywordPropertyHistoryRepo::save);

    payer.ifPresent(value -> {
      Map<String, EmailKeywordPayerHistory> existingPayerByKw =
          keywordPayerHistoryRepo.findByKeywordInAndPayer(keywords, value).stream()
              .collect(Collectors.toMap(EmailKeywordPayerHistory::getKeyword, history -> history));
      batchUpsert(
          keywords,
          existingPayerByKw,
          keyword ->
              EmailKeywordPayerHistory.builder()
                  .keyword(keyword)
                  .payer(value)
                  .occurrences(1)
                  .build(),
          keywordPayerHistoryRepo::save);
    });
  }

  private void recordClassification(
      FinancialActivity activity, Long neutralCategoryId, List<String> keywords) {
    if (activity == null || neutralCategoryId == null || keywords.isEmpty()) {
      return;
    }
    NeutralCategory category = entityManager.getReference(NeutralCategory.class, neutralCategoryId);
    Map<String, EmailKeywordClassificationHistory> existingByKeyword =
        keywordClassificationHistoryRepo
            .findByKeywordInAndActivityIdAndNeutralCategoryId(
                keywords, activity.getId(), category.getId())
            .stream()
            .collect(
                Collectors.toMap(
                    EmailKeywordClassificationHistory::getKeyword, history -> history));
    batchUpsert(
        keywords,
        existingByKeyword,
        keyword ->
            EmailKeywordClassificationHistory.builder()
                .keyword(keyword)
                .activity(activity)
                .neutralCategory(category)
                .occurrences(1)
                .build(),
        keywordClassificationHistoryRepo::save);
  }

  private List<String> getStoredKeywords(String sourceId) {
    if (StringUtils.isBlank(sourceId)) {
      return List.of();
    }
    return parsedKeywordsRepo.findBySourceId(sourceId).stream()
        .map(ParsedEmailKeywords::getKeyword)
        .toList();
  }

  private void clearStoredKeywords(String sourceId) {
    if (StringUtils.isBlank(sourceId)) {
      return;
    }
    int deleted = parsedKeywordsRepo.deleteBySourceId(sourceId);
    if (deleted > 0) {
      log.debug("Cleared {} parsed keyword rows for sourceId={}", deleted, sourceId);
    }
  }

  private Optional<Counterparty> resolvePayerByNameOrAlias(String name) {
    return counterpartyStore
        .findByNameIgnoreCase(name)
        .or(() -> counterpartyStore.findByAliasIgnoreCase(name));
  }

  private <T extends HasOccurrences> void upsert(
      Optional<T> existing, Supplier<T> factory, Consumer<T> save) {
    existing.ifPresentOrElse(
        h -> {
          h.setOccurrences(h.getOccurrences() + 1);
          save.accept(h);
        },
        () -> {
          try {
            save.accept(factory.get());
          } catch (DataIntegrityViolationException e) {
            if (!isUniqueConstraintViolation(e)) {
              throw e;
            }
            log.debug("upsert: concurrent insert detected, skipping: {}", e.getMessage());
          }
        });
  }

  private <T extends HasOccurrences> void batchUpsert(
      List<String> keys,
      Map<String, T> existingByKey,
      Function<String, T> factory,
      Consumer<T> save) {
    for (String key : keys) {
      upsert(Optional.ofNullable(existingByKey.get(key)), () -> factory.apply(key), save);
    }
  }

  private static boolean isUniqueConstraintViolation(DataIntegrityViolationException e) {
    Throwable cause = e.getCause();
    while (cause != null) {
      if (cause instanceof ConstraintViolationException cve) {
        int errorCode = cve.getErrorCode();
        return "23505".equals(cve.getSQLState())
            || errorCode == SQL_SERVER_UNIQUE_CONSTRAINT_VIOLATION
            || errorCode == SQL_SERVER_UNIQUE_INDEX_VIOLATION;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
