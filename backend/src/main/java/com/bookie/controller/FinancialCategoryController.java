package com.bookie.controller;

import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.compatibility.api.LegacyFinancialCategoryMapper;
import com.bookie.model.TransactionDirection;
import com.bookie.service.FinancialCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/financial-categories")
@RequiredArgsConstructor
public class FinancialCategoryController {

  private final FinancialCategoryService financialCategoryService;

  @Operation(operationId = "getFinancialCategories")
  @GetMapping
  public List<FinancialCategoryResponse> getAll(
      @RequestParam(required = false) TransactionDirection direction,
      @RequestParam(required = false) Long activityId) {
    return financialCategoryService.findCompatible(direction, activityId).stream()
        .map(LegacyFinancialCategoryMapper::toResponse)
        .toList();
  }
}
