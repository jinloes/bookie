package com.bookie.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microsoft.kiota.ApiException;
import org.junit.jupiter.api.Test;

class MicrosoftGraphFailuresTest {

  @Test
  void authenticationFailuresRequireReconnectAndManualReview() {
    assertFailure(401, IntegrationFailureKind.RECONNECT_REQUIRED, false, true);
    assertFailure(403, IntegrationFailureKind.RECONNECT_REQUIRED, false, true);
  }

  @Test
  void rateLimitsAndServerFailuresAreRetryable() {
    assertFailure(429, IntegrationFailureKind.RATE_LIMITED, true, false);
    assertFailure(500, IntegrationFailureKind.TRANSIENT, true, false);
    assertFailure(503, IntegrationFailureKind.TRANSIENT, true, false);
  }

  @Test
  void otherClientFailuresAreTerminalManualReview() {
    assertFailure(400, IntegrationFailureKind.TERMINAL, false, true);
  }

  private static void assertFailure(
      int status,
      IntegrationFailureKind expectedKind,
      boolean expectedRetryable,
      boolean expectedManualReview) {
    ApiException cause = mock(ApiException.class);
    when(cause.getResponseStatusCode()).thenReturn(status);

    IntegrationException result = MicrosoftGraphFailures.from("operation", cause);

    assertThat(result.getKind()).isEqualTo(expectedKind);
    assertThat(result.getStatusCode()).isEqualTo(status);
    assertThat(result.isRetryable()).isEqualTo(expectedRetryable);
    assertThat(result.isManualReviewRequired()).isEqualTo(expectedManualReview);
    assertThat(result.getCause()).isSameAs(cause);
  }
}
