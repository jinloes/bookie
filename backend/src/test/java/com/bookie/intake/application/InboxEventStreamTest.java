package com.bookie.intake.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class InboxEventStreamTest {

  private InboxEventStream eventStream;

  @BeforeEach
  void setUp() {
    eventStream = new InboxEventStream();
  }

  @Nested
  class Subscribe {

    @Test
    void returnsEmitter() {
      assertThat(eventStream.subscribe()).isNotNull();
    }

    @Test
    void eachCallReturnsDistinctEmitter() {
      SseEmitter first = eventStream.subscribe();
      SseEmitter second = eventStream.subscribe();
      assertThat(first).isNotSameAs(second);
    }
  }

  @Nested
  class Emit {

    @Test
    void doesNotThrowWhenNoSubscribers() {
      assertThatNoException().isThrownBy(() -> eventStream.emit("test-event", "payload"));
    }

    @Test
    void doesNotThrowWithActiveSubscriber() {
      eventStream.subscribe();
      assertThatNoException().isThrownBy(() -> eventStream.emit("test-event", "data"));
    }

    @Test
    void removesBrokenEmittersAfterSendFailure() {
      SseEmitter broken =
          new SseEmitter(Long.MAX_VALUE) {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
              throw new IOException("broken pipe");
            }
          };
      SseEmitter healthy = eventStream.subscribe();

      @SuppressWarnings("unchecked")
      List<SseEmitter> emitters =
          (List<SseEmitter>) ReflectionTestUtils.getField(eventStream, "emitters");
      assertThat(emitters).isNotNull();
      emitters.add(broken);

      assertThatNoException().isThrownBy(() -> eventStream.emit("event", "value"));
      assertThat(emitters).contains(healthy).doesNotContain(broken);
    }

    @Test
    void removesEmitterWhenSendThrowsRuntimeException() {
      SseEmitter broken =
          new SseEmitter(Long.MAX_VALUE) {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
              throw new IllegalStateException("already completed");
            }
          };

      @SuppressWarnings("unchecked")
      List<SseEmitter> emitters =
          (List<SseEmitter>) ReflectionTestUtils.getField(eventStream, "emitters");
      assertThat(emitters).isNotNull();
      emitters.add(broken);

      assertThatNoException().isThrownBy(() -> eventStream.emit("event", "value"));
      assertThat(emitters).doesNotContain(broken);
    }
  }

  @Nested
  class InboxUpdated {

    @Test
    void sendsTheNamedInboxEventWithTheItemSummary() throws IOException {
      List<SseEmitter.SseEventBuilder> sent = new java.util.ArrayList<>();
      SseEmitter recording =
          new SseEmitter(Long.MAX_VALUE) {
            @Override
            public void send(SseEventBuilder builder) {
              sent.add(builder);
            }
          };
      emitters().add(recording);

      eventStream.inboxUpdated(
          InboxItem.builder()
              .id(7L)
              .state(InboxState.READY)
              .origin(ExpenseSource.RECEIPT)
              .proposedDirection(TransactionDirection.EXPENSE)
              .build());

      assertThat(sent)
          .singleElement()
          .satisfies(
              event ->
                  assertThat(render(event))
                      .contains("event:inbox-updated")
                      .contains("\"id\":7")
                      .contains("\"state\":\"READY\"")
                      .contains("\"origin\":\"RECEIPT\"")
                      .contains("\"proposedDirection\":\"EXPENSE\""));
    }

    @Test
    void sendsNullFieldsForAnItemWithoutStateOriginOrDirection() {
      List<SseEmitter.SseEventBuilder> sent = new java.util.ArrayList<>();
      emitters()
          .add(
              new SseEmitter(Long.MAX_VALUE) {
                @Override
                public void send(SseEventBuilder builder) {
                  sent.add(builder);
                }
              });

      eventStream.inboxUpdated(InboxItem.builder().id(8L).build());

      assertThat(render(sent.getFirst()))
          .contains("\"state\":null")
          .contains("\"origin\":null")
          .contains("\"proposedDirection\":null");
    }

    @SuppressWarnings("unchecked")
    private List<SseEmitter> emitters() {
      return (List<SseEmitter>) ReflectionTestUtils.getField(eventStream, "emitters");
    }

    private String render(SseEmitter.SseEventBuilder event) {
      StringBuilder rendered = new StringBuilder();
      for (var part : event.build()) {
        Object data = part.getData();
        rendered.append(
            data instanceof java.util.Map<?, ?> map ? toJson(map) : String.valueOf(data));
      }
      return rendered.toString();
    }

    private String toJson(java.util.Map<?, ?> map) {
      try {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
