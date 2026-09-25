package com.bookie.intake.application;

import com.bookie.intake.domain.InboxItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Broadcasts best-effort inbox change notifications to connected clients. Delivery is not
 * persisted; clients keep a polling backstop for any missed event.
 */
@Slf4j
@Service
public class InboxEventStream {

  public static final String INBOX_UPDATED = "inbox-updated";

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

  public void inboxUpdated(InboxItem item) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", item.getId());
    payload.put("state", item.getState() == null ? null : item.getState().name());
    payload.put("origin", item.getOrigin() == null ? null : item.getOrigin().name());
    payload.put(
        "proposedDirection",
        item.getProposedDirection() == null ? null : item.getProposedDirection().name());
    emit(INBOX_UPDATED, payload);
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
