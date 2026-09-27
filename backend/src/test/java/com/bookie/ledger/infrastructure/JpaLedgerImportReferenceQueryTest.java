package com.bookie.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.config.ClockConfiguration;
import com.bookie.ledger.application.LedgerImportReferenceQuery.LinkedTransaction;
import com.bookie.ledger.domain.FinancialTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

// Runs the JPQL against the Flyway schema so the queries are validated, not just their callers.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({ClockConfiguration.class, JpaLedgerImportReferenceQuery.class})
class JpaLedgerImportReferenceQueryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private JpaLedgerImportReferenceQuery query;

  private FinancialActivity activity;
  private NeutralCategory expenseCategory;
  private NeutralCategory incomeCategory;

  @BeforeEach
  void setUp() {
    activity =
        entityManager
            .getEntityManager()
            .createQuery(
                "select activity from FinancialActivity activity"
                    + " where activity.systemKey = 'NEEDS_CLASSIFICATION'",
                FinancialActivity.class)
            .getSingleResult();
    expenseCategory = category(TransactionDirection.EXPENSE);
    incomeCategory = category(TransactionDirection.INCOME);
  }

  private NeutralCategory category(TransactionDirection direction) {
    return entityManager
        .getEntityManager()
        .createQuery(
            "select category from NeutralCategory category"
                + " where category.direction = :direction and category.active = true"
                + " order by category.id",
            NeutralCategory.class)
        .setParameter("direction", direction)
        .setMaxResults(1)
        .getSingleResult();
  }

  @Nested
  class FindSavedParentExternalIds {

    @Test
    void returnsEmptySetWhenNoIdsAreRequested() {
      assertThat(query.findSavedParentExternalIds("OUTLOOK_EMAIL", List.of())).isEmpty();
      assertThat(query.findSavedParentExternalIds("OUTLOOK_EMAIL", null)).isEmpty();
    }

    @Test
    void collapsesActiveAttachmentReferencesToTheirDistinctParents() {
      persist(TransactionDirection.EXPENSE, "OUTLOOK_EMAIL", "message-1:a", "message-1", null);
      persist(TransactionDirection.EXPENSE, "OUTLOOK_EMAIL", "message-1:b", "message-1", null);
      persist(TransactionDirection.EXPENSE, "OUTLOOK_EMAIL", "message-2:a", "message-2", null);
      persist(TransactionDirection.EXPENSE, "MANUAL", "manual-1", "message-3", null);
      FinancialTransaction deleted =
          persist(TransactionDirection.EXPENSE, "OUTLOOK_EMAIL", "message-4:a", "message-4", null);
      deleted.tombstone(LocalDateTime.now());
      flushAndClear();

      Set<String> saved =
          query.findSavedParentExternalIds(
              "OUTLOOK_EMAIL", List.of("message-1", "message-2", "message-3", "message-4"));

      assertThat(saved).containsExactlyInAnyOrder("message-1", "message-2");
    }
  }

  @Nested
  class ExistsActive {

    @Test
    void matchesOnlyAnActiveTransactionForTheExactOriginAndSource() {
      persist(TransactionDirection.EXPENSE, "RECEIPT", "drive-1", null, null);
      FinancialTransaction deleted =
          persist(TransactionDirection.EXPENSE, "RECEIPT", "drive-2", null, null);
      deleted.tombstone(LocalDateTime.now());
      flushAndClear();

      assertThat(query.existsActive("RECEIPT", "drive-1")).isTrue();
      assertThat(query.existsActive("OUTLOOK_EMAIL", "drive-1")).isFalse();
      assertThat(query.existsActive("RECEIPT", "drive-2")).isFalse();
      assertThat(query.existsActive("RECEIPT", "unknown")).isFalse();
    }
  }

  @Nested
  class FindActiveLinks {

    @Test
    void returnsNothingWhenNoIdsAreRequested() {
      assertThat(query.findActiveLinks(List.of())).isEmpty();
      assertThat(query.findActiveLinks(null)).isEmpty();
    }

    @Test
    void findsActiveTransactionsByAttachmentOrImportIdentityOncePerMatch() {
      FinancialTransaction attached =
          persist(TransactionDirection.EXPENSE, "OUTLOOK_EMAIL", "message-1", null, "drive-1");
      FinancialTransaction imported =
          persist(TransactionDirection.INCOME, "RECEIPT", "drive-2", null, null);
      FinancialTransaction both =
          persist(TransactionDirection.EXPENSE, "RECEIPT", "drive-3", null, "drive-3");
      FinancialTransaction deleted =
          persist(TransactionDirection.EXPENSE, "RECEIPT", "drive-4", null, "drive-4");
      deleted.tombstone(LocalDateTime.now());
      flushAndClear();

      List<LinkedTransaction> links =
          query.findActiveLinks(List.of("drive-1", "drive-2", "drive-3", "drive-4"));

      assertThat(links)
          .containsExactlyInAnyOrder(
              new LinkedTransaction(
                  "drive-1", attached.getId(), attached.getVersion(), TransactionDirection.EXPENSE),
              new LinkedTransaction(
                  "drive-2", imported.getId(), imported.getVersion(), TransactionDirection.INCOME),
              new LinkedTransaction(
                  "drive-3", both.getId(), both.getVersion(), TransactionDirection.EXPENSE));
    }

    @Test
    void findsEveryTransactionThatSharesOneStatementFile() {
      FinancialTransaction first =
          persist(TransactionDirection.INCOME, "VENMO", "payment-1", null, "statement");
      FinancialTransaction second =
          persist(TransactionDirection.INCOME, "VENMO", "payment-2", null, "statement");
      flushAndClear();

      assertThat(query.findActiveLinks(List.of("statement")))
          .containsExactly(
              new LinkedTransaction(
                  "statement", first.getId(), first.getVersion(), TransactionDirection.INCOME),
              new LinkedTransaction(
                  "statement", second.getId(), second.getVersion(), TransactionDirection.INCOME));
    }
  }

  private FinancialTransaction persist(
      TransactionDirection direction,
      String origin,
      String externalId,
      String parentExternalId,
      String attachmentExternalId) {
    FinancialTransaction transaction =
        FinancialTransaction.builder()
            .amount(new BigDecimal("25.00"))
            .direction(direction)
            .date(LocalDate.of(2026, 8, 1))
            .description("Synthetic transaction")
            .activity(activity)
            .neutralCategory(
                direction == TransactionDirection.INCOME ? incomeCategory : expenseCategory)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    transaction.replaceImportReference(origin, externalId, null, parentExternalId);
    if (attachmentExternalId != null) {
      transaction.replaceAttachment("ONEDRIVE", attachmentExternalId, "receipt.pdf", null);
    }
    return entityManager.persist(transaction);
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
