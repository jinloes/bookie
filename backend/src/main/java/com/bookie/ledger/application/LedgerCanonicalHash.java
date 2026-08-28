package com.bookie.ledger.application;

import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.TransactionAttachment;
import com.bookie.ledger.domain.TransactionImportReference;
import com.bookie.model.ExpenseCategory;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

final class LedgerCanonicalHash {

  private LedgerCanonicalHash() {}

  static String calculate(
      LegacyTransactionKey key,
      FinancialTransaction transaction,
      ExpenseCategory legacyExpenseCategory) {
    TransactionImportReference importReference =
        transaction.getImportReferences().stream()
            .min(
                Comparator.comparing(
                    reference -> reference.getId() == null ? 0L : reference.getId()))
            .orElse(null);
    TransactionAttachment attachment =
        transaction.getAttachments().stream()
            .min(Comparator.comparing(value -> value.getId() == null ? 0L : value.getId()))
            .orElse(null);
    String payload =
        String.join(
            "|",
            "v2",
            key.getTable().name(),
            key.getId().toString(),
            transaction.getDirection().name(),
            transaction.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
            transaction.getDate().toString(),
            lengthPrefixed(transaction.getDescription()),
            transaction.getActivity().getId().toString(),
            transaction.getNeutralCategory().getId().toString(),
            stringValue(transaction.getCounterpartyId()),
            stringValue(legacyExpenseCategory),
            importReference == null ? "" : stringValue(importReference.getOrigin()),
            lengthPrefixed(importReference == null ? null : importReference.getExternalId()),
            lengthPrefixed(importReference == null ? null : importReference.getSourceLabel()),
            attachment == null ? "" : stringValue(attachment.getStorageProvider()),
            lengthPrefixed(attachment == null ? null : attachment.getExternalId()),
            lengthPrefixed(attachment == null ? null : attachment.getFileName()),
            attachment == null ? "" : stringValue(attachment.getSha256()));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String lengthPrefixed(String value) {
    String safeValue = stringValue(value);
    return safeValue.length() + ":" + safeValue;
  }

  private static String stringValue(Object value) {
    return value == null ? "" : value.toString();
  }
}
