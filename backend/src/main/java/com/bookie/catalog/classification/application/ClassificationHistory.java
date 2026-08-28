package com.bookie.catalog.classification.application;

import com.bookie.catalog.property.domain.Property;
import com.bookie.model.HistoryHint;
import com.bookie.model.TransactionDirection;
import java.util.List;
import java.util.Optional;

/** Compatibility port for classification history while legacy history tables remain in service. */
public interface ClassificationHistory {

  void storeKeywords(String sourceId, List<String> keywords);

  void record(ConfirmedClassification classification);

  List<HistoryHint> getPropertyHints(String payerName, List<String> keywords);

  List<HistoryHint> getCategoryForPayer(String payerName);

  List<String> getAllPayerPropertyHints();

  List<CounterpartyKeywordHistory> getAllPayerKeywords();

  List<PropertyKeywordHistory> getAllPropertyKeywords();

  List<HistoryHint> getPayerHints(List<String> keywords);

  List<HistoryHint> getCategoryHints(List<String> keywords);

  List<HistoryHint> getActivityHints(List<String> keywords);

  List<HistoryHint> getFinancialCategoryHints(
      Long activityId, TransactionDirection direction, List<String> keywords);

  Optional<Property> findMostLikelyPropertyForCounterparty(Long counterpartyId);
}
