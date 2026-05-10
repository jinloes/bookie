package com.bookie.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Manages SSE subscriptions and broadcasts events to all connected clients. */
@Slf4j
@Service
public class SseService {

  private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1_000L;

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(e -> emitters.remove(emitter));
    return emitter;
  }

  public void emit(String eventName, Object data) {
    List<SseEmitter> dead = new ArrayList<>();
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
      } catch (Exception e) {
        dead.add(emitter);
      }
    }
    emitters.removeAll(dead);
  }
}
