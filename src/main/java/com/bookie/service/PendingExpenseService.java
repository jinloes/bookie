package com.bookie.service;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Payer;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.Property;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.bookie.repository.PropertyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PendingExpenseService {

  private final PendingExpenseRepository pendingRepository;
  private final ExpenseService expenseService;
  private final PropertyRepository propertyRepository;
  private final PayerRepository payerRepository;
  private final PayerService payerService;

  public List<PendingExpense> findAll() {
    return pendingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  public Optional<PendingExpense> findBySourceId(String sourceId) {
    return pendingRepository.findBySourceId(sourceId);
  }

  @Transactional
  public PendingExpense create(String sourceId, ExpenseSource sourceType, String subject) {
    PendingExpense pending =
        PendingExpense.builder()
            .sourceId(sourceId)
            .sourceType(sourceType)
            .subject(subject)
            .status(PendingExpenseStatus.PROCESSING)
            .createdAt(LocalDateTime.now())
            .build();
    return pendingRepository.save(pending);
  }

  @Transactional
  public void markReady(Long id, EmailSuggestion suggestion, List<String> unrecognizedAliases) {
    PendingExpense pending =
        pendingRepository
            .findById(id)
            .orElseThrow(() -> new RuntimeException("Pending expense not found: " + id));
    pending.setStatus(PendingExpenseStatus.READY);
    pending.setAmount(suggestion.amount() != null ? BigDecimal.valueOf(suggestion.amount()) : null);
    pending.setDescription(suggestion.description());
    pending.setDate(suggestion.date() != null ? LocalDate.parse(suggestion.date()) : null);
    pending.setCategory(suggestion.category());
    pending.setPropertyName(suggestion.propertyName());
    pending.setPayerName(suggestion.payerName());
    pending.getUnrecognizedAliases().addAll(CollectionUtils.emptyIfNull(unrecognizedAliases));
    pendingRepository.save(pending);
  }

  @Transactional
  public void markFailed(Long id, String error) {
    pendingRepository
        .findById(id)
        .ifPresent(
            pending -> {
              pending.setStatus(PendingExpenseStatus.FAILED);
              pending.setErrorMessage(error);
              pendingRepository.save(pending);
            });
  }

  @Transactional
  public Expense saveAsExpense(Long pendingId, SavePendingExpenseRequest request) {
    PendingExpense pending =
        pendingRepository
            .findById(pendingId)
            .orElseThrow(() -> new RuntimeException("Pending expense not found: " + pendingId));

    Property property =
        Optional.ofNullable(request.propertyId())
            .flatMap(propertyRepository::findById)
            .orElse(null);
    Payer payer =
        Optional.ofNullable(request.payerId()).flatMap(payerRepository::findById).orElse(null);

    Expense expense =
        Expense.builder()
            .amount(request.amount())
            .description(request.description())
            .date(request.date())
            .category(ExpenseCategory.valueOf(request.category()))
            .property(property)
            .payer(payer)
            .sourceType(pending.getSourceType())
            .sourceId(pending.getSourceId())
            .build();

    Expense saved = expenseService.save(expense);

    if (StringUtils.hasText(pending.getPayerName())) {
      CollectionUtils.emptyIfNull(pending.getUnrecognizedAliases())
          .forEach(alias -> payerService.addAliasIfAbsent(pending.getPayerName(), alias));
    }

    pendingRepository.deleteById(pendingId);
    return saved;
  }

  @Transactional
  public void dismiss(Long id) {
    pendingRepository.deleteById(id);
  }
}
