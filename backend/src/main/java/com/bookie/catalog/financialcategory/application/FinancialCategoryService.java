package com.bookie.catalog.financialcategory.application;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.application.ReportingProfileResolution;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinancialCategoryService {

  public record CompatibleCategory(
      NeutralCategory category, String taxTreatment, String reportLine) {}

  private final Clock clock;
  private final CategoryCatalog categoryCatalog;
  private final ActivityCatalog activityCatalog;
  private final ReportPolicyResolver reportPolicyResolver;

  public List<CompatibleCategory> findCompatible(TransactionDirection direction, Long activityId) {
    List<NeutralCategory> activeCategories =
        filterByDirection(categoryCatalog.findAllActive(), direction);
    if (activityId == null) {
      return activeCategories.stream()
          .map(category -> new CompatibleCategory(category, null, null))
          .toList();
    }

    FinancialActivity activity = activityCatalog.findActiveById(activityId);
    LocalDate effectiveOn = LocalDate.now(clock);
    requireReportingProfile(activity.getId(), effectiveOn);
    List<CompatibleCategory> compatible = new ArrayList<>();
    for (NeutralCategory category : activeCategories) {
      ReportPolicyResolution resolution =
          reportPolicyResolver.resolveNeutral(activity.getId(), category, effectiveOn);
      if (resolution instanceof ReportPolicyResolution.Resolved resolved) {
        if (resolved.neutralCategory().isActive()
            && resolved.mappingActive()
            && resolved.neutralCategory().getDirection() == category.getDirection()) {
          compatible.add(
              new CompatibleCategory(
                  category, resolved.reportingProfile().getKey().name(), resolved.reportLine()));
        }
      } else if (resolution instanceof ReportPolicyResolution.Missing missing
          && missing.component() == ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING) {
        // Not mapped under this activity's reporting profile.
      } else {
        throw parityFailure("Report-policy category inventory is incomplete or ambiguous");
      }
    }
    return List.copyOf(compatible);
  }

  public NeutralCategory findById(Long id) {
    return categoryCatalog
        .findById(id)
        .orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Financial category not found: " + id));
  }

  public NeutralCategory resolve(
      Long neutralCategoryId,
      String categoryKey,
      TransactionDirection direction,
      FinancialActivity activity) {
    return resolve(neutralCategoryId, categoryKey, direction, activity, LocalDate.now(clock));
  }

  public NeutralCategory resolve(
      Long neutralCategoryId,
      String categoryKey,
      TransactionDirection direction,
      FinancialActivity activity,
      LocalDate effectiveOn) {
    NeutralCategory category;
    if (neutralCategoryId != null) {
      category = findById(neutralCategoryId);
    } else if (categoryKey != null && !categoryKey.isBlank()) {
      category =
          categoryCatalog
              .findByKey(categoryKey)
              .orElseThrow(() ->
                  new ResponseStatusException(
                      HttpStatus.BAD_REQUEST, "Unknown financial category: " + categoryKey));
    } else {
      return defaultFor(activity, direction, effectiveOn);
    }

    validateBasicCompatibility(category, direction);
    requireCompatiblePolicy(category, activity, resolutionDate(effectiveOn), true);
    return category;
  }

  public NeutralCategory defaultFor(FinancialActivity activity, TransactionDirection direction) {
    return defaultFor(activity, direction, LocalDate.now(clock));
  }

  public NeutralCategory defaultFor(
      FinancialActivity activity, TransactionDirection direction, LocalDate effectiveOn) {
    ReportingProfileKey reportingProfileKey =
        requireReportingProfile(activity.getId(), resolutionDate(effectiveOn)).getKey();
    String key =
        switch (reportingProfileKey) {
          case SCHEDULE_E ->
              direction == TransactionDirection.INCOME ? "RENTAL_INCOME" : "OTHER_EXPENSE";
          case SCHEDULE_C ->
              direction == TransactionDirection.INCOME ? "SERVICE_INCOME" : "OTHER_EXPENSE";
          case W2 ->
              direction == TransactionDirection.INCOME ? "WAGES" : "EMPLOYMENT_OTHER_EXPENSE";
          case NONE -> direction == TransactionDirection.INCOME ? "OTHER_INCOME" : "OTHER_EXPENSE";
        };
    NeutralCategory category =
        categoryCatalog
            .findByKey(key)
            .orElseThrow(
                () -> new IllegalStateException("Required neutral category is missing: " + key));
    requireCompatiblePolicy(category, activity, resolutionDate(effectiveOn), false);
    return category;
  }

  private List<NeutralCategory> filterByDirection(
      List<NeutralCategory> categories, TransactionDirection direction) {
    if (direction == null) {
      return categories;
    }
    return categories.stream().filter(category -> category.getDirection() == direction).toList();
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
      NeutralCategory category,
      FinancialActivity activity,
      LocalDate effectiveOn,
      boolean incompatibleIsBadRequest) {
    ReportPolicyResolution resolution =
        reportPolicyResolver.resolveNeutral(activity.getId(), category, effectiveOn);
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
        && resolution instanceof ReportPolicyResolution.Missing missing
        && missing.component() == ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Category reporting profile does not match the selected financial activity");
    }
    throw parityFailure("Report-policy resolution is missing or ambiguous");
  }

  private void validateBasicCompatibility(
      NeutralCategory category, TransactionDirection direction) {
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
