package com.bookie.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.ledger.application.LedgerReadMode;
import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.model.CreateExpenseRequest;
import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.TransactionDirection;
import com.bookie.model.UpdateExpenseRequest;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.reporting.application.ReportService;
import com.bookie.reporting.domain.ReportResults;
import com.bookie.service.ExpenseService;
import com.bookie.service.IncomeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ledger_dual_write;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=validate",
      "bookie.auto-import.enabled=false",
      "bookie.ledger.read-mode=COMPARE",
      "bookie.reporting.read-mode=COMPARE"
    })
@Transactional
class LedgerDualWriteIntegrationTest {

  @Autowired private IncomeService incomeService;
  @Autowired private ExpenseService expenseService;
  @Autowired private LedgerTransactionService transactionService;
  @Autowired private ReportService reportService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void legacyCrudDualWritesAndKeepsReadAndReportParity() {
    Long activityId =
        id("SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
    Long incomeCategoryId =
        id("SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");
    Long expenseCategoryId =
        id("SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'");

    Income income =
        incomeService.create(
            new CreateIncomeRequest(
                new BigDecimal("850.00"),
                "Synthetic income",
                LocalDate.of(2026, 8, 1),
                "Synthetic source",
                null,
                null,
                ExpenseSource.MANUAL,
                null,
                null,
                activityId,
                incomeCategoryId));
    Expense expense =
        expenseService.create(
            new CreateExpenseRequest(
                new BigDecimal("125.25"),
                "Synthetic expense",
                LocalDate.of(2026, 8, 2),
                ExpenseCategory.OTHER,
                null,
                null,
                null,
                null,
                ExpenseSource.MANUAL,
                activityId,
                expenseCategoryId));

    assertThat(count("incomes")).isEqualTo(1);
    assertThat(count("expenses")).isEqualTo(1);
    assertThat(count("financial_transactions")).isEqualTo(2);
    assertThat(count("legacy_transaction_map")).isEqualTo(2);
    assertThat(incomeService.findAll()).extracting(Income::getId).containsExactly(income.getId());
    assertThat(expenseService.findAll())
        .extracting(Expense::getId)
        .containsExactly(expense.getId());
    assertThat(incomeService.getTotalIncome()).isEqualByComparingTo("850.00");
    assertThat(expenseService.getTotalExpenses()).isEqualByComparingTo("125.25");

    ReportResults.Cashflow report =
        reportService.cashflow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    assertThat(report.totalIncome()).isEqualByComparingTo("850.00");
    assertThat(report.totalExpenses()).isEqualByComparingTo("125.25");
    assertThat(reportService.scheduleE(2026).activities()).isEmpty();

    incomeService.update(
        income.getId(),
        new UpdateIncomeRequest(
            new BigDecimal("900.00"),
            "Updated synthetic income",
            LocalDate.of(2026, 8, 3),
            "Updated source",
            null,
            null,
            activityId,
            incomeCategoryId));
    expenseService.update(
        expense.getId(),
        new UpdateExpenseRequest(
            new BigDecimal("100.00"),
            "Updated synthetic expense",
            LocalDate.of(2026, 8, 4),
            ExpenseCategory.OTHER,
            null,
            null,
            null,
            null,
            activityId,
            expenseCategoryId));

    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT SUM(amount)
                FROM financial_transactions
                WHERE direction = 'INCOME' AND deleted_at IS NULL
                """,
                BigDecimal.class))
        .isEqualByComparingTo("900.00");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT SUM(amount)
                FROM financial_transactions
                WHERE direction = 'EXPENSE' AND deleted_at IS NULL
                """,
                BigDecimal.class))
        .isEqualByComparingTo("100.00");

    incomeService.delete(income.getId());
    expenseService.delete(expense.getId());

