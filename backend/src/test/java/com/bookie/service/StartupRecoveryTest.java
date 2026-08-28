package com.bookie.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.BackgroundJobService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartupRecoveryTest {

  @Mock private BackgroundJobService backgroundJobService;

  @InjectMocks private StartupRecovery startupRecovery;

  @Nested
  class RecoverExpiredLeases {

    @Test
    void expiredLeases_areReleasedForRetry() {
      when(backgroundJobService.recoverExpiredLeases()).thenReturn(2);

      startupRecovery.recoverExpiredLeases();

      verify(backgroundJobService).recoverExpiredLeases();
    }

    @Test
    void noExpiredLeases_isANoOp() {
      when(backgroundJobService.recoverExpiredLeases()).thenReturn(0);

      startupRecovery.recoverExpiredLeases();

      verify(backgroundJobService).recoverExpiredLeases();
    }
  }
}
