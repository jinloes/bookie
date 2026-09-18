package com.bookie.compatibility.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.intake.application.LegacyInboxSnapshot;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyInboxSnapshotsTest {

  @Test
  void mapsEveryLegacyPendingExpenseFieldWithoutRetainingTheEntity() {
    LocalDate date = LocalDate.of(2026, 8, 24);
    LocalDateTime createdAt = LocalDateTime.of(2026, 8, 24, 12, 30);
    List<String> aliases = new ArrayList<>(List.of("alias-one", "alias-two"));
    PendingExpense pending =
        PendingExpense.builder()
            .id(7L)
            .sourceType(ExpenseSource.RECEIPT)
            .sourceId("receipt-id")
            .emailType(EmailType.EXPENSE)
            .subject("receipt.pdf")
            .status(PendingExpenseStatus.READY)
            .amount(new BigDecimal("123.45"))
            .description("Legacy description")
            .date(date)
            .category("REPAIRS")
            .propertyName("Rental")
            .payerName("Vendor")
            .activity(FinancialActivity.builder().id(11L).build())
            .financialCategory(FinancialCategory.builder().id(12L).build())
            .configuredActivityId(13L)
            .classificationAmbiguous(true)
            .errorMessage("review")
            .createdAt(createdAt)
            .unrecognizedAliases(aliases)
            .build();

    LegacyInboxSnapshot snapshot = LegacyInboxSnapshots.from(pending);
    aliases.add("later-mutation");

    assertThat(snapshot)
        .isEqualTo(
            LegacyInboxSnapshot.builder()
                .origin(ExpenseSource.RECEIPT)
                .legacySourceType("RECEIPT")
                .legacySourceId("receipt-id")
                .rawStatus("READY")
                .proposedDirection(TransactionDirection.EXPENSE)
                .subject("receipt.pdf")
                .proposedAmount(new BigDecimal("123.45"))
                .proposedDescription("Legacy description")
                .proposedDate(date)
                .proposedCategory("REPAIRS")
                .proposedPropertyName("Rental")
                .proposedCounterpartyName("Vendor")
                .legacyActivityId(11L)
                .legacyCategoryId(12L)
                .configuredActivityId(13L)
                .classificationAmbiguous(true)
                .errorMessage("review")
                .receiptExternalId("receipt-id")
                .receiptFileName("receipt.pdf")
                .createdAt(createdAt)
                .unrecognizedAliases(List.of("alias-one", "alias-two"))
                .build());
  }

  @Test
  void mapsOutlookAttachmentIdentity() {
    PendingExpense pending =
        PendingExpense.builder()
            .sourceType(ExpenseSource.OUTLOOK_EMAIL)
            .sourceId("derived-source")
            .outlookMessageId("message")
            .outlookAttachmentId("attachment-1")
            .outlookAttachmentName("one.pdf")
            .status(PendingExpenseStatus.PROCESSING)
            .unrecognizedAliases(List.of())
            .build();

    LegacyInboxSnapshot snapshot = LegacyInboxSnapshots.from(pending);

    assertThat(snapshot.getOutlookMessageId()).isEqualTo("message");
    assertThat(snapshot.getOutlookAttachmentId()).isEqualTo("attachment-1");
    assertThat(snapshot.getOutlookAttachmentName()).isEqualTo("one.pdf");
  }
}
