package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.domain.Property;
import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.BankImportCommand;
import com.bookie.intake.application.BankImportResult;
import com.bookie.intake.application.InboxItemQueued;
import com.bookie.intake.application.InboxItemSaved;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.application.SaveInboxItemCommand;
import com.bookie.intake.application.VenmoImportCommand;
import com.bookie.intake.application.VenmoImportResult;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.domain.ExpenseSource;
import com.bookie.ledger.domain.FinancialTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InboxCommandServiceTest {

  private static final LocalDate DATE = LocalDate.of(2025, 6, 3);

  @Mock private InboxItemStore inboxItemStore;
  @Mock private BackgroundJobService backgroundJobService;
  @Mock private LedgerTransactionService ledgerTransactionService;
  @Mock private CounterpartyCatalog counterpartyCatalog;
  @Mock private OutlookService outlookService;
  @Mock private InboxItemCreationService inboxItemCreationService;
  @Mock private VenmoImportService venmoImportService;
  @Mock private BankImportService bankImportService;
  @Mock private ApplicationEventPublisher events;
  @Mock private PlatformTransactionManager transactionManager;

  private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
  private InboxCommandService service;

  @BeforeEach
  void setUp() {
    service =
        new InboxCommandService(
            inboxItemStore,
            backgroundJobService,
            ledgerTransactionService,
            counterpartyCatalog,
            outlookService,
            inboxItemCreationService,
            venmoImportService,
            bankImportService,
            events,
            transactionManager);
    lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    lenient().when(inboxItemStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Nested
  class Save {

    @Test
    void savesAReceiptAsALedgerExpenseWithItsAttachmentAndSchedulesTheMove() {
      InboxItem item = receiptItem();
      item.setProposedCounterpartyName("ACME Plumbing");
      item.addArtifact(alias("acct-4411"));
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(ledgerTransactionService.createFromInbox(any())).thenReturn(transaction(null));
      when(counterpartyCatalog.findOptionalById(3L))
          .thenReturn(Optional.of(Counterparty.builder().id(3L).name("Acme").build()));

      InboxItem saved = service.save(5L, command(TransactionDirection.EXPENSE, 3L, "ignored"));

      LedgerTransactionInput input = capturedInput();
      assertThat(input.getAmount()).isEqualByComparingTo("45.10");
      assertThat(input.getDirection()).isEqualTo(TransactionDirection.EXPENSE);
      assertThat(input.getDate()).isEqualTo(DATE);
      assertThat(input.getActivityId()).isEqualTo(42L);
      assertThat(input.getNeutralCategoryId()).isEqualTo(8L);
      assertThat(input.getCounterpartyId()).isEqualTo(3L);
      assertThat(input.getOrigin()).isEqualTo("RECEIPT");
      assertThat(input.getExternalId()).isEqualTo("drive-1");
      assertThat(input.getSourceLabel()).isNull();
      assertThat(input.getParentExternalId()).isNull();
      assertThat(input.getAttachmentStorageProvider()).isEqualTo("ONEDRIVE");
      assertThat(input.getAttachmentExternalId()).isEqualTo("drive-1");
      assertThat(input.getAttachmentFileName()).isEqualTo("receipt.pdf");

      verify(counterpartyCatalog).addAliasIfAbsent("ACME Plumbing", "acct-4411");
      verify(counterpartyCatalog).addAliasIfAbsent("Acme", "ACME Plumbing");
      assertThat(saved.getState()).isEqualTo(InboxState.SAVED);
      assertThat(saved.getRawStatus()).isEqualTo("SAVED");
      assertThat(saved.getFinancialTransactionId()).isEqualTo(77L);
      assertThat(saved.getProposedAmount()).isEqualByComparingTo("45.10");
      assertThat(saved.getLegacyActivityId()).isEqualTo(42L);
      assertThat(saved.getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);

      // The kick is published inside the save transaction; the listener runs after commit.
      InOrder order = inOrder(backgroundJobService, events, transactionManager);
      order.verify(backgroundJobService).schedule(item, BackgroundJobType.MOVE_RECEIPT, 2025);
      order.verify(events).publishEvent(new InboxItemSaved(5L));
      order.verify(transactionManager).commit(transactionStatus);
      verify(backgroundJobService, never()).scheduleIfAbsent(any(), any());
      // The durable MOVE_RECEIPT job files the receipt; the filing manual create path is unused.
      verify(ledgerTransactionService, never()).create(any());
    }

    @Test
    void savesAnOutlookAttachmentWithItsParentMessageAndTranslatesTheId() {
      InboxItem item = item(ExpenseSource.OUTLOOK_EMAIL, "outlook-attachment:sha");
      item.addArtifact(
          InboxArtifact.builder()
              .type(InboxArtifactType.OUTLOOK_EMAIL)
              .externalId("message-1")
              .textValue("attachment-1")
              .build());
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(ledgerTransactionService.createFromInbox(any())).thenReturn(transaction(null));

      service.save(5L, command(TransactionDirection.INCOME, null, "Tenant"));

      LedgerTransactionInput input = capturedInput();
      assertThat(input.getOrigin()).isEqualTo("OUTLOOK_EMAIL");
      assertThat(input.getExternalId()).isEqualTo("outlook-attachment:sha");
      assertThat(input.getParentExternalId()).isEqualTo("message-1");
      assertThat(input.getSourceLabel()).isEqualTo("Tenant");
      assertThat(input.getAttachmentExternalId()).isNull();
      assertThat(input.getAttachmentStorageProvider()).isNull();
      assertThat(item.getProposedSourceLabel()).isEqualTo("Tenant");
      verify(outlookService).validateEmailAutoMove(ExpenseSource.OUTLOOK_EMAIL);
      verify(backgroundJobService).scheduleIfAbsent(item, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      verify(backgroundJobService).schedule(item, BackgroundJobType.MOVE_OUTLOOK, null);
      verify(events).publishEvent(new InboxItemSaved(5L));
      verifyNoInteractions(counterpartyCatalog);
    }

    @Test
    void anOutlookItemWithAKnownImmutableIdIsNotTranslatedAgain() {
      InboxItem item = item(ExpenseSource.OUTLOOK_EMAIL, "message-1");
      item.setImmutableSourceId("immutable-1");
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(ledgerTransactionService.createFromInbox(any())).thenReturn(transaction(null));

      service.save(5L, command(TransactionDirection.EXPENSE, null, null));

      assertThat(capturedInput().getParentExternalId()).isNull();
      verify(backgroundJobService, never()).scheduleIfAbsent(any(), any());
    }

    @ParameterizedTest
    @EnumSource(
        value = ExpenseSource.class,
        names = {"VENMO", "BANK"})
    void importedIncomeKeepsItsSenderAndLabelUnlessTheReviewPicksOthers(ExpenseSource origin) {
      InboxItem item = item(origin, "imported-1");
      item.setLegacyCounterpartyId(12L);
      item.setProposedSourceLabel("Jane");
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      Property property = Property.builder().id(4L).build();
      when(ledgerTransactionService.createFromInbox(any())).thenReturn(transaction(property));

      InboxItem saved = service.save(5L, command(TransactionDirection.INCOME, null, null));

      LedgerTransactionInput input = capturedInput();
      assertThat(input.getOrigin()).isEqualTo(origin.name());
      assertThat(input.getExternalId()).isEqualTo("imported-1");
      assertThat(input.getAttachmentExternalId()).isNull();
      assertThat(input.getCounterpartyId()).isEqualTo(12L);
      assertThat(input.getSourceLabel()).isEqualTo("Jane");
      assertThat(saved.getLegacyCounterpartyId()).isEqualTo(12L);
      assertThat(saved.getLegacyPropertyId()).isEqualTo(4L);
      assertThat(saved.getExternalSyncState()).isEqualTo(ExternalSyncState.NOT_REQUIRED);
      verify(backgroundJobService, never()).schedule(any(), any(), any());
      verifyNoInteractions(events);
    }

    @ParameterizedTest
    @EnumSource(
        value = ExpenseSource.class,
        names = {"VENMO", "BANK"})
    void importedIncomeUsesTheReviewedSenderAndLabel(ExpenseSource origin) {
      InboxItem item = item(origin, "imported-1");
      item.setLegacyCounterpartyId(12L);
      item.setProposedSourceLabel("Jane");
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(ledgerTransactionService.createFromInbox(any())).thenReturn(transaction(null));

      InboxItem saved = service.save(5L, command(TransactionDirection.INCOME, 13L, "Janet"));

      assertThat(capturedInput().getCounterpartyId()).isEqualTo(13L);
      assertThat(capturedInput().getSourceLabel()).isEqualTo("Janet");
      assertThat(saved.getLegacyCounterpartyId()).isEqualTo(13L);
      assertThat(saved.getLegacyPropertyId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = ExpenseSource.class,
        names = {"VENMO", "BANK"})
    void importedIncomeCannotBeSavedAsAnExpense(ExpenseSource origin) {
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item(origin, "imported-1")));

      assertStatus(
          () -> service.save(5L, command(TransactionDirection.EXPENSE, null, null)),
          HttpStatus.BAD_REQUEST);
      verifyNoInteractions(ledgerTransactionService);
    }

    @Test
    void doesNotRelearnTheExtractedNameForTheSameCounterparty() {
      InboxItem item = receiptItem();
      item.setProposedCounterpartyName("acme");
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(ledgerTransactionService.createFromInbox(any())).thenReturn(transaction(null));
      when(counterpartyCatalog.findOptionalById(3L))
          .thenReturn(Optional.of(Counterparty.builder().id(3L).name("Acme").build()));

      service.save(5L, command(TransactionDirection.EXPENSE, 3L, null));

      verify(counterpartyCatalog, never()).addAliasIfAbsent(anyString(), anyString());
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"READY"},
        mode = EnumSource.Mode.EXCLUDE)
    void onlyAReadyItemCanBeSaved(InboxState state) {
      InboxItem item = receiptItem();
      item.setState(state);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));

      assertStatus(
          () -> service.save(5L, command(TransactionDirection.EXPENSE, null, null)),
          HttpStatus.CONFLICT);
      verifyNoInteractions(ledgerTransactionService, events);
    }

    @Test
    void rejectsAnUnknownItem() {
      when(inboxItemStore.findById(5L)).thenReturn(Optional.empty());

      assertStatus(
          () -> service.save(5L, command(TransactionDirection.EXPENSE, null, null)),
          HttpStatus.NOT_FOUND);
    }

    @Test
    void rollsBackWithoutKickingWhenAutoMoveIsMisconfigured() {
      InboxItem item = item(ExpenseSource.OUTLOOK_EMAIL, "message-1");
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No destination folder"))
          .when(outlookService)
          .validateEmailAutoMove(ExpenseSource.OUTLOOK_EMAIL);

      assertStatus(
          () -> service.save(5L, command(TransactionDirection.EXPENSE, null, null)),
          HttpStatus.BAD_REQUEST);
      verify(transactionManager).rollback(transactionStatus);
      verifyNoInteractions(ledgerTransactionService, events);
      assertThat(item.getState()).isEqualTo(InboxState.READY);
    }
  }

  @Nested
  class Dismiss {

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"RECEIVED", "QUEUED", "PROCESSING", "READY", "FAILED"})
    void dismissesAReceiptAndStopsItsJobs(InboxState state) {
      InboxItem item = receiptItem();
      item.setState(state);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));

      service.dismiss(5L);

      assertThat(item.getState()).isEqualTo(InboxState.DISMISSED);
      verify(backgroundJobService).terminalizeActive(5L, "DISMISSED");
      verify(backgroundJobService, never()).schedule(any(), any(), any());
      verifyNoInteractions(events);
    }

    @Test
    void dismissingAnOutlookItemStillMovesTheMessage() {
      InboxItem item = item(ExpenseSource.OUTLOOK_EMAIL, "message-1");
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));

      service.dismiss(5L);

      assertThat(item.getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
      verify(backgroundJobService).terminalizeActive(5L, "DISMISSED");
      verify(backgroundJobService).scheduleIfAbsent(item, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      verify(backgroundJobService).schedule(item, BackgroundJobType.MOVE_OUTLOOK, null);
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"SAVE_PENDING", "SAVED", "DISMISSED"})
    void rejectsAnItemThatIsNotDismissible(InboxState state) {
      InboxItem item = receiptItem();
      item.setState(state);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));

      assertStatus(() -> service.dismiss(5L), HttpStatus.CONFLICT);
      verify(backgroundJobService, never()).terminalizeActive(any(), any());
    }
  }

  @Nested
  class Reparse {

    @Test
    void requeuesAParsedItemAndPublishesItsParseKickInsideTheTransaction() {
      InboxItem item = item(ExpenseSource.OUTLOOK_EMAIL, "message-1");
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(inboxItemCreationService.requeueForRetry(item)).thenReturn(item);

      assertThat(service.reparse(5L)).isSameAs(item);

      InOrder order = inOrder(inboxItemCreationService, events, transactionManager);
      order.verify(inboxItemCreationService).requeueForRetry(item);
      order.verify(events).publishEvent(new InboxItemQueued(5L));
      order.verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void requeuesAFailedReceipt() {
      InboxItem item = receiptItem();
      item.setState(InboxState.FAILED);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(inboxItemCreationService.requeueForRetry(item)).thenReturn(item);

      service.reparse(5L);

      verify(events).publishEvent(new InboxItemQueued(5L));
    }

    @Test
    void importedItemsHaveNothingToReparse() {
      when(inboxItemStore.findById(5L))
          .thenReturn(Optional.of(item(ExpenseSource.VENMO, "venmo-1")));

      assertStatus(() -> service.reparse(5L), HttpStatus.CONFLICT);
      verifyNoInteractions(inboxItemCreationService, events);
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"RECEIVED", "QUEUED", "PROCESSING", "SAVE_PENDING", "SAVED", "DISMISSED"})
    void onlyReadyOrFailedItemsCanBeReparsed(InboxState state) {
      InboxItem item = receiptItem();
      item.setState(state);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));

      assertStatus(() -> service.reparse(5L), HttpStatus.CONFLICT);
      verifyNoInteractions(inboxItemCreationService, events);
    }
  }

  @Test
  void importVenmoDelegatesToTheImporter() {
    VenmoImportCommand command = VenmoImportCommand.builder().content(new byte[] {1}).build();
    VenmoImportResult result = VenmoImportResult.builder().importedRows(2).build();
    when(venmoImportService.importStatement(command)).thenReturn(result);

    assertThat(service.importVenmo(command)).isSameAs(result);
  }

  @Test
  void importBankDelegatesToTheImporter() {
    BankImportCommand command = BankImportCommand.builder().content(new byte[] {1}).build();
    BankImportResult result = BankImportResult.builder().importedRows(3).build();
    when(bankImportService.importStatement(command)).thenReturn(result);

    assertThat(service.importBank(command)).isSameAs(result);
  }

  private LedgerTransactionInput capturedInput() {
    ArgumentCaptor<LedgerTransactionInput> captor =
        ArgumentCaptor.forClass(LedgerTransactionInput.class);
    verify(ledgerTransactionService).createFromInbox(captor.capture());
    return captor.getValue();
  }

  private static SaveInboxItemCommand command(
      TransactionDirection direction, Long counterpartyId, String sourceLabel) {
    return SaveInboxItemCommand.builder()
        .direction(direction)
        .amount(new BigDecimal("45.10"))
        .date(DATE)
        .description("Faucet repair")
        .activityId(42L)
        .neutralCategoryId(8L)
        .counterpartyId(counterpartyId)
        .sourceLabel(sourceLabel)
        .build();
  }

  private static FinancialTransaction transaction(Property property) {
    return FinancialTransaction.builder()
        .id(77L)
        .amount(new BigDecimal("45.10"))
        .date(DATE)
        .description("Faucet repair")
        .activity(FinancialActivity.builder().id(42L).property(property).build())
        .build();
  }

  private static InboxItem receiptItem() {
    InboxItem item = item(ExpenseSource.RECEIPT, "drive-1");
    item.setReceiptExternalId("drive-1");
    item.setReceiptFileName("receipt.pdf");
    return item;
  }

  private static InboxItem item(ExpenseSource origin, String sourceId) {
    return InboxItem.builder()
        .id(5L)
        .origin(origin)
        .legacySourceId(sourceId)
        .state(InboxState.READY)
        .externalSyncState(ExternalSyncState.NOT_REQUIRED)
        .build();
  }

  private static InboxArtifact alias(String value) {
    return InboxArtifact.builder()
        .type(InboxArtifactType.UNRECOGNIZED_ALIAS)
        .textValue(value)
        .build();
  }

  private static void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(status));
  }
}
