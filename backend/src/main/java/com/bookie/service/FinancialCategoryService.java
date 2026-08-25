package com.bookie.service;

import com.bookie.model.ExpenseCategory;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.FinancialCategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinancialCategoryService {

  private final FinancialCategoryRepository financialCategoryRepository;
  private final FinancialActivityService financialActivityService;

  public List<FinancialCategory> findCompatible(TransactionDirection direction, Long activityId) {
    if (activityId == null && direction == null) {
      return financialCategoryRepository.findAllByActiveTrueOrderByDirectionAscLabelAsc();
    }
    if (activityId == null) {
      return financialCategoryRepository.findAllByActiveTrueOrderByDirectionAscLabelAsc().stream()
          .filter(category -> category.getDirection() == direction)
          .toList();
    }
    FinancialActivity activity = financialActivityService.findActiveById(activityId);
    if (direction == null) {
      return financialCategoryRepository.findAllByActiveTrueOrderByDirectionAscLabelAsc().stream()
          .filter(category -> category.getTaxTreatment() == activity.getTaxTreatment())
          .toList();
    }
    return financialCategoryRepository
        .findAllByDirectionAndTaxTreatmentAndActiveTrueOrderByLabelAsc(
            direction, activity.getTaxTreatment());
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
      category = defaultFor(activity, direction);
    }
    validateCompatibility(category, direction, activity);
    return category;
  }

  public FinancialCategory defaultFor(FinancialActivity activity, TransactionDirection direction) {
    String key =
        switch (activity.getTaxTreatment()) {
          case SCHEDULE_E -> direction == TransactionDirection.INCOME ? "RENTAL_INCOME" : "OTHER";
          case SCHEDULE_C ->
              direction == TransactionDirection.INCOME
                  ? "SERVICE_INCOME"
                  : "SCHEDULE_C_OTHER_EXPENSE";
          case W2 ->
              direction == TransactionDirection.INCOME ? "WAGES" : "EMPLOYMENT_OTHER_EXPENSE";
          case NONE -> direction == TransactionDirection.INCOME ? "OTHER_INCOME" : "OTHER_EXPENSE";
        };
    return financialCategoryRepository
        .findByKey(key)
        .orElseThrow(
            () -> new IllegalStateException("Required financial category is missing: " + key));
  }

  public ExpenseCategory toLegacyExpenseCategory(FinancialCategory category) {
    try {
      return ExpenseCategory.valueOf(category.getKey());
    } catch (IllegalArgumentException ignored) {
      return ExpenseCategory.OTHER;
    }
  }

  private void validateCompatibility(
      FinancialCategory category, TransactionDirection direction, FinancialActivity activity) {
    if (!category.isActive()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Financial category is inactive: " + category.getId());
    }
    if (category.getDirection() != direction) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Category direction does not match the transaction");
    }
    if (category.getTaxTreatment() != activity.getTaxTreatment()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Category tax treatment does not match the selected financial activity");
    }
  }
}
