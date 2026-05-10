package com.bookie.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Collects aliases that could not be resolved during a single email parse session. Uses a
 * ThreadLocal so it works from both HTTP request threads and @Async task threads. Callers must
 * invoke {@link #clear()} before and after each parse to avoid cross-task contamination when
 * thread-pool threads are reused.
 */
@Component
public class ParseSessionContext {

  private static final ThreadLocal<List<String>> ALIASES = ThreadLocal.withInitial(ArrayList::new);

  public void clear() {
    ALIASES.remove();
  }

  public void addUnrecognizedAlias(String alias) {
    ALIASES.get().add(alias);
  }

  public List<String> getUnrecognizedAliases() {
    return List.copyOf(ALIASES.get());
  }
}
