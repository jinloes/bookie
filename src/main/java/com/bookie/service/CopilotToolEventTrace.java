package com.bookie.service;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

@Component
public class CopilotToolEventTrace {

  private final ConcurrentLinkedQueue<String> toolStartNames = new ConcurrentLinkedQueue<>();

  public void clear() {
    toolStartNames.clear();
  }

  public void recordToolStart(String toolName) {
    if (toolName != null && !toolName.isBlank()) {
      toolStartNames.add(toolName);
    }
  }

  public List<String> snapshotToolStarts() {
    return List.copyOf(toolStartNames);
  }
}
