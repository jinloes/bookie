package com.bookie.service;

import com.bookie.config.OutlookProperties;
import com.bookie.repository.OutlookTokenRepository;
import com.microsoft.aad.msal4j.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MsalTokenService {

  private static final Set<String> SCOPES =
      Set.of("Mail.Read", "Files.ReadWrite", "offline_access");

  private final OutlookProperties properties;
  private final PublicClientApplication msalApp;

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
    AuthorizationRequestUrlParameters params =
        AuthorizationRequestUrlParameters.builder(properties.redirectUri(), SCOPES)
            .responseMode(ResponseMode.QUERY)
            .build();
    return msalApp.getAuthorizationRequestUrl(params).toString();
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
    try {
      Set<IAccount> accounts = msalApp.getAccounts().get();
      if (accounts.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Outlook not connected");
      }
      SilentParameters params =
          SilentParameters.builder(SCOPES, accounts.iterator().next()).build();
      return msalApp.acquireTokenSilently(params).get().accessToken();
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Token refresh failed: " + e.getMessage());
    }
  }
}
