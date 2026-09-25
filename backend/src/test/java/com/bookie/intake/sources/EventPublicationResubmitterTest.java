package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;

@ExtendWith(MockitoExtension.class)
class EventPublicationResubmitterTest {

  private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private IncompleteEventPublications incompletePublications;
  @Captor private ArgumentCaptor<Predicate<EventPublication>> filter;

  private EventPublicationResubmitter resubmitter;

  @BeforeEach
  void setUp() {
    resubmitter = new EventPublicationResubmitter(incompletePublications, CLOCK);
  }

  @Nested
  class Resubmit {

    @Test
    void doesNothingBeforeTheApplicationIsReady() {
      resubmitter.resubmit();

      verifyNoInteractions(incompletePublications);
    }

    @Test
    void resubmitsOnlyPublicationsOlderThanFiveMinutesOnceReady() {
      resubmitter.ready();

      resubmitter.resubmit();

      verify(incompletePublications).resubmitIncompletePublications(filter.capture());
      assertThat(filter.getValue().test(publishedAt(NOW.minusSeconds(301)))).isTrue();
      assertThat(filter.getValue().test(publishedAt(NOW.minusSeconds(300)))).isFalse();
      assertThat(filter.getValue().test(publishedAt(NOW.minusSeconds(60)))).isFalse();
    }
  }

  private static EventPublication publishedAt(Instant instant) {
    EventPublication publication = mock(EventPublication.class);
    when(publication.getPublicationDate()).thenReturn(instant);
    return publication;
  }
}
