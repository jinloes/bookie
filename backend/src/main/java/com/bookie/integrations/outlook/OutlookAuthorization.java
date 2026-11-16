package com.bookie.integrations.outlook;

public interface OutlookAuthorization {

  String getAuthorizationUrl();

  default String getAuthorizationUrl(boolean desktopClient) {
    return getAuthorizationUrl();
  }

  default boolean consumeDesktopClientFlag() {
    return false;
  }

  boolean validateState(String state);

  void handleCallback(String code);

  boolean isConnected();

  String getValidAccessToken();
}
