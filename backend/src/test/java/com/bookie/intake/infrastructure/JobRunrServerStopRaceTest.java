package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.BackgroundJobServerStatus;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Drives a real JobRunr server through an on-demand stop that races its own heartbeat. */
@Tag("slow")
class JobRunrServerStopRaceTest {

  private final DurableBackgroundJobWorker.Settings settings =
      DurableBackgroundJobWorker.Settings.builder()
          .enabled(true)
          .allowedTypes(Set.of(BackgroundJobType.MOVE_RECEIPT))
          .initialDelayMillis(0)
          .pollIntervalMillis(5000)
          .maxJobsPerPoll(10)
          .heartbeatTimeoutMultiplier(4)
          .build();

  @Test
  void heartbeatInFlightWhileStoppingDoesNotRestartTheServer() throws InterruptedException {
    try (InMemoryStorageProvider memory = new InMemoryStorageProvider()) {
      AtomicBoolean holdNextHeartbeat = new AtomicBoolean();
      CountDownLatch heartbeatInFlight = new CountDownLatch(1);
      CountDownLatch rowDeleted = new CountDownLatch(1);
      StorageProvider racing = mock(StorageProvider.class, delegatesTo(memory));
      doAnswer(
              invocation -> {
                if (holdNextHeartbeat.compareAndSet(true, false)) {
                  heartbeatInFlight.countDown();
                  rowDeleted.await(15, TimeUnit.SECONDS);
                }
                return memory.signalBackgroundJobServerAlive(
                    invocation.<BackgroundJobServerStatus>getArgument(0));
              })
          .when(racing)
          .signalBackgroundJobServerAlive(any());
      doAnswer(
              invocation -> {
                memory.signalBackgroundJobServerStopped(invocation.getArgument(0));
                rowDeleted.countDown();
                return null;
              })
          .when(racing)
          .signalBackgroundJobServerStopped(any());
      BackgroundJobServer server =
          new BackgroundJobServer(
              new JobRunrIntakeStorageProvider(racing, settings),
              new JacksonJsonMapper(),
              null,
              BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration()
                  .andWorkerCount(1)
                  .andPollIntervalInSeconds(5));
      try {
        server.start();
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> !memory.getBackgroundJobServers().isEmpty());
        holdNextHeartbeat.set(true);
        assertThat(heartbeatInFlight.await(15, TimeUnit.SECONDS)).isTrue();

        server.stop();

        // JobRunr's reset path would re-announce the server shortly after stop returns.
        await()
            .during(Duration.ofSeconds(3))
            .atMost(Duration.ofSeconds(5))
            .until(() -> memory.getBackgroundJobServers().isEmpty());
      } finally {
        server.stop();
      }
    }
  }
}
