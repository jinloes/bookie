package com.bookie.ledger.api;

import com.bookie.catalog.activity.api.FinancialActivityResponse;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record TransactionResponse(
    Long id,
    BigDecimal amount,
    TransactionDirection direction,
    LocalDate date,
    String description,
    FinancialActivityResponse activity,
    Long propertyId,
    NeutralCategoryResponse category,
    Long counterpartyId,
    List<TransactionAttachmentResponse> attachments,
    List<TransactionImportReferenceResponse> importReferences,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version) {

  public static TransactionResponse from(FinancialTransaction transaction) {
    return new TransactionResponse(
        transaction.getId(),
        transaction.getAmount(),
        transaction.getDirection(),
        transaction.getDate(),
        transaction.getDescription(),
        FinancialActivityResponse.from(transaction.getActivity()),
        transaction.getActivity().getProperty() == null
            ? null
            : transaction.getActivity().getProperty().getId(),
        NeutralCategoryResponse.from(transaction.getNeutralCategory()),
        transaction.getCounterpartyId(),
        transaction.getAttachments().stream()
            .sorted(Comparator.comparing(value -> value.getId() == null ? 0L : value.getId()))
            .map(TransactionAttachmentResponse::from)
            .toList(),
        transaction.getImportReferences().stream()
            .sorted(Comparator.comparing(value -> value.getId() == null ? 0L : value.getId()))
            .map(TransactionImportReferenceResponse::from)
            .toList(),
        transaction.getCreatedAt(),
        transaction.getUpdatedAt(),
        transaction.getVersion());
  }
}
