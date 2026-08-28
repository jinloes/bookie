package com.bookie.ledger.application;

import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionMap;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LedgerTransactionService implements LedgerLifecycle {

  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  private final LedgerTransactionStore transactionStore;
  private final LedgerReferenceResolver referenceResolver;
  private final LegacyTransactionWriter legacyTransactionWriter;

  @Transactional
  public FinancialTransaction create(LedgerTransactionInput input) {
    LedgerTransactionInput normalizedInput = validateAndNormalize(input);
    ResolvedLegacyReferences references =
        referenceResolver.resolveForUnified(
            normalizedInput.getActivityId(),
            normalizedInput.getNeutralCategoryId(),
            normalizedInput.getCounterpartyId(),
            normalizedInput.getDate());
    validateActiveCategory(references);
    validateDirection(normalizedInput.getDirection(), references.neutralCategory().getDirection());
    LegacyTransactionSnapshot legacySnapshot =
        legacyTransactionWriter.create(normalizedInput, references);
    return synchronizeLegacy(legacySnapshot);
  }

  @Transactional
  public FinancialTransaction update(Long id, Long expectedVersion, LedgerTransactionInput input) {
    LedgerTransactionInput normalizedInput = validateAndNormalize(input);
    FinancialTransaction existing = requireActive(id);
    if (!Objects.equals(existing.getVersion(), expectedVersion)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Transaction was modified by another operation");
    }
    LegacyTransactionMap legacyMap =
        transactionStore
            .findMapByTransactionId(id)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unified transaction is missing its legacy mapping: " + id));
    ResolvedLegacyReferences references =
        referenceResolver.resolveForUnified(
            normalizedInput.getActivityId(),
            normalizedInput.getNeutralCategoryId(),
            normalizedInput.getCounterpartyId(),
            normalizedInput.getDate());
    validateActiveCategory(references);
    validateDirection(normalizedInput.getDirection(), references.neutralCategory().getDirection());
    if ((normalizedInput.getDirection() == TransactionDirection.INCOME
            && legacyMap.getKey().getTable()
                != com.bookie.ledger.domain.LegacyTransactionTable.INCOMES)
        || (normalizedInput.getDirection() == TransactionDirection.EXPENSE
            && legacyMap.getKey().getTable()
                != com.bookie.ledger.domain.LegacyTransactionTable.EXPENSES)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Transaction direction cannot be changed");
    }
    LegacyTransactionSnapshot legacySnapshot =
        legacyTransactionWriter.update(legacyMap.getKey(), normalizedInput, references);
    return synchronizeLegacy(legacySnapshot);
  }

  @Transactional
  public void delete(Long id, Long expectedVersion) {
    FinancialTransaction transaction = requireActive(id);
    if (!Objects.equals(transaction.getVersion(), expectedVersion)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Transaction was modified by another operation");
    }
    LegacyTransactionMap legacyMap =
        transactionStore
            .findMapByTransactionId(id)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unified transaction is missing its legacy mapping: " + id));
    legacyTransactionWriter.delete(legacyMap.getKey());
    tombstone(legacyMap, transaction);
  }

  @Transactional
  public FinancialTransaction synchronizeLegacy(LegacyTransactionSnapshot snapshot) {
    validateSnapshot(snapshot);
    ResolvedLedgerReferences references =
        referenceResolver.resolveFromLegacy(
            snapshot.getActivityId(),
            snapshot.getLegacyCategoryId(),
            snapshot.getLegacyCounterpartyId());
    validateDirection(snapshot.getDirection(), references.neutralCategory().getDirection());

    LegacyTransactionMap legacyMap = transactionStore.findMap(snapshot.getKey()).orElse(null);
    FinancialTransaction transaction =
        legacyMap == null ? new FinancialTransaction() : legacyMap.getTransaction();
    ensureSourceIdentityAvailable(
        snapshot.getOrigin(), snapshot.getExternalId(), transaction.getId());
    applySnapshot(transaction, snapshot, references);
    transaction = transactionStore.save(transaction);

    if (legacyMap == null) {
      legacyMap =
          LegacyTransactionMap.builder()
              .key(snapshot.getKey())
              .transaction(transaction)
              .mappedAt(LocalDateTime.now())
              .build();
    }
    legacyMap.setLegacyExpenseCategory(snapshot.getLegacyExpenseCategory());
    legacyMap.setCanonicalHash(
        LedgerCanonicalHash.calculate(
            snapshot.getKey(), transaction, snapshot.getLegacyExpenseCategory()));
    transactionStore.saveMap(legacyMap);
    return transaction;
  }

  @Transactional
  public void tombstoneLegacy(LegacyTransactionKey key) {
    LegacyTransactionMap legacyMap =
        transactionStore
            .findMap(key)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Refusing to delete an unmapped legacy financial record: "
                            + key.getTable()
                            + "/"
                            + key.getId()));
    tombstone(legacyMap, legacyMap.getTransaction());
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

  @Transactional(readOnly = true)
  public Optional<FinancialTransaction> findByLegacyKey(LegacyTransactionKey key) {
    return transactionStore.findActiveByLegacyKey(key);
  }

  @Transactional(readOnly = true)
  public LegacyTransactionMap findMapByTransactionId(Long transactionId) {
    return transactionStore
        .findMapByTransactionId(transactionId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unified transaction is missing its legacy mapping: " + transactionId));
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
      Long legacyCategoryId =
          transaction.getDirection() == TransactionDirection.INCOME
              ? incomeLegacyCategoryId
              : expenseLegacyCategoryId;
      ResolvedLedgerReferences references =
          referenceResolver.resolveFromLegacy(replacementActivityId, legacyCategoryId, null);
      transaction.setActivity(references.activity());
      transaction.setNeutralCategory(references.neutralCategory());
      persistWithUpdatedHash(transaction);
    }
  }

  @Override
  @Transactional
  public void detachCounterparty(Long counterpartyId) {
    for (FinancialTransaction transaction : transactionStore.findAllActive()) {
      if (Objects.equals(transaction.getCounterpartyId(), counterpartyId)) {
        transaction.setCounterpartyId(null);
        persistWithUpdatedHash(transaction);
      }
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
          HttpStatus.BAD_REQUEST,
          "Only ONEDRIVE attachments are representable during compatibility mode");
    }
    if (input.getDirection() == TransactionDirection.EXPENSE
        && StringUtils.isNotBlank(input.getSourceLabel())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Expense source labels are not representable during compatibility mode");
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
                : StringUtils.defaultIfBlank(input.getOrigin(), "MANUAL"))
        .externalId(StringUtils.trimToNull(input.getExternalId()))
        .sourceLabel(StringUtils.trimToNull(input.getSourceLabel()))
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

  private void validateSnapshot(LegacyTransactionSnapshot snapshot) {
    if (snapshot == null
        || snapshot.getKey() == null
        || snapshot.getAmount() == null
        || snapshot.getAmount().signum() <= 0
        || snapshot.getDirection() == null
        || snapshot.getDate() == null
        || StringUtils.isBlank(snapshot.getDescription())
        || snapshot.getActivityId() == null
        || snapshot.getLegacyCategoryId() == null) {
      throw new IllegalStateException("Legacy financial record cannot be mirrored losslessly");
    }
  }

  private void applySnapshot(
      FinancialTransaction transaction,
      LegacyTransactionSnapshot snapshot,
      ResolvedLedgerReferences references) {
    transaction.setAmount(snapshot.getAmount().setScale(2, RoundingMode.UNNECESSARY));
    transaction.setDirection(snapshot.getDirection());
    transaction.setDate(snapshot.getDate());
    transaction.setDescription(snapshot.getDescription());
    transaction.setActivity(references.activity());
    transaction.setNeutralCategory(references.neutralCategory());
    transaction.setCounterpartyId(references.counterpartyId());
    transaction.replaceImportReference(
        snapshot.getOrigin(), snapshot.getExternalId(), snapshot.getSourceLabel());
    String attachmentSha256 = snapshot.getAttachmentSha256();
    if (!snapshot.isNormalizedMetadataAuthoritative()
        && attachmentSha256 == null
        && transaction.getAttachments().size() == 1) {
      var existingAttachment = transaction.getAttachments().iterator().next();
      if (Objects.equals(existingAttachment.getExternalId(), snapshot.getAttachmentExternalId())
          && Objects.equals(existingAttachment.getFileName(), snapshot.getAttachmentFileName())) {
        attachmentSha256 = existingAttachment.getSha256();
      }
    }
    transaction.replaceAttachment(
        snapshot.getAttachmentStorageProvider(),
        snapshot.getAttachmentExternalId(),
        snapshot.getAttachmentFileName(),
        attachmentSha256);
    transaction.reactivate();
  }

  private void ensureSourceIdentityAvailable(
      String origin, String externalId, Long currentTransactionId) {
    if (StringUtils.isBlank(externalId)) {
      return;
    }
    String normalizedOrigin = StringUtils.defaultIfBlank(origin, "MANUAL");
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

  private void persistWithUpdatedHash(FinancialTransaction transaction) {
    FinancialTransaction saved = transactionStore.save(transaction);
    LegacyTransactionMap legacyMap =
        transactionStore
            .findMapByTransactionId(saved.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unified transaction is missing its legacy mapping: " + saved.getId()));
    legacyMap.setCanonicalHash(
        LedgerCanonicalHash.calculate(
            legacyMap.getKey(), saved, legacyMap.getLegacyExpenseCategory()));
    transactionStore.saveMap(legacyMap);
  }

  private void tombstone(
      LegacyTransactionMap legacyMap, FinancialTransaction financialTransaction) {
    financialTransaction.tombstone(LocalDateTime.now());
    FinancialTransaction saved = transactionStore.save(financialTransaction);
    legacyMap.setCanonicalHash(
        LedgerCanonicalHash.calculate(
            legacyMap.getKey(), saved, legacyMap.getLegacyExpenseCategory()));
    transactionStore.saveMap(legacyMap);
  }

  private void validateDirection(
      TransactionDirection transactionDirection, TransactionDirection categoryDirection) {
    if (transactionDirection != categoryDirection) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Category direction does not match the transaction");
    }
  }

  private void validateActiveCategory(ResolvedLegacyReferences references) {
    if (!references.neutralCategory().isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Neutral category is inactive");
    }
  }
}
