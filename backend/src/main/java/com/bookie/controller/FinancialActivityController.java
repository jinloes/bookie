package com.bookie.controller;

import com.bookie.model.ActivityType;
import com.bookie.model.TaxTreatment;
import com.bookie.model.UpsertFinancialActivityRequest;
import com.bookie.service.FinancialActivityService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class FinancialActivityController {

  private final FinancialActivityService financialActivityService;

  @Operation(operationId = "getFinancialActivities")
  @GetMapping
  public List<ApiResponses.FinancialActivityResponse> getAll() {
    return financialActivityService.findAll().stream()
        .map(ApiResponses.FinancialActivityResponse::from)
        .toList();
  }

  @Operation(operationId = "createFinancialActivity")
  @PostMapping
  public ApiResponses.FinancialActivityResponse create(
      @Valid @RequestBody UpsertFinancialActivityRequest request) {
    return ApiResponses.FinancialActivityResponse.from(financialActivityService.create(request));
  }

  @Operation(operationId = "updateFinancialActivity")
  @PutMapping("/{id}")
  public ApiResponses.FinancialActivityResponse update(
      @PathVariable Long id, @Valid @RequestBody UpsertFinancialActivityRequest request) {
    return ApiResponses.FinancialActivityResponse.from(
        financialActivityService.update(id, request));
  }

  @Operation(operationId = "getFinancialActivityTypes")
  @GetMapping("/types")
  public List<ApiResponses.EnumOptionResponse> getActivityTypes() {
    return Arrays.stream(ActivityType.values())
        .map(type -> new ApiResponses.EnumOptionResponse(type.name(), type.label))
        .toList();
  }

  @Operation(operationId = "getTaxTreatments")
  @GetMapping("/tax-treatments")
  public List<ApiResponses.EnumOptionResponse> getTaxTreatments() {
    return Arrays.stream(TaxTreatment.values())
        .map(treatment -> new ApiResponses.EnumOptionResponse(treatment.name(), treatment.label))
        .toList();
  }
}
