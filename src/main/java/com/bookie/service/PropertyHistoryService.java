package com.bookie.service;

import com.bookie.model.EmailKeywordCategoryHistory;
import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.HasOccurrences;
import com.bookie.model.ParsedEmailKeywords;
import com.bookie.model.Payer;
import com.bookie.model.PayerCategoryHistory;
import com.bookie.model.PayerPropertyHistory;
import com.bookie.model.Property;
import com.bookie.repository.EmailKeywordCategoryHistoryRepository;
import com.bookie.repository.EmailKeywordPayerHistoryRepository;
import com.bookie.repository.EmailKeywordPropertyHistoryRepository;
import com.bookie.repository.ParsedEmailKeywordsRepository;
import com.bookie.repository.PayerCategoryHistoryRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import com.bookie.util.AccountNumbers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
    parsedKeywordsRepo.deleteBySourceId(sourceId);
    List<ParsedEmailKeywords> entities =
        AccountNumbers.normalize(keywords).stream()
            .distinct()
            .map(k -> ParsedEmailKeywords.builder().sourceId(sourceId).keyword(k).build())
            .toList();
    parsedKeywordsRepo.saveAll(entities);
  }

  /**
   * Records payer→property and keyword→property/payer associations from a confirmed expense.
   * Keywords are looked up from temporary storage (if the expense originated from an Outlook email)
   * and deleted after recording.
   */
  @Transactional
  public void record(Expense expense) {
    Property property = expense.getProperty();
    if (property == null) {
      return;
    }

    // Resolve the full Payer from the DB — the request body only contains { id } with no name
    Optional<Payer> payer =
        expense.getPayer() != null
            ? payerRepository.findById(expense.getPayer().getId())
            : Optional.empty();

    // Resolve the full Property from the DB for the same reason
    Optional<Property> resolvedProperty = propertyRepository.findById(property.getId());
    if (resolvedProperty.isEmpty()) {
      return;
    }
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

    if (expense.getSourceType() == ExpenseSource.OUTLOOK_EMAIL && expense.getSourceId() != null) {
      List<String> keywords =
          parsedKeywordsRepo.findBySourceId(expense.getSourceId()).stream()
              .map(ParsedEmailKeywords::getKeyword)
              .toList();
      keywords.forEach(
          k ->
              upsert(
                  keywordPropertyHistoryRepo.findByKeywordAndPropertyId(k, fullProperty.getId()),
                  () ->
                      EmailKeywordPropertyHistory.builder()
                          .keyword(k)
                          .property(fullProperty)
                          .occurrences(1)
                          .build(),
                  keywordPropertyHistoryRepo::save));
      payer.ifPresent(
          p ->
              keywords.forEach(
                  k ->
                      upsert(
                          keywordPayerHistoryRepo.findByKeywordAndPayer(k, p),
                          () ->
                              EmailKeywordPayerHistory.builder()
                                  .keyword(k)
                                  .payer(p)
                                  .occurrences(1)
                                  .build(),
                          keywordPayerHistoryRepo::save)));
      if (expense.getCategory() != null) {
        ExpenseCategory cat = expense.getCategory();
        keywords.forEach(
            k ->
                upsert(
                    keywordCategoryHistoryRepo.findByKeywordAndCategory(k, cat),
                    () ->
                        EmailKeywordCategoryHistory.builder()
                            .keyword(k)
                            .category(cat)
                            .occurrences(1)
                            .build(),
                    keywordCategoryHistoryRepo::save));
      }
      parsedKeywordsRepo.deleteBySourceId(expense.getSourceId());
    }
  }

  /**
   * Returns property hints ranked by frequency for use as AI tool context.
   *
   * @param payerName payer name or alias to look up history for, or null
   * @param keywords normalized keywords extracted from the email
   * @return hints in the form "Bob's Plumbing → 123 Main St (4 times)"
   */
  public List<String> getPropertyHints(String payerName, List<String> keywords) {
    var hints = new ArrayList<String>();

    if (StringUtils.isNotBlank(payerName)) {
      resolvePayerByNameOrAlias(payerName)
          .ifPresent(
              payer ->
                  payerPropertyHistoryRepo
                      .findByPayerIdOrderByOccurrencesDesc(payer.getId())
                      .forEach(
                          h ->
                              hints.add(
                                  "%s → %s (%d times)"
                                      .formatted(
                                          h.getPayer().getName(),
                                          h.getProperty().getName(),
                                          h.getOccurrences()))));
    }

    if (!CollectionUtils.isEmpty(keywords)) {
      keywordPropertyHistoryRepo
          .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
          .forEach(
              h ->
                  hints.add(
                      "Keyword '%s' → %s (%d times)"
                          .formatted(
                              h.getKeyword(), h.getProperty().getName(), h.getOccurrences())));
    }

    return hints;
  }

  /**
   * Returns a category hint for a payer only when there is strong historical consistency: the top
   * category must account for ≥90% of all uses and the payer must have at least 3 confirmed
   * expenses. Multi-category vendors (e.g. Amazon) return an empty list so the AI decides based on
   * the actual items purchased.
   */
  public List<String> getCategoryForPayer(String payerName) {
    return resolvePayerByNameOrAlias(payerName)
        .map(
            payer -> {
              List<PayerCategoryHistory> rows =
                  payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(payer.getId());
              int total = rows.stream().mapToInt(PayerCategoryHistory::getOccurrences).sum();
              if (rows.isEmpty() || total < 3) {
                return List.<String>of();
              }
              PayerCategoryHistory top = rows.get(0);
              if ((double) top.getOccurrences() / total < 0.9) {
                return List.<String>of();
              }
              return List.of(
                  "%s (%d times)".formatted(top.getCategory().name(), top.getOccurrences()));
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

  public List<String> getPayerHints(List<String> keywords) {
    if (CollectionUtils.isEmpty(keywords)) {
      return List.of();
    }
    return keywordPayerHistoryRepo
        .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
        .stream()
        .map(
            h ->
                "Keyword '%s' → %s (%d times)"
                    .formatted(h.getKeyword(), h.getPayer().getName(), h.getOccurrences()))
        .toList();
  }

  public List<String> getCategoryHints(List<String> keywords) {
    if (CollectionUtils.isEmpty(keywords)) {
      return List.of();
    }
    return keywordCategoryHistoryRepo
        .findByKeywordInOrderByOccurrencesDesc(AccountNumbers.normalize(keywords))
        .stream()
        .map(
            h ->
                "Keyword '%s' → %s (%d times)"
                    .formatted(h.getKeyword(), h.getCategory().name(), h.getOccurrences()))
        .toList();
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
   * DataIntegrityViolationException on insert (concurrent first-save race) is suppressed so the
   * expense save itself is never rolled back by a history conflict.
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
            // Another thread inserted the same key concurrently; one write wins, the other skips
            log.debug("upsert: concurrent insert detected, skipping: {}", e.getMessage());
          }
        });
  }
}
