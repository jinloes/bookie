package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
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
      SseEmitter emitter = sseService.subscribe();
      assertThat(emitter).isNotNull();
    }
  }

  @Nested
  class Emit {

    @Test
    void doesNotThrowWhenNoSubscribers() {
      sseService.emit("test-event", "payload");
    }
  }
}
