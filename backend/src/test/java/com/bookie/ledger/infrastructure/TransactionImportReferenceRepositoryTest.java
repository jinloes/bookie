package com.bookie.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.TransactionImportReference;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

// Validate against the Flyway schema so the new parent_external_id mapping is exercised.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
class TransactionImportReferenceRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private TransactionImportReferenceRepository repository;

  private FinancialActivity activity;
  private NeutralCategory category;

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
    category =
        entityManager
            .getEntityManager()
            .createQuery(
                "select category from NeutralCategory category"
                    + " where category.direction = :direction and category.active = true"
                    + " order by category.id",
                NeutralCategory.class)
            .setParameter("direction", TransactionDirection.EXPENSE)
            .setMaxResults(1)
            .getSingleResult();
  }

  @Nested
  class FindByOriginAndParentExternalIdInAndTransactionDeletedAtIsNull {

    @Test
    void returnsOnlyActiveReferencesForTheRequestedOriginAndParents() {
      persistTransaction("OUTLOOK_EMAIL", "message-1:attachment-1", "message-1", null);
      persistTransaction("OUTLOOK_EMAIL", "message-2:attachment-1", "message-2", null);
      persistTransaction("MANUAL", "manual-1", "message-1", null);
      persistTransaction(
          "OUTLOOK_EMAIL", "message-3:attachment-1", "message-3", LocalDateTime.now());
      persistTransaction("OUTLOOK_EMAIL", "message-4:attachment-1", "message-4", null);
      entityManager.flush();
      entityManager.clear();

      List<TransactionImportReference> references =
          repository.findByOriginAndParentExternalIdInAndTransactionDeletedAtIsNull(
              "OUTLOOK_EMAIL", List.of("message-1", "message-2", "message-3", "unknown"));

      assertThat(references)
          .extracting(TransactionImportReference::getParentExternalId)
          .containsExactlyInAnyOrder("message-1", "message-2");
    }
  }

  private void persistTransaction(
      String origin, String externalId, String parentExternalId, LocalDateTime deletedAt) {
    FinancialTransaction transaction =
        FinancialTransaction.builder()
            .amount(new BigDecimal("25.00"))
            .direction(TransactionDirection.EXPENSE)
            .date(LocalDate.of(2026, 8, 1))
            .description("Synthetic expense")
            .activity(activity)
            .neutralCategory(category)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deletedAt(deletedAt)
            .build();
    transaction.replaceImportReference(origin, externalId, null, parentExternalId);
    entityManager.persist(transaction);
  }
}
