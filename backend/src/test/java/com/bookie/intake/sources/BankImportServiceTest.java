package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.financialcategory.application.FinancialCategoryService;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.catalog.property.domain.Property;
import com.bookie.intake.application.BankImportCommand;
import com.bookie.intake.application.BankImportResult;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.integrations.bank.BankInputException;
import com.bookie.integrations.bank.BankStatement;
import com.bookie.integrations.bank.BankStatementInputPort;
import com.bookie.integrations.bank.BankTransaction;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.ExpenseSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BankImportServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneOffset.UTC);
  private static final byte[] OFX = "ofx".getBytes();
  private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
  private static final String SOURCE_ID = "ofx:" + "a".repeat(64);

  @Mock private CounterpartyCatalog counterpartyCatalog;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private FinancialCategoryService financialCategoryService;
  @Mock private ClassificationHistory classificationHistory;
  @Mock private BankStatementInputPort bankStatementInput;
  @Mock private InboxItemStore inboxItemStore;
  @Mock private LedgerImportReferenceQuery ledgerImportReferenceQuery;

  private final Property mainSt = Property.builder().id(4L).name("Main St").build();
  private final FinancialActivity rental =
      FinancialActivity.builder().id(42L).name("Main St rental").property(mainSt).build();
  private final FinancialActivity needsClassification =
      FinancialActivity.builder()
          .id(1L)
          .systemKey(ActivityCatalog.NEEDS_CLASSIFICATION_KEY)
          .build();
  private final Counterparty jane = Counterparty.builder().id(12L).name("Jane Doe").build();

  private BankImportService service;

  @BeforeEach
  void setUp() {
    service =
        new BankImportService(
            CLOCK,
            counterpartyCatalog,
            activityCatalog,
            financialCategoryService,
            classificationHistory,
            bankStatementInput,
            inboxItemStore,
            ledgerImportReferenceQuery);
    lenient().when(activityCatalog.resolveForProperty(null)).thenReturn(needsClassification);
    lenient().when(activityCatalog.resolveForProperty(mainSt)).thenReturn(rental);
    lenient()
        .when(financialCategoryService.defaultFor(any(), eq(TransactionDirection.INCOME), any()))
        .thenReturn(FinancialCategory.builder().id(30L).build());
    lenient()
        .when(inboxItemStore.findBySourceIdentity(eq(ExpenseSource.BANK), anyString()))
        .thenReturn(Optional.empty());
    lenient().when(inboxItemStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Nested
  class ImportStatement {

    @Test
    void importsADepositAsReadyBankIncomeForTheResolvedPayer(CapturedOutput output) {
      when(counterpartyCatalog.findByName("JANE DOE")).thenReturn(Optional.of(jane));
      when(classificationHistory.findMostLikelyPropertyForCounterparty(12L))
          .thenReturn(Optional.of(mainSt));
      parse(statement(0, row("1500.00", "JANE DOE", "August rent")));

      BankImportResult result = service.importStatement(command(null));

      assertThat(result)
          .isEqualTo(
              BankImportResult.builder().totalRows(1).importedRows(1).activityName(null).build());
      InboxItem item = savedItem();
      assertThat(item.getOrigin()).isEqualTo(ExpenseSource.BANK);
      assertThat(item.getState()).isEqualTo(InboxState.READY);
      assertThat(item.getExternalSyncState()).isEqualTo(ExternalSyncState.NOT_REQUIRED);
      assertThat(item.getLegacySourceType()).isEqualTo("BANK");
      assertThat(item.getLegacySourceId()).isEqualTo(SOURCE_ID);
      assertThat(item.getRawStatus()).isEqualTo("READY");
      assertThat(item.getProposedDirection()).isEqualTo(TransactionDirection.INCOME);
      assertThat(item.getProposedAmount()).isEqualByComparingTo("1500.00");
      assertThat(item.getProposedDate()).isEqualTo(DATE);
      assertThat(item.getProposedDescription()).isEqualTo("JANE DOE - August rent");
      assertThat(item.getProposedSourceLabel()).isEqualTo("Jane Doe");
      assertThat(item.getLegacyCounterpartyId()).isEqualTo(12L);
      assertThat(item.getLegacyPropertyId()).isEqualTo(4L);
      assertThat(item.getLegacyActivityId()).isEqualTo(42L);
      assertThat(item.getLegacyCategoryId()).isEqualTo(30L);
      assertThat(item.isClassificationAmbiguous()).isFalse();
      assertThat(item.getReceiptExternalId()).isNull();
      assertThat(item.getReceiptFileName()).isNull();
      assertThat(item.getArtifacts()).isEmpty();
      assertThat(item.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
      assertThat(output)
          .contains("Bank statement import: 1 rows, 1 imported")
          .doesNotContain("JANE DOE")
          .doesNotContain(SOURCE_ID);
    }

    @Test
    void resolvesThePayerByAlias() {
      when(counterpartyCatalog.findByName("JDOE")).thenReturn(Optional.empty());
      when(counterpartyCatalog.findByAlias("JDOE")).thenReturn(Optional.of(jane));
      when(classificationHistory.findMostLikelyPropertyForCounterparty(12L))
          .thenReturn(Optional.empty());
      parse(statement(0, row("10.00", "JDOE", null)));

      service.importStatement(command(null));

      InboxItem item = savedItem();
      assertThat(item.getLegacyCounterpartyId()).isEqualTo(12L);
      assertThat(item.getLegacyPropertyId()).isNull();
      assertThat(item.getLegacyActivityId()).isEqualTo(1L);
      assertThat(item.isClassificationAmbiguous()).isTrue();
    }

    @Test
    void anUnknownNameNeedsClassificationAndKeepsTheBankName() {
      when(counterpartyCatalog.findByName("ACME PAYROLL")).thenReturn(Optional.empty());
      when(counterpartyCatalog.findByAlias("ACME PAYROLL")).thenReturn(Optional.empty());
      parse(statement(0, row("10.00", "ACME PAYROLL", "acme payroll")));

      service.importStatement(command(null));

      InboxItem item = savedItem();
      assertThat(item.getLegacyCounterpartyId()).isNull();
      assertThat(item.getProposedSourceLabel()).isEqualTo("ACME PAYROLL");
      assertThat(item.getProposedDescription()).isEqualTo("ACME PAYROLL");
      assertThat(item.isClassificationAmbiguous()).isTrue();
      verifyNoInteractions(classificationHistory);
    }

    @ParameterizedTest
    @CsvSource(
        nullValues = "null",
        value = {"null, Transfer in, Bank - Transfer in", "' ', null, Bank"})
    void aDepositWithoutANameIsLabelledBank(String name, String memo, String description) {
      parse(statement(0, row("10.00", name, memo)));

      service.importStatement(command(null));

      InboxItem item = savedItem();
      assertThat(item.getProposedSourceLabel()).isEqualTo("Bank");
      assertThat(item.getProposedDescription()).isEqualTo(description);
      verifyNoInteractions(counterpartyCatalog);
    }

    @Test
    void aSelectedActivityAppliesToEveryRow() {
      when(activityCatalog.resolveForTransaction(42L, null)).thenReturn(rental);
      when(counterpartyCatalog.findByName("JANE DOE")).thenReturn(Optional.of(jane));
      parse(statement(0, row("10.00", "JANE DOE", null)));

      BankImportResult result = service.importStatement(command(42L));

      assertThat(result.activityName()).isEqualTo("Main St rental");
      InboxItem item = savedItem();
      assertThat(item.getLegacyActivityId()).isEqualTo(42L);
      assertThat(item.getLegacyPropertyId()).isEqualTo(4L);
      verifyNoInteractions(classificationHistory);
      verify(activityCatalog, never()).resolveForProperty(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-45.10", "0.00"})
    void skipsDebitsAndZeroAmounts(String amount) {
      parse(statement(2, row(amount, "ACME", null)));

      BankImportResult result = service.importStatement(command(null));

      assertThat(result.skippedDebitRows()).isOne();
      assertThat(result.skippedInvalidRows()).isEqualTo(2);
      assertThat(result.importedRows()).isZero();
      verify(inboxItemStore, never()).save(any());
    }

    @Test
    void skipsADepositAlreadySavedToTheLedger() {
      when(ledgerImportReferenceQuery.existsActive("BANK", SOURCE_ID)).thenReturn(true);
      parse(statement(0, row("10.00", "ACME", null)));

      assertThat(service.importStatement(command(null)).skippedDuplicateRows()).isOne();
      verify(inboxItemStore, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"DISMISSED"},
        mode = EnumSource.Mode.EXCLUDE)
    void skipsADepositWithAnyOtherExistingInboxItem(InboxState state) {
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.BANK, SOURCE_ID))
          .thenReturn(Optional.of(InboxItem.builder().id(3L).state(state).build()));
      parse(statement(0, row("10.00", "ACME", null)));

      assertThat(service.importStatement(command(null)).skippedDuplicateRows()).isOne();
      verify(inboxItemStore, never()).save(any());
    }

    @Test
    void reproposesADismissedDeposit() {
      InboxItem dismissed =
          InboxItem.builder()
              .id(3L)
              .origin(ExpenseSource.BANK)
              .state(InboxState.DISMISSED)
              .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
              .errorMessage("old")
              .build();
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.BANK, SOURCE_ID))
          .thenReturn(Optional.of(dismissed));
      when(counterpartyCatalog.findByName("ACME")).thenReturn(Optional.empty());
      when(counterpartyCatalog.findByAlias("ACME")).thenReturn(Optional.empty());
      parse(statement(0, row("10.00", "ACME", null)));

      BankImportResult result = service.importStatement(command(null));

      assertThat(result.importedRows()).isOne();
      assertThat(dismissed.getState()).isEqualTo(InboxState.READY);
      assertThat(dismissed.getErrorMessage()).isNull();
      assertThat(dismissed.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
      verify(inboxItemStore).save(dismissed);
    }

    @Test
    void anAmbiguousPayerImportsAsAnUnresolvedDeposit() {
      // The store reports an ambiguous name or alias as empty.
      when(counterpartyCatalog.findByName("ACME")).thenReturn(Optional.empty());
      when(counterpartyCatalog.findByAlias("ACME")).thenReturn(Optional.empty());
      parse(statement(0, row("10.00", "ACME", null)));

      BankImportResult result = service.importStatement(command(null));

      assertThat(result.importedRows()).isOne();
      assertThat(result.skippedInvalidRows()).isZero();
      InboxItem item = savedItem();
      assertThat(item.getState()).isEqualTo(InboxState.READY);
      assertThat(item.getLegacyCounterpartyId()).isNull();
      assertThat(item.getProposedSourceLabel()).isEqualTo("ACME");
      assertThat(item.getLegacyActivityId()).isEqualTo(1L);
      assertThat(item.isClassificationAmbiguous()).isTrue();
    }

    @Test
    void aDepositThatFailsToResolveCountsAsInvalid(CapturedOutput output) {
      when(counterpartyCatalog.findByName("ACME")).thenThrow(new IllegalStateException("db down"));
      parse(statement(0, row("10.00", "ACME", null)));

      BankImportResult result = service.importStatement(command(null));

      assertThat(result.skippedInvalidRows()).isOne();
      assertThat(result.importedRows()).isZero();
      assertThat(output)
          .contains("row 0")
          .contains("IllegalStateException")
          .doesNotContain("ACME")
          .doesNotContain("db down");
    }

    @Test
    void rejectsAnUnreadableStatementAsABadRequest() {
      when(bankStatementInput.parse(OFX))
          .thenThrow(new BankInputException("Not an OFX bank statement."));

      assertThatThrownBy(() -> service.importStatement(command(null)))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              failure -> {
                assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(failure.getReason()).isEqualTo("Not an OFX bank statement.");
              });
      verifyNoInteractions(inboxItemStore);
    }
  }

  private void parse(BankStatement statement) {
    when(bankStatementInput.parse(OFX)).thenReturn(statement);
  }

  private InboxItem savedItem() {
    ArgumentCaptor<InboxItem> captor = ArgumentCaptor.forClass(InboxItem.class);
    verify(inboxItemStore, atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  private static BankImportCommand command(Long activityId) {
    return BankImportCommand.builder().content(OFX).activityId(activityId).build();
  }

  private static BankStatement statement(int invalidRows, BankTransaction... transactions) {
    return BankStatement.builder()
        .totalRows(transactions.length + invalidRows)
        .invalidRows(invalidRows)
        .transactions(List.of(transactions))
        .build();
  }

  private static BankTransaction row(String amount, String name, String memo) {
    return BankTransaction.builder()
        .sourceId(SOURCE_ID)
        .amount(new BigDecimal(amount))
        .date(DATE)
        .name(name)
        .memo(memo)
        .build();
  }
}
