package com.bookie.intake.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InboxStateMachineTest {

  private static final Map<InboxState, EnumSet<InboxState>> ALLOWED = allowedTransitions();

  @Nested
  class CanTransition {

    @Test
    void implementsEveryStateEdgeExplicitly() {
      for (InboxState source : InboxState.values()) {
        for (InboxState target : InboxState.values()) {
          boolean expected = source == target || ALLOWED.get(source).contains(target);
          assertThat(InboxStateMachine.canTransition(source, target))
              .as("%s -> %s", source, target)
              .isEqualTo(expected);
        }
      }
    }

    @Test
    void rejectsNullEndpoints() {
      assertThat(InboxStateMachine.canTransition(null, InboxState.READY)).isFalse();
      assertThat(InboxStateMachine.canTransition(InboxState.READY, null)).isFalse();
      assertThat(InboxStateMachine.canTransition(null, null)).isFalse();
    }
  }

  @Nested
  class Transition {

    @Test
    void appliesAllowedTransition() {
      InboxItem item = InboxItem.builder().state(InboxState.QUEUED).build();

      InboxStateMachine.transition(item, InboxState.PROCESSING);

      assertThat(item.getState()).isEqualTo(InboxState.PROCESSING);
    }

    @Test
    void allowsIdempotentTransition() {
      InboxItem item = InboxItem.builder().state(InboxState.READY).build();

      InboxStateMachine.transition(item, InboxState.READY);

      assertThat(item.getState()).isEqualTo(InboxState.READY);
    }

    @Test
    void savedItemCannotReturnToWorkflow() {
      InboxItem item = InboxItem.builder().state(InboxState.SAVED).build();

      assertThatThrownBy(() -> InboxStateMachine.transition(item, InboxState.QUEUED))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SAVED")
          .hasMessageContaining("QUEUED");
    }
  }

  private static Map<InboxState, EnumSet<InboxState>> allowedTransitions() {
    Map<InboxState, EnumSet<InboxState>> allowed = new EnumMap<>(InboxState.class);
    allowed.put(
        InboxState.RECEIVED,
        EnumSet.of(InboxState.QUEUED, InboxState.READY, InboxState.FAILED, InboxState.DISMISSED));
    allowed.put(
        InboxState.QUEUED,
        EnumSet.of(
            InboxState.PROCESSING, InboxState.READY, InboxState.FAILED, InboxState.DISMISSED));
    allowed.put(
        InboxState.PROCESSING,
        EnumSet.of(InboxState.QUEUED, InboxState.READY, InboxState.FAILED, InboxState.DISMISSED));
    allowed.put(
        InboxState.READY,
        EnumSet.of(
            InboxState.QUEUED, InboxState.SAVE_PENDING, InboxState.FAILED, InboxState.DISMISSED));
    allowed.put(
        InboxState.SAVE_PENDING, EnumSet.of(InboxState.READY, InboxState.SAVED, InboxState.FAILED));
    allowed.put(
        InboxState.FAILED,
        EnumSet.of(InboxState.RECEIVED, InboxState.QUEUED, InboxState.DISMISSED));
    allowed.put(InboxState.DISMISSED, EnumSet.of(InboxState.RECEIVED, InboxState.QUEUED));
    allowed.put(InboxState.SAVED, EnumSet.noneOf(InboxState.class));
    return Map.copyOf(allowed);
  }
}
