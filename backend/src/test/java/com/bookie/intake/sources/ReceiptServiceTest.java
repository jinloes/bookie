package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.application.InboxCommands;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.integrations.onedrive.OneDriveItem;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.integrations.outlook.OutlookAuthorization;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.application.LedgerImportReferenceQuery.LinkedTransaction;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.domain.ExpenseSource;
import com.bookie.ledger.domain.FinancialTransaction;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC);

  @Mock private OneDrivePort oneDrive;
  @Mock private OutlookAuthorization msalTokenService;
  @Mock private OutlookSettingsRepository outlookSettingsRepository;
  @Mock private ReceiptHashRepository receiptHashRepository;
  @Mock private LedgerImportReferenceQuery ledgerImportReferenceQuery;
  @Mock private LedgerTransactionService ledgerTransactionService;
  @Mock private InboxItemStore inboxItemStore;
  @Mock private InboxCommands inboxCommands;

  @Mock
  private org.springframework.beans.factory.ObjectProvider<InboxCommands> inboxCommandsProvider;

  private ReceiptService receiptService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    receiptService =
        new ReceiptService(
            CLOCK,
            oneDrive,
            msalTokenService,
            outlookSettingsRepository,
            receiptHashRepository,
            ledgerImportReferenceQuery,
            ledgerTransactionService,
            inboxItemStore,
            inboxCommandsProvider);
  }

  @Nested
  class HasReceiptChecksum {

    @Test
    void missingExpectedChecksumFailsClosedWithoutDownloading() {
      assertThat(receiptService.hasReceiptChecksum("receipt-1", " ")).isFalse();

      verify(oneDrive, never()).download(anyString());
    }

    @Test
    void comparesTheCurrentRemoteBytes() {
      when(oneDrive.download("receipt-1"))
          .thenReturn(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));

      assertThat(
              receiptService.hasReceiptChecksum(
                  "receipt-1", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
          .isTrue();
    }

    @Test
    void checksumMismatchFailsClosed() {
      when(oneDrive.download("receipt-1"))
          .thenReturn(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));

      assertThat(receiptService.hasReceiptChecksum("receipt-1", "0".repeat(64))).isFalse();
    }

    @Test
    void missingRemoteContentFailsClosed() {
      when(oneDrive.download("receipt-1")).thenReturn(null);

      assertThat(receiptService.hasReceiptChecksum("receipt-1", "0".repeat(64))).isFalse();
    }

    @Test
    void checksumReadFailureIsRetryableForDurableMoves() throws IOException {
      InputStream brokenContent =
          new InputStream() {
            @Override
            public int read() throws IOException {
              throw new IOException("connection reset");
            }
          };
      when(oneDrive.download("receipt-1")).thenReturn(brokenContent);

      assertThatThrownBy(() -> receiptService.hasReceiptChecksum("receipt-1", "expected"))
          .isInstanceOf(IntegrationException.class)
          .satisfies(
              failure ->
                  assertThat(((IntegrationException) failure).getKind())
                      .isEqualTo(IntegrationFailureKind.TRANSIENT));
    }
  }

  @Nested
  class MoveTaxesFolder {

    @Test
    void movesTheReceiptIntoTheTaxYearFolder() {
      when(outlookSettingsRepository.findById(1L)).thenReturn(Optional.empty());
      when(oneDrive.getItem("receipt-1")).thenReturn(Optional.empty());
      when(oneDrive.ensureFolder(OutlookSettings.DEFAULT_RECEIPTS_FOLDER, "2026"))
          .thenReturn("year-folder");

      receiptService.moveTaxesFolder("receipt-1", 2026);

      verify(oneDrive).move("receipt-1", "year-folder");
    }

    @Test
    void logsAndSwallowsAFailedMove() {
      when(outlookSettingsRepository.findById(1L)).thenReturn(Optional.empty());
      when(oneDrive.getItem("receipt-1")).thenThrow(new IllegalStateException("offline"));

      assertThatCode(() -> receiptService.moveTaxesFolder("receipt-1", 2026))
          .doesNotThrowAnyException();
      verify(oneDrive, never()).move(any(), any());
    }
  }

  @Nested
  class ListReceipts {

    @Test
    void includesLooseFilesDroppedDirectlyInBaseFolder() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(OutlookSettings.builder().receiptsFolderBase("bookie/taxes").build()));
      when(oneDrive.listChildren("bookie/taxes/pending")).thenReturn(List.of());
      // A file copied directly into the base folder (not the pending/ subfolder, not a year
      // subfolder) — this is the scenario a user hits when they drag/drop straight into the
      // configured OneDrive folder without knowing about the pending/ convention.
      when(oneDrive.listChildren("bookie/taxes")).thenReturn(List.of(file("loose-1", "loose.pdf")));
      when(ledgerImportReferenceQuery.findActiveLinks(List.of("loose-1"))).thenReturn(List.of());

      var receipts = receiptService.listReceipts();

      assertThat(receipts).hasSize(1);
      assertThat(receipts.get(0).id()).isEqualTo("loose-1");
      assertThat(receipts.get(0).pending()).isTrue();
      assertThat(receipts.get(0).year()).isZero();
    }

    @Test
    void usesOneBatchLookupForLinkedExpenseAndIncomeTransactions() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(OutlookSettings.builder().receiptsFolderBase("bookie/taxes").build()));
      when(oneDrive.listChildren("bookie/taxes/pending"))
          .thenReturn(List.of(file("pending-1", "pending.pdf")));
      when(oneDrive.listChildren("bookie/taxes"))
          .thenReturn(List.of(folder("folder-2026", "2026")));
      when(oneDrive.listChildrenById("folder-2026"))
          .thenReturn(List.of(file("year-1", "final.pdf")));

      when(ledgerImportReferenceQuery.findActiveLinks(List.of("pending-1", "year-1")))
          .thenReturn(
              List.of(
                  new LinkedTransaction("pending-1", 11L, 0L, TransactionDirection.EXPENSE),
                  new LinkedTransaction("year-1", 22L, 0L, TransactionDirection.INCOME)));

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
      verify(ledgerImportReferenceQuery).findActiveLinks(List.of("pending-1", "year-1"));
    }

    @Test
    void prefersTheExpenseLinkWhenAReceiptBacksBothDirections() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(OutlookSettings.builder().receiptsFolderBase("bookie/taxes").build()));
      when(oneDrive.listChildren("bookie/taxes/pending"))
          .thenReturn(List.of(file("pending-1", "pending.pdf")));
      when(oneDrive.listChildren("bookie/taxes")).thenReturn(List.of());

      when(ledgerImportReferenceQuery.findActiveLinks(List.of("pending-1")))
          .thenReturn(
              List.of(
                  new LinkedTransaction("pending-1", 85L, 0L, TransactionDirection.INCOME),
                  new LinkedTransaction("pending-1", 84L, 0L, TransactionDirection.EXPENSE)));

      var receipts = receiptService.listReceipts();

      assertThat(receipts).hasSize(1);
      assertThat(receipts.get(0).expenseId()).isEqualTo(84L);
      assertThat(receipts.get(0).incomeId()).isNull();
      assertThat(receipts.get(0).pending()).isTrue();
    }

    @Test
    void includesFilesFromConfiguredImportFolders() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .receiptsFolderBase("bookie/taxes")
                      .receiptsImportFolders(List.of("Scans/Receipts"))
                      .build()));
      when(oneDrive.listChildren("bookie/taxes/pending")).thenReturn(List.of());
      when(oneDrive.listChildren("bookie/taxes")).thenReturn(List.of());
      when(oneDrive.listChildren("Scans/Receipts"))
          .thenReturn(List.of(file("scanned-1", "scanned.pdf")));
      when(ledgerImportReferenceQuery.findActiveLinks(List.of("scanned-1"))).thenReturn(List.of());

      var receipts = receiptService.listReceipts();

      assertThat(receipts).hasSize(1);
      assertThat(receipts.get(0).id()).isEqualTo("scanned-1");
      assertThat(receipts.get(0).pending()).isTrue();
      assertThat(receipts.get(0).year()).isZero();
    }

    @Test
    void dedupesFileAppearingInBothPendingFolderAndImportFolder() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .receiptsFolderBase("bookie/taxes")
                      .receiptsImportFolders(List.of("bookie/taxes/pending"))
                      .build()));
      when(oneDrive.listChildren("bookie/taxes/pending"))
          .thenReturn(List.of(file("pending-1", "pending.pdf")));
      when(oneDrive.listChildren("bookie/taxes")).thenReturn(List.of());
      when(ledgerImportReferenceQuery.findActiveLinks(List.of("pending-1"))).thenReturn(List.of());

      var receipts = receiptService.listReceipts();

      assertThat(receipts).hasSize(1);
    }

    @Test
    void returnsEmptyWhenNoFilesAndSkipsBatchQueries() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(OutlookSettings.builder().receiptsFolderBase("bookie/taxes").build()));
      when(oneDrive.listChildren("bookie/taxes/pending")).thenReturn(List.of());
      when(oneDrive.listChildren("bookie/taxes")).thenReturn(List.of());

      assertThat(receiptService.listReceipts()).isEmpty();
      verify(ledgerImportReferenceQuery, never()).findActiveLinks(any());
    }
  }

  @Nested
  class ReceiptsImportFolders {

    @Test
    void getReceiptsImportFoldersReturnsEmptyWhenNoSettingsRow() {
      when(outlookSettingsRepository.findById(1L)).thenReturn(Optional.empty());

      assertThat(receiptService.getReceiptsImportFolders()).isEmpty();
    }

    @Test
    void updateReceiptsImportFoldersTrimsBlanksAndDuplicates() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(Optional.of(OutlookSettings.builder().id(1L).build()));

      receiptService.updateReceiptsImportFolders(
          List.of(" Scans/Receipts ", "", "Scans/Receipts", "  "));

      org.mockito.ArgumentCaptor<OutlookSettings> captor =
          org.mockito.ArgumentCaptor.forClass(OutlookSettings.class);
      verify(outlookSettingsRepository).save(captor.capture());
      assertThat(captor.getValue().getReceiptsImportFolders()).containsExactly("Scans/Receipts");
    }
  }

  @Nested
  class DeleteReceipt {

    @Test
    void continuesDatabaseCleanupWhenOneDriveDeleteFails() {
      doThrow(new RuntimeException("delete failed")).when(oneDrive).delete("item-1");
      when(ledgerImportReferenceQuery.findActiveLinks(List.of("item-1")))
          .thenReturn(
              List.of(
                  new LinkedTransaction("item-1", 7L, 2L, TransactionDirection.EXPENSE),
                  new LinkedTransaction("item-1", 7L, 2L, TransactionDirection.EXPENSE),
                  new LinkedTransaction("item-1", 8L, 0L, TransactionDirection.INCOME)));
      when(ledgerTransactionService.findById(7L))
          .thenReturn(FinancialTransaction.builder().id(7L).version(3L).build());
      when(ledgerTransactionService.findById(8L))
          .thenReturn(FinancialTransaction.builder().id(8L).version(0L).build());
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "item-1"))
          .thenReturn(Optional.of(item(9L, InboxState.READY)));
      when(inboxCommandsProvider.getObject()).thenReturn(inboxCommands);

      receiptService.deleteReceipt("item-1");

      verify(ledgerTransactionService).delete(7L, 3L);
      verify(ledgerTransactionService).delete(8L, 0L);
      verify(inboxCommands).dismiss(9L);
      verify(receiptHashRepository).deleteByDriveItemId("item-1");
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"SAVE_PENDING", "SAVED", "DISMISSED"})
    void leavesNonDismissibleInboxItemsUntouched(InboxState state) {
      when(ledgerImportReferenceQuery.findActiveLinks(List.of("item-1"))).thenReturn(List.of());
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "item-1"))
          .thenReturn(Optional.of(item(9L, state)));

      receiptService.deleteReceipt("item-1");

      verify(inboxCommandsProvider, never()).getObject();
      verify(ledgerTransactionService, never()).delete(any(), any());
      verify(receiptHashRepository).deleteByDriveItemId("item-1");
    }
  }

  private static InboxItem item(Long id, InboxState state) {
    return InboxItem.builder().id(id).origin(ExpenseSource.RECEIPT).state(state).build();
  }

  private OneDriveItem file(String id, String name) {
    return OneDriveItem.builder().id(id).name(name).build();
  }

  private OneDriveItem folder(String id, String name) {
    return OneDriveItem.builder().id(id).name(name).folder(true).build();
  }
}
