package com.bookie.integrations;

import com.microsoft.kiota.ApiException;

public final class MicrosoftGraphFailures {

  private MicrosoftGraphFailures() {}

  public static IntegrationException from(String operation, ApiException cause) {
    int statusCode = cause.getResponseStatusCode();
    IntegrationFailureKind kind =
        switch (statusCode) {
          case 401, 403 -> IntegrationFailureKind.RECONNECT_REQUIRED;
          case 404 -> IntegrationFailureKind.NOT_FOUND;
          case 429 -> IntegrationFailureKind.RATE_LIMITED;
          default ->
              statusCode >= 500
                  ? IntegrationFailureKind.TRANSIENT
                  : IntegrationFailureKind.TERMINAL;
        };
    return IntegrationException.builder()
        .kind(kind)
        .message("%s failed with Microsoft Graph status %d".formatted(operation, statusCode))
        .statusCode(statusCode)
        .cause(cause)
        .build();
  }

  public static IntegrationException invalidResponse(String operation) {
    return IntegrationException.builder()
        .kind(IntegrationFailureKind.INVALID_RESPONSE)
        .message(operation + " returned an incomplete Microsoft Graph response")
        .build();
  }
}
