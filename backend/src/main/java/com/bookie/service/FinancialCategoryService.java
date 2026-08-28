package com.bookie.service;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportPolicyReadMode;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfileResolution;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.FinancialCategoryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinancialCategoryService {

  private final FinancialCategoryRepository financialCategoryRepository;
  private final ActivityCatalog activityCatalog;
  private final ReportPolicyResolver reportPolicyResolver;

  @Value("${bookie.report-policy.mode:NEW}")
  private ReportPolicyReadMode reportPolicyReadMode = ReportPolicyReadMode.NEW;

  public List<FinancialCategory> findCompatible(TransactionDirection direction, Long activityId) {
    List<FinancialCategory> activeCategories =
        financialCategoryRepository.findAllByActiveTrueOrderByDirectionAscLabelAsc();
    if (activityId == null) {
      return filterByDirection(activeCategories, direction);
    }

    FinancialActivity activity = activityCatalog.findActiveById(activityId);
    List<FinancialCategory> legacyCategories =
        filterByDirection(activeCategories, direction).stream()
            .filter(category -> category.getTaxTreatment() == activity.getTaxTreatment())
            .toList();
    if (reportPolicyReadMode == ReportPolicyReadMode.LEGACY) {
      return legacyCategories;
    }

    LocalDate effectiveOn = LocalDate.now();
    ReportingProfile reportingProfile = requireReportingProfile(activity.getId(), effectiveOn);
    if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
      assertLegacyProfileMatches(activity, reportingProfile);
    }
    List<FinancialCategory> policyCategories =
        filterByDirection(activeCategories, direction).stream()
            .filter(category -> isPolicyCompatible(category, activity, effectiveOn))
            .toList();
    if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
      assertSameCategories(legacyCategories, policyCategories);
      return legacyCategories;
    }
    return policyCategories;
  }

  public FinancialCategory findById(Long id) {
    return financialCategoryRepository
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
    return resolve(categoryId, legacyKey, direction, activity, LocalDate.now());
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
          financialCategoryRepository
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
    if (reportPolicyReadMode == ReportPolicyReadMode.LEGACY) {
      validateLegacyProfile(category, activity);
      return category;
    }
    if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
      validateLegacyProfile(category, activity);
      assertPolicyParity(category, activity, resolutionDate);
      return category;
    }
    requireCompatiblePolicy(category, activity, resolutionDate, true);
    return category;
  }

  public FinancialCategory defaultFor(FinancialActivity activity, TransactionDirection direction) {
    return defaultFor(activity, direction, LocalDate.now());
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
        financialCategoryRepository
            .findByKey(key)
            .orElseThrow(
                () -> new IllegalStateException("Required financial category is missing: " + key));
    if (reportPolicyReadMode != ReportPolicyReadMode.LEGACY) {
      requireCompatiblePolicy(category, activity, resolutionDate(effectiveOn), false);
    }
    return category;
  }

  public boolean isCompatible(
      FinancialCategory category,
      FinancialActivity activity,
      TransactionDirection direction,
      LocalDate effectiveOn) {
    if (category == null
        || !category.isActive()
        || category.getDirection() != direction
        || (reportPolicyReadMode != ReportPolicyReadMode.NEW
            && category.getTaxTreatment() != activity.getTaxTreatment())) {
      return false;
    }
    if (reportPolicyReadMode == ReportPolicyReadMode.LEGACY) {
      return true;
    }
    if (reportPolicyReadMode == ReportPolicyReadMode.NEW) {
      return isPolicyCompatible(category, activity, resolutionDate(effectiveOn));
    }
    requireCompatiblePolicy(category, activity, resolutionDate(effectiveOn), false);
    return true;
  }

  public boolean usesReportingProfile(
      FinancialActivity activity,
      TaxTreatment expectedLegacyTreatment,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    boolean legacyMatches = activity.getTaxTreatment() == expectedLegacyTreatment;
    if (reportPolicyReadMode == ReportPolicyReadMode.LEGACY) {
      return legacyMatches;
    }

    LocalDate periodStart = resolutionDate(effectiveFrom);
    LocalDate periodEnd = resolutionDate(effectiveTo);
    ReportingProfilePeriodResolution resolution =
        reportPolicyResolver.resolveProfileForPeriod(activity.getId(), periodStart, periodEnd);
    if (!(resolution instanceof ReportingProfilePeriodResolution.Resolved resolved)
        || !resolved.reportingProfile().isActive()) {
      throw parityFailure("Activity reporting profile period is missing or ambiguous");
    }
    ReportingProfile reportingProfile = resolved.reportingProfile();
    ReportingProfileKey legacyKey = ReportingProfileKey.valueOf(activity.getTaxTreatment().name());
    if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
      if (reportingProfile.getKey() != legacyKey) {
        throw parityFailure("Activity reporting profile differs from its legacy treatment");
      }
      return legacyMatches;
    }
    return reportingProfile.getKey() == ReportingProfileKey.valueOf(expectedLegacyTreatment.name());
  }

  public boolean isIncludedInReport(
      FinancialCategory category,
      FinancialActivity activity,
      TaxTreatment expectedLegacyTreatment,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    boolean legacyIncludes = category.getTaxTreatment() == expectedLegacyTreatment;
    if (reportPolicyReadMode == ReportPolicyReadMode.LEGACY) {
      return legacyIncludes;
    }

    boolean policyIncludesAtStart =
        policyIncludesReportCategory(category, activity, resolutionDate(effectiveFrom));
    boolean policyIncludesAtEnd =
        policyIncludesReportCategory(category, activity, resolutionDate(effectiveTo));
    if (policyIncludesAtStart != policyIncludesAtEnd) {
      throw parityFailure("Report category mapping changes within the report period");
    }
    if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
      if (legacyIncludes != policyIncludesAtStart) {
        throw parityFailure("Report category inclusion differs from legacy behavior");
      }
      return legacyIncludes;
    }
    return policyIncludesAtStart;
  }

  public ExpenseCategory toLegacyExpenseCategory(FinancialCategory category) {
    try {
      return ExpenseCategory.valueOf(category.getKey());
    } catch (IllegalArgumentException ignored) {
      return ExpenseCategory.OTHER;
    }
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
    ReportingProfileKey legacyKey = ReportingProfileKey.valueOf(activity.getTaxTreatment().name());
    if (reportPolicyReadMode == ReportPolicyReadMode.LEGACY) {
      return legacyKey;
    }
    ReportingProfile reportingProfile = requireReportingProfile(activity.getId(), effectiveOn);
    if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
      assertLegacyProfileMatches(activity, reportingProfile);
      return legacyKey;
    }
    return reportingProfile.getKey();
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
        if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
          throw parityFailure("Resolved report policy differs from legacy category behavior");
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Financial category is incompatible with the transaction");
      }
      if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
        assertResolvedPolicyMatchesLegacy(category, activity, resolved);
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

  private void assertPolicyParity(
      FinancialCategory category, FinancialActivity activity, LocalDate effectiveOn) {
    requireCompatiblePolicy(category, activity, effectiveOn, false);
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

  private void assertResolvedPolicyMatchesLegacy(
      FinancialCategory category,
      FinancialActivity activity,
      ReportPolicyResolution.Resolved resolved) {
    if (!category.getId().equals(resolved.legacyCategoryId())
        || resolved.reportingProfile().getKey()
            != ReportingProfileKey.valueOf(activity.getTaxTreatment().name())
        || resolved.neutralCategory().getDirection() != category.getDirection()
        || !Objects.equals(resolved.reportLine(), category.getTaxLine())
        || resolved.mappingActive() != category.isActive()) {
      throw parityFailure("Resolved report policy differs from legacy category behavior");
    }
  }

  private void assertLegacyProfileMatches(
      FinancialActivity activity, ReportingProfile reportingProfile) {
    if (reportingProfile.getKey()
        != ReportingProfileKey.valueOf(activity.getTaxTreatment().name())) {
      throw parityFailure("Activity reporting profile differs from its legacy treatment");
    }
  }

  private void assertSameCategories(
      List<FinancialCategory> legacyCategories, List<FinancialCategory> policyCategories) {
    List<Long> legacyIds = legacyCategories.stream().map(FinancialCategory::getId).toList();
    List<Long> policyIds = policyCategories.stream().map(FinancialCategory::getId).toList();
    if (!legacyIds.equals(policyIds)) {
      throw parityFailure("Compatible category results differ from legacy behavior");
    }
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

  private void validateLegacyProfile(FinancialCategory category, FinancialActivity activity) {
    if (category.getTaxTreatment() != activity.getTaxTreatment()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Category tax treatment does not match the selected financial activity");
    }
  }

  private LocalDate resolutionDate(LocalDate effectiveOn) {
    return effectiveOn == null ? LocalDate.now() : effectiveOn;
  }

  private ReportPolicyParityException parityFailure(String message) {
    return new ReportPolicyParityException(message);
  }
}
