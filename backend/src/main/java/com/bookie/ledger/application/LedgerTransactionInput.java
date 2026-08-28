package com.bookie.ledger.application;

import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LedgerTransactionInput {

  BigDecimal amount;
  TransactionDirection direction;
  LocalDate date;
  String description;
  Long activityId;
  Long neutralCategoryId;
  Long counterpartyId;
  String origin;
  String externalId;
  String sourceLabel;
  String attachmentStorageProvider;
  String attachmentExternalId;
  String attachmentFileName;
  String attachmentSha256;
}
