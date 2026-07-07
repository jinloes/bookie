package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.Expense;
import com.bookie.model.Income;
import com.bookie.model.OutlookSettings;
import com.bookie.model.PendingExpense;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.OutlookSettingsRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.bookie.repository.ReceiptHashRepository;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private GraphServiceClient graphClient;

  @Mock private MsalTokenService msalTokenService;
  @Mock private ExpenseRepository expenseRepository;
  @Mock private IncomeRepository incomeRepository;
  @Mock private OutlookSettingsRepository outlookSettingsRepository;
  @Mock private ReceiptHashRepository receiptHashRepository;
  @Mock private PendingExpenseRepository pendingExpenseRepository;

  @InjectMocks private ReceiptService receiptService;

  @Nested
  class ListReceipts {

    @Test
    void usesBatchLookupForLinkedExpenseAndIncome() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(OutlookSettings.builder().receiptsFolderBase("bookie/taxes").build()));
      when(graphClient.me().drive().get().getId()).thenReturn("drive-1");
      when(graphClient
              .drives()
              .byDriveId("drive-1")
              .items()
              .byDriveItemId("root:/bookie/taxes/pending:")
              .children()
              .get())
          .thenReturn(response(file("pending-1", "pending.pdf")));
      when(graphClient
              .drives()
              .byDriveId("drive-1")
              .items()
              .byDriveItemId("root:/bookie/taxes:")
              .children()
              .get())
          .thenReturn(response(folder("folder-2026", "2026")));
      when(graphClient
              .drives()
              .byDriveId("drive-1")
              .items()
              .byDriveItemId("folder-2026")
              .children()
              .get())
          .thenReturn(response(file("year-1", "final.pdf")));

      Expense expense = Expense.builder().id(11L).receiptOneDriveId("pending-1").build();
      Income income = Income.builder().id(22L).receiptOneDriveId("year-1").build();
      when(expenseRepository.findByReceiptOneDriveIdIn(List.of("pending-1", "year-1")))
          .thenReturn(List.of(expense));
      when(incomeRepository.findByReceiptOneDriveIdIn(List.of("pending-1", "year-1")))
          .thenReturn(List.of(income));

      var receipts = receiptService.listReceipts();

      assertThat(receipts).hasSize(2);
      assertThat(receipts)
          .anySatisfy(
              receipt -> {
                assertThat(receipt.id()).isEqualTo("pending-1");
                assertThat(receipt.expenseId()).isEqualTo(11L);
                assertThat(receipt.incomeId()).isNull();
                assertThat(receipt.pending()).isTrue();
              })
          .anySatisfy(
              receipt -> {
                assertThat(receipt.id()).isEqualTo("year-1");
                assertThat(receipt.expenseId()).isNull();
                assertThat(receipt.incomeId()).isEqualTo(22L);
                assertThat(receipt.pending()).isFalse();
                assertThat(receipt.year()).isEqualTo(2026);
              });
      verify(expenseRepository).findByReceiptOneDriveIdIn(List.of("pending-1", "year-1"));
      verify(incomeRepository).findByReceiptOneDriveIdIn(List.of("pending-1", "year-1"));
      verify(expenseRepository, never()).findByReceiptOneDriveId(anyString());
      verify(incomeRepository, never()).findByReceiptOneDriveId(anyString());
    }

    @Test
    void returnsEmptyWhenNoFilesAndSkipsBatchQueries() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(OutlookSettings.builder().receiptsFolderBase("bookie/taxes").build()));
      when(graphClient.me().drive().get().getId()).thenReturn("drive-1");
      when(graphClient
              .drives()
              .byDriveId("drive-1")
              .items()
              .byDriveItemId("root:/bookie/taxes/pending:")
              .children()
              .get())
          .thenReturn(response());
      when(graphClient
              .drives()
              .byDriveId("drive-1")
              .items()
              .byDriveItemId("root:/bookie/taxes:")
              .children()
              .get())
          .thenReturn(response());

      assertThat(receiptService.listReceipts()).isEmpty();
      verify(expenseRepository, never()).findByReceiptOneDriveIdIn(any());
      verify(incomeRepository, never()).findByReceiptOneDriveIdIn(any());
    }
  }

  @Nested
  class DeleteReceipt {

    @Test
    void continuesDatabaseCleanupWhenOneDriveDeleteFails() {
      when(graphClient.me().drive().get()).thenThrow(new RuntimeException("delete failed"));

      Expense expense = Expense.builder().id(7L).receiptOneDriveId("item-1").build();
      Income income = Income.builder().id(8L).receiptOneDriveId("item-1").build();
      PendingExpense pending = PendingExpense.builder().id(9L).sourceId("item-1").build();
      when(expenseRepository.findByReceiptOneDriveId("item-1")).thenReturn(Optional.of(expense));
      when(incomeRepository.findByReceiptOneDriveId("item-1")).thenReturn(Optional.of(income));
      when(pendingExpenseRepository.findBySourceId("item-1")).thenReturn(Optional.of(pending));

      receiptService.deleteReceipt("item-1");

      verify(expenseRepository).deleteById(7L);
      verify(incomeRepository).deleteById(8L);
      verify(pendingExpenseRepository).deleteById(9L);
      verify(receiptHashRepository).deleteByDriveItemId("item-1");
    }
  }

  private DriveItemCollectionResponse response(DriveItem... items) {
    DriveItemCollectionResponse response = new DriveItemCollectionResponse();
    response.setValue(List.of(items));
    return response;
  }

  private DriveItem file(String id, String name) {
    DriveItem item = new DriveItem();
    item.setId(id);
    item.setName(name);
    return item;
  }

  private DriveItem folder(String id, String name) {
    DriveItem item = file(id, name);
    item.setFolder(new Folder());
    return item;
  }
}
