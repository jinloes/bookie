package com.bookie.intake.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class InboxStateMachine {

  private static final Map<InboxState, EnumSet<InboxState>> TRANSITIONS =
      new EnumMap<>(InboxState.class);

  static {
    allow(
        InboxState.RECEIVED,
        InboxState.QUEUED,
        InboxState.READY,
        InboxState.FAILED,
        InboxState.DISMISSED);
    allow(
        InboxState.QUEUED,
        InboxState.PROCESSING,
        InboxState.READY,
        InboxState.FAILED,
        InboxState.DISMISSED);
    allow(
        InboxState.PROCESSING,
        InboxState.QUEUED,
        InboxState.READY,
        InboxState.FAILED,
        InboxState.DISMISSED);
    allow(
        InboxState.READY,
        InboxState.QUEUED,
        InboxState.SAVE_PENDING,
        InboxState.FAILED,
        InboxState.DISMISSED);
    allow(InboxState.SAVE_PENDING, InboxState.READY, InboxState.SAVED, InboxState.FAILED);
    allow(InboxState.FAILED, InboxState.RECEIVED, InboxState.QUEUED, InboxState.DISMISSED);
    allow(InboxState.DISMISSED, InboxState.RECEIVED, InboxState.QUEUED);
    allow(InboxState.SAVED);
  }

  private InboxStateMachine() {}

  public static boolean canTransition(InboxState current, InboxState target) {
    return current != null
        && target != null
        && (current == target
            || TRANSITIONS
                .getOrDefault(current, EnumSet.noneOf(InboxState.class))
                .contains(target));
  }

  public static void transition(InboxItem item, InboxState target) {
    InboxState current = item.getState();
    if (!canTransition(current, target)) {
      throw new IllegalStateException(
          "Invalid inbox transition from %s to %s".formatted(current, target));
    }
    item.setState(target);
  }

  private static void allow(InboxState source, InboxState... targets) {
    TRANSITIONS.put(
        source,
        targets.length == 0 ? EnumSet.noneOf(InboxState.class) : EnumSet.of(targets[0], targets));
  }
}
