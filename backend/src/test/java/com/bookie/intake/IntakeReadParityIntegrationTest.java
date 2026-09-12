package com.bookie.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.service.PendingExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "bookie.auto-import.enabled=false",
      "bookie.intake.read-mode=COMPARE",
      "bookie.intake.worker.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:intake-read-parity;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
class IntakeReadParityIntegrationTest {

  @Autowired private PendingExpenseService pendingExpenseService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void sameTransactionDualWritePassesFailClosedPendingReadComparison() {
    PendingExpense created =
        pendingExpenseService.create(
            "synthetic-outlook-message", ExpenseSource.OUTLOOK_EMAIL, "Synthetic subject");

    assertThat(pendingExpenseService.findAll())
        .extracting(PendingExpense::getId)
        .containsExactly(created.getId());
  }

  @Test
  void requeueingReadyReceiptReusesPendingRowWithoutPoisoningHibernateSession() {
    PendingExpense created =
        pendingExpenseService.create(
            "synthetic-receipt", ExpenseSource.RECEIPT, "original-receipt.pdf");
    jdbcTemplate.update(
        "UPDATE pending_expenses SET status = 'READY', amount = 12.34 WHERE id = ?",
        created.getId());

    PendingExpense requeued =
        pendingExpenseService
            .findOrCreate("synthetic-receipt", ExpenseSource.RECEIPT, "updated-receipt.pdf")
            .pending();

    assertThat(requeued.getId()).isEqualTo(created.getId());
    assertThat(requeued.getStatus()).isEqualTo(PendingExpenseStatus.PROCESSING);
    assertThat(requeued.getSubject()).isEqualTo("updated-receipt.pdf");
    assertThat(requeued.getAmount()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pending_expenses WHERE source_id = 'synthetic-receipt'",
                Long.class))
        .isOne();
    assertThat(pendingExpenseService.findAll())
        .extracting(PendingExpense::getId)
        .contains(created.getId());
  }
}
