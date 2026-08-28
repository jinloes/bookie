package com.bookie.datalifecycle.restore;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class RestoreRestartCoordinator {

  private static final Duration RESPONSE_FLUSH_DELAY = Duration.ofMillis(250);

  private final RestoreJournalStore journalStore;
  private final ConfigurableApplicationContext applicationContext;
  private final AtomicBoolean shutdownScheduled = new AtomicBoolean();

  public RestoreRestartCoordinator(
      RestoreJournalStore journalStore, ConfigurableApplicationContext applicationContext) {
    this.journalStore = journalStore;
    this.applicationContext = applicationContext;
  }

  public void requestShutdown() throws IOException {
    RestoreJournal journal =
        journalStore.read().orElseThrow(() -> new IOException("No validated restore is staged"));
    if (journal.state() != RestoreState.VALIDATED) {
      throw new IOException(
          "Restore " + journal.restoreId() + " cannot restart from state " + journal.state());
    }
    if (!shutdownScheduled.compareAndSet(false, true)) {
      return;
    }

    Thread.ofPlatform()
        .name("bookie-controlled-restore-shutdown")
        .daemon(false)
        .start(
            () -> {
              try {
                Thread.sleep(RESPONSE_FLUSH_DELAY);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                applicationContext.close();
              }
            });
  }
}
