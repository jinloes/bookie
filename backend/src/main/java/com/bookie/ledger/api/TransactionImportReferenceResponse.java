package com.bookie.ledger.api;

import com.bookie.ledger.domain.TransactionImportReference;

public record TransactionImportReferenceResponse(
    Long id, String origin, String externalId, String sourceLabel) {

  static TransactionImportReferenceResponse from(TransactionImportReference reference) {
    return new TransactionImportReferenceResponse(
        reference.getId(),
        reference.getOrigin(),
        reference.getExternalId(),
        reference.getSourceLabel());
  }
}
