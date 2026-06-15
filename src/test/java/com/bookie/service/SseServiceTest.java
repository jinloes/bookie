package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseServiceTest {

  private SseService sseService;

  @BeforeEach
  void setUp() {
    sseService = new SseService();
  }

  @Nested
  class Subscribe {

    @Test
    void returnsEmitter() {
      assertThat(sseService.subscribe()).isNotNull();
    }

    @Test
    void eachCallReturnsDistinctEmitter() {
      SseEmitter first = sseService.subscribe();
      SseEmitter second = sseService.subscribe();
      assertThat(first).isNotSameAs(second);
    }
  }

  @Nested
  class Emit {

    @Test
    void doesNotThrowWhenNoSubscribers() {
      assertThatNoException().isThrownBy(() -> sseService.emit("test-event", "payload"));
    }

    @Test
    void doesNotThrowWithActiveSubscriber() {
      sseService.subscribe();
      assertThatNoException().isThrownBy(() -> sseService.emit("test-event", "data"));
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
      SseEmitter healthy = sseService.subscribe();

      @SuppressWarnings("unchecked")
      List<SseEmitter> emitters =
          (List<SseEmitter>) ReflectionTestUtils.getField(sseService, "emitters");
      assertThat(emitters).isNotNull();
      emitters.add(broken);

      assertThatNoException().isThrownBy(() -> sseService.emit("event", "value"));
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
          (List<SseEmitter>) ReflectionTestUtils.getField(sseService, "emitters");
      assertThat(emitters).isNotNull();
      emitters.add(broken);

      assertThatNoException().isThrownBy(() -> sseService.emit("event", "value"));
      assertThat(emitters).doesNotContain(broken);
    }
  }
}
