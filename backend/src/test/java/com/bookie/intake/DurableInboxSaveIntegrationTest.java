package com.bookie.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.intake.application.InboxQueryService;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.service.PendingExpenseService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:durable_inbox_save;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=validate",
      "bookie.auto-import.enabled=false",
      "bookie.intake.worker.enabled=false"
    })
@Transactional
class DurableInboxSaveIntegrationTest {

  @Autowired private PendingExpenseService pendingExpenseService;
  @Autowired private InboxQueryService inboxQueryService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  @Test
  void saveCommitsLegacyAndUnifiedLedgerRowsWithDurableExternalJob() {
    Long activityId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'",
            Long.class);
    Long categoryId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'", Long.class);

    var pending =
        pendingExpenseService.create(
            "receipt-item-001", ExpenseSource.RECEIPT, "synthetic-receipt.pdf");
    pendingExpenseService.markReady(
        pending.getId(),
        EmailSuggestion.builder()
            .emailType(EmailType.EXPENSE)
            .amount(91.25)
            .description("Synthetic durable expense")
            .date("2026-08-24")
            .category("OTHER")
            .activityId(activityId)
            .categoryId(categoryId)
            .classificationAmbiguous(false)
            .build(),
        List.of("Synthetic Alias"));

    Expense saved =
        pendingExpenseService.saveAsExpense(
            pending.getId(),
            new SavePendingExpenseRequest(
                new BigDecimal("91.25"),
                "Synthetic durable expense",
                LocalDate.of(2026, 8, 24),
                "OTHER",
                null,
                null,
                activityId,
                categoryId));
    entityManager.flush();

    assertThat(count("pending_expenses")).isZero();
    assertThat(count("expenses")).isEqualTo(1);
    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(count("legacy_transaction_map")).isEqualTo(1);

    InboxItem item = inboxQueryService.findAll().getFirst();
    assertThat(item.getState()).isEqualTo(InboxState.SAVED);
    assertThat(item.getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
    assertThat(item.getFinancialTransactionId()).isNotNull();
    assertThat(item.getLegacySourceId()).isEqualTo("receipt-item-001");
    assertThat(saved.getSourceId()).isEqualTo("receipt-item-001");
    assertThat(inboxQueryService.findJobs(item.getId()))
        .extracting(job -> job.getType())
        .containsExactlyInAnyOrder(BackgroundJobType.PARSE_RECEIPT, BackgroundJobType.MOVE_RECEIPT);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT target_year
                FROM background_jobs
                WHERE type = 'MOVE_RECEIPT'
                """,
                Integer.class))
        .isEqualTo(2026);
  }

  private long count(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }
}
