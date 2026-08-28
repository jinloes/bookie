package com.bookie.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.service.PendingExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

  @Test
  void sameTransactionDualWritePassesFailClosedPendingReadComparison() {
    PendingExpense created =
        pendingExpenseService.create(
            "synthetic-outlook-message", ExpenseSource.OUTLOOK_EMAIL, "Synthetic subject");

    assertThat(pendingExpenseService.findAll())
        .extracting(PendingExpense::getId)
        .containsExactly(created.getId());
  }
}
