package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class RestoreRestartCoordinatorTest {

  @Test
  void closesTheApplicationContextOnlyAfterAValidatedRestore() throws Exception {
    RestoreJournalStore store = mock(RestoreJournalStore.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    when(store.read()).thenReturn(Optional.of(journal(RestoreState.VALIDATED)));
    RestoreRestartCoordinator coordinator = new RestoreRestartCoordinator(store, context);

    coordinator.requestShutdown();
    coordinator.requestShutdown();

    verify(context, timeout(2_000).times(1)).close();
  }

  @Test
  void refusesShutdownWhenActivationIsNotPending() throws Exception {
    RestoreJournalStore store = mock(RestoreJournalStore.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    when(store.read()).thenReturn(Optional.of(journal(RestoreState.POST_START_VALIDATED)));
    RestoreRestartCoordinator coordinator = new RestoreRestartCoordinator(store, context);

    assertThatThrownBy(coordinator::requestShutdown)
        .isInstanceOf(java.io.IOException.class)
        .hasMessageContaining("cannot restart");
    verify(context, timeout(300).times(0)).close();
  }

  private static RestoreJournal journal(RestoreState state) {
    String now = Instant.now().toString();
    return new RestoreJournal(
        RestoreJournal.CURRENT_VERSION,
        "restore-123",
        state,
        "file-1",
        "backup.sql",
        1,
        "source-hash",
        "/data",
        "/data/bookiedb.mv.db",
        "/data/shadow.mv.db",
        "/data/rollback.mv.db",
        "/data/failed.mv.db",
        "/data/manifest.json",
        "/data/audit.json",
        "live-hash",
        "shadow-hash",
        "manifest-hash",
        now,
        now,
        "message",
        "checksum");
  }
}
