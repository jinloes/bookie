package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.repository.PendingExpenseRepository;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartupRecoveryTest {

  @Mock private PendingExpenseRepository pendingExpenseRepository;

  @InjectMocks private StartupRecovery startupRecovery;

  @Nested
  class ResetStuckProcessing {

    @Test
    void stuckItems_resetToFailed() {
      PendingExpense stuck = new PendingExpense();
      stuck.setId(1L);
      stuck.setStatus(PendingExpenseStatus.PROCESSING);
      when(pendingExpenseRepository.findByStatus(PendingExpenseStatus.PROCESSING))
          .thenReturn(List.of(stuck));

      startupRecovery.resetStuckProcessing();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<PendingExpense>> captor = ArgumentCaptor.forClass(List.class);
      verify(pendingExpenseRepository).saveAll(captor.capture());
      List<PendingExpense> saved = captor.getValue();
      assertThat(saved).hasSize(1);
      assertThat(saved.get(0).getStatus()).isEqualTo(PendingExpenseStatus.FAILED);
      assertThat(saved.get(0).getErrorMessage()).isNotBlank();
    }

    @Test
    void noStuckItems_doesNotSave() {
      when(pendingExpenseRepository.findByStatus(PendingExpenseStatus.PROCESSING))
          .thenReturn(List.of());

      startupRecovery.resetStuckProcessing();

      verify(pendingExpenseRepository, never()).saveAll(anyList());
    }
  }
}
