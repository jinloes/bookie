package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bookie.config.OutlookProperties;
import com.bookie.repository.OutlookTokenRepository;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MsalTokenServiceTest {

  private static MsalTokenService newServiceWithMissingClientId() {
    OutlookTokenRepository tokenRepository = mock(OutlookTokenRepository.class);
    OutlookProperties properties =
        new OutlookProperties("", "secret", "tenant", "http://localhost");
    return new MsalTokenService(tokenRepository, properties);
  }

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

  @Nested
  class MissingOutlookConfiguration {

    @Test
    void constructorDoesNotThrowWhenClientIdMissing() {
      assertThatCode(MsalTokenServiceTest::newServiceWithMissingClientId)
          .doesNotThrowAnyException();
    }

    @Test
    void isConnectedReturnsFalseWhenClientIdMissing() {
      MsalTokenService svc = newServiceWithMissingClientId();
      assertThat(svc.isConnected()).isFalse();
    }

    @Test
    void getAuthorizationUrlThrowsServiceUnavailableWhenClientIdMissing() {
      MsalTokenService svc = newServiceWithMissingClientId();

      assertThatThrownBy(svc::getAuthorizationUrl)
          .isInstanceOf(ResponseStatusException.class)
          .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
          .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
  }
}
