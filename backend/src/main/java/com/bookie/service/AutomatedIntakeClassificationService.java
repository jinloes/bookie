package com.bookie.service;

import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HistoryHint;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
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

  private final FinancialActivityService financialActivityService;
  private final FinancialCategoryService financialCategoryService;
  private final PropertyHistoryService propertyHistoryService;

  public Resolution resolve(
      TransactionDirection direction,
      Long configuredActivityId,
      String suggestedPropertyName,
      List<String> keywords,
      String counterpartyName) {
    boolean ambiguous = false;
    FinancialActivity activity;

    if (configuredActivityId != null) {
      activity = financialActivityService.findActiveById(configuredActivityId);
    } else {
      List<String> activityCandidates =
          distinctValues(propertyHistoryService.getActivityHints(keywords));
      if (activityCandidates.size() == 1) {
        activity = findActiveActivityByName(activityCandidates.get(0));
      } else if (activityCandidates.isEmpty() && StringUtils.isNotBlank(suggestedPropertyName)) {
        activity = financialActivityService.resolveForSuggestedProperty(suggestedPropertyName);
      } else {
        activity = financialActivityService.getNeedsClassification();
        ambiguous = true;
      }
    }

    if (FinancialActivityService.NEEDS_CLASSIFICATION_KEY.equals(activity.getSystemKey())) {
      ambiguous = true;
    }

    List<String> categoryCandidates =
        distinctValues(
            propertyHistoryService.getFinancialCategoryHints(
                activity.getId(), direction, keywords));
    if (categoryCandidates.isEmpty() && activity.getTaxTreatment() == TaxTreatment.SCHEDULE_E) {
      categoryCandidates = legacyRentalCategoryCandidates(keywords, counterpartyName);
    }

    FinancialCategory category;
    if (categoryCandidates.size() == 1) {
      try {
        category =
            financialCategoryService.resolve(null, categoryCandidates.get(0), direction, activity);
      } catch (ResponseStatusException staleHistory) {
        category = financialCategoryService.defaultFor(activity, direction);
        ambiguous = true;
      }
    } else {
      category = financialCategoryService.defaultFor(activity, direction);
      ambiguous = true;
    }

    return new Resolution(activity, category, ambiguous);
  }

  public Resolution resolveFromFreeform(
      TransactionDirection direction, String userMessage, String counterpartyName) {
    String normalized = StringUtils.defaultString(userMessage).toLowerCase(Locale.ROOT);
    List<FinancialActivity> matches =
        financialActivityService.findAll().stream()
            .filter(FinancialActivity::isActive)
            .filter(activity -> activity.getOwner() != null && activity.getOwner().isActive())
            .filter(activity -> StringUtils.isNotBlank(activity.getName()))
            .filter(activity -> normalized.contains(activity.getName().toLowerCase(Locale.ROOT)))
            .toList();
    Long configuredActivityId = matches.size() == 1 ? matches.get(0).getId() : null;
    return resolve(direction, configuredActivityId, null, List.of(), counterpartyName);
  }

  private FinancialActivity findActiveActivityByName(String name) {
    return financialActivityService.findAll().stream()
        .filter(FinancialActivity::isActive)
        .filter(activity -> activity.getOwner() != null && activity.getOwner().isActive())
        .filter(activity -> activity.getName().equalsIgnoreCase(name))
        .findFirst()
        .orElseGet(financialActivityService::getNeedsClassification);
  }

  private List<String> legacyRentalCategoryCandidates(
      List<String> keywords, String counterpartyName) {
    List<String> keywordCandidates =
        distinctValues(propertyHistoryService.getCategoryHints(keywords));
    if (!keywordCandidates.isEmpty()) {
      return keywordCandidates;
    }
    return distinctValues(propertyHistoryService.getCategoryForPayer(counterpartyName));
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
