package com.bookie.catalog.financialcategory.application;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfileResolution;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinancialCategoryService {

  private final Clock clock;

  private final FinancialCategoryStore financialCategoryStore;
  private final ActivityCatalog activityCatalog;
  private final ReportPolicyResolver reportPolicyResolver;

  public List<FinancialCategory> findCompatible(TransactionDirection direction, Long activityId) {
    List<FinancialCategory> activeCategories = financialCategoryStore.findAllActive();
    if (activityId == null) {
      return filterByDirection(activeCategories, direction);
    }

    FinancialActivity activity = activityCatalog.findActiveById(activityId);
    LocalDate effectiveOn = LocalDate.now(clock);
    requireReportingProfile(activity.getId(), effectiveOn);
    return filterByDirection(activeCategories, direction).stream()
        .filter(category -> isPolicyCompatible(category, activity, effectiveOn))
        .toList();
  }

  public FinancialCategory findById(Long id) {
    return financialCategoryStore
        .findById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Financial category not found: " + id));
  }

  public FinancialCategory resolve(
      Long categoryId,
      String legacyKey,
      TransactionDirection direction,
      FinancialActivity activity) {
    return resolve(categoryId, legacyKey, direction, activity, LocalDate.now(clock));
  }

  public FinancialCategory resolve(
      Long categoryId,
      String legacyKey,
      TransactionDirection direction,
      FinancialActivity activity,
      LocalDate effectiveOn) {
    FinancialCategory category;
    if (categoryId != null) {
      category = findById(categoryId);
    } else if (legacyKey != null && !legacyKey.isBlank()) {
      category =
          financialCategoryStore
              .findByKey(legacyKey)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "Unknown financial category: " + legacyKey));
    } else {
      return defaultFor(activity, direction, effectiveOn);
    }

    validateBasicCompatibility(category, direction);
    LocalDate resolutionDate = resolutionDate(effectiveOn);
    requireCompatiblePolicy(category, activity, resolutionDate, true);
    return category;
  }

  public FinancialCategory defaultFor(FinancialActivity activity, TransactionDirection direction) {
    return defaultFor(activity, direction, LocalDate.now(clock));
  }

  public FinancialCategory defaultFor(
      FinancialActivity activity, TransactionDirection direction, LocalDate effectiveOn) {
    ReportingProfileKey reportingProfileKey =
        effectiveReportingProfileKey(activity, resolutionDate(effectiveOn));
    String key =
        switch (reportingProfileKey) {
          case SCHEDULE_E -> direction == TransactionDirection.INCOME ? "RENTAL_INCOME" : "OTHER";
          case SCHEDULE_C ->
              direction == TransactionDirection.INCOME
                  ? "SERVICE_INCOME"
                  : "SCHEDULE_C_OTHER_EXPENSE";
          case W2 ->
              direction == TransactionDirection.INCOME ? "WAGES" : "EMPLOYMENT_OTHER_EXPENSE";
          case NONE -> direction == TransactionDirection.INCOME ? "OTHER_INCOME" : "OTHER_EXPENSE";
        };
    FinancialCategory category =
        financialCategoryStore
            .findByKey(key)
            .orElseThrow(
                () -> new IllegalStateException("Required financial category is missing: " + key));
    requireCompatiblePolicy(category, activity, resolutionDate(effectiveOn), false);
    return category;
  }

  public boolean isCompatible(
      FinancialCategory category,
      FinancialActivity activity,
      TransactionDirection direction,
      LocalDate effectiveOn) {
    if (category == null || !category.isActive() || category.getDirection() != direction) {
      return false;
    }
    return isPolicyCompatible(category, activity, resolutionDate(effectiveOn));
  }

  public boolean usesReportingProfile(
      FinancialActivity activity,
      TaxTreatment expectedLegacyTreatment,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    LocalDate periodStart = resolutionDate(effectiveFrom);
    LocalDate periodEnd = resolutionDate(effectiveTo);
    ReportingProfilePeriodResolution resolution =
        reportPolicyResolver.resolveProfileForPeriod(activity.getId(), periodStart, periodEnd);
    if (!(resolution instanceof ReportingProfilePeriodResolution.Resolved resolved)
        || !resolved.reportingProfile().isActive()) {
      throw parityFailure("Activity reporting profile period is missing or ambiguous");
    }
    ReportingProfile reportingProfile = resolved.reportingProfile();
    return reportingProfile.getKey() == ReportingProfileKey.valueOf(expectedLegacyTreatment.name());
  }

  public boolean isIncludedInReport(
      FinancialCategory category,
      FinancialActivity activity,
      TaxTreatment expectedLegacyTreatment,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    boolean policyIncludesAtStart =
        policyIncludesReportCategory(category, activity, resolutionDate(effectiveFrom));
    boolean policyIncludesAtEnd =
        policyIncludesReportCategory(category, activity, resolutionDate(effectiveTo));
    if (policyIncludesAtStart != policyIncludesAtEnd) {
      throw parityFailure("Report category mapping changes within the report period");
    }
    return policyIncludesAtStart;
  }

  private List<FinancialCategory> filterByDirection(
      List<FinancialCategory> categories, TransactionDirection direction) {
    if (direction == null) {
      return categories;
    }
    return categories.stream().filter(category -> category.getDirection() == direction).toList();
  }

  private boolean isPolicyCompatible(
      FinancialCategory category, FinancialActivity activity, LocalDate effectiveOn) {
    ReportPolicyResolution resolution =
        reportPolicyResolver.resolve(activity.getId(), category.getId(), effectiveOn);
    if (resolution instanceof ReportPolicyResolution.Resolved resolved) {
      return resolved.neutralCategory().isActive()
          && resolved.mappingActive()
          && resolved.neutralCategory().getDirection() == category.getDirection();
    }
    if (resolution instanceof ReportPolicyResolution.Incompatible) {
      return false;
    }
    if (resolution instanceof ReportPolicyResolution.Missing missing
        && missing.component() == ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING) {
      return false;
    }
    throw parityFailure("Report-policy category inventory is incomplete or ambiguous");
  }

  private ReportingProfileKey effectiveReportingProfileKey(
      FinancialActivity activity, LocalDate effectiveOn) {
    return requireReportingProfile(activity.getId(), effectiveOn).getKey();
  }

  private ReportingProfile requireReportingProfile(Long activityId, LocalDate effectiveOn) {
    ReportingProfileResolution resolution =
        reportPolicyResolver.resolveProfile(activityId, effectiveOn);
    if (resolution instanceof ReportingProfileResolution.Resolved resolved) {
      if (!resolved.reportingProfile().isActive()) {
        throw parityFailure("Resolved reporting profile is inactive");
      }
      return resolved.reportingProfile();
    }
    throw parityFailure("Activity reporting profile is missing or ambiguous");
  }

  private void requireCompatiblePolicy(
      FinancialCategory category,
      FinancialActivity activity,
      LocalDate effectiveOn,
      boolean incompatibleIsBadRequest) {
    ReportPolicyResolution resolution =
        reportPolicyResolver.resolve(activity.getId(), category.getId(), effectiveOn);
    if (resolution instanceof ReportPolicyResolution.Resolved resolved) {
      if (!resolved.neutralCategory().isActive()
          || !resolved.mappingActive()
          || resolved.neutralCategory().getDirection() != category.getDirection()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Financial category is incompatible with the transaction");
      }
      return;
    }
    if (incompatibleIsBadRequest
        && (resolution instanceof ReportPolicyResolution.Incompatible
            || (resolution instanceof ReportPolicyResolution.Missing missing
                && missing.component()
                    == ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Category reporting profile does not match the selected financial activity");
    }
    throw parityFailure("Report-policy resolution is missing or ambiguous");
  }

  private boolean policyIncludesReportCategory(
      FinancialCategory category, FinancialActivity activity, LocalDate effectiveOn) {
    ReportPolicyResolution resolution =
        reportPolicyResolver.resolve(activity.getId(), category.getId(), effectiveOn);
    if (resolution instanceof ReportPolicyResolution.Incompatible
        || (resolution instanceof ReportPolicyResolution.Missing missing
            && missing.component()
                == ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING)) {
      return false;
    }
    if (!(resolution instanceof ReportPolicyResolution.Resolved resolved)) {
      throw parityFailure("Report category mapping is missing or ambiguous");
    }
    if (!category.getId().equals(resolved.legacyCategoryId())
        || resolved.reportingProfile().getKey()
            != ReportingProfileKey.valueOf(category.getTaxTreatment().name())
        || resolved.neutralCategory().getDirection() != category.getDirection()
        || !Objects.equals(resolved.reportLine(), category.getTaxLine())
        || resolved.mappingActive() != category.isActive()) {
      throw parityFailure("Resolved report mapping differs from legacy category output");
    }
    return true;
  }

  private void validateBasicCompatibility(
      FinancialCategory category, TransactionDirection direction) {
    if (!category.isActive()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Financial category is inactive: " + category.getId());
    }
    if (category.getDirection() != direction) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Category direction does not match the transaction");
    }
  }

  private LocalDate resolutionDate(LocalDate effectiveOn) {
    return effectiveOn == null ? LocalDate.now(clock) : effectiveOn;
  }

  private ReportPolicyParityException parityFailure(String message) {
    return new ReportPolicyParityException(message);
  }
}
