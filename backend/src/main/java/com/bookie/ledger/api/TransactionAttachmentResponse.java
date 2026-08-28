package com.bookie.ledger.api;

import com.bookie.ledger.domain.TransactionAttachment;

public record TransactionAttachmentResponse(
    Long id, String storageProvider, String externalId, String fileName, String sha256) {

  static TransactionAttachmentResponse from(TransactionAttachment attachment) {
    return new TransactionAttachmentResponse(
        attachment.getId(),
        attachment.getStorageProvider(),
        attachment.getExternalId(),
        attachment.getFileName(),
        attachment.getSha256());
  }
}
