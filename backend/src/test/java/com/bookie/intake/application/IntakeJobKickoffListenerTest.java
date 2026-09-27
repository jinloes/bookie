package com.bookie.intake.application;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntakeJobKickoffListenerTest {

  @Mock private DurableBackgroundJobWorker worker;

  @InjectMocks private IntakeJobKickoffListener listener;

  @Nested
  class On {

    @Test
    void aSavedItemKicksTheRelayForItsExternalSyncJob() {
      listener.on(new InboxItemSaved(5L));

      verify(worker).runAvailableJob(null);
    }

    @Test
    void aQueuedItemKicksTheRelayForItsParseJob() {
      listener.on(new InboxItemQueued(5L));

      verify(worker).runAvailableJob(null);
    }

    @Test
    void aQueuedJobWithoutAnInboxItemKicksTheRelay() {
      listener.on(new BackgroundJobQueued(7L));

      verify(worker).runAvailableJob(null);
    }
  }
}
