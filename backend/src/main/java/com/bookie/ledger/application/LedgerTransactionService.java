package com.bookie.ledger.application;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.ConfirmedClassification;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.TransactionImportReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LedgerTransactionService implements LedgerLifecycle {

  private final Clock clock;

  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
  private static final String RECEIPT_ORIGIN = "RECEIPT";
  private static final String MANUAL_ORIGIN = "MANUAL";

  private final LedgerTransactionStore transactionStore;
  private final LedgerReferenceResolver referenceResolver;
  private final ClassificationHistory classificationHistory;
  // Resolved lazily: counterparty deletion checks ledger references through this service.
  private final ObjectProvider<CounterpartyCatalog> counterpartyCatalog;
  private final ApplicationEventPublisher events;

  /**
   * Records a manually entered transaction. A RECEIPT attachment is filed into its tax year after
   * commit ({@link TransactionRecorded}), so it may not be filed yet when this returns.
   */
  @Transactional
  public FinancialTransaction create(LedgerTransactionInput input) {
    LedgerTransactionInput normalizedInput = validateAndNormalize(input);
    FinancialTransaction created = record(normalizedInput);
    publishReceiptIfNeeded(created, normalizedInput);
    return created;
  }

  /**
   * Records the transaction an inbox save confirms. It never files the attachment: the save's
   * durable MOVE_RECEIPT job moves the receipt after commit, with its checksum guard.
   */
  @Transactional
  public FinancialTransaction createFromInbox(LedgerTransactionInput input) {
    return record(validateAndNormalize(input));
  }

  private FinancialTransaction record(LedgerTransactionInput normalizedInput) {
    ResolvedTransactionReferences references = resolve(normalizedInput);
    ensureSourceIdentityAvailable(
        normalizedInput.getOrigin(), normalizedInput.getExternalId(), null);
    FinancialTransaction transaction = new FinancialTransaction();
    apply(transaction, normalizedInput, references, normalizedInput.getParentExternalId());
    FinancialTransaction created = transactionStore.save(transaction);
    recordClassification(created, normalizedInput, references);
    return created;
  }

  @Transactional
  public FinancialTransaction update(Long id, Long expectedVersion, LedgerTransactionInput input) {
    LedgerTransactionInput normalizedInput = validateAndNormalize(input);
    FinancialTransaction existing = requireActive(id);
    if (!Objects.equals(existing.getVersion(), expectedVersion)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Transaction was modified by another operation");
    }
    ResolvedTransactionReferences references = resolve(normalizedInput);
    if (normalizedInput.getDirection() != existing.getDirection()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Transaction direction cannot be changed");
    }
    ensureSourceIdentityAvailable(
        normalizedInput.getOrigin(), normalizedInput.getExternalId(), existing.getId());
    Optional<TransactionImportReference> priorReference =
        existing.getImportReferences().stream().findFirst();
    // The parent identity belongs to the source record, so it survives only while that source
    // identity is unchanged.
    boolean sameSourceIdentity =
        priorReference.isPresent()
            && Objects.equals(
                priorReference.get().getOrigin(),
                StringUtils.defaultIfBlank(normalizedInput.getOrigin(), MANUAL_ORIGIN))
            && Objects.equals(
                priorReference.get().getExternalId(), normalizedInput.getExternalId());
    apply(
        existing,
        normalizedInput,
        references,
        sameSourceIdentity
            ? priorReference.get().getParentExternalId()
            : normalizedInput.getParentExternalId());
    FinancialTransaction updated = transactionStore.save(existing);
    recordClassification(updated, normalizedInput, references);
    return updated;
  }

  @Transactional
  public void delete(Long id, Long expectedVersion) {
    FinancialTransaction transaction = requireActive(id);
    if (!Objects.equals(transaction.getVersion(), expectedVersion)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Transaction was modified by another operation");
    }
    transaction.tombstone(LocalDateTime.now(clock));
    transactionStore.save(transaction);
  }

  @Transactional(readOnly = true)
  public List<FinancialTransaction> findAll() {
    return transactionStore.findAllActive();
  }

  @Transactional(readOnly = true)
  public List<FinancialTransaction> findAll(TransactionDirection direction) {
    return transactionStore.findAllActiveByDirection(direction);
  }

  @Transactional(readOnly = true)
  public FinancialTransaction findById(Long id) {
    return requireActive(id);
  }

  @Override
  @Transactional
  public void reassignActivity(
      Long activityId,
      Long replacementActivityId,
      Long incomeLegacyCategoryId,
      Long expenseLegacyCategoryId) {
    for (FinancialTransaction transaction :
        transactionStore.findAllActiveByActivityId(activityId)) {
      Long financialCategoryId =
          transaction.getDirection() == TransactionDirection.INCOME
              ? incomeLegacyCategoryId
              : expenseLegacyCategoryId;
      ResolvedTransactionReferences references =
          referenceResolver.resolveFinancialCategory(replacementActivityId, financialCategoryId);
      transaction.setActivity(references.activity());
      transaction.setNeutralCategory(references.neutralCategory());
      transactionStore.save(transaction);
    }
  }

  @Override
  @Transactional
  public void detachCounterparty(Long counterpartyId) {
    for (FinancialTransaction transaction :
        transactionStore.findAllActiveByCounterpartyId(counterpartyId)) {
      transaction.setCounterpartyId(null);
      transactionStore.save(transaction);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public long countActivityReferences(Long activityId) {
    return transactionStore.countActiveByActivityId(activityId);
  }

  @Override
  @Transactional(readOnly = true)
  public long countCounterpartyReferences(Long counterpartyId) {
    return transactionStore.countActiveByCounterpartyId(counterpartyId);
  }

  private ResolvedTransactionReferences resolve(LedgerTransactionInput input) {
    ResolvedTransactionReferences references =
        referenceResolver.resolve(
            input.getActivityId(),
            input.getNeutralCategoryId(),
            input.getCounterpartyId(),
            input.getDate());
    if (!references.neutralCategory().isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Neutral category is inactive");
    }
    validateDirection(input.getDirection(), references.neutralCategory().getDirection());
    return references;
  }

  private void apply(
      FinancialTransaction transaction,
      LedgerTransactionInput input,
      ResolvedTransactionReferences references,
      String parentExternalId) {
    transaction.setAmount(input.getAmount());
    transaction.setDirection(input.getDirection());
    transaction.setDate(input.getDate());
    transaction.setDescription(input.getDescription());
    transaction.setActivity(references.activity());
    transaction.setNeutralCategory(references.neutralCategory());
    transaction.setCounterpartyId(references.counterpartyId());
    transaction.replaceImportReference(
        input.getOrigin(), input.getExternalId(), input.getSourceLabel(), parentExternalId);
    transaction.replaceAttachment(
        input.getAttachmentStorageProvider(),
        input.getAttachmentExternalId(),
        input.getAttachmentFileName(),
        input.getAttachmentSha256());
  }

  private void recordClassification(
      FinancialTransaction transaction,
      LedgerTransactionInput input,
      ResolvedTransactionReferences references) {
    classificationHistory.record(
        ConfirmedClassification.builder()
            .kind(
                transaction.getDirection() == TransactionDirection.INCOME
                    ? ConfirmedClassification.Kind.INCOME
                    : ConfirmedClassification.Kind.EXPENSE)
            .sourceId(input.getExternalId())
            .activity(references.activity())
            .financialCategoryId(references.financialCategoryId())
            .property(references.activity().getProperty())
            .counterparty(
                Optional.ofNullable(references.counterpartyId())
                    .flatMap(id -> counterpartyCatalog.getObject().findOptionalById(id))
                    .orElse(null))
            .build());
  }

  private void publishReceiptIfNeeded(FinancialTransaction created, LedgerTransactionInput input) {
    if (!RECEIPT_ORIGIN.equals(input.getOrigin())
        || StringUtils.isBlank(input.getAttachmentExternalId())) {
      return;
    }
    events.publishEvent(
        new TransactionRecorded(
            created.getId(), input.getOrigin(), input.getAttachmentExternalId(), input.getDate()));
  }

  private LedgerTransactionInput validateAndNormalize(LedgerTransactionInput input) {
    if (input == null
        || input.getAmount() == null
        || input.getDirection() == null
        || input.getDate() == null
        || input.getActivityId() == null
        || input.getNeutralCategoryId() == null
        || StringUtils.isBlank(input.getDescription())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Transaction fields are incomplete");
    }
    if (input.getAmount().signum() <= 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Transaction amount must be positive");
    }
    BigDecimal normalizedAmount;
    try {
      normalizedAmount = input.getAmount().setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Transaction amount supports at most two decimal places");
    }
    if (StringUtils.isNotBlank(input.getAttachmentSha256())
        && !SHA_256.matcher(input.getAttachmentSha256()).matches()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Attachment SHA-256 must be lowercase hexadecimal");
    }
    if (StringUtils.isNotBlank(input.getAttachmentStorageProvider())
        && !"ONEDRIVE".equals(input.getAttachmentStorageProvider())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Only ONEDRIVE attachments are supported");
    }
    if (input.getDirection() == TransactionDirection.EXPENSE
        && StringUtils.isNotBlank(input.getSourceLabel())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Expense source labels are not supported");
    }
    return LedgerTransactionInput.builder()
        .amount(normalizedAmount)
        .direction(input.getDirection())
        .date(input.getDate())
        .description(input.getDescription().trim())
        .activityId(input.getActivityId())
        .neutralCategoryId(input.getNeutralCategoryId())
        .counterpartyId(input.getCounterpartyId())
        .origin(
            input.getOrigin() == null
                    && input.getExternalId() == null
                    && input.getSourceLabel() == null
                ? null
                : StringUtils.defaultIfBlank(input.getOrigin(), MANUAL_ORIGIN))
        .externalId(StringUtils.trimToNull(input.getExternalId()))
        .sourceLabel(StringUtils.trimToNull(input.getSourceLabel()))
        .parentExternalId(StringUtils.trimToNull(input.getParentExternalId()))
        .attachmentStorageProvider(
            input.getAttachmentStorageProvider() == null
                    && input.getAttachmentExternalId() == null
                    && input.getAttachmentFileName() == null
                    && input.getAttachmentSha256() == null
                ? null
                : StringUtils.defaultIfBlank(input.getAttachmentStorageProvider(), "ONEDRIVE"))
        .attachmentExternalId(StringUtils.trimToNull(input.getAttachmentExternalId()))
        .attachmentFileName(StringUtils.trimToNull(input.getAttachmentFileName()))
        .attachmentSha256(StringUtils.trimToNull(input.getAttachmentSha256()))
        .build();
  }

  private void ensureSourceIdentityAvailable(
      String origin, String externalId, Long currentTransactionId) {
    if (StringUtils.isBlank(externalId)) {
      return;
    }
    String normalizedOrigin = StringUtils.defaultIfBlank(origin, MANUAL_ORIGIN);
    transactionStore
        .findBySourceIdentity(normalizedOrigin, externalId)
        .filter(existing -> !Objects.equals(existing.getId(), currentTransactionId))
        .ifPresent(
            ignored -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "A transaction already uses this source identity");
            });
  }

  private FinancialTransaction requireActive(Long id) {
    return transactionStore
        .findActiveById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));
  }

  private void validateDirection(
      TransactionDirection transactionDirection, TransactionDirection categoryDirection) {
    if (transactionDirection != categoryDirection) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Category direction does not match the transaction");
    }
  }
}
