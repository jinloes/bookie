package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.ConfirmedClassification;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.bookie.intake.application.LegacyInboxReadSelector;
import com.bookie.intake.application.LegacyInboxSynchronizer;
import com.bookie.integrations.venmo.VenmoCsvInputAdapter;
import com.bookie.integrations.venmo.VenmoInputPort;
import com.bookie.ledger.application.LedgerReadMode;
import com.bookie.ledger.compatibility.LegacyLedgerReadAdapter;
import com.bookie.ledger.compatibility.LegacyLedgerSynchronizer;
import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import com.bookie.model.PendingIncome;
import com.bookie.model.PendingIncomeStatus;
import com.bookie.model.ReceiptDto;
import com.bookie.model.TransactionDirection;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.PendingIncomeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

  @Mock private IncomeRepository incomeRepository;
  @Mock private PropertyCatalog propertyCatalog;
  @Mock private CounterpartyCatalog counterpartyCatalog;
  @Mock private ReceiptService receiptService;
  @Mock private PendingIncomeRepository pendingIncomeRepository;
  @Mock private ClassificationHistory classificationHistory;
  @Mock private ActivityCatalog financialActivityService;
  @Mock private FinancialCategoryService financialCategoryService;
  @Mock private LegacyLedgerSynchronizer ledgerSynchronizer;
  @Mock private LegacyLedgerReadAdapter ledgerReadAdapter;
  @Spy private VenmoInputPort venmoInput = new VenmoCsvInputAdapter();
  @Mock private LegacyInboxSynchronizer inboxSynchronizer;
  @Mock private LegacyInboxReadSelector inboxReadSelector;

  @InjectMocks private IncomeService incomeService;

  private Income income;
  private Property property;
  private Counterparty payer;

  @BeforeEach
  void setUp() {
    lenient()
        .when(pendingIncomeRepository.save(any()))
        .thenAnswer(
            invocation -> {
              PendingIncome pending = invocation.getArgument(0);
              if (pending.getId() == null) {
                pending.setId(99L);
              }
              return pending;
            });
    lenient()
        .when(inboxReadSelector.select(any(), any(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    property =
        Property.builder()
            .id(1L)
            .name("123 Main St")
            .address("123 Main St")
            .type(PropertyType.SINGLE_FAMILY)
            .build();
    payer = Counterparty.builder().id(2L).name("Tenant A").type(CounterpartyType.PERSON).build();
    income =
        Income.builder()
            .id(1L)
            .amount(new BigDecimal("1200.00"))
            .description("Monthly rent")
            .date(LocalDate.of(2024, 1, 1))
            .source("Rent")
            .property(property)
            .payer(payer)
            .build();
    lenient()
        .when(financialActivityService.resolveForTransaction(any(), any()))
        .thenAnswer(invocation -> activityFor(invocation.getArgument(1) == null ? null : property));
    lenient()
        .when(financialActivityService.resolveForProperty(nullable(Property.class)))
        .thenAnswer(invocation -> activityFor(invocation.getArgument(0)));
    lenient()
        .when(
            financialCategoryService.resolve(
                any(), any(), eq(TransactionDirection.INCOME), any(), any()))
        .thenAnswer(invocation -> incomeCategoryFor((FinancialActivity) invocation.getArgument(3)));
    lenient()
        .when(financialCategoryService.defaultFor(any(), eq(TransactionDirection.INCOME), any()))
        .thenAnswer(invocation -> incomeCategoryFor((FinancialActivity) invocation.getArgument(0)));
    lenient()
        .when(
            financialCategoryService.isCompatible(
                any(), any(), eq(TransactionDirection.INCOME), any()))
        .thenAnswer(
            invocation -> {
              FinancialCategory category = invocation.getArgument(0);
              FinancialActivity activity = invocation.getArgument(1);
              return category != null
                  && category.isActive()
                  && category.getDirection() == TransactionDirection.INCOME
                  && category.getTaxTreatment() == activity.getTaxTreatment();
            });
  }

  @Test
  void findAll_usesUnifiedLedgerByDefault() {
    when(ledgerReadAdapter.findAllIncomes()).thenReturn(List.of(income));

    List<Income> result = incomeService.findAll();

    assertThat(result).hasSize(1).containsExactly(income);
    verify(incomeRepository, never()).findAll(any(Sort.class));
  }

  @Test
  void findById_found_returnsIncome() {
    ReflectionTestUtils.setField(incomeService, "ledgerReadMode", LedgerReadMode.LEGACY);
    when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));

    Income result = incomeService.findById(1L);

    assertThat(result).isEqualTo(income);
  }

  @Test
  void findById_notFound_throwsException() {
    ReflectionTestUtils.setField(incomeService, "ledgerReadMode", LedgerReadMode.LEGACY);
    when(incomeRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> incomeService.findById(99L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("99");
  }

  @Test
  void save_persistsAndReturnsIncome() {
    when(incomeRepository.save(income)).thenReturn(income);

    Income result = incomeService.save(income);

    assertThat(result).isEqualTo(income);
    verify(incomeRepository).save(income);
    verify(ledgerSynchronizer).synchronize(income);
    verify(classificationHistory).record(any(ConfirmedClassification.class));
  }

  @Nested
  class PendingIncomeLifecycle {

    @Test
    void acceptanceSynchronizesSavePendingAndSavedBeforeRemovingLegacyRow() {
      PendingIncome pending = pendingIncome(7L);
      when(pendingIncomeRepository.findById(7L)).thenReturn(Optional.of(pending));
      when(incomeRepository.save(any(Income.class)))
          .thenAnswer(
              invocation -> {
                Income saved = invocation.getArgument(0);
                saved.setId(88L);
                return saved;
              });
      UpdateIncomeRequest updates =
          new UpdateIncomeRequest(
              new BigDecimal("1250.00"),
              "Updated rent",
              LocalDate.of(2026, 8, 1),
              "Rent",
              property.getId(),
              null);

      Income accepted = incomeService.acceptPendingIncome(7L, updates);

      assertThat(accepted.getId()).isEqualTo(88L);
      verify(inboxSynchronizer).savePending(any(), any());
      verify(inboxSynchronizer).saved(any(), any(), any());
      verify(pendingIncomeRepository).deleteById(7L);
    }

    @Test
    void rejectionSynchronizesDismissalBeforeRemovingLegacyRow() {
      PendingIncome pending = pendingIncome(8L);
      when(pendingIncomeRepository.findById(8L)).thenReturn(Optional.of(pending));

      incomeService.rejectPendingIncome(8L);

      verify(inboxSynchronizer).dismissed(any(), any());
      verify(pendingIncomeRepository).delete(pending);
    }
  }

  @Nested
  class Create {

    @Test
    void withProperty_resolvesPropertyAndSaves() {
      CreateIncomeRequest req =
          new CreateIncomeRequest(
              new BigDecimal("1200.00"),
              "Monthly rent",
              LocalDate.of(2024, 1, 1),
              "Rent",
              1L,
              2L,
              ExpenseSource.MANUAL,
              null,
              null);
      when(counterpartyCatalog.findById(2L)).thenReturn(payer);
      when(incomeRepository.save(any())).thenReturn(income);

      Income result = incomeService.create(req);

      assertThat(result).isEqualTo(income);
      verify(financialActivityService).resolveForTransaction(null, 1L);
      verify(counterpartyCatalog).findById(2L);
      verify(incomeRepository).save(any());
    }

    @Test
    void withNullPropertyId_savesWithoutProperty() {
      CreateIncomeRequest req =
          new CreateIncomeRequest(
              new BigDecimal("1200.00"),
              "Monthly rent",
              LocalDate.of(2024, 1, 1),
              "Rent",
              null,
              null,
              null,
              null,
              null);
      when(incomeRepository.save(any())).thenReturn(income);

      Income result = incomeService.create(req);

      assertThat(result).isEqualTo(income);
      verify(incomeRepository).save(any());
    }

    @Test
    void manualCreate_usesFreeTextSourceAndServerControlledManualOrigin() {
      CreateIncomeRequest req =
          new CreateIncomeRequest(
              new BigDecimal("2400.00"),
              "August paycheck",
              LocalDate.of(2026, 8, 15),
              "School District",
              null,
              null,
              ExpenseSource.VENMO,
              null,
              null);
      ArgumentCaptor<Income> captor = ArgumentCaptor.forClass(Income.class);
      when(incomeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      incomeService.create(req);

      verify(incomeRepository).save(captor.capture());
      assertThat(captor.getValue().getSource()).isEqualTo("School District");
      assertThat(captor.getValue().getSourceType()).isEqualTo(ExpenseSource.MANUAL);
      assertThat(captor.getValue().getProperty()).isNull();
      assertThat(captor.getValue().getPayer()).isNull();
    }

    @Test
    void w2PaycheckPersistsEmploymentActivityAndWagesCategory() {
      FinancialActivity teaching =
          FinancialActivity.builder()
              .id(10L)
              .name("Teaching")
              .taxTreatment(TaxTreatment.W2)
              .active(true)
              .build();
      FinancialCategory wages =
          FinancialCategory.builder()
              .id(30L)
              .key("WAGES")
              .direction(TransactionDirection.INCOME)
              .taxTreatment(TaxTreatment.W2)
              .active(true)
              .build();
      when(financialActivityService.resolveForTransaction(10L, null)).thenReturn(teaching);
      when(financialCategoryService.resolve(
              30L, null, TransactionDirection.INCOME, teaching, LocalDate.of(2026, 8, 15)))
          .thenReturn(wages);
      when(incomeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Income saved =
          incomeService.create(
              new CreateIncomeRequest(
                  new BigDecimal("2400.00"),
                  "School district paycheck",
                  LocalDate.of(2026, 8, 15),
                  "School District",
                  null,
                  null,
                  null,
                  null,
                  null,
                  10L,
                  30L));

      assertThat(saved.getActivity()).isEqualTo(teaching);
      assertThat(saved.getFinancialCategory()).isEqualTo(wages);
    }
  }

  @Nested
  class Update {

    @BeforeEach
    void useLegacyReadMode() {
      ReflectionTestUtils.setField(incomeService, "ledgerReadMode", LedgerReadMode.LEGACY);
    }

    @Test
    void updatesFieldsAndSaves() {
      Property otherProp =
          Property.builder()
              .id(2L)
              .name("456 Oak Ave")
              .address("456 Oak Ave")
              .type(PropertyType.SINGLE_FAMILY)
              .build();
      Counterparty otherPayer =
          Counterparty.builder().id(3L).name("Tenant B").type(CounterpartyType.PERSON).build();
      UpdateIncomeRequest req =
          new UpdateIncomeRequest(
              new BigDecimal("1400.00"), "Updated rent", LocalDate.of(2024, 2, 1), "Rent", 2L, 3L);
      when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));
      when(financialActivityService.resolveForTransaction(null, 2L))
          .thenReturn(activityFor(otherProp));
      when(counterpartyCatalog.findById(3L)).thenReturn(otherPayer);
      when(incomeRepository.save(income)).thenReturn(income);

      incomeService.update(1L, req);

      assertThat(income.getAmount()).isEqualByComparingTo("1400.00");
      assertThat(income.getDescription()).isEqualTo("Updated rent");
      assertThat(income.getSource()).isEqualTo("Rent");
      assertThat(income.getProperty()).isEqualTo(otherProp);
      assertThat(income.getPayer()).isEqualTo(otherPayer);
      verify(incomeRepository).save(income);
    }

    @Test
    void withNullPropertyId_clearsProperty() {
      UpdateIncomeRequest req =
          new UpdateIncomeRequest(
              new BigDecimal("1200.00"),
              "Monthly rent",
              LocalDate.of(2024, 1, 1),
              "Rent",
              null,
              null);
      when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));
      when(incomeRepository.save(income)).thenReturn(income);

      incomeService.update(1L, req);

      assertThat(income.getProperty()).isNull();
      assertThat(income.getPayer()).isNull();
    }
  }

  @Test
  void delete_callsRepositoryDeleteById() {
    incomeService.delete(1L);

    verify(incomeRepository).deleteById(1L);
  }

  @Test
  void getTotalIncome_usesUnifiedLedgerByDefault() {
    when(ledgerReadAdapter.getTotalIncome()).thenReturn(new BigDecimal("3600.00"));

    BigDecimal total = incomeService.getTotalIncome();

    assertThat(total).isEqualByComparingTo("3600.00");
    verify(incomeRepository, never()).getTotalIncome();
  }

  @Nested
  class ImportVenmoCsv {

    @BeforeEach
    void setUp() {
      lenient()
          .when(classificationHistory.findMostLikelyPropertyForCounterparty(any()))
          .thenReturn(Optional.empty());
    }

    @Test
    void importsMatchingSenderAndSkipsOutgoingAndDuplicates() throws Exception {
      String csv =
          """
                            Account Statement - (@demo-user),,,,,,,,,,,,,,,,,,,,,
                            Account Activity,,,,,,,,,,,,,,,,,,,,,
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total),Amount (fee),Funding Source,Destination,Beginning Balance,Ending Balance,Statement Period Venmo Fees,Year to Date Venmo Fees
                            ,tx1,2026-01-01T10:00:00,Payment,Complete,Rent Jan,@alice,Demo User,+ $1,200.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            ,tx2,2026-01-01T10:10:00,Payment,Complete,Ignore me,@bob,Demo User,+ $300.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            ,tx3,2026-01-01T10:20:00,Payment,Complete,Outgoing,Demo User,@alice,- $50.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            ,tx4,2026-01-01T10:30:00,Payment,Complete,Rent Feb,@alice,Demo User,+ $900.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            """;
      when(incomeRepository.existsBySourceTypeAndSourceId(ExpenseSource.VENMO, "tx1"))
          .thenReturn(false);
      when(incomeRepository.existsBySourceTypeAndSourceId(ExpenseSource.VENMO, "tx4"))
          .thenReturn(true);
      when(receiptService.isConnected()).thenReturn(false);

      Counterparty selectedPayer =
          Counterparty.builder()
              .id(2L)
              .name("Tenant A")
              .type(CounterpartyType.PERSON)
              .aliases(List.of("Alice"))
              .accounts(java.util.Set.of("@alice"))
              .build();
      when(counterpartyCatalog.findById(2L)).thenReturn(selectedPayer);

      var result = incomeService.importVenmoCsv(csv.getBytes(), "venmo.csv", "2", null);

      assertThat(result.totalRows()).isEqualTo(4);
      assertThat(result.importedRows()).isEqualTo(1);
      assertThat(result.skippedSenderRows()).isEqualTo(1);
      assertThat(result.skippedOutgoingRows()).isEqualTo(1);
      assertThat(result.skippedDuplicateRows()).isEqualTo(1);
      assertThat(result.skippedInvalidRows()).isEqualTo(0);
      assertThat(result.senderFilter()).isEqualTo("Tenant A");
      ArgumentCaptor<PendingIncome> savedPendingCaptor =
          ArgumentCaptor.forClass(PendingIncome.class);
      verify(pendingIncomeRepository).save(savedPendingCaptor.capture());
      assertThat(savedPendingCaptor.getValue().getDescription()).isEqualTo("Venmo - Rent Jan");
      verify(inboxSynchronizer).created(any(), any(), anyBoolean());
    }

    @Test
    void skipsRowsWithMissingRequiredFields() throws Exception {
      String csv =
          """
                            Account Statement - (@demo-user),,,,,,,,,,,,,,,,,,,,,
                            Account Activity,,,,,,,,,,,,,,,,,,,,,
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total),Amount (fee),Funding Source,Destination,Beginning Balance,Ending Balance,Statement Period Venmo Fees,Year to Date Venmo Fees
                            ,,2026-01-03T09:15:00,Payment,Complete,Missing id,@alice,Demo User,+ $1,200.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            ,tx2,2026-01-03T09:20:00,Payment,Complete,Bad amount,@alice,Demo User,abc,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            ,tx3,,Payment,Complete,Missing date,@alice,Demo User,+ $1,200.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            """;

      when(receiptService.isConnected()).thenReturn(false);
      var result = incomeService.importVenmoCsv(csv.getBytes(), null, null, null);

      assertThat(result.totalRows()).isEqualTo(3);
      assertThat(result.importedRows()).isEqualTo(0);
      assertThat(result.skippedInvalidRows()).isEqualTo(3);
      verify(incomeRepository, never()).save(any(Income.class));
      verify(incomeRepository, never())
          .existsBySourceTypeAndSourceId(eq(ExpenseSource.VENMO), any(String.class));
    }

    @Test
    void handlesVenmoStatementPreambleAndSignedAmounts() throws Exception {
      String csv =
          """
                            Account Statement - (@JoyceYaya-Yao) ,,,,,,,,,,,,,,,,,,,,,
                            Account Activity,,,,,,,,,,,,,,,,,,,,,
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total)
                            ,tx1,2026-01-01T12:20:27,Payment,Complete,Jan rent,HengHsiang Liao,Joyce Yaya Inloes,+ $900.00
                            ,tx2,2026-01-02T12:20:27,Payment,Complete,Outgoing,Joyce Yaya Inloes,Kent You,- $12.30
                            """;
      when(incomeRepository.existsBySourceTypeAndSourceId(ExpenseSource.VENMO, "tx1"))
          .thenReturn(false);
      when(receiptService.isConnected()).thenReturn(false);

      var result = incomeService.importVenmoCsv(csv.getBytes(), null, null, null);

      assertThat(result.totalRows()).isEqualTo(2);
      assertThat(result.importedRows()).isEqualTo(1);
      assertThat(result.skippedOutgoingRows()).isEqualTo(1);
      assertThat(result.skippedInvalidRows()).isEqualTo(0);
      ArgumentCaptor<PendingIncome> savedPendingCaptor =
          ArgumentCaptor.forClass(PendingIncome.class);
      verify(pendingIncomeRepository).save(savedPendingCaptor.capture());
      assertThat(savedPendingCaptor.getValue().getDescription()).isEqualTo("Venmo - Jan rent");
    }

    @Test
    void usesSenderWhenNoteIsMissing() throws Exception {
      String csv =
          """
                            Account Statement - (@demo-user),,,,,,,,,,,,,,,,,,,,,
                            Account Activity,,,,,,,,,,,,,,,,,,,,,
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total),Amount (fee),Funding Source,Destination,Beginning Balance,Ending Balance,Statement Period Venmo Fees,Year to Date Venmo Fees
                            ,tx1,2026-01-04T08:00:00,Payment,Complete,,@Alice,Demo User,+ $900.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            """;
      when(incomeRepository.existsBySourceTypeAndSourceId(ExpenseSource.VENMO, "tx1"))
          .thenReturn(false);
      when(receiptService.isConnected()).thenReturn(false);

      var result = incomeService.importVenmoCsv(csv.getBytes(), null, null, null);

      assertThat(result.totalRows()).isEqualTo(1);
      assertThat(result.importedRows()).isEqualTo(1);
      ArgumentCaptor<PendingIncome> savedPendingCaptor =
          ArgumentCaptor.forClass(PendingIncome.class);
      verify(pendingIncomeRepository).save(savedPendingCaptor.capture());
      assertThat(savedPendingCaptor.getValue().getDescription())
          .isEqualTo("Venmo payment from Alice");
    }

    @Test
    void explicitNonRentalActivityScopesImportedRowsWithoutInventingProperty() throws Exception {
      String csv =
          """
                            Account Activity
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total)
                            ,demo-reimburse-001,2026-08-23T08:00:00,Payment,Complete,Classroom reimbursement,@synthetic-district,Demo User,+ $78.45
                            """;
      FinancialActivity teaching =
          FinancialActivity.builder()
              .id(42L)
              .name("Teaching — Synthetic District")
              .taxTreatment(TaxTreatment.W2)
              .active(true)
              .build();
      when(financialActivityService.resolveForTransaction(42L, null)).thenReturn(teaching);
      when(receiptService.isConnected()).thenReturn(false);
      when(incomeRepository.existsBySourceTypeAndSourceId(
              ExpenseSource.VENMO, "demo-reimburse-001"))
          .thenReturn(false);

      var result = incomeService.importVenmoCsv(csv.getBytes(), "synthetic.csv", null, null, "42");

      ArgumentCaptor<PendingIncome> savedPendingCaptor =
          ArgumentCaptor.forClass(PendingIncome.class);
      verify(pendingIncomeRepository).save(savedPendingCaptor.capture());
      PendingIncome pending = savedPendingCaptor.getValue();
      assertThat(pending.getActivity()).isEqualTo(teaching);
      assertThat(pending.getProperty()).isNull();
      assertThat(pending.isClassificationAmbiguous()).isFalse();
      assertThat(result.activityName()).isEqualTo("Teaching — Synthetic District");
      assertThat(result.propertyName()).isNull();
    }

    @Test
    void uploadsStatementToOneDriveAndLinksImportedIncome() throws Exception {
      String csv =
          """
                            Account Statement - (@demo-user),,,,,,,,,,,,,,,,,,,,,
                            Account Activity,,,,,,,,,,,,,,,,,,,,,
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total),Amount (fee),Funding Source,Destination,Beginning Balance,Ending Balance,Statement Period Venmo Fees,Year to Date Venmo Fees
                            ,tx1,2026-01-04T08:00:00,Payment,Complete,January rent,@Alice,Demo User,+ $900.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            """;
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.uploadReceipt(eq("venmo-jan.csv"), any()))
          .thenReturn(
              new UploadReceiptResponse(
                  new ReceiptDto("od-item-1", "venmo-jan.csv", 0, null, null, null, null, true),
                  false));
      when(incomeRepository.existsBySourceTypeAndSourceId(ExpenseSource.VENMO, "tx1"))
          .thenReturn(false);

      var result = incomeService.importVenmoCsv(csv.getBytes(), "venmo-jan.csv", null, null);

      assertThat(result.importedRows()).isEqualTo(1);
      ArgumentCaptor<PendingIncome> savedPendingCaptor =
          ArgumentCaptor.forClass(PendingIncome.class);
      verify(pendingIncomeRepository).save(savedPendingCaptor.capture());
      assertThat(savedPendingCaptor.getValue().getReceiptOneDriveId()).isEqualTo("od-item-1");
      assertThat(savedPendingCaptor.getValue().getReceiptFileName()).isEqualTo("venmo-jan.csv");
    }

    @Test
    void doesNotMoveArchivedStatementWhenNoRowsImported() throws Exception {
      String csv =
          """
                            Account Statement - (@demo-user),,,,,,,,,,,,,,,,,,,,,
                            Account Activity,,,,,,,,,,,,,,,,,,,,,
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total),Amount (fee),Funding Source,Destination,Beginning Balance,Ending Balance,Statement Period Venmo Fees,Year to Date Venmo Fees
                            ,tx1,2026-01-04T08:00:00,Payment,Complete,Outgoing,@Alice,Demo User,- $900.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            """;
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.uploadReceipt(eq("venmo-jan.csv"), any()))
          .thenReturn(
              new UploadReceiptResponse(
                  new ReceiptDto("od-item-1", "venmo-jan.csv", 0, null, null, null, null, true),
                  false));

      var result = incomeService.importVenmoCsv(csv.getBytes(), "venmo-jan.csv", null, null);

      assertThat(result.importedRows()).isEqualTo(0);
      verify(receiptService, never()).moveTaxesFolder(any(), anyInt());
    }

    @Test
    void autoDetectsPropertyFromPayerHistoryWhenNotExplicitlyProvided() throws Exception {
      String csv =
          """
                            Account Statement - (@demo-user),,,,,,,,,,,,,,,,,,,,,
                            Account Activity,,,,,,,,,,,,,,,,,,,,,
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total),Amount (fee),Funding Source,Destination,Beginning Balance,Ending Balance,Statement Period Venmo Fees,Year to Date Venmo Fees
                            ,tx1,2026-01-04T08:00:00,Payment,Complete,Rent,@alice,Demo User,+ $900.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            """;
      when(incomeRepository.existsBySourceTypeAndSourceId(ExpenseSource.VENMO, "tx1"))
          .thenReturn(false);
      when(receiptService.isConnected()).thenReturn(false);

      Counterparty selectedPayer =
          Counterparty.builder()
              .id(2L)
              .name("Tenant A")
              .type(CounterpartyType.PERSON)
              .aliases(List.of("Alice"))
              .accounts(java.util.Set.of("@alice"))
              .build();
      when(counterpartyCatalog.findById(2L)).thenReturn(selectedPayer);

      Property autoDetectedProperty =
          Property.builder()
              .id(1L)
              .name("123 Main St")
              .address("123 Main St")
              .type(PropertyType.SINGLE_FAMILY)
              .build();
      lenient()
          .when(classificationHistory.findMostLikelyPropertyForCounterparty(2L))
          .thenReturn(Optional.of(autoDetectedProperty));

      var result = incomeService.importVenmoCsv(csv.getBytes(), "venmo.csv", "2", null);

      assertThat(result.importedRows()).isEqualTo(1);
      ArgumentCaptor<PendingIncome> savedPendingCaptor =
          ArgumentCaptor.forClass(PendingIncome.class);
      verify(pendingIncomeRepository).save(savedPendingCaptor.capture());
      assertThat(savedPendingCaptor.getValue().getProperty()).isEqualTo(autoDetectedProperty);
    }

    @Test
    void autoDetectsPropertyFromRowSenderWhenNoPayerFilterProvided() throws Exception {
      String csv =
          """
                            Account Statement - (@demo-user),,,,,,,,,,,,,,,,,,,,,
                            Account Activity,,,,,,,,,,,,,,,,,,,,,
                            ,ID,Datetime,Type,Status,Note,From,To,Amount (total),Amount (fee),Funding Source,Destination,Beginning Balance,Ending Balance,Statement Period Venmo Fees,Year to Date Venmo Fees
                            ,tx99,2026-05-01T10:00:00,Payment,Complete,May Rent,@alice,Demo User,+ $900.00,$0.00,Venmo balance,,,$0.00,$0.00,$0.00
                            """;
      when(incomeRepository.existsBySourceTypeAndSourceId(ExpenseSource.VENMO, "tx99"))
          .thenReturn(false);
      when(receiptService.isConnected()).thenReturn(false);

      Counterparty rowPayer =
          Counterparty.builder()
              .id(5L)
              .name("alice")
              .type(CounterpartyType.PERSON)
              .aliases(List.of())
              .accounts(java.util.Set.of())
              .build();
      // Sender in CSV is "@alice" — stripped to "alice" and resolved by name
      lenient().when(counterpartyCatalog.findByName("alice")).thenReturn(Optional.of(rowPayer));

      Property autoDetectedProperty =
          Property.builder()
              .id(3L)
              .name("456 Oak Ave")
              .address("456 Oak Ave")
              .type(PropertyType.SINGLE_FAMILY)
              .build();
      lenient()
          .when(classificationHistory.findMostLikelyPropertyForCounterparty(5L))
          .thenReturn(Optional.of(autoDetectedProperty));

      // No payer filter and no propertyId — should auto-detect from row sender
      var result = incomeService.importVenmoCsv(csv.getBytes(), "venmo.csv", null, null);

      assertThat(result.importedRows()).isEqualTo(1);
      ArgumentCaptor<PendingIncome> savedPendingCaptor =
          ArgumentCaptor.forClass(PendingIncome.class);
      verify(pendingIncomeRepository).save(savedPendingCaptor.capture());
      assertThat(savedPendingCaptor.getValue().getPayer()).isEqualTo(rowPayer);
      assertThat(savedPendingCaptor.getValue().getProperty()).isEqualTo(autoDetectedProperty);
    }
  }

  private FinancialActivity activityFor(Property assignedProperty) {
    return FinancialActivity.builder()
        .id(assignedProperty == null ? 99L : assignedProperty.getId() + 100L)
        .name(assignedProperty == null ? "Needs classification" : assignedProperty.getName())
        .taxTreatment(assignedProperty == null ? TaxTreatment.NONE : TaxTreatment.SCHEDULE_E)
        .property(assignedProperty)
        .active(true)
        .build();
  }

  private PendingIncome pendingIncome(Long id) {
    FinancialActivity activity = activityFor(property);
    return PendingIncome.builder()
        .id(id)
        .amount(new BigDecimal("1200.00"))
        .description("Monthly rent")
        .date(LocalDate.of(2026, 7, 1))
        .source("Rent")
        .sourceType(ExpenseSource.VENMO)
        .sourceId("venmo-" + id)
        .property(property)
        .payer(payer)
        .activity(activity)
        .financialCategory(incomeCategoryFor(activity))
        .status(PendingIncomeStatus.READY)
        .createdAt(LocalDateTime.of(2026, 7, 1, 12, 0))
        .build();
  }

  private FinancialCategory incomeCategoryFor(FinancialActivity activity) {
    return FinancialCategory.builder()
        .id(200L)
        .key(
            activity.getTaxTreatment() == TaxTreatment.SCHEDULE_E
                ? "RENTAL_INCOME"
                : "OTHER_INCOME")
        .label("Income")
        .direction(TransactionDirection.INCOME)
        .taxTreatment(activity.getTaxTreatment())
        .active(true)
        .build();
  }
}
