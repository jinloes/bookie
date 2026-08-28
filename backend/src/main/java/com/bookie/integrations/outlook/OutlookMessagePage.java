package com.bookie.integrations.outlook;

import java.util.List;

public record OutlookMessagePage(List<OutlookMessage> messages, String nextLink) {

  public OutlookMessagePage {
    messages = messages == null ? List.of() : List.copyOf(messages);
  }
}
