package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    void delegatesIdentityClaimAndLockQueries() {
      BackgroundJob job = BackgroundJob.builder().id(1L).build();
      LocalDateTime now = LocalDateTime.now();
      when(repository.save(job)).thenReturn(job);
      when(repository.findForUpdate(1L)).thenReturn(Optional.of(job));
      when(repository.findClaimableDue(
              now, Set.of(BackgroundJobType.MOVE_RECEIPT), PageRequest.of(0, 10)))
          .thenReturn(List.of(job));
      assertThat(store.save(job)).isSameAs(job);
      assertThat(store.findForUpdate(1L)).contains(job);
      assertThat(store.findClaimableDue(now, 10, Set.of(BackgroundJobType.MOVE_RECEIPT)))
          .containsExactly(job);
      assertThat(store.findClaimableDue(now, 10, Set.of())).isEmpty();
      assertThat(store.existsDueOrActiveWork(now, Set.of())).isFalse();
      assertThat(store.findEarliestFutureAvailableAt(now, Set.of())).isEmpty();
      when(repository.findEarliestFutureAvailableAt(now, Set.of(BackgroundJobType.MOVE_RECEIPT)))
          .thenReturn(now.plusMinutes(2));
      assertThat(store.findEarliestFutureAvailableAt(now, Set.of(BackgroundJobType.MOVE_RECEIPT)))
          .contains(now.plusMinutes(2));
      store.findById(1L);
      store.findByIdempotencyKey("key");
      store.findLatest(1L, BackgroundJobType.MOVE_RECEIPT);
      store.findByInboxItemId(1L);
      store.findByState(BackgroundJobState.LEASED);
      store.existsDueOrActiveWork(now, Set.of(BackgroundJobType.MOVE_RECEIPT));
      store.terminalizeActiveForInbox(1L, "DISMISSED", now);
      verify(repository).findById(1L);
      verify(repository).findByIdempotencyKey("key");
      verify(repository)
          .findFirstByInboxItemIdAndTypeOrderByIdDesc(1L, BackgroundJobType.MOVE_RECEIPT);
      verify(repository).findAllByInboxItemIdOrderByCreatedAtAsc(1L);
      verify(repository).findAllByStateOrderByIdAsc(BackgroundJobState.LEASED);
      verify(repository).existsDueOrActiveWork(now, Set.of(BackgroundJobType.MOVE_RECEIPT));
      verify(repository)
          .terminalizeActiveForInbox(
              1L, "DISMISSED", now, BackgroundJobState.COMPLETED, BackgroundJobState.TERMINAL);
    }
  }
}
