package com.bookie.catalog.classification.api;

import com.bookie.catalog.classification.application.ClassificationHistory;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ClassificationHistoryController {

  private final ClassificationHistory classificationHistory;

  @Operation(operationId = "getPropertyKeywords")
  @GetMapping("/api/properties/keywords")
  public List<PropertyKeywordResponse> getPropertyKeywords() {
    return classificationHistory.getAllPropertyKeywords().stream()
        .map(PropertyKeywordResponse::from)
        .toList();
  }

  @Operation(operationId = "getCounterpartyKeywords")
  @GetMapping("/api/counterparties/keywords")
  public List<CounterpartyKeywordResponse> getCounterpartyKeywords() {
    return classificationHistory.getAllPayerKeywords().stream()
        .map(CounterpartyKeywordResponse::from)
        .toList();
  }
}
