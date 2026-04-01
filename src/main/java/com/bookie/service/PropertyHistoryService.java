package com.bookie.service;

import com.bookie.model.EmailKeywordCategoryHistory;
import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Records and retrieves property/payer associations learned from confirmed expenses. Associations
 * are accumulated over time and used as weighted hints during email parsing to improve the accuracy
 * of AI-suggested property and payer matches.
 */
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

    payer.ifPresent(p -> upsertPayerProperty(p, fullProperty));

    if (payer.isPresent() && expense.getCategory() != null) {
      upsertPayerCategory(payer.get(), expense.getCategory());
    }

    if (expense.getSourceType() == ExpenseSource.OUTLOOK_EMAIL && expense.getSourceId() != null) {
      List<String> keywords =
          parsedKeywordsRepo.findBySourceId(expense.getSourceId()).stream()
              .map(ParsedEmailKeywords::getKeyword)
              .toList();
      keywords.forEach(k -> upsertKeywordProperty(k, fullProperty));
      payer.ifPresent(p -> keywords.forEach(k -> upsertKeywordPayer(k, p)));
      if (expense.getCategory() != null) {
        keywords.forEach(k -> upsertKeywordCategory(k, expense.getCategory()));
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

    if (StringUtils.hasText(payerName)) {
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
   * Returns category hints for a payer, ranked by frequency. Resolves the payer by name or alias.
   */
  public List<String> getCategoryForPayer(String payerName) {
    return resolvePayerByNameOrAlias(payerName)
        .map(
            payer ->
                payerCategoryHistoryRepo
                    .findByPayer_IdOrderByOccurrencesDesc(payer.getId())
                    .stream()
                    .map(h -> "%s (%d times)".formatted(h.getCategory().name(), h.getOccurrences()))
                    .toList())
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

  private void upsertPayerCategory(Payer payer, ExpenseCategory category) {
    payerCategoryHistoryRepo
        .findByPayerAndCategory(payer, category)
        .ifPresentOrElse(
            h -> {
              h.setOccurrences(h.getOccurrences() + 1);
              payerCategoryHistoryRepo.save(h);
            },
            () ->
                payerCategoryHistoryRepo.save(
                    PayerCategoryHistory.builder()
                        .payer(payer)
                        .category(category)
                        .occurrences(1)
                        .build()));
  }

  private void upsertPayerProperty(Payer payer, Property property) {
    payerPropertyHistoryRepo
        .findByPayerIdAndPropertyId(payer.getId(), property.getId())
        .ifPresentOrElse(
            h -> {
              h.setOccurrences(h.getOccurrences() + 1);
              payerPropertyHistoryRepo.save(h);
            },
            () ->
                payerPropertyHistoryRepo.save(
                    PayerPropertyHistory.builder()
                        .payer(payer)
                        .property(property)
                        .occurrences(1)
                        .build()));
  }

  private void upsertKeywordProperty(String keyword, Property property) {
    keywordPropertyHistoryRepo
        .findByKeywordAndPropertyId(keyword, property.getId())
        .ifPresentOrElse(
            h -> {
              h.setOccurrences(h.getOccurrences() + 1);
              keywordPropertyHistoryRepo.save(h);
            },
            () ->
                keywordPropertyHistoryRepo.save(
                    EmailKeywordPropertyHistory.builder()
                        .keyword(keyword)
                        .property(property)
                        .occurrences(1)
                        .build()));
  }

  private void upsertKeywordCategory(String keyword, ExpenseCategory category) {
    keywordCategoryHistoryRepo
        .findByKeywordAndCategory(keyword, category)
        .ifPresentOrElse(
            h -> {
              h.setOccurrences(h.getOccurrences() + 1);
              keywordCategoryHistoryRepo.save(h);
            },
            () ->
                keywordCategoryHistoryRepo.save(
                    EmailKeywordCategoryHistory.builder()
                        .keyword(keyword)
                        .category(category)
                        .occurrences(1)
                        .build()));
  }

  private void upsertKeywordPayer(String keyword, Payer payer) {
    keywordPayerHistoryRepo
        .findByKeywordAndPayer(keyword, payer)
        .ifPresentOrElse(
            h -> {
              h.setOccurrences(h.getOccurrences() + 1);
              keywordPayerHistoryRepo.save(h);
            },
            () ->
                keywordPayerHistoryRepo.save(
                    EmailKeywordPayerHistory.builder()
                        .keyword(keyword)
                        .payer(payer)
                        .occurrences(1)
                        .build()));
  }
}
