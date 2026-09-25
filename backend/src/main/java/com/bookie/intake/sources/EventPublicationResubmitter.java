package com.bookie.intake.sources;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resubmits event publications whose listener failed, such as a receipt filing whose OneDrive move
 * threw, until they complete. Publications younger than {@link #MINIMUM_AGE} are left alone so an
 * in-flight listener is not invoked twice; nothing runs before the application is Ready.
 */
@Component
@RequiredArgsConstructor
public class EventPublicationResubmitter {

  static final Duration MINIMUM_AGE = Duration.ofMinutes(5);

  private final IncompleteEventPublications incompletePublications;
  private final Clock clock;
  private volatile boolean ready;

  @EventListener(ApplicationReadyEvent.class)
  public void ready() {
    ready = true;
  }

  @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
  public void resubmit() {
    if (!ready) {
      return;
    }
    Instant cutoff = clock.instant().minus(MINIMUM_AGE);
    incompletePublications.resubmitIncompletePublications(
        publication -> publication.getPublicationDate().isBefore(cutoff));
  }
}
