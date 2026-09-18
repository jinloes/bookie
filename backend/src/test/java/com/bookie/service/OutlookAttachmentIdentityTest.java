package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutlookAttachmentIdentityTest {

  @Nested
  class SourceId {

    @Test
    void isStableBoundedAndSeparatesMessageAndAttachmentInputs() {
      String first = OutlookAttachmentIdentity.sourceId("message-a", "attachment-a");

      assertThat(first)
          .isEqualTo(OutlookAttachmentIdentity.sourceId("message-a", "attachment-a"))
          .startsWith("outlook-attachment:")
          .hasSize(83);
      assertThat(first)
          .isNotEqualTo(OutlookAttachmentIdentity.sourceId("message-a", "attachment-b"))
          .isNotEqualTo(OutlookAttachmentIdentity.sourceId("message-b", "attachment-a"));
      assertThat(OutlookAttachmentIdentity.sourceId("ab", "c"))
          .isNotEqualTo(OutlookAttachmentIdentity.sourceId("a", "bc"));
    }

    @Test
    void rejectsBlankIdentityParts() {
      assertThatThrownBy(() -> OutlookAttachmentIdentity.sourceId("", "attachment"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> OutlookAttachmentIdentity.sourceId("message", " "))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class Label {

    @Test
    void combinesSubjectAndAttachmentWithBlankFallbacks() {
      assertThat(OutlookAttachmentIdentity.label("PayPal receipts", "receipt-1.pdf"))
          .isEqualTo("PayPal receipts - receipt-1.pdf");
      assertThat(OutlookAttachmentIdentity.label("", "receipt-1.pdf")).isEqualTo("receipt-1.pdf");
      assertThat(OutlookAttachmentIdentity.label("PayPal receipts", null))
          .isEqualTo("PayPal receipts - Attachment");
    }
  }
}
