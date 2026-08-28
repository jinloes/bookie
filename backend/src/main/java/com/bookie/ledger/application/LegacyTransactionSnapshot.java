package com.bookie.ledger.application;

import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LegacyTransactionSnapshot {

  LegacyTransactionKey key;
  BigDecimal amount;
  TransactionDirection direction;
  LocalDate date;
  String description;
  Long activityId;
  Long legacyCategoryId;
  Long legacyCounterpartyId;
  ExpenseCategory legacyExpenseCategory;
  String origin;
  String externalId;
  String sourceLabel;
  String attachmentStorageProvider;
  String attachmentExternalId;
  String attachmentFileName;
  String attachmentSha256;
  boolean normalizedMetadataAuthoritative;
}
