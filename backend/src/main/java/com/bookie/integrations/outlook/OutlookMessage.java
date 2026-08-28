package com.bookie.integrations.outlook;

import java.time.OffsetDateTime;
import java.util.List;

public record OutlookMessage(
    OutlookMessageIdentity identity,
    String subject,
    String sender,
    OffsetDateTime receivedAt,
    String preview,
    String body,
    String parentFolderId,
    List<OutlookAttachment> attachments) {

  public OutlookMessage {
    attachments = attachments == null ? List.of() : List.copyOf(attachments);
  }
}
