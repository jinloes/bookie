package com.bookie.catalog.classification.api;

import com.bookie.catalog.classification.application.CounterpartyKeywordHistory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailKeywordPayerHistory")
public record CounterpartyKeywordResponse(
    Long id, String keyword, CounterpartyKeywordRefResponse payer, int occurrences, Long version) {

  public record CounterpartyKeywordRefResponse(Long id, String name) {}

  public static CounterpartyKeywordResponse from(CounterpartyKeywordHistory history) {
    CounterpartyKeywordRefResponse payer =
        history.counterpartyId() == null && history.counterpartyName() == null
            ? null
            : new CounterpartyKeywordRefResponse(
                history.counterpartyId(), history.counterpartyName());
    return new CounterpartyKeywordResponse(
        history.id(), history.keyword(), payer, history.occurrences(), history.version());
  }
}
