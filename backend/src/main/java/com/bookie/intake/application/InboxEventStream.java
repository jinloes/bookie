package com.bookie.intake.application;

import com.bookie.intake.domain.InboxItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Broadcasts best-effort inbox change notifications to connected clients after the writing
 * transaction commits. Delivery is not persisted; clients keep a polling backstop for any missed
 * event.
 */
@Slf4j
@Service
public class InboxEventStream {

  public static final String INBOX_UPDATED = "inbox-updated";

  private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1_000L;

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  public SseEmitter subscribe() {
    SseEmitter emitter = newEmitter();
    try {
      // Flush response headers immediately through Tomcat and any dev proxy.
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (Exception e) {
      log.debug("Closing SSE emitter after initial handshake failure", e);
      emitter.complete();
      return emitter;
    }
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(e -> emitters.remove(emitter));
    return emitter;
  }

  SseEmitter newEmitter() {
    return new SseEmitter(EMITTER_TIMEOUT_MS);
  }

  /** Keeps open SSE connections alive across proxies/webviews without touching the database. */
  @Scheduled(fixedRateString = "${bookie.intake.sse-heartbeat-ms:25000}")
  public void sendHeartbeat() {
    if (emitters.isEmpty()) {
      return;
    }
    List<SseEmitter> dead = new ArrayList<>();
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().comment("keepalive"));
      } catch (Exception e) {
        log.debug("Removing SSE emitter after heartbeat failure", e);
        dead.add(emitter);
      }
    }
    emitters.removeAll(dead);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(InboxItemUpdated event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", event.id());
    payload.put("state", event.state() == null ? null : event.state().name());
    payload.put("origin", event.origin() == null ? null : event.origin().name());
    payload.put(
        "proposedDirection",
        event.proposedDirection() == null ? null : event.proposedDirection().name());
    emit(INBOX_UPDATED, payload);
  }

  public void inboxUpdated(InboxItem item) {
    on(InboxItemUpdated.from(item));
  }

  void emit(String eventName, Object data) {
    List<SseEmitter> dead = new ArrayList<>();
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
      } catch (Exception e) {
        log.debug("Removing SSE emitter after send failure", e);
        dead.add(emitter);
      }
    }
    emitters.removeAll(dead);
  }
}
