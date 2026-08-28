package com.bookie.integrations.outlook;

import org.apache.commons.lang3.StringUtils;

public record OutlookMessageIdentity(String legacyId, String immutableId) {

  public OutlookMessageIdentity {
    if (StringUtils.isAllBlank(legacyId, immutableId)) {
      throw new IllegalArgumentException("At least one Outlook message ID is required");
    }
  }

  public static OutlookMessageIdentity unresolved(String messageId) {
    return new OutlookMessageIdentity(messageId, null);
  }

  public String durableId() {
    return StringUtils.defaultIfBlank(immutableId, legacyId);
  }
}
