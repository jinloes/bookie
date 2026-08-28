package com.bookie.integrations.outlook;

import com.microsoft.kiota.BaseRequestConfiguration;

public final class GraphImmutableIdHeaders {

  public static final String HEADER_NAME = "Prefer";
  public static final String HEADER_VALUE = "IdType=\"ImmutableId\"";

  private GraphImmutableIdHeaders() {}

  public static void apply(BaseRequestConfiguration configuration) {
    configuration.headers.tryAdd(HEADER_NAME, HEADER_VALUE);
  }
}
