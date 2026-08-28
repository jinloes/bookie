package com.bookie.service;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HistoryHint;
import com.bookie.model.TransactionDirection;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves intake classifications from explicit context and confirmed history. The model never
 * chooses an owner, activity, or tax treatment; those values always come from a stored activity.
 */
@Service
@RequiredArgsConstructor
public class AutomatedIntakeClassificationService {

  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryService financialCategoryService;
  private final ClassificationHistory classificationHistory;

  public Resolution resolve(
      TransactionDirection direction,
      Long configuredActivityId,
      String suggestedPropertyName,
      List<String> keywords,
      String counterpartyName) {
    return resolve(
        direction,
        configuredActivityId,
        suggestedPropertyName,
        keywords,
        counterpartyName,
        LocalDate.now());
  }

  public Resolution resolve(
      TransactionDirection direction,
      Long configuredActivityId,
      String suggestedPropertyName,
      List<String> keywords,
      String counterpartyName,
      LocalDate effectiveOn) {
    boolean ambiguous = false;
    FinancialActivity activity;

    if (configuredActivityId != null) {
      activity = activityCatalog.findActiveById(configuredActivityId);
    } else {
      List<String> activityCandidates =
          distinctValues(classificationHistory.getActivityHints(keywords));
      if (activityCandidates.size() == 1) {
        activity = findActiveActivityByName(activityCandidates.get(0));
      } else if (activityCandidates.isEmpty() && StringUtils.isNotBlank(suggestedPropertyName)) {
        activity = activityCatalog.resolveForSuggestedProperty(suggestedPropertyName);
      } else {
        activity = activityCatalog.getNeedsClassification();
        ambiguous = true;
      }
    }

    if (ActivityCatalog.NEEDS_CLASSIFICATION_KEY.equals(activity.getSystemKey())) {
      ambiguous = true;
    }

    List<String> categoryCandidates =
        distinctValues(
            classificationHistory.getFinancialCategoryHints(activity.getId(), direction, keywords));
    if (categoryCandidates.isEmpty() && activity.getTaxTreatment() == TaxTreatment.SCHEDULE_E) {
      categoryCandidates = legacyRentalCategoryCandidates(keywords, counterpartyName);
    }

    FinancialCategory category;
    if (categoryCandidates.size() == 1) {
      try {
        category =
            financialCategoryService.resolve(
                null, categoryCandidates.get(0), direction, activity, effectiveOn);
      } catch (ResponseStatusException staleHistory) {
        category = financialCategoryService.defaultFor(activity, direction, effectiveOn);
        ambiguous = true;
      }
    } else {
      category = financialCategoryService.defaultFor(activity, direction, effectiveOn);
      ambiguous = true;
    }

    return new Resolution(activity, category, ambiguous);
  }

  public Resolution resolveFromFreeform(
      TransactionDirection direction, String userMessage, String counterpartyName) {
    return resolveFromFreeform(direction, userMessage, counterpartyName, LocalDate.now());
  }

  public Resolution resolveFromFreeform(
      TransactionDirection direction,
      String userMessage,
      String counterpartyName,
      LocalDate effectiveOn) {
    String normalized = StringUtils.defaultString(userMessage).toLowerCase(Locale.ROOT);
    List<FinancialActivity> matches =
        activityCatalog.findAll().stream()
            .filter(FinancialActivity::isActive)
            .filter(activity -> activity.getOwner() != null && activity.getOwner().isActive())
            .filter(activity -> StringUtils.isNotBlank(activity.getName()))
            .filter(activity -> normalized.contains(activity.getName().toLowerCase(Locale.ROOT)))
            .toList();
    Long configuredActivityId = matches.size() == 1 ? matches.get(0).getId() : null;
    return resolve(direction, configuredActivityId, null, List.of(), counterpartyName, effectiveOn);
  }

  private FinancialActivity findActiveActivityByName(String name) {
    return activityCatalog.findAll().stream()
        .filter(FinancialActivity::isActive)
        .filter(activity -> activity.getOwner() != null && activity.getOwner().isActive())
        .filter(activity -> activity.getName().equalsIgnoreCase(name))
        .findFirst()
        .orElseGet(activityCatalog::getNeedsClassification);
  }

  private List<String> legacyRentalCategoryCandidates(
      List<String> keywords, String counterpartyName) {
    List<String> keywordCandidates =
        distinctValues(classificationHistory.getCategoryHints(keywords));
    if (!keywordCandidates.isEmpty()) {
      return keywordCandidates;
    }
    return distinctValues(classificationHistory.getCategoryForPayer(counterpartyName));
  }

  private List<String> distinctValues(List<HistoryHint> hints) {
    Set<String> values = new LinkedHashSet<>();
    for (HistoryHint hint : hints) {
      if (hint != null && StringUtils.isNotBlank(hint.value())) {
        values.add(hint.value());
      }
    }
    return List.copyOf(values);
  }

  public record Resolution(
      FinancialActivity activity, FinancialCategory category, boolean classificationAmbiguous) {}
}
