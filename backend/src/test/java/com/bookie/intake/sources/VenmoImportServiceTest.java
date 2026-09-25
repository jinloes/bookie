package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.financialcategory.application.FinancialCategoryService;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.application.VenmoImportCommand;
import com.bookie.intake.application.VenmoImportResult;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.integrations.venmo.VenmoInputException;
import com.bookie.integrations.venmo.VenmoInputPort;
import com.bookie.integrations.venmo.VenmoStatement;
import com.bookie.integrations.venmo.VenmoTransaction;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.ExpenseSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VenmoImportServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-02-01T08:00:00Z"), ZoneOffset.UTC);
  private static final byte[] CSV = "csv".getBytes();
  private static final LocalDate DATE = LocalDate.of(2025, 5, 1);

  @Mock private CounterpartyCatalog counterpartyCatalog;
  @Mock private PropertyCatalog propertyCatalog;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private FinancialCategoryService financialCategoryService;
  @Mock private ClassificationHistory classificationHistory;
  @Mock private VenmoInputPort venmoInput;
  @Mock private ReceiptService receiptService;
  @Mock private InboxItemStore inboxItemStore;
  @Mock private LedgerImportReferenceQuery ledgerImportReferenceQuery;
  @Mock private ReceiptHashRepository receiptHashRepository;

  private final Property mainSt = Property.builder().id(4L).name("Main St").build();
  private final FinancialActivity rental =
      FinancialActivity.builder().id(42L).name("Main St rental").property(mainSt).build();
  private final FinancialActivity needsClassification =
      FinancialActivity.builder()
          .id(1L)
          .systemKey(ActivityCatalog.NEEDS_CLASSIFICATION_KEY)
          .build();
  private final Counterparty jane =
      Counterparty.builder()
          .id(12L)
          .name("Jane Doe")
          .aliases(new ArrayList<>(List.of("JD")))
          .accounts(new java.util.HashSet<>(Set.of("jane-venmo")))
          .build();

  private VenmoImportService service;

  @BeforeEach
  void setUp() {
    service =
        new VenmoImportService(
            CLOCK,
            counterpartyCatalog,
            propertyCatalog,
            activityCatalog,
            financialCategoryService,
            classificationHistory,
            venmoInput,
            receiptService,
            inboxItemStore,
            ledgerImportReferenceQuery,
            receiptHashRepository);
    lenient().when(activityCatalog.resolveForProperty(null)).thenReturn(needsClassification);
    lenient().when(activityCatalog.resolveForProperty(mainSt)).thenReturn(rental);
    lenient()
        .when(financialCategoryService.defaultFor(any(), eq(TransactionDirection.INCOME), any()))
        .thenReturn(FinancialCategory.builder().id(30L).build());
    lenient()
        .when(inboxItemStore.findBySourceIdentity(eq(ExpenseSource.VENMO), anyString()))
        .thenReturn(Optional.empty());
    lenient().when(inboxItemStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void importsIncomingRowsAsReadyInboxIncomeWithTheArchivedStatement() throws IOException {
    when(receiptService.isConnected()).thenReturn(true);
    when(receiptService.uploadReceipt("statement.csv", CSV))
        .thenReturn(
            new UploadReceiptResponse(
                new ReceiptDto("drive-9", "statement.csv", 0, null, null, null, null, true),
                false));
    when(receiptHashRepository.findByDriveItemId("drive-9"))
        .thenReturn(Optional.of(ReceiptHash.builder().sha256("abc").build()));
    when(counterpartyCatalog.findByName("Jane Doe")).thenReturn(Optional.of(jane));
    when(classificationHistory.findMostLikelyPropertyForCounterparty(12L))
        .thenReturn(Optional.of(mainSt));
    parse(
        new VenmoStatement(
            1, 0, List.of(row("venmo-1", "@Jane Doe", "25.00", DATE, "Venmo - rent"))));

    VenmoImportResult result = service.importStatement(command(null, null, null));

    assertThat(result.totalRows()).isEqualTo(1);
    assertThat(result.importedRows()).isEqualTo(1);
    InboxItem item = savedItems().getFirst();
    assertThat(item.getOrigin()).isEqualTo(ExpenseSource.VENMO);
    assertThat(item.getState()).isEqualTo(InboxState.READY);
    assertThat(item.getRawStatus()).isEqualTo("READY");
    assertThat(item.getLegacySourceType()).isEqualTo("VENMO");
    assertThat(item.getLegacySourceId()).isEqualTo("venmo-1");
    assertThat(item.getProposedDirection()).isEqualTo(TransactionDirection.INCOME);
    assertThat(item.getProposedAmount()).isEqualByComparingTo("25.00");
    assertThat(item.getProposedDescription()).isEqualTo("Venmo - rent");
    assertThat(item.getProposedDate()).isEqualTo(DATE);
    assertThat(item.getProposedSourceLabel()).isEqualTo("Jane Doe");
    assertThat(item.getLegacyActivityId()).isEqualTo(42L);
    assertThat(item.getLegacyCategoryId()).isEqualTo(30L);
    assertThat(item.getLegacyPropertyId()).isEqualTo(4L);
    assertThat(item.getLegacyCounterpartyId()).isEqualTo(12L);
    assertThat(item.isClassificationAmbiguous()).isFalse();
    assertThat(item.getReceiptExternalId()).isEqualTo("drive-9");
    assertThat(item.getReceiptFileName()).isEqualTo("statement.csv");
    assertThat(item.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    assertThat(item.getArtifacts())
        .singleElement()
        .satisfies(
            artifact -> {
              assertThat(artifact.getType()).isEqualTo(InboxArtifactType.RECEIPT);
              assertThat(artifact.getExternalId()).isEqualTo("drive-9");
              assertThat(artifact.getSha256()).isEqualTo("abc");
            });
    verify(receiptService).moveTaxesFolder("drive-9", 2025);
  }

  @Test
  void countsSkippedRowsByReason() throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(counterpartyCatalog.findById(12L)).thenReturn(jane);
    when(ledgerImportReferenceQuery.existsActive("VENMO", "saved")).thenReturn(true);
    when(ledgerImportReferenceQuery.existsActive("VENMO", "active")).thenReturn(false);
    when(inboxItemStore.findBySourceIdentity(ExpenseSource.VENMO, "active"))
        .thenReturn(Optional.of(InboxItem.builder().id(3L).state(InboxState.READY).build()));
    when(classificationHistory.findMostLikelyPropertyForCounterparty(12L))
        .thenReturn(Optional.empty());
    parse(
        new VenmoStatement(
            8,
            1,
            List.of(
                row("outgoing", "Jane Doe", "-5.00", DATE, "refund"),
                row("zero", "Jane Doe", "0.00", DATE, "zero"),
                row("other", "Someone Else", "5.00", DATE, "gift"),
                row("blank-sender", " ", "5.00", DATE, "gift"),
                row(" ", "Jane Doe", "5.00", DATE, "no id"),
                row("no-date", "Jane Doe", "5.00", null, "no date"),
                row("saved", "Jane Doe", "5.00", DATE, "saved"),
                row("active", "Jane Doe", "5.00", DATE, "active"))));

    VenmoImportResult result = service.importStatement(command(12L, null, null));

    assertThat(result.totalRows()).isEqualTo(8);
    assertThat(result.importedRows()).isZero();
    assertThat(result.skippedOutgoingRows()).isEqualTo(2);
    assertThat(result.skippedSenderRows()).isEqualTo(2);
    assertThat(result.skippedInvalidRows()).isEqualTo(3);
    assertThat(result.skippedDuplicateRows()).isEqualTo(2);
    assertThat(result.senderFilter()).isEqualTo("Jane Doe");
    assertThat(result.propertyName()).isNull();
    verify(inboxItemStore, never()).save(any());
    verify(receiptService, never()).moveTaxesFolder(anyString(), anyInt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"jane doe", "@JD", "jane-venmo"})
  void matchesTheSelectedPayerByNameAliasOrAccount(String sender) throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(counterpartyCatalog.findById(12L)).thenReturn(jane);
    when(classificationHistory.findMostLikelyPropertyForCounterparty(12L))
        .thenReturn(Optional.of(mainSt));
    parse(new VenmoStatement(1, 0, List.of(row("venmo-1", sender, "5.00", DATE, "rent"))));

    VenmoImportResult result = service.importStatement(command(12L, null, null));

    assertThat(result.importedRows()).isOne();
    assertThat(result.propertyName()).isEqualTo("Main St");
    assertThat(savedItems().getFirst().getLegacyCounterpartyId()).isEqualTo(12L);
  }

  @Test
  void aSelectedActivityOverridesPropertyDetection() throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(propertyCatalog.findById(4L)).thenReturn(mainSt);
    when(activityCatalog.resolveForTransaction(42L, 4L)).thenReturn(rental);
    parse(new VenmoStatement(1, 0, List.of(row("venmo-1", "Unknown", "5.00", DATE, "rent"))));

    VenmoImportResult result = service.importStatement(command(null, 42L, 4L));

    assertThat(result.activityName()).isEqualTo("Main St rental");
    assertThat(result.propertyName()).isEqualTo("Main St");
    InboxItem item = savedItems().getFirst();
    assertThat(item.getLegacyActivityId()).isEqualTo(42L);
    assertThat(item.getLegacyCounterpartyId()).isNull();
    assertThat(item.getProposedSourceLabel()).isEqualTo("Unknown");
    verify(classificationHistory, never()).findMostLikelyPropertyForCounterparty(any());
  }

  @Test
  void anUnknownSenderNeedsClassificationAndKeepsTheVenmoLabel() throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(counterpartyCatalog.findByName("Stranger")).thenReturn(Optional.empty());
    when(counterpartyCatalog.findByAlias("Stranger")).thenReturn(Optional.empty());
    parse(
        new VenmoStatement(
            2,
            0,
            List.of(
                row("venmo-1", "@Stranger", "5.00", DATE, "gift"),
                row("venmo-2", null, "6.00", DATE, "gift"))));

    service.importStatement(command(null, null, null));

    List<InboxItem> items = savedItems();
    assertThat(items)
        .extracting(InboxItem::getProposedSourceLabel)
        .containsExactly("@Stranger", "Venmo");
    assertThat(items).allSatisfy(item -> assertThat(item.isClassificationAmbiguous()).isTrue());
    assertThat(items).allSatisfy(item -> assertThat(item.getLegacyPropertyId()).isNull());
  }

  @Test
  void aSelectedPropertyAppliesToEveryRow() throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(propertyCatalog.findById(4L)).thenReturn(mainSt);
    when(counterpartyCatalog.findByName("Someone")).thenReturn(Optional.empty());
    when(counterpartyCatalog.findByAlias("Someone")).thenReturn(Optional.of(jane));
    parse(new VenmoStatement(1, 0, List.of(row("venmo-1", "Someone", "5.00", DATE, "rent"))));

    VenmoImportResult result = service.importStatement(command(null, null, 4L));

    assertThat(result.propertyName()).isEqualTo("Main St");
    InboxItem item = savedItems().getFirst();
    assertThat(item.getLegacyActivityId()).isEqualTo(42L);
    assertThat(item.getLegacyCounterpartyId()).isEqualTo(12L);
    verify(classificationHistory, never()).findMostLikelyPropertyForCounterparty(any());
  }

  @Test
  void reimportsADismissedRowAsAFreshReadyItem() throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    InboxItem dismissed =
        InboxItem.builder()
            .id(3L)
            .origin(ExpenseSource.VENMO)
            .state(InboxState.DISMISSED)
            .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0))
            .build();
    when(inboxItemStore.findBySourceIdentity(ExpenseSource.VENMO, "venmo-1"))
        .thenReturn(Optional.of(dismissed));
    when(counterpartyCatalog.findByName("x")).thenReturn(Optional.empty());
    when(counterpartyCatalog.findByAlias("x")).thenReturn(Optional.empty());
    parse(new VenmoStatement(1, 0, List.of(row("venmo-1", "x", "5.00", DATE, "rent"))));

    VenmoImportResult result = service.importStatement(command(null, null, null));

    assertThat(result.importedRows()).isOne();
    assertThat(dismissed.getState()).isEqualTo(InboxState.READY);
    assertThat(dismissed.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    verify(inboxItemStore).save(dismissed);
  }

  @ParameterizedTest
  @EnumSource(
      value = InboxState.class,
      names = {"DISMISSED"},
      mode = EnumSource.Mode.EXCLUDE)
  void skipsARowWithAnyOtherExistingInboxItem(InboxState state) throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(inboxItemStore.findBySourceIdentity(ExpenseSource.VENMO, "venmo-1"))
        .thenReturn(Optional.of(InboxItem.builder().id(3L).state(state).build()));
    parse(new VenmoStatement(1, 0, List.of(row("venmo-1", "x", "5.00", DATE, "rent"))));

    assertThat(service.importStatement(command(null, null, null)).skippedDuplicateRows()).isOne();
  }

  @Test
  void aRowThatFailsToResolveCountsAsInvalid() throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(counterpartyCatalog.findByName("x")).thenThrow(new IllegalStateException("db down"));
    parse(new VenmoStatement(1, 0, List.of(row("venmo-1", "x", "5.00", DATE, "rent"))));

    VenmoImportResult result = service.importStatement(command(null, null, null));

    assertThat(result.skippedInvalidRows()).isOne();
    assertThat(result.importedRows()).isZero();
  }

  @Test
  void importsWithoutAnAttachmentWhenTheUploadReturnsNoId() throws IOException {
    when(receiptService.isConnected()).thenReturn(true);
    when(receiptService.uploadReceipt("venmo-statement.csv", CSV)).thenReturn(null);
    when(counterpartyCatalog.findByName("x")).thenReturn(Optional.empty());
    when(counterpartyCatalog.findByAlias("x")).thenReturn(Optional.empty());
    parse(new VenmoStatement(1, 0, List.of(row("venmo-1", "x", "5.00", DATE, "rent"))));

    service.importStatement(VenmoImportCommand.builder().content(CSV).fileName(" ").build());

    InboxItem item = savedItems().getFirst();
    assertThat(item.getReceiptExternalId()).isNull();
    assertThat(item.getArtifacts()).isEmpty();
    verify(receiptService, never()).moveTaxesFolder(anyString(), anyInt());
  }

  @Test
  void rejectsAMalformedStatementAfterArchivingNothing() throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(venmoInput.parse(CSV)).thenThrow(new VenmoInputException("Missing header"));

    assertThatThrownBy(() -> service.importStatement(command(null, null, null)))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> {
              assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(failure.getReason()).isEqualTo("Missing header");
            });
  }

  @Test
  void surfacesUnreadableStatements() throws IOException {
    when(receiptService.isConnected()).thenReturn(false);
    when(venmoInput.parse(CSV)).thenThrow(new IOException("closed"));

    assertThatThrownBy(() -> service.importStatement(command(null, null, null)))
        .isInstanceOf(UncheckedIOException.class)
        .hasRootCauseMessage("closed");
  }

  private void parse(VenmoStatement statement) throws IOException {
    when(venmoInput.parse(CSV)).thenReturn(statement);
  }

  private List<InboxItem> savedItems() {
    ArgumentCaptor<InboxItem> captor = ArgumentCaptor.forClass(InboxItem.class);
    verify(inboxItemStore, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getAllValues();
  }

  private static VenmoImportCommand command(Long counterpartyId, Long activityId, Long propertyId) {
    return VenmoImportCommand.builder()
        .content(CSV)
        .fileName("statement.csv")
        .counterpartyId(counterpartyId)
        .activityId(activityId)
        .propertyId(propertyId)
        .build();
  }

  private static VenmoTransaction row(
      String sourceId, String sender, String amount, LocalDate date, String description) {
    return new VenmoTransaction(sourceId, sender, new BigDecimal(amount), date, description);
  }
}
