package com.bookie.integrations.outlook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MsalTokenServiceTest {

  private static MsalTokenService newServiceWithMissingClientId() {
    OutlookTokenRepository tokenRepository = mock(OutlookTokenRepository.class);
    TokenCacheCrypto tokenCacheCrypto = mock(TokenCacheCrypto.class);
    OutlookProperties properties =
        new OutlookProperties("", "secret", "tenant", "http://localhost");
    return new MsalTokenService(
        tokenRepository,
        properties,
        tokenCacheCrypto,
        Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC));
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

    Field clockField = MsalTokenService.class.getDeclaredField("clock");
    clockField.setAccessible(true);
    clockField.set(svc, Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC));

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

  @Nested
  class AuthorizationScopes {

    @Test
    void requestsPermissionRequiredForExchangeIdTranslation() {
      MsalTokenService service =
          new MsalTokenService(
              mock(OutlookTokenRepository.class),
              new OutlookProperties(
                  "client-id", "", "", "http://localhost:48763/api/outlook/callback"),
              mock(TokenCacheCrypto.class),
              Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC));

      assertThat(service.getAuthorizationUrl()).contains("User.Read");
    }
  }

  @Nested
  class TokenExpiry {

    @Test
    void considersTokenValidOnlyWhenItExpiresAfterTheFiveMinuteBuffer() throws Exception {
      MsalTokenService service = allocateService();
      IAuthenticationResult result = mock(IAuthenticationResult.class);
      when(result.expiresOnDate()).thenReturn(Date.from(Instant.parse("2026-09-22T19:05:01Z")));

      Boolean valid = ReflectionTestUtils.invokeMethod(service, "tokenExpiresAfter", result, 5);
      assertThat(valid).isTrue();
    }

    @Test
    void treatsExpiryAtTheFiveMinuteBufferBoundaryAsExpired() throws Exception {
      MsalTokenService service = allocateService();
      IAuthenticationResult result = mock(IAuthenticationResult.class);
      when(result.expiresOnDate()).thenReturn(Date.from(Instant.parse("2026-09-22T19:05:00Z")));

      Boolean valid = ReflectionTestUtils.invokeMethod(service, "tokenExpiresAfter", result, 5);
      assertThat(valid).isFalse();
    }
  }
}
