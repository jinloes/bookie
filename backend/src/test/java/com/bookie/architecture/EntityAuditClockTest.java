package com.bookie.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.ledger.domain.ExpenseSource;
import com.bookie.ledger.domain.FinancialTransaction;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(EntityAuditClockTest.FixedClockConfiguration.class)
@Transactional
class EntityAuditClockTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 19, 0);
  private static final LocalDateTime PRESET_CREATED_AT = LocalDateTime.of(2020, 1, 2, 3, 4);

  @Autowired private EntityManager entityManager;

  @Test
  void persistsAndUpdatesAuditFieldsUsingTheInjectedClockAndPreservesPresetCreationTime() {
    HouseholdMember owner = HouseholdMember.builder().name("Clock Test Owner").build();
    entityManager.persist(owner);
    FinancialActivity activity =
        FinancialActivity.builder()
            .name("Clock Test Activity")
            .activityType(ActivityType.SELF_EMPLOYMENT)
            .taxTreatment(TaxTreatment.SCHEDULE_C)
            .owner(owner)
            .build();
    entityManager.persist(activity);
    NeutralCategory category =
        NeutralCategory.builder()
            .key("CLOCK_TEST_CATEGORY")
            .label("Clock Test Category")
            .direction(TransactionDirection.EXPENSE)
            .build();
    entityManager.persist(category);

    InboxItem inboxItem =
        InboxItem.builder()
            .origin(ExpenseSource.MANUAL)
            .rawStatus("RECEIVED")
            .createdAt(PRESET_CREATED_AT)
            .build();
    entityManager.persist(inboxItem);
    entityManager.flush();

    BackgroundJob backgroundJob =
        BackgroundJob.builder()
            .inboxItem(inboxItem)
            .type(BackgroundJobType.MOVE_RECEIPT)
            .idempotencyKey("clock-test")
            .build();
    BackgroundJob prepopulatedJob =
        BackgroundJob.builder()
            .inboxItem(inboxItem)
            .type(BackgroundJobType.MOVE_RECEIPT)
            .idempotencyKey("clock-test-prepopulated")
            .state(BackgroundJobState.AVAILABLE)
            .maxAttempts(3)
            .availableAt(PRESET_CREATED_AT)
            .createdAt(PRESET_CREATED_AT)
            .updatedAt(PRESET_CREATED_AT)
            .build();
    FinancialTransaction transaction =
        FinancialTransaction.builder()
            .amount(new BigDecimal("12.34"))
            .direction(TransactionDirection.EXPENSE)
            .date(LocalDate.of(2026, 9, 22))
            .description("Clock test transaction")
            .activity(activity)
            .neutralCategory(category)
            .build();
    FinancialTransaction prepopulatedTransaction =
        FinancialTransaction.builder()
            .amount(new BigDecimal("56.78"))
            .direction(TransactionDirection.EXPENSE)
            .date(LocalDate.of(2026, 9, 21))
            .description("Prepopulated clock test transaction")
            .activity(activity)
            .neutralCategory(category)
            .createdAt(PRESET_CREATED_AT)
            .updatedAt(PRESET_CREATED_AT)
            .build();
    entityManager.persist(backgroundJob);
    entityManager.persist(prepopulatedJob);
    entityManager.persist(transaction);
    entityManager.persist(prepopulatedTransaction);
    entityManager.flush();

    assertThat(inboxItem.getCreatedAt()).isEqualTo(PRESET_CREATED_AT);
    assertThat(inboxItem.getUpdatedAt()).isEqualTo(NOW);
    assertThat(backgroundJob.getCreatedAt()).isEqualTo(NOW);
    assertThat(backgroundJob.getUpdatedAt()).isEqualTo(NOW);
    assertThat(prepopulatedJob.getCreatedAt()).isEqualTo(PRESET_CREATED_AT);
    assertThat(prepopulatedJob.getUpdatedAt()).isEqualTo(PRESET_CREATED_AT);
    assertThat(prepopulatedJob.getAvailableAt()).isEqualTo(PRESET_CREATED_AT);
    assertThat(prepopulatedJob.getMaxAttempts()).isEqualTo(3);
    assertThat(transaction.getCreatedAt()).isEqualTo(NOW);
    assertThat(transaction.getUpdatedAt()).isEqualTo(NOW);
    assertThat(prepopulatedTransaction.getCreatedAt()).isEqualTo(PRESET_CREATED_AT);
    assertThat(prepopulatedTransaction.getUpdatedAt()).isEqualTo(PRESET_CREATED_AT);
    assertThat(inboxItem.getExternalSyncState()).isEqualTo(ExternalSyncState.NOT_REQUIRED);

    Long inboxItemId = inboxItem.getId();
    Long backgroundJobId = backgroundJob.getId();
    Long prepopulatedJobId = prepopulatedJob.getId();
    Long transactionId = transaction.getId();
    Long prepopulatedTransactionId = prepopulatedTransaction.getId();
    entityManager.clear();

    InboxItem storedInboxItem = entityManager.find(InboxItem.class, inboxItemId);
    BackgroundJob storedBackgroundJob = entityManager.find(BackgroundJob.class, backgroundJobId);
    BackgroundJob storedPrepopulatedJob =
        entityManager.find(BackgroundJob.class, prepopulatedJobId);
    FinancialTransaction storedTransaction =
        entityManager.find(FinancialTransaction.class, transactionId);
    FinancialTransaction storedPrepopulatedTransaction =
        entityManager.find(FinancialTransaction.class, prepopulatedTransactionId);
    storedInboxItem.setRawStatus("UPDATED");
    storedBackgroundJob.setLastError("updated");
    storedPrepopulatedJob.setLastError("updated");
    storedTransaction.setDescription("Updated clock test transaction");
    storedPrepopulatedTransaction.setDescription("Updated prepopulated clock test transaction");
    entityManager.flush();
    entityManager.clear();

    assertThat(entityManager.find(InboxItem.class, inboxItemId).getUpdatedAt()).isEqualTo(NOW);
    assertThat(entityManager.find(BackgroundJob.class, backgroundJobId).getUpdatedAt())
        .isEqualTo(NOW);
    assertThat(entityManager.find(BackgroundJob.class, prepopulatedJobId).getUpdatedAt())
        .isEqualTo(NOW);
    assertThat(entityManager.find(FinancialTransaction.class, transactionId).getUpdatedAt())
        .isEqualTo(NOW);
    assertThat(
            entityManager
                .find(FinancialTransaction.class, prepopulatedTransactionId)
                .getUpdatedAt())
        .isEqualTo(NOW);
    assertThat(entityManager.find(InboxItem.class, inboxItemId).getCreatedAt())
        .isEqualTo(PRESET_CREATED_AT);
  }

  @TestConfiguration
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC);
    }
  }
}
