package com.bookie.intake.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.util.BackgroundWorkControl;
import com.bookie.util.WriteFreezeState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntakeJobKickoffListenerTest {

  @Mock private BackgroundWorkControl workControl;
  @Mock private WriteFreezeState writeFreeze;

  @InjectMocks private IntakeJobKickoffListener listener;

  @Nested
  class On {

    @Test
    void aSavedItemRequestsADrain() {
      listener.on(new InboxItemSaved(5L));

      verify(workControl).requestDrain();
    }

    @Test
    void aQueuedItemRequestsADrain() {
      listener.on(new InboxItemQueued(5L));

      verify(workControl).requestDrain();
    }

    @Test
    void aQueuedJobWithoutAnInboxItemRequestsADrain() {
      listener.on(new BackgroundJobQueued(7L));

      verify(workControl).requestDrain();
    }

    @Test
    void doesNotRequestADrainWhileWritesAreFrozen() {
      when(writeFreeze.isFrozen()).thenReturn(true);

      listener.on(new InboxItemQueued(5L));

      verifyNoInteractions(workControl);
    }
  }
}
