package com.bookie.compatibility.intake;

import com.bookie.intake.application.LegacyInboxSnapshot;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingIncome;
import com.bookie.model.TransactionDirection;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

/** Maps retained pending entities into the entity-free intake compatibility port. */
public final class LegacyInboxSnapshots {

  private LegacyInboxSnapshots() {}

  public static LegacyInboxSnapshot from(PendingExpense pending) {
    TransactionDirection direction =
        pending.getEmailType() == null
            ? null
            : pending.getEmailType() == EmailType.INCOME
                ? TransactionDirection.INCOME
                : TransactionDirection.EXPENSE;
    return LegacyInboxSnapshot.builder()
        .origin(pending.getSourceType())
        .legacySourceType(pending.getSourceType() == null ? null : pending.getSourceType().name())
        .legacySourceId(pending.getSourceId())
        .rawStatus(pending.getStatus().name())
        .proposedDirection(direction)
        .subject(pending.getSubject())
        .proposedAmount(pending.getAmount())
        .proposedDescription(pending.getDescription())
        .proposedDate(pending.getDate())
        .proposedCategory(pending.getCategory())
        .proposedPropertyName(pending.getPropertyName())
        .proposedCounterpartyName(pending.getPayerName())
        .legacyActivityId(pending.getActivity() == null ? null : pending.getActivity().getId())
        .legacyCategoryId(
            pending.getFinancialCategory() == null ? null : pending.getFinancialCategory().getId())
        .configuredActivityId(pending.getConfiguredActivityId())
        .classificationAmbiguous(pending.isClassificationAmbiguous())
        .errorMessage(pending.getErrorMessage())
        .receiptExternalId(
            pending.getSourceType() == ExpenseSource.RECEIPT ? pending.getSourceId() : null)
        .receiptFileName(
            pending.getSourceType() == ExpenseSource.RECEIPT ? pending.getSubject() : null)
        .createdAt(pending.getCreatedAt())
        .unrecognizedAliases(
            List.copyOf(CollectionUtils.emptyIfNull(pending.getUnrecognizedAliases())))
        .build();
  }

  public static LegacyInboxSnapshot from(PendingIncome pending) {
    return LegacyInboxSnapshot.builder()
        .origin(pending.getSourceType())
        .legacySourceType(pending.getSourceType() == null ? null : pending.getSourceType().name())
        .legacySourceId(pending.getSourceId())
        .rawStatus(pending.getStatus().name())
        .proposedDirection(TransactionDirection.INCOME)
        .proposedAmount(pending.getAmount())
        .proposedDescription(pending.getDescription())
        .proposedDate(pending.getDate())
        .proposedSourceLabel(pending.getSource())
        .legacyActivityId(pending.getActivity() == null ? null : pending.getActivity().getId())
        .legacyCategoryId(
            pending.getFinancialCategory() == null ? null : pending.getFinancialCategory().getId())
        .legacyPropertyId(pending.getProperty() == null ? null : pending.getProperty().getId())
        .legacyCounterpartyId(pending.getPayer() == null ? null : pending.getPayer().getId())
        .classificationAmbiguous(pending.isClassificationAmbiguous())
        .errorMessage(pending.getErrorMessage())
        .receiptExternalId(pending.getReceiptOneDriveId())
        .receiptFileName(pending.getReceiptFileName())
        .createdAt(pending.getCreatedAt())
        .unrecognizedAliases(List.of())
        .build();
  }
}
