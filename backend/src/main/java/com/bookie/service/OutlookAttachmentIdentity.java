package com.bookie.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.commons.lang3.StringUtils;

public final class OutlookAttachmentIdentity {

  private static final String PREFIX = "outlook-attachment:";

  private OutlookAttachmentIdentity() {}

  public static String sourceId(String messageId, String attachmentId) {
    if (StringUtils.isAnyBlank(messageId, attachmentId)) {
      throw new IllegalArgumentException("Outlook message and attachment IDs must not be blank");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(messageId.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(attachmentId.getBytes(StandardCharsets.UTF_8));
      return PREFIX + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  public static String label(String subject, String attachmentName) {
    String name = StringUtils.defaultIfBlank(attachmentName, "Attachment");
    return StringUtils.isBlank(subject) ? name : subject + " - " + name;
  }
}
