package com.bookie.service;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.compatibility.intake.LegacyInboxSnapshots;
import com.bookie.intake.application.LegacyInboxSynchronizer;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.PendingExpenseRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PendingExpenseCreationService {

  private final PendingExpenseRepository pendingRepository;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryService financialCategoryService;
  private final LegacyInboxSynchronizer inboxSynchronizer;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PendingExpense create(
      String sourceId, ExpenseSource sourceType, String subject, Long configuredActivityId) {
    FinancialActivity activity =
        configuredActivityId == null
            ? activityCatalog.getNeedsClassification()
            : activityCatalog.findActiveById(configuredActivityId);
    PendingExpense pending =
        PendingExpense.builder()
            .sourceId(sourceId)
            .sourceType(sourceType)
            .subject(subject)
            .activity(activity)
            .financialCategory(
                financialCategoryService.defaultFor(activity, TransactionDirection.EXPENSE))
            .configuredActivityId(configuredActivityId)
            .classificationAmbiguous(true)
            .status(PendingExpenseStatus.PROCESSING)
            .createdAt(LocalDateTime.now())
            .build();
    PendingExpense saved = pendingRepository.save(pending);
    inboxSynchronizer.created(pendingKey(saved.getId()), LegacyInboxSnapshots.from(saved), true);
    return saved;
  }

  private LegacyPendingKey pendingKey(Long id) {
    return new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, id);
  }
}
