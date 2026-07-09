package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.PendingIncome;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.model.ReceiptDto;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PendingIncomeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

  @Mock private IncomeRepository incomeRepository;
  @Mock private PropertyService propertyService;
  @Mock private PayerService payerService;
  @Mock private ReceiptService receiptService;
  @Mock private PayerPropertyHistoryRepository payerPropertyHistoryRepository;
  @Mock private PendingIncomeRepository pendingIncomeRepository;
  @Mock private PropertyHistoryService propertyHistoryService;

  @InjectMocks private IncomeService incomeService;

  private Income income;
  private Property property;
  private Payer payer;

  @BeforeEach
  void setUp() {
    property =
        Property.builder()
            .id(1L)
            .name("123 Main St")
            .address("123 Main St")
            .type(PropertyType.SINGLE_FAMILY)
            .build();
    payer = Payer.builder().id(2L).name("Tenant A").type(PayerType.PERSON).build();
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
  }

  @Test
  void findAll_returnsAllIncomes() {
    when(incomeRepository.findAll(any(Sort.class))).thenReturn(List.of(income));

    List<Income> result = incomeService.findAll();

    assertThat(result).hasSize(1).containsExactly(income);
  }

  @Test
  void findById_found_returnsIncome() {
    when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));

    Income result = incomeService.findById(1L);

    assertThat(result).isEqualTo(income);
  }

  @Test
  void findById_notFound_throwsException() {
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
      when(propertyService.findById(1L)).thenReturn(property);
      when(payerService.findById(2L)).thenReturn(payer);
      when(incomeRepository.save(any())).thenReturn(income);

      Income result = incomeService.create(req);

      assertThat(result).isEqualTo(income);
      verify(propertyService).findById(1L);
      verify(payerService).findById(2L);
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
  }

  @Nested
  class Update {

    @Test
    void updatesFieldsAndSaves() {
      Property otherProp =
          Property.builder()
              .id(2L)
              .name("456 Oak Ave")
              .address("456 Oak Ave")
              .type(PropertyType.SINGLE_FAMILY)
              .build();
      Payer otherPayer = Payer.builder().id(3L).name("Tenant B").type(PayerType.PERSON).build();
      UpdateIncomeRequest req =
          new UpdateIncomeRequest(
              new BigDecimal("1400.00"), "Updated rent", LocalDate.of(2024, 2, 1), "Rent", 2L, 3L);
      when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));
      when(propertyService.findById(2L)).thenReturn(otherProp);
      when(payerService.findById(3L)).thenReturn(otherPayer);
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
  void getTotalIncome_returnsTotalFromRepository() {
    when(incomeRepository.getTotalIncome()).thenReturn(new BigDecimal("3600.00"));

    BigDecimal total = incomeService.getTotalIncome();

    assertThat(total).isEqualByComparingTo("3600.00");
  }

  @Nested
  class ImportVenmoCsv {

    @BeforeEach
    void setUp() {
      lenient()
          .when(payerPropertyHistoryRepository.findByPayerIdOrderByOccurrencesDesc(any()))
          .thenReturn(List.of());
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

      Payer selectedPayer =
          Payer.builder()
              .id(2L)
              .name("Tenant A")
              .type(PayerType.PERSON)
              .aliases(List.of("Alice"))
              .accounts(java.util.Set.of("@alice"))
              .build();
      when(payerService.findById(2L)).thenReturn(selectedPayer);

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

      Payer selectedPayer =
          Payer.builder()
              .id(2L)
              .name("Tenant A")
              .type(PayerType.PERSON)
              .aliases(List.of("Alice"))
              .accounts(java.util.Set.of("@alice"))
              .build();
      when(payerService.findById(2L)).thenReturn(selectedPayer);

      Property autoDetectedProperty =
          Property.builder()
              .id(1L)
              .name("123 Main St")
              .address("123 Main St")
              .type(PropertyType.SINGLE_FAMILY)
              .build();
      var payerPropertyHistory = new com.bookie.model.PayerPropertyHistory();
      payerPropertyHistory.setProperty(autoDetectedProperty);
      lenient()
          .when(payerPropertyHistoryRepository.findByPayerIdOrderByOccurrencesDesc(2L))
          .thenReturn(List.of(payerPropertyHistory));

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

      Payer rowPayer =
          Payer.builder()
              .id(5L)
              .name("alice")
              .type(PayerType.PERSON)
              .aliases(List.of())
              .accounts(java.util.Set.of())
              .build();
      // Sender in CSV is "@alice" — stripped to "alice" and resolved by name
      lenient().when(payerService.findByName("alice")).thenReturn(Optional.of(rowPayer));

      Property autoDetectedProperty =
          Property.builder()
              .id(3L)
              .name("456 Oak Ave")
              .address("456 Oak Ave")
              .type(PropertyType.SINGLE_FAMILY)
              .build();
      var payerPropertyHistory = new com.bookie.model.PayerPropertyHistory();
      payerPropertyHistory.setProperty(autoDetectedProperty);
      lenient()
          .when(payerPropertyHistoryRepository.findByPayerIdOrderByOccurrencesDesc(5L))
          .thenReturn(List.of(payerPropertyHistory));

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
}
