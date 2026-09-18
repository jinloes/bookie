package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.intake.application.LegacyInboxSynchronizer;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.PendingExpenseRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingExpenseCreationServiceTest {

  @Mock private PendingExpenseRepository pendingRepository;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private FinancialCategoryService financialCategoryService;
  @Mock private LegacyInboxSynchronizer inboxSynchronizer;

  @InjectMocks private PendingExpenseCreationService service;

  @Nested
  class Create {

    @Test
    void withoutConfiguredActivity_createsPendingWithClassificationDefaults() {
      FinancialActivity activity = FinancialActivity.builder().id(1L).build();
      FinancialCategory category = FinancialCategory.builder().id(2L).build();
      when(activityCatalog.getNeedsClassification()).thenReturn(activity);
      when(financialCategoryService.defaultFor(activity, TransactionDirection.EXPENSE))
          .thenReturn(category);
      when(pendingRepository.save(any(PendingExpense.class)))
          .thenAnswer(
              invocation -> {
                PendingExpense pending = invocation.getArgument(0);
                pending.setId(3L);
                return pending;
              });

      PendingExpense result =
          service.create("receipt-1", ExpenseSource.RECEIPT, "receipt.pdf", null);

      assertThat(result.getId()).isEqualTo(3L);
      assertThat(result.getActivity()).isEqualTo(activity);
      assertThat(result.getFinancialCategory()).isEqualTo(category);
      assertThat(result.getStatus()).isEqualTo(PendingExpenseStatus.PROCESSING);
      assertThat(result.isClassificationAmbiguous()).isTrue();
      assertThat(result.getCreatedAt()).isNotNull();
      verify(inboxSynchronizer).created(any(), any(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void withConfiguredActivity_preservesIntakeContext() {
      FinancialActivity activity = FinancialActivity.builder().id(42L).build();
      FinancialCategory category = FinancialCategory.builder().id(4L).build();
      when(activityCatalog.findActiveById(42L)).thenReturn(activity);
      when(financialCategoryService.defaultFor(activity, TransactionDirection.EXPENSE))
          .thenReturn(category);
      when(pendingRepository.save(any(PendingExpense.class)))
          .thenAnswer(
              invocation -> {
                PendingExpense pending = invocation.getArgument(0);
                pending.setId(5L);
                return pending;
              });

      PendingExpense result =
          service.create("message-1", ExpenseSource.OUTLOOK_EMAIL, "Pay advice", 42L);

      assertThat(result.getConfiguredActivityId()).isEqualTo(42L);
      assertThat(result.getActivity()).isEqualTo(activity);
      assertThat(result.getFinancialCategory()).isEqualTo(category);
    }

    @Test
    void outlookAttachmentPersistsParentAndAttachmentMetadata() {
      FinancialActivity activity = FinancialActivity.builder().id(42L).build();
      FinancialCategory category = FinancialCategory.builder().id(4L).build();
      when(activityCatalog.findActiveById(42L)).thenReturn(activity);
      when(financialCategoryService.defaultFor(activity, TransactionDirection.EXPENSE))
          .thenReturn(category);
      when(pendingRepository.save(any(PendingExpense.class)))
          .thenAnswer(
              invocation -> {
                PendingExpense pending = invocation.getArgument(0);
                pending.setId(6L);
                return pending;
              });

      PendingExpense result =
          service.create(
              "derived-source",
              ExpenseSource.OUTLOOK_EMAIL,
              "PayPal receipts - one.pdf",
              42L,
              "message",
              "attachment-1",
              "one.pdf");

      assertThat(result.getOutlookMessageId()).isEqualTo("message");
      assertThat(result.getOutlookAttachmentId()).isEqualTo("attachment-1");
      assertThat(result.getOutlookAttachmentName()).isEqualTo("one.pdf");
    }
  }
}
