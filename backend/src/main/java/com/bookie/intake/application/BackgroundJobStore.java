package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BackgroundJobStore {

  BackgroundJob save(BackgroundJob job);

  Optional<BackgroundJob> findById(Long id);

  Optional<BackgroundJob> findByIdempotencyKey(String idempotencyKey);

  Optional<BackgroundJob> findLatest(LegacyPendingKey key, BackgroundJobType type);

  List<BackgroundJob> findByInboxItemId(Long inboxItemId);

  List<JobCandidate> findClaimable(LocalDateTime now, int limit);

  boolean claim(
      Long id,
      Long expectedVersion,
      String leaseOwner,
      LocalDateTime leaseExpiresAt,
      LocalDateTime now);

  List<BackgroundJob> findExpiredLeases(LocalDateTime now);

  int terminalizeActiveForInbox(Long inboxItemId, String reason, LocalDateTime now);

  record JobCandidate(Long id, Long version) {}
}
