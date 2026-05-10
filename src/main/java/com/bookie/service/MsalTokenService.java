package com.bookie.service;

import com.bookie.config.OutlookProperties;
import com.bookie.repository.OutlookTokenRepository;
import com.microsoft.aad.msal4j.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MsalTokenService {

  private static final Set<String> SCOPES =
      Set.of("Mail.ReadWrite", "Files.ReadWrite", "offline_access");

  private static final int TOKEN_EXPIRY_BUFFER_MINUTES = 5;

  private final OutlookProperties properties;
  private final PublicClientApplication msalApp;
  // Cached result avoids a network round-trip on every Graph API call; refreshed only when
  // the token is within TOKEN_EXPIRY_BUFFER_MINUTES of expiry.
  private final AtomicReference<IAuthenticationResult> cachedToken = new AtomicReference<>();
  private final AtomicReference<String> pendingOauthState = new AtomicReference<>();

  public MsalTokenService(OutlookTokenRepository tokenRepository, OutlookProperties properties) {
    this.properties = properties;
    try {
      this.msalApp =
          PublicClientApplication.builder(properties.clientId())
              .authority("https://login.microsoftonline.com/consumers/")
              .setTokenCacheAccessAspect(new H2TokenCacheAspect(tokenRepository))
              .build();
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Invalid MSAL authority URL", e);
    }
  }

  public String getAuthorizationUrl() {
    String state = UUID.randomUUID().toString();
    pendingOauthState.set(state);
    AuthorizationRequestUrlParameters params =
        AuthorizationRequestUrlParameters.builder(properties.redirectUri(), SCOPES)
            .responseMode(ResponseMode.QUERY)
            .state(state)
            .build();
    return msalApp.getAuthorizationRequestUrl(params).toString();
  }

  public boolean validateState(String state) {
    String expected = pendingOauthState.getAndSet(null);
    return expected != null && expected.equals(state);
  }

  public void handleCallback(String code) {
    try {
      AuthorizationCodeParameters params =
          AuthorizationCodeParameters.builder(code, new URI(properties.redirectUri()))
              .scopes(SCOPES)
              .build();
      msalApp.acquireToken(params).get();
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Token exchange failed: " + e.getMessage());
    }
  }

  public boolean isConnected() {
    return !msalApp.getAccounts().join().isEmpty();
  }

  public String getValidAccessToken() {
    IAuthenticationResult cached = cachedToken.get();
    if (cached != null && tokenExpiresAfter(cached, TOKEN_EXPIRY_BUFFER_MINUTES)) {
      return cached.accessToken();
    }
    try {
      Set<IAccount> accounts = msalApp.getAccounts().get();
      if (accounts.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Outlook not connected");
      }
      SilentParameters params =
          SilentParameters.builder(SCOPES, accounts.iterator().next()).build();
      IAuthenticationResult result = msalApp.acquireTokenSilently(params).get();
      cachedToken.set(result);
      return result.accessToken();
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      // MsalInteractionRequiredException means the stored refresh token can't satisfy the
      // requested scopes (e.g. Mail.ReadWrite was added after the user last consented).
      // Clear the in-memory cache so isConnected() returns false and the UI prompts reconnect.
      if (isCausedByInteractionRequired(e)) {
        cachedToken.set(null);
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Outlook reconnection required: permissions have changed");
      }
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Token refresh failed: " + e.getMessage());
    }
  }

  private boolean isCausedByInteractionRequired(Throwable t) {
    while (t != null) {
      if (t instanceof MsalInteractionRequiredException) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  private boolean tokenExpiresAfter(IAuthenticationResult result, int bufferMinutes) {
    return result
        .expiresOnDate()
        .toInstant()
        .isAfter(Instant.now().plus(bufferMinutes, ChronoUnit.MINUTES));
  }
}