    assertThat(count("incomes")).isZero();
    assertThat(count("expenses")).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE deleted_at IS NOT NULL",
                Long.class))
        .isEqualTo(2);
    assertThat(count("legacy_transaction_map")).isEqualTo(2);
  }

  @Test
  void v2CrudWritesRollbackCompatibleLegacyRowsAndNormalizedMetadata() {
    Long activityId =
        id("SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
    Long neutralCategoryId =
        id("SELECT id FROM neutral_categories WHERE category_key = 'OTHER_INCOME'");
    LedgerTransactionInput createInput =
        LedgerTransactionInput.builder()
            .amount(new BigDecimal("500.00"))
            .direction(TransactionDirection.INCOME)
            .date(LocalDate.of(2026, 8, 5))
            .description("V2 synthetic income")
            .activityId(activityId)
            .neutralCategoryId(neutralCategoryId)
            .origin("VENMO")
            .externalId("v2-source-001")
            .sourceLabel("Synthetic sender")
            .attachmentStorageProvider("ONEDRIVE")
            .attachmentExternalId("v2-file-001")
            .attachmentFileName("v2-statement.pdf")
            .attachmentSha256("a".repeat(64))
            .build();

    FinancialTransaction created = transactionService.create(createInput);

    assertThat(count("incomes")).isEqualTo(1);
    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(count("transaction_import_references")).isEqualTo(1);
    assertThat(count("transaction_attachments")).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT source_id FROM incomes", String.class))
        .isEqualTo("v2-source-001");
    assertThat(
            jdbcTemplate.queryForObject("SELECT sha256 FROM transaction_attachments", String.class))
        .isEqualTo("a".repeat(64));

    LedgerTransactionInput updateInput =
        LedgerTransactionInput.builder()
            .amount(new BigDecimal("525.00"))
            .direction(TransactionDirection.INCOME)
            .date(LocalDate.of(2026, 8, 6))
            .description("Updated V2 synthetic income")
            .activityId(activityId)
            .neutralCategoryId(neutralCategoryId)
            .origin("VENMO")
            .externalId("v2-source-001")
            .sourceLabel("Synthetic sender")
            .attachmentStorageProvider("ONEDRIVE")
            .attachmentExternalId("v2-file-001")
            .attachmentFileName("v2-statement.pdf")
            .attachmentSha256("a".repeat(64))
            .build();
    FinancialTransaction updated =
        transactionService.update(created.getId(), created.getVersion(), updateInput);

    assertThat(updated.getAmount()).isEqualByComparingTo("525.00");
    assertThat(jdbcTemplate.queryForObject("SELECT amount FROM incomes", BigDecimal.class))
        .isEqualByComparingTo("525.00");

    ReflectionTestUtils.setField(incomeService, "ledgerReadMode", LedgerReadMode.UNIFIED);
    try {
      assertThat(incomeService.findAll())
          .singleElement()
          .satisfies(
              income -> {
                assertThat(income.getAmount()).isEqualByComparingTo("525.00");
                assertThat(income.getSourceId()).isEqualTo("v2-source-001");
                assertThat(income.getReceiptOneDriveId()).isEqualTo("v2-file-001");
              });
    } finally {
      ReflectionTestUtils.setField(incomeService, "ledgerReadMode", LedgerReadMode.COMPARE);
    }

    Long legacyId = jdbcTemplate.queryForObject("SELECT id FROM incomes", Long.class);
    Long legacyCategoryId =
        id("SELECT id FROM financial_categories WHERE category_key = 'OTHER_INCOME'");
    incomeService.update(
        legacyId,
        new UpdateIncomeRequest(
            new BigDecimal("530.00"),
            "Legacy-compatible update",
            LocalDate.of(2026, 8, 7),
            "Synthetic sender",
            null,
            null,
            activityId,
            legacyCategoryId));
    assertThat(
            jdbcTemplate.queryForObject("SELECT sha256 FROM transaction_attachments", String.class))
        .isEqualTo("a".repeat(64));

    FinancialTransaction refreshed = transactionService.findById(updated.getId());
    transactionService.delete(refreshed.getId(), refreshed.getVersion());

    assertThat(count("incomes")).isZero();
    assertThat(transactionService.findAll()).isEmpty();
    assertThat(count("legacy_transaction_map")).isEqualTo(1);
  }

  private long count(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  private Long id(String sql) {
    return jdbcTemplate.queryForObject(sql, Long.class);
  }
}
