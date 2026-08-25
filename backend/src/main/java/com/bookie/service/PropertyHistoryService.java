package com.bookie.service;

import com.bookie.model.EmailKeywordCategoryHistory;
import com.bookie.model.EmailKeywordClassificationHistory;
import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HasOccurrences;
import com.bookie.model.HistoryHint;
import com.bookie.model.Income;
import com.bookie.model.ParsedEmailKeywords;
import com.bookie.model.Payer;
import com.bookie.model.PayerCategoryHistory;
import com.bookie.model.PayerPropertyHistory;
import com.bookie.model.Property;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.EmailKeywordCategoryHistoryRepository;
import com.bookie.repository.EmailKeywordClassificationHistoryRepository;
import com.bookie.repository.EmailKeywordPayerHistoryRepository;
import com.bookie.repository.EmailKeywordPropertyHistoryRepository;
import com.bookie.repository.ParsedEmailKeywordsRepository;
import com.bookie.repository.PayerCategoryHistoryRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import com.bookie.util.AccountNumbers;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and retrieves property/payer associations learned from confirmed expenses. Associations
 * are accumulated over time and used as weighted hints during email parsing to improve the accuracy
 * of AI-suggested property and payer matches.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyHistoryService {

  private final PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  private final PayerCategoryHistoryRepository payerCategoryHistoryRepo;
  private final EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  private final EmailKeywordPayerHistoryRepository keywordPayerHistoryRepo;
  private final EmailKeywordCategoryHistoryRepository keywordCategoryHistoryRepo;
  private final EmailKeywordClassificationHistoryRepository keywordClassificationHistoryRepo;
  private final ParsedEmailKeywordsRepository parsedKeywordsRepo;
  private final PayerRepository payerRepository;
  private final PropertyRepository propertyRepository;

  /**
   * Stores keywords extracted from an email at parse time, keyed by the message ID. These are
   * consumed and deleted when the resulting expense is saved.
   */
  @Transactional
  public void storeKeywords(String sourceId, List<String> keywords) {
    if (CollectionUtils.isEmpty(keywords)) {
      return;
    }
    // Delete before insert so retries (from @Retryable or circuit-breaker recovery) are idempotent
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

  /**
   * Records payer→property and keyword→property/payer associations from a confirmed expense.
   * Keywords are looked up from temporary storage (if the expense originated from an Outlook email)
   * and deleted after recording.
   */
  @Transactional
  public void record(Expense expense) {
    List<String> keywords = getStoredKeywords(expense.getSourceId());
    recordClassification(expense.getActivity(), expense.getFinancialCategory(), keywords);

    Property property = expense.getProperty();
    Optional<Payer> payer =
        expense.getPayer() != null
            ? payerRepository.findById(expense.getPayer().getId())
            : Optional.empty();
    Optional<Property> resolvedProperty =
        property == null ? Optional.empty() : propertyRepository.findById(property.getId());

    if (resolvedProperty.isPresent()) {
      Property fullProperty = resolvedProperty.get();
      payer.ifPresent(
          p ->
              upsert(
                  payerPropertyHistoryRepo.findByPayerIdAndPropertyId(
                      p.getId(), fullProperty.getId()),
                  () ->
                      PayerPropertyHistory.builder()
                          .payer(p)
                          .property(fullProperty)
                          .occurrences(1)
                          .build(),
                  payerPropertyHistoryRepo::save));

      if (payer.isPresent() && expense.getCategory() != null) {
        Payer p = payer.get();
        upsert(
            payerCategoryHistoryRepo.findByPayerAndCategory(p, expense.getCategory()),
            () ->
                PayerCategoryHistory.builder()
                    .payer(p)
                    .category(expense.getCategory())
                    .occurrences(1)
                    .build(),
            payerCategoryHistoryRepo::save);
      }

      if (!keywords.isEmpty()) {
        recordLegacyRentalKeywordHistory(expense, fullProperty, payer, keywords);
      }
    }

    clearStoredKeywords(expense.getSourceId());
  }

  /** Records payer→property association from a confirmed income record. */
  @Transactional
  public void record(Income income) {
    List<String> keywords = getStoredKeywords(income.getSourceId());
    recordClassification(income.getActivity(), income.getFinancialCategory(), keywords);

    Property property = income.getProperty();
    if (property != null && income.getPayer() != null) {
      propertyRepository
          .findById(property.getId())
          .ifPresent(
              fullProperty ->
                  payerRepository
                      .findById(income.getPayer().getId())
                      .ifPresent(
                          payer ->
                              upsert(
                                  payerPropertyHistoryRepo.findByPayerIdAndPropertyId(
                                      payer.getId(), fullProperty.getId()),
                                  () ->
                                      PayerPropertyHistory.builder()
                                          .payer(payer)
                                          .property(fullProperty)
                                          .occurrences(1)
                                          .build(),
                                  payerPropertyHistoryRepo::save)));
    }

    clearStoredKeywords(income.getSourceId());
  }

  /**
   * Returns property hints ranked by frequency for use as AI tool context.
   *
   * @param payerName payer name or alias to look up history for, or null
   * @param keywords normalized keywords extracted from the email
   * @return ranked structured hints
   */
  public List<HistoryHint> getPropertyHints(String payerName, List<String> keywords) {
    var hints = new ArrayList<HistoryHint>();

    if (StringUtils.isNotBlank(payerName)) {
      resolvePayerByNameOrAlias(payerName)
          .ifPresent(
              payer ->
                  payerPropertyHistoryRepo
                      .findByPayerIdOrderByOccurrencesDesc(payer.getId())
                      .forEach(
                          h ->
                              hints.add(
                                  new HistoryHint(
                                      h.getProperty().getName(),
                                      h.getOccurrences(),
                                      "payer-history"))));
    }

    if (!CollectionUtils.isEmpty(keywords)) {
      keywordPropertyHistoryRepo
          .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
          .forEach(
              h ->
                  hints.add(
                      new HistoryHint(
                          h.getProperty().getName(), h.getOccurrences(), "keyword-history")));
    }

    return hints;
  }

  /**
   * Returns a category hint for a payer only when there is strong historical consistency: the top
   * category must account for ≥90% of all uses and the payer must have at least 3 confirmed
   * expenses. Multi-category vendors (e.g. Amazon) return an empty list so the AI decides based on
   * the actual items purchased.
   */
  public List<HistoryHint> getCategoryForPayer(String payerName) {
    if (StringUtils.isBlank(payerName)) {
      return List.of();
    }
    return resolvePayerByNameOrAlias(payerName)
        .map(
            payer -> {
              List<PayerCategoryHistory> rows =
                  payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(payer.getId());
              int total = rows.stream().mapToInt(PayerCategoryHistory::getOccurrences).sum();
              if (rows.isEmpty() || total < 3) {
                return List.<HistoryHint>of();
              }
              PayerCategoryHistory top = rows.get(0);
              if ((double) top.getOccurrences() / total < 0.9) {
                return List.<HistoryHint>of();
              }
              return List.of(
                  new HistoryHint(
                      top.getCategory().name(), top.getOccurrences(), "payer-category-history"));
            })
        .orElse(List.of());
  }

  public List<String> getAllPayerPropertyHints() {
    return payerPropertyHistoryRepo.findAll().stream()
        .map(
            h ->
                "%s → %s (%d times)"
                    .formatted(
                        h.getPayer().getName(), h.getProperty().getName(), h.getOccurrences()))
        .toList();
  }

  public List<EmailKeywordPayerHistory> getAllPayerKeywords() {
    return keywordPayerHistoryRepo.findAll();
  }

  public List<EmailKeywordPropertyHistory> getAllPropertyKeywords() {
    return keywordPropertyHistoryRepo.findAll();
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

  public List<HistoryHint> getCategoryHints(List<String> keywords) {
    if (CollectionUtils.isEmpty(keywords)) {
      return List.of();
    }
    return keywordCategoryHistoryRepo
        .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
        .stream()
        .map(h -> new HistoryHint(h.getCategory().name(), h.getOccurrences(), "keyword-history"))
        .toList();
  }

  public List<HistoryHint> getActivityHints(List<String> keywords) {
    if (CollectionUtils.isEmpty(keywords)) {
      return List.of();
    }
    Map<Long, HistoryHint> byActivity = new LinkedHashMap<>();
    keywordClassificationHistoryRepo
        .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
        .forEach(
            history ->
                byActivity.merge(
                    history.getActivity().getId(),
                    new HistoryHint(
                        history.getActivity().getName(),
                        history.getOccurrences(),
                        "activity-keyword-history"),
                    (left, right) ->
                        new HistoryHint(
                            left.value(),
                            left.occurrences() + right.occurrences(),
                            left.source())));
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
        .filter(history -> history.getFinancialCategory().isActive())
        .filter(history -> history.getFinancialCategory().getDirection() == direction)
        .forEach(
            history ->
                byCategory.merge(
                    history.getFinancialCategory().getId(),
                    new HistoryHint(
                        history.getFinancialCategory().getKey(),
                        history.getOccurrences(),
                        "activity-category-keyword-history"),
                    (left, right) ->
                        new HistoryHint(
                            left.value(),
                            left.occurrences() + right.occurrences(),
                            left.source())));
    return byCategory.values().stream()
        .sorted(Comparator.comparingInt(HistoryHint::occurrences).reversed())
        .toList();
  }

  private void recordLegacyRentalKeywordHistory(
      Expense expense, Property fullProperty, Optional<Payer> payer, List<String> keywords) {
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

    payer.ifPresent(
        value -> {
          Map<String, EmailKeywordPayerHistory> existingPayerByKw =
              keywordPayerHistoryRepo.findByKeywordInAndPayer(keywords, value).stream()
                  .collect(
                      Collectors.toMap(EmailKeywordPayerHistory::getKeyword, history -> history));
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

    if (expense.getCategory() != null) {
      ExpenseCategory category = expense.getCategory();
      Map<String, EmailKeywordCategoryHistory> existingCategoryByKeyword =
          keywordCategoryHistoryRepo.findByKeywordInAndCategory(keywords, category).stream()
              .collect(
                  Collectors.toMap(EmailKeywordCategoryHistory::getKeyword, history -> history));
      batchUpsert(
          keywords,
          existingCategoryByKeyword,
          keyword ->
              EmailKeywordCategoryHistory.builder()
                  .keyword(keyword)
                  .category(category)
                  .occurrences(1)
                  .build(),
          keywordCategoryHistoryRepo::save);
    }
  }

  private void recordClassification(
      FinancialActivity activity, FinancialCategory category, List<String> keywords) {
    if (activity == null || category == null || keywords.isEmpty()) {
      return;
    }
    Map<String, EmailKeywordClassificationHistory> existingByKeyword =
        keywordClassificationHistoryRepo
            .findByKeywordInAndActivityIdAndFinancialCategoryId(
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
                .financialCategory(category)
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

  /** Resolves a payer by canonical name first, then by alias. */
  private Optional<Payer> resolvePayerByNameOrAlias(String name) {
    return payerRepository
        .findByNameIgnoreCase(name)
        .or(() -> payerRepository.findByAliasIgnoreCase(name));
  }

  /**
   * Increments occurrences on an existing history entry, or creates a new one with occurrences=1.
   * The find uses PESSIMISTIC_WRITE locking to prevent lost updates on concurrent saves. A
   * unique-constraint violation on insert (concurrent first-save race) is suppressed so the expense
   * save itself is never rolled back by a history conflict; other integrity violations (FK, NOT
   * NULL, etc.) re-throw.
   */
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

  /**
   * Increment-or-insert across many keys when the existing rows have already been pre-fetched in a
   * single query. Saves one query per key (vs. {@link #upsert} which would issue a SELECT per
   * call).
   */
  private <T extends HasOccurrences> void batchUpsert(
      List<String> keys,
      Map<String, T> existingByKey,
      Function<String, T> factory,
      Consumer<T> save) {
    for (String key : keys) {
      upsert(Optional.ofNullable(existingByKey.get(key)), () -> factory.apply(key), save);
    }
  }

  /**
   * Distinguishes unique-constraint races (expected on concurrent first-insert) from other
   * integrity failures (NOT NULL, FK, etc.) that indicate a real bug. SQL state {@code 23505} is
   * H2/Postgres' code for unique violation; {@code 23000} is the generic SQL standard variant.
   */
  private static boolean isUniqueConstraintViolation(DataIntegrityViolationException e) {
    Throwable cause = e.getCause();
    while (cause != null) {
      if (cause instanceof ConstraintViolationException cve) {
        String state = cve.getSQLState();
        return "23505".equals(state) || "23000".equals(state);
      }
      cause = cause.getCause();
    }
    return false;
  }
}
