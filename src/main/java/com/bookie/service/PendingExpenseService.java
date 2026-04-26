package com.bookie.service;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.Payer;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.Property;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.model.SavePendingIncomeRequest;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.bookie.repository.PropertyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingExpenseService {

  private final PendingExpenseRepository pendingRepository;
  private final ExpenseService expenseService;
  private final IncomeService incomeService;
  private final PropertyRepository propertyRepository;
  private final PayerRepository payerRepository;
  private final PayerService payerService;
  private final OutlookService outlookService;

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
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pending expense not found: " + id));
    pending.setStatus(PendingExpenseStatus.READY);
    pending.setEmailType(suggestion.emailType());
    pending.setAmount(suggestion.amount() != null ? BigDecimal.valueOf(suggestion.amount()) : null);
    pending.setDescription(suggestion.description());
    pending.setDate(suggestion.date() != null ? LocalDate.parse(suggestion.date()) : null);
    pending.setCategory(suggestion.category());
    pending.setPropertyName(suggestion.propertyName());
    pending.setPayerName(suggestion.payerName());
    log.debug(
        "markReady: id={} payerName='{}' propertyName='{}'",
        id,
        suggestion.payerName(),
        suggestion.propertyName());
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

  /**
   * Saves the pending record as an Expense. External effects (email move, receipt move) are
   * intentionally excluded here and performed by {@link InboxSaveOrchestrator} after this
   * transaction commits.
   */
  @Transactional
  public Expense saveAsExpense(Long pendingId, SavePendingExpenseRequest request) {
    PendingExpense pending =
        pendingRepository
            .findById(pendingId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pending expense not found: " + pendingId));

    outlookService.validateEmailAutoMove(pending.getSourceType());

    Property property =
        Optional.ofNullable(request.propertyId())
            .flatMap(propertyRepository::findById)
            .orElse(null);
    Payer payer =
        Optional.ofNullable(request.payerId()).flatMap(payerRepository::findById).orElse(null);

    boolean fromReceipt = pending.getSourceType() == ExpenseSource.RECEIPT;
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
            .receiptOneDriveId(fromReceipt ? pending.getSourceId() : null)
            .receiptFileName(fromReceipt ? pending.getSubject() : null)
            .build();

    Expense saved = expenseService.save(expense);

    if (StringUtils.isNotBlank(pending.getPayerName())) {
      CollectionUtils.emptyIfNull(pending.getUnrecognizedAliases())
          .forEach(alias -> payerService.addAliasIfAbsent(pending.getPayerName(), alias));
    }

    pendingRepository.deleteById(pendingId);
    return saved;
  }

  /**
   * Saves the pending record as an Income. External effects are performed by {@link
   * InboxSaveOrchestrator} after this transaction commits.
   */
  @Transactional
  public Income saveAsIncome(Long pendingId, SavePendingIncomeRequest request) {
    PendingExpense pending =
        pendingRepository
            .findById(pendingId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pending expense not found: " + pendingId));

    outlookService.validateEmailAutoMove(pending.getSourceType());

    Property property =
        Optional.ofNullable(request.propertyId())
            .flatMap(propertyRepository::findById)
            .orElse(null);

    boolean fromReceipt = pending.getSourceType() == ExpenseSource.RECEIPT;
    Income income =
        Income.builder()
            .amount(request.amount())
            .description(request.description())
            .date(request.date())
            .source(request.source())
            .property(property)
            .sourceId(pending.getSourceId())
            .sourceType(pending.getSourceType())
            .receiptOneDriveId(fromReceipt ? pending.getSourceId() : null)
            .receiptFileName(fromReceipt ? pending.getSubject() : null)
            .build();

    Income saved = incomeService.save(income);

    pendingRepository.deleteById(pendingId);
    return saved;
  }

  @Transactional
  public void dismiss(Long id) {
    pendingRepository.deleteById(id);
  }

  /**
   * Resets a FAILED or READY pending expense back to PROCESSING so it can be re-queued. Returns the
   * updated record so the caller can re-trigger the appropriate parse job.
   */
  @Transactional
  public PendingExpense resetForRetry(Long id) {
    PendingExpense pending =
        pendingRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pending expense not found: " + id));
    if (pending.getStatus() == PendingExpenseStatus.PROCESSING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Already processing");
    }
    pending.setStatus(PendingExpenseStatus.PROCESSING);
    pending.setErrorMessage(null);
    return pendingRepository.save(pending);
  }

  /** Return type for {@link #findOrCreate}. */
  public record FindOrCreateResult(PendingExpense pending, boolean alreadyProcessing) {}

  /**
   * Returns the existing {@link PendingExpense} unchanged when it is already {@code PROCESSING};
   * otherwise dismisses any stale entry and creates a fresh one ready for queuing. A unique
   * constraint on {@code sourceId} prevents duplicate inserts under concurrent requests; {@link
   * DataIntegrityViolationException} is caught and the existing record is returned instead.
   */
  @Transactional
  public FindOrCreateResult findOrCreate(
      String sourceId, ExpenseSource sourceType, String subject) {
    try {
      Optional<PendingExpense> existing = findBySourceId(sourceId);
      if (existing.isPresent() && existing.get().getStatus() == PendingExpenseStatus.PROCESSING) {
        return new FindOrCreateResult(existing.get(), true);
      }
      existing.ifPresent(e -> dismiss(e.getId()));
      return new FindOrCreateResult(create(sourceId, sourceType, subject), false);
    } catch (DataIntegrityViolationException e) {
      // Concurrent request inserted first; return the now-existing PROCESSING record
      return pendingRepository
          .findBySourceId(sourceId)
          .map(p -> new FindOrCreateResult(p, true))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "findOrCreate conflict but no record found for sourceId=" + sourceId, e));
    }
  }
}
