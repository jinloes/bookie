package com.bookie.intake.application;

import com.bookie.model.ExpenseSource;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LegacyInboxSnapshot {

  ExpenseSource origin;
  String legacySourceType;
  String legacySourceId;
  String rawStatus;
  TransactionDirection proposedDirection;
  String subject;
  BigDecimal proposedAmount;
  String proposedDescription;
  LocalDate proposedDate;
  String proposedCategory;
  String proposedPropertyName;
  String proposedCounterpartyName;
  String proposedSourceLabel;
  Long legacyActivityId;
  Long legacyCategoryId;
  Long legacyPropertyId;
  Long legacyCounterpartyId;
  Long configuredActivityId;
  boolean classificationAmbiguous;
  String errorMessage;
  String receiptExternalId;
  String receiptFileName;
  String outlookMessageId;
  String outlookAttachmentId;
  String outlookAttachmentName;
  LocalDateTime createdAt;
  List<String> unrecognizedAliases;
}
