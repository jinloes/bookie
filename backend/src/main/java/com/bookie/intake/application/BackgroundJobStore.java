package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BackgroundJobStore {

  BackgroundJob save(BackgroundJob job);

  Optional<BackgroundJob> findById(Long id);

  Optional<BackgroundJob> findByIdempotencyKey(String idempotencyKey);

  Optional<BackgroundJob> findLatest(LegacyPendingKey key, BackgroundJobType type);

  List<BackgroundJob> findByInboxItemId(Long inboxItemId);

  Optional<BackgroundJob> findForUpdate(Long id);

  List<BackgroundJob> findUnboundDue(
      LocalDateTime now, int limit, Set<BackgroundJobType> allowedTypes);

  List<UUID> findActiveExecutionIds();

  List<BackgroundJob> findByExecutionId(UUID executionId);

  int terminalizeActiveForInbox(Long inboxItemId, String reason, LocalDateTime now);
}
