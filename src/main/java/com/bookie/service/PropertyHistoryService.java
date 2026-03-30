package com.bookie.service;

import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.ParsedEmailKeywords;
import com.bookie.model.Payer;
import com.bookie.model.PayerPropertyHistory;
import com.bookie.model.Property;
import com.bookie.repository.EmailKeywordPayerHistoryRepository;
import com.bookie.repository.EmailKeywordPropertyHistoryRepository;
import com.bookie.repository.ParsedEmailKeywordsRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and retrieves property/payer associations learned from confirmed expenses. Associations
 * are accumulated over time and used as weighted hints during email parsing to improve the accuracy
 * of AI-suggested property and payer matches.
 */
@Service
@RequiredArgsConstructor
public class PropertyHistoryService {

  private final PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  private final EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  private final EmailKeywordPayerHistoryRepository keywordPayerHistoryRepo;
  private final ParsedEmailKeywordsRepository parsedKeywordsRepo;
  private final PayerRepository payerRepository;
  private final PropertyRepository propertyRepository;

  /**
   * Stores keywords extracted from an email at parse time, keyed by the message ID. These are
   * consumed and deleted when the resulting expense is saved.
   */
  @Transactional
  public void storeKeywords(String sourceId, List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) return;
    keywords.stream()
        .map(k -> k.toLowerCase().trim())
        .filter(k -> !k.isBlank())
        .distinct()
        .map(k -> ParsedEmailKeywords.builder().sourceId(sourceId).keyword(k).build())
        .forEach(parsedKeywordsRepo::save);
  }

  /**
   * Records payer→property and keyword→property/payer associations from a confirmed expense.
   * Keywords are looked up from temporary storage (if the expense originated from an Outlook email)
   * and deleted after recording.
   */
  @Transactional
  public void record(Expense expense) {
    Property property = expense.getProperty();
    if (property == null) return;

    // Resolve the full Payer from the DB — the request body only contains { id } with no name
    Optional<Payer> payer =
        expense.getPayer() != null
            ? payerRepository.findById(expense.getPayer().getId())
            : Optional.empty();

    // Resolve the full Property from the DB for the same reason
    Optional<Property> resolvedProperty = propertyRepository.findById(property.getId());
    if (resolvedProperty.isEmpty()) return;
    Property fullProperty = resolvedProperty.get();

    payer.ifPresent(p -> upsertPayerProperty(p, fullProperty));

    if (expense.getSourceType() == ExpenseSource.OUTLOOK_EMAIL && expense.getSourceId() != null) {
      List<String> keywords =
          parsedKeywordsRepo.findBySourceId(expense.getSourceId()).stream()
              .map(ParsedEmailKeywords::getKeyword)
              .toList();
      keywords.forEach(k -> upsertKeywordProperty(k, fullProperty));
      payer.ifPresent(p -> keywords.forEach(k -> upsertKeywordPayer(k, p.getName())));
      parsedKeywordsRepo.deleteBySourceId(expense.getSourceId());
    }
  }

  /**
   * Returns property hints ranked by frequency for use as AI tool context.
   *
   * @param payerName known payer name to look up history for, or null
   * @param keywords normalized keywords extracted from the email
   * @return hints in the form "Bob's Plumbing → 123 Main St (4 times)"
   */
  public List<String> getPropertyHints(String payerName, List<String> keywords) {
    var hints = new java.util.ArrayList<String>();

    if (payerName != null && !payerName.isBlank()) {
      payerPropertyHistoryRepo
          .findByPayer_NameIgnoreCaseOrderByOccurrencesDesc(payerName)
          .forEach(
              h ->
                  hints.add(
                      "%s → %s (%d times)"
                          .formatted(
                              h.getPayer().getName(),
                              h.getProperty().getName(),
                              h.getOccurrences())));
    }

    if (keywords != null && !keywords.isEmpty()) {
      List<String> normalized =
          keywords.stream().map(k -> k.toLowerCase().trim()).filter(k -> !k.isBlank()).toList();
      keywordPropertyHistoryRepo
          .findByKeywordInOrderByOccurrencesDesc(normalized)
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
   * Returns payer hints from keywords ranked by frequency for use as AI tool context.
   *
   * @param keywords normalized keywords extracted from the email
   * @return hints in the form "Keyword 'acc-7891' → National Grid (3 times)"
   */
  public List<EmailKeywordPayerHistory> getAllPayerKeywords() {
    return keywordPayerHistoryRepo.findAll();
  }

  public List<EmailKeywordPropertyHistory> getAllPropertyKeywords() {
    return keywordPropertyHistoryRepo.findAll();
  }

  public List<String> getPayerHints(List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) return List.of();
    List<String> normalized =
        keywords.stream().map(k -> k.toLowerCase().trim()).filter(k -> !k.isBlank()).toList();
    return keywordPayerHistoryRepo.findByKeywordInOrderByOccurrencesDesc(normalized).stream()
        .map(
            h ->
                "Keyword '%s' → %s (%d times)"
                    .formatted(h.getKeyword(), h.getPayerName(), h.getOccurrences()))
        .toList();
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

  private void upsertKeywordPayer(String keyword, String payerName) {
    keywordPayerHistoryRepo
        .findByKeywordAndPayerName(keyword, payerName)
        .ifPresentOrElse(
            h -> {
              h.setOccurrences(h.getOccurrences() + 1);
              keywordPayerHistoryRepo.save(h);
            },
            () ->
                keywordPayerHistoryRepo.save(
                    EmailKeywordPayerHistory.builder()
                        .keyword(keyword)
                        .payerName(payerName)
                        .occurrences(1)
                        .build()));
  }
}
