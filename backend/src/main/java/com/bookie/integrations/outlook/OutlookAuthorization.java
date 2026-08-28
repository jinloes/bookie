package com.bookie.integrations.outlook;

public interface OutlookAuthorization {

  String getAuthorizationUrl();

  boolean validateState(String state);

  void handleCallback(String code);

  boolean isConnected();

  String getValidAccessToken();
}
