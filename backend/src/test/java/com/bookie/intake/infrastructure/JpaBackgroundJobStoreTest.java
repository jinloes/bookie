package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class JpaBackgroundJobStoreTest {
  @Mock BackgroundJobRepository repository;
  @InjectMocks JpaBackgroundJobStore store;

  @Nested
  class Persistence {
    @Test
    void delegatesIdentityBindingAndLockQueriesWithoutRetryElection() {
      BackgroundJob job = BackgroundJob.builder().id(1L).build();
      UUID execution = UUID.randomUUID();
      LocalDateTime now = LocalDateTime.now();
      when(repository.save(job)).thenReturn(job);
      when(repository.findForUpdate(1L)).thenReturn(Optional.of(job));
      when(repository.findUnboundDue(
              now, Set.of(BackgroundJobType.MOVE_RECEIPT), PageRequest.of(0, 10)))
          .thenReturn(List.of(job));
      assertThat(store.save(job)).isSameAs(job);
      assertThat(store.findForUpdate(1L)).contains(job);
      assertThat(store.findUnboundDue(now, 10, Set.of(BackgroundJobType.MOVE_RECEIPT)))
          .containsExactly(job);
      store.findById(1L);
      store.findByIdempotencyKey("key");
      store.findLatest(
          new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, 1L),
          BackgroundJobType.MOVE_RECEIPT);
      store.findByInboxItemId(1L);
      store.findByExecutionId(execution);
      store.findActiveExecutionIds();
      store.terminalizeActiveForInbox(1L, "DISMISSED", now);
      verify(repository).findById(1L);
      verify(repository).findByIdempotencyKey("key");
      verify(repository)
          .findFirstByLegacyPendingTableAndLegacyPendingIdAndTypeOrderByIdDesc(
              LegacyPendingTable.PENDING_EXPENSES, 1L, BackgroundJobType.MOVE_RECEIPT);
      verify(repository).findAllByInboxItemIdOrderByCreatedAtAsc(1L);
      verify(repository).findAllByExecutionIdOrderByIdAsc(execution);
      verify(repository).findActiveExecutionIds();
      verify(repository)
          .terminalizeActiveForInbox(
              1L, "DISMISSED", now, BackgroundJobState.COMPLETED, BackgroundJobState.TERMINAL);
    }
  }
}
