package com.bookie.catalog.classification.api;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.CounterpartyKeywordHistory;
import com.bookie.catalog.classification.application.PropertyKeywordHistory;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Preserves the legacy history routes while classification history remains compatibility
 * infrastructure.
 */
@RestController
@RequiredArgsConstructor
public class LegacyClassificationHistoryController {

  private final ClassificationHistory classificationHistory;

  @Operation(operationId = "getPropertyKeywords")
  @GetMapping("/api/properties/keywords")
  public List<PropertyKeywordHistory> getPropertyKeywords() {
    return classificationHistory.getAllPropertyKeywords();
  }

  @Operation(operationId = "getPayerKeywords")
  @GetMapping("/api/payers/keywords")
  public List<CounterpartyKeywordHistory> getPayerKeywords() {
    return classificationHistory.getAllPayerKeywords();
  }
}
