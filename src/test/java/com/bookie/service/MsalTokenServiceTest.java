package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MsalTokenServiceTest {

  /**
   * Allocates a MsalTokenService instance without invoking the constructor (which would attempt
   * MSAL network I/O), then wires the pendingOauthState field so validateState logic can be
   * exercised in isolation.
   */
  @SuppressWarnings("unchecked")
  private static MsalTokenService allocateService() throws Exception {
    Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
    MsalTokenService svc = (MsalTokenService) unsafe.allocateInstance(MsalTokenService.class);

    Field stateField = MsalTokenService.class.getDeclaredField("pendingOauthState");
    stateField.setAccessible(true);
    stateField.set(svc, new AtomicReference<>());

    return svc;
  }

  private static AtomicReference<String> pendingStateOf(MsalTokenService svc) throws Exception {
    Field f = MsalTokenService.class.getDeclaredField("pendingOauthState");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    AtomicReference<String> ref = (AtomicReference<String>) f.get(svc);
    return ref;
  }

  @Nested
  class ValidateState {

    @Test
    void returnsFalseWhenNoPendingState() throws Exception {
      MsalTokenService svc = allocateService();
      assertThat(svc.validateState("any-state")).isFalse();
    }

    @Test
    void returnsFalseWhenStateDoesNotMatch() throws Exception {
      MsalTokenService svc = allocateService();
      pendingStateOf(svc).set("expected-state");
      assertThat(svc.validateState("wrong-state")).isFalse();
    }

    @Test
    void returnsTrueWhenStateMatches() throws Exception {
      MsalTokenService svc = allocateService();
      pendingStateOf(svc).set("correct-state");
      assertThat(svc.validateState("correct-state")).isTrue();
    }

    @Test
    void consumesStateOnFirstCall() throws Exception {
      MsalTokenService svc = allocateService();
      pendingStateOf(svc).set("one-time-state");
      assertThat(svc.validateState("one-time-state")).isTrue();
      assertThat(svc.validateState("one-time-state")).isFalse();
    }

    @Test
    void returnsFalseForNullState() throws Exception {
      MsalTokenService svc = allocateService();
      pendingStateOf(svc).set("some-state");
      assertThat(svc.validateState(null)).isFalse();
    }
  }
}
