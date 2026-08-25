package com.bookie.service;

import com.bookie.model.ActivityType;
import com.bookie.model.CreatePropertyRequest;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Property;
import com.bookie.model.TaxTreatment;
import com.bookie.model.UpdatePropertyRequest;
import com.bookie.repository.EmailKeywordPropertyHistoryRepository;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.FinancialActivityRepository;
import com.bookie.repository.FinancialCategoryRepository;
import com.bookie.repository.HouseholdMemberRepository;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.bookie.repository.PendingIncomeRepository;
import com.bookie.repository.PropertyRepository;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PropertyService {

  private final PropertyRepository propertyRepository;
  private final ExpenseRepository expenseRepository;
  private final IncomeRepository incomeRepository;
  private final PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  private final EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  private final PendingIncomeRepository pendingIncomeRepository;
  private final PendingExpenseRepository pendingExpenseRepository;
  private final FinancialActivityRepository financialActivityRepository;
  private final FinancialCategoryRepository financialCategoryRepository;
  private final HouseholdMemberRepository householdMemberRepository;

  public List<Property> findAll() {
    return propertyRepository.findAll();
  }

  public Property findById(Long id) {
    return propertyRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found: " + id));
  }

  public Property save(Property property) {
    return propertyRepository.save(property);
  }

  @Transactional
  public Property create(CreatePropertyRequest req) {
    Property property =
        Property.builder()
            .name(req.name())
            .address(req.address())
            .type(req.type())
            .notes(req.notes())
            .accounts(req.accounts() != null ? new HashSet<>(req.accounts()) : new HashSet<>())
            .build();
    Property saved = propertyRepository.save(property);
    var owner =
        householdMemberRepository
            .findBySystemKey(HouseholdMemberService.DEFAULT_HOUSEHOLD_KEY)
            .orElseThrow(
                () -> new IllegalStateException("Required default household member is missing"));
    String activityName =
        financialActivityRepository.findAll().stream()
                .anyMatch(activity -> activity.getName().equalsIgnoreCase(saved.getName()))
            ? saved.getName() + " (" + saved.getId() + ")"
            : saved.getName();
    financialActivityRepository.save(
        FinancialActivity.builder()
            .name(activityName)
            .activityType(ActivityType.RENTAL)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .owner(owner)
            .property(saved)
            .active(true)
            .build());
    return saved;
  }

  public Property update(Long id, UpdatePropertyRequest req) {
    Property existing = findById(id);
    existing.setName(req.name());
    existing.setAddress(req.address());
    existing.setType(req.type());
    existing.setNotes(req.notes());
    existing.setAccounts(req.accounts() != null ? new HashSet<>(req.accounts()) : new HashSet<>());
    return propertyRepository.save(existing);
  }

  @Transactional
  public void delete(Long id) {
    financialActivityRepository
        .findByPropertyId(id)
        .ifPresent(
            rentalActivity -> {
              FinancialActivity replacement =
                  financialActivityRepository
                      .findBySystemKey(FinancialActivityService.NEEDS_CLASSIFICATION_KEY)
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Required Needs classification financial activity is missing"));
              FinancialCategory incomeCategory = requiredCategory("OTHER_INCOME");
              FinancialCategory expenseCategory = requiredCategory("OTHER_EXPENSE");
              expenseRepository.reassignClassification(
                  rentalActivity.getId(), replacement, expenseCategory);
              incomeRepository.reassignClassification(
                  rentalActivity.getId(), replacement, incomeCategory);
              pendingIncomeRepository.reassignClassification(
                  rentalActivity.getId(), replacement, incomeCategory);
              pendingExpenseRepository.reassignIncomeClassification(
                  rentalActivity.getId(), replacement, incomeCategory);
              pendingExpenseRepository.reassignExpenseClassification(
                  rentalActivity.getId(), replacement, expenseCategory);
              financialActivityRepository.delete(rentalActivity);
            });
    expenseRepository.clearPropertyById(id);
    incomeRepository.clearPropertyById(id);
    pendingIncomeRepository.clearPropertyById(id);
    payerPropertyHistoryRepo.deleteByPropertyId(id);
    keywordPropertyHistoryRepo.deleteByPropertyId(id);
    propertyRepository.deleteById(id);
  }

  private FinancialCategory requiredCategory(String key) {
    return financialCategoryRepository
        .findByKey(key)
        .orElseThrow(
            () -> new IllegalStateException("Required financial category is missing: " + key));
  }
}
