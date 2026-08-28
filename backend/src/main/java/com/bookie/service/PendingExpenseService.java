package com.bookie.service;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.domain.Property;
import com.bookie.compatibility.intake.LegacyInboxSnapshots;
import com.bookie.intake.application.LegacyInboxReadSelector;
import com.bookie.intake.application.LegacyInboxSynchronizer;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.model.SavePendingIncomeRequest;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.PendingExpenseRepository;
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
  private final CounterpartyCatalog counterpartyCatalog;
  private final OutlookService outlookService;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryService financialCategoryService;
  private final LegacyInboxSynchronizer inboxSynchronizer;
  private final LegacyInboxReadSelector inboxReadSelector;

  @Transactional(readOnly = true)
  public List<PendingExpense> findAll() {
    List<PendingExpense> legacy =
        pendingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    return inboxReadSelector.select(
        LegacyPendingTable.PENDING_EXPENSES,
        legacy,
        PendingExpense::getId,
        LegacyInboxSnapshots::from);
  }

  public Optional<PendingExpense> findBySourceId(String sourceId) {
    return pendingRepository.findBySourceId(sourceId);
  }

  @Transactional
  public PendingExpense create(String sourceId, ExpenseSource sourceType, String subject) {
    return create(sourceId, sourceType, subject, null);
  }

  @Transactional
  public PendingExpense create(
      String sourceId, ExpenseSource sourceType, String subject, Long configuredActivityId) {
    FinancialActivity activity =
        configuredActivityId == null
            ? activityCatalog.getNeedsClassification()
            : activityCatalog.findActiveById(configuredActivityId);
    PendingExpense pending =
        PendingExpense.builder()
            .sourceId(sourceId)
            .sourceType(sourceType)
            .subject(subject)
            .activity(activity)
            .financialCategory(
                financialCategoryService.defaultFor(activity, TransactionDirection.EXPENSE))
            .configuredActivityId(configuredActivityId)
            .classificationAmbiguous(true)
            .status(PendingExpenseStatus.PROCESSING)
            .createdAt(LocalDateTime.now())
            .build();
    PendingExpense saved = pendingRepository.save(pending);
    inboxSynchronizer.created(pendingKey(saved.getId()), LegacyInboxSnapshots.from(saved), true);
    return saved;
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
    FinancialActivity activity =
        suggestion.activityId() != null
            ? activityCatalog.findActiveById(suggestion.activityId())
            : activityCatalog.resolveForSuggestedProperty(suggestion.propertyName());
    pending.setActivity(activity);
    TransactionDirection direction =
        suggestion.emailType() == com.bookie.model.EmailType.INCOME
            ? TransactionDirection.INCOME
            : TransactionDirection.EXPENSE;
    FinancialCategory category;
    boolean classificationAmbiguous = suggestion.classificationAmbiguous();
    try {
      category =
          financialCategoryService.resolve(
              suggestion.categoryId(),
              suggestion.categoryId() == null ? suggestion.category() : null,
              direction,
              activity,
              pending.getDate());
    } catch (ResponseStatusException incompatibleSuggestion) {
      category = financialCategoryService.defaultFor(activity, direction, pending.getDate());
      classificationAmbiguous = true;
    }
    pending.setFinancialCategory(category);
    pending.setClassificationAmbiguous(classificationAmbiguous);
    log.debug(
        "markReady: id={} payerName='{}' propertyName='{}'",
        id,
        suggestion.payerName(),
        suggestion.propertyName());
    pending.getUnrecognizedAliases().addAll(CollectionUtils.emptyIfNull(unrecognizedAliases));
    PendingExpense saved = pendingRepository.save(pending);
    inboxSynchronizer.ready(pendingKey(id), LegacyInboxSnapshots.from(saved));
  }

  @Transactional
  public void markFailed(Long id, String error) {
    pendingRepository
        .findById(id)
        .ifPresentOrElse(
            pending -> {
              pending.setStatus(PendingExpenseStatus.FAILED);
              pending.setErrorMessage(error);
              PendingExpense saved = pendingRepository.save(pending);
              inboxSynchronizer.failed(pendingKey(id), LegacyInboxSnapshots.from(saved));
            },
            () -> log.warn("Could not mark pending expense {} as failed: record not found", id));
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

    Long activityId =
        request.activityId() != null
            ? request.activityId()
            : (request.propertyId() == null && pending.getActivity() != null
                ? pending.getActivity().getId()
                : null);
    FinancialActivity activity =
        activityCatalog.resolveForTransaction(activityId, request.propertyId());
    Property property = activity.getProperty();
    FinancialCategory category =
        financialCategoryService.resolve(
            request.categoryId(),
            request.category(),
            TransactionDirection.EXPENSE,
            activity,
            request.date());
    Counterparty payer =
        Optional.ofNullable(request.payerId())
            .flatMap(counterpartyCatalog::findOptionalById)
            .orElse(null);

    pending.setEmailType(EmailType.EXPENSE);
    pending.setAmount(request.amount());
    pending.setDescription(request.description());
    pending.setDate(request.date());
    pending.setCategory(request.category());
    pending.setActivity(activity);
    pending.setFinancialCategory(category);
    inboxSynchronizer.savePending(pendingKey(pendingId), LegacyInboxSnapshots.from(pending));

    boolean fromReceipt = pending.getSourceType() == ExpenseSource.RECEIPT;
    Expense expense =
        Expense.builder()
            .amount(request.amount())
            .description(request.description())
            .date(request.date())
            .category(financialCategoryService.toLegacyExpenseCategory(category))
            .financialCategory(category)
            .property(property)
            .payer(payer)
            .activity(activity)
            .sourceType(pending.getSourceType())
            .sourceId(pending.getSourceId())
            .receiptOneDriveId(fromReceipt ? pending.getSourceId() : null)
            .receiptFileName(fromReceipt ? pending.getSubject() : null)
            .build();

    Expense saved = expenseService.save(expense);

    if (StringUtils.isNotBlank(pending.getPayerName())) {
      CollectionUtils.emptyIfNull(pending.getUnrecognizedAliases())
          .forEach(alias -> counterpartyCatalog.addAliasIfAbsent(pending.getPayerName(), alias));
      // If the user confirmed with a different payer than the raw email name, record the email
      // name as an alias so future occurrences of the same vendor are resolved automatically.
      if (payer != null && !payer.getName().equalsIgnoreCase(pending.getPayerName())) {
        counterpartyCatalog.addAliasIfAbsent(payer.getName(), pending.getPayerName());
      }
    }

    inboxSynchronizer.saved(
        pendingKey(pendingId),
        LegacyInboxSnapshots.from(pending),
        new LegacyTransactionKey(LegacyTransactionTable.EXPENSES, saved.getId()));
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

    Long activityId =
        request.activityId() != null
            ? request.activityId()
            : (request.propertyId() == null && pending.getActivity() != null
                ? pending.getActivity().getId()
                : null);
    FinancialActivity activity =
        activityCatalog.resolveForTransaction(activityId, request.propertyId());
    Property property = activity.getProperty();
    FinancialCategory category =
        financialCategoryService.resolve(
            request.categoryId(), null, TransactionDirection.INCOME, activity, request.date());

    pending.setEmailType(EmailType.INCOME);
    pending.setAmount(request.amount());
    pending.setDescription(request.description());
    pending.setDate(request.date());
    pending.setActivity(activity);
    pending.setFinancialCategory(category);
    inboxSynchronizer.savePending(pendingKey(pendingId), LegacyInboxSnapshots.from(pending));

    boolean fromReceipt = pending.getSourceType() == ExpenseSource.RECEIPT;
    Income income =
        Income.builder()
            .amount(request.amount())
            .description(request.description())
            .date(request.date())
            .source(request.source())
            .property(property)
            .activity(activity)
            .financialCategory(category)
            .sourceId(pending.getSourceId())
            .sourceType(pending.getSourceType())
            .receiptOneDriveId(fromReceipt ? pending.getSourceId() : null)
            .receiptFileName(fromReceipt ? pending.getSubject() : null)
            .build();

    Income saved = incomeService.save(income);

    inboxSynchronizer.saved(
        pendingKey(pendingId),
        LegacyInboxSnapshots.from(pending),
        new LegacyTransactionKey(LegacyTransactionTable.INCOMES, saved.getId()));
    pendingRepository.deleteById(pendingId);
    return saved;
  }

  @Transactional
  public void dismiss(Long id) {
    PendingExpense pending =
        pendingRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pending expense not found: " + id));
    dismiss(pending);
  }

  private void dismiss(PendingExpense pending) {
    inboxSynchronizer.dismissed(pendingKey(pending.getId()), LegacyInboxSnapshots.from(pending));
    pendingRepository.deleteById(pending.getId());
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
    PendingExpense saved = pendingRepository.save(pending);
    inboxSynchronizer.retryQueued(pendingKey(id), LegacyInboxSnapshots.from(saved));
    return saved;
  }

  /** Return type for {@link #findOrCreate}. */
  public record FindOrCreateResult(PendingExpense pending, boolean alreadyProcessing) {}

  /**
   * Returns the existing {@link PendingExpense} unchanged when it is already {@code PROCESSING};
   * otherwise dismisses any stale entry and creates a fresh one ready for queuing. A unique
   * constraint on {@code sourceId} prevents duplicate inserts under concurrent requests; {@link
   * DataIntegrityViolationException} is caught and the existing record is returned instead.
   *
   * <p>Rejects the request outright if this source has already been saved as an Expense or Income.
   * Without this check a re-parse (e.g. a stale "Parse" click, a retry, or a duplicate webhook)
   * would create a pending item that can never be saved: {@code saveAsExpense}/{@code saveAsIncome}
   * would fail with a confusing DB-conflict error once the unique {@code (sourceType, sourceId)}
   * constraint on the expenses/incomes tables is hit.
   */
  @Transactional
  public FindOrCreateResult findOrCreate(
      String sourceId, ExpenseSource sourceType, String subject) {
    return findOrCreate(sourceId, sourceType, subject, null);
  }

  @Transactional
  public FindOrCreateResult findOrCreate(
      String sourceId, ExpenseSource sourceType, String subject, Long configuredActivityId) {
    if (expenseService.findBySourceId(sourceId).isPresent()
        || incomeService.existsBySourceId(sourceType, sourceId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This item has already been saved. Refresh to see the update.");
    }
    try {
      Optional<PendingExpense> existing = findBySourceId(sourceId);
      if (existing.isPresent() && existing.get().getStatus() == PendingExpenseStatus.PROCESSING) {
        return new FindOrCreateResult(existing.get(), true);
      }
      existing.ifPresent(this::dismiss);
      return new FindOrCreateResult(
          create(sourceId, sourceType, subject, configuredActivityId), false);
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

  private LegacyPendingKey pendingKey(Long id) {
    return new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, id);
  }
}
