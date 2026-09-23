package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;

class RestoreBootstrapListenerTest {

  private final RestoreBootstrapListener listener =
      new RestoreBootstrapListener(
          Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC));

  @Test
  void readsTheResolvedSpringDataDirectoryBeforeDatasourceInitialization(@TempDir Path directory) {
    ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
    ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
    when(event.getEnvironment()).thenReturn(environment);
    when(environment.getRequiredProperty("bookie.data-dir")).thenReturn(directory.toString());

    assertThatCode(() -> listener.onApplicationEvent(event)).doesNotThrowAnyException();
  }
}
