package com.bookie.catalog.classification.application;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.property.domain.Property;
import java.util.List;
import java.util.Optional;

/** Port for learned property, counterparty, activity, and category history. */
public interface ClassificationHistory {

  void storeKeywords(String sourceId, List<String> keywords);

  void record(ConfirmedClassification classification);

  List<HistoryHint> getPropertyHints(String payerName, List<String> keywords);

  List<CounterpartyKeywordHistory> getAllPayerKeywords();

  List<PropertyKeywordHistory> getAllPropertyKeywords();

  List<HistoryHint> getPayerHints(List<String> keywords);

  List<HistoryHint> getActivityHints(List<String> keywords);

  List<HistoryHint> getFinancialCategoryHints(
      Long activityId, TransactionDirection direction, List<String> keywords);

  Optional<Property> findMostLikelyPropertyForCounterparty(Long counterpartyId);
}
