package com.bookie.catalog.activity.api;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.api.EnumOptionResponse;
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

  private final ActivityCatalog activityCatalog;

  @Operation(operationId = "getFinancialActivities")
  @GetMapping
  public List<FinancialActivityResponse> getAll() {
    return activityCatalog.findAll().stream().map(FinancialActivityResponse::from).toList();
  }

  @Operation(operationId = "createFinancialActivity")
  @PostMapping
  public FinancialActivityResponse create(
      @Valid @RequestBody UpsertFinancialActivityRequest request) {
    return FinancialActivityResponse.from(activityCatalog.create(request.toCommand()));
  }

  @Operation(operationId = "updateFinancialActivity")
  @PutMapping("/{id}")
  public FinancialActivityResponse update(
      @PathVariable Long id, @Valid @RequestBody UpsertFinancialActivityRequest request) {
    return FinancialActivityResponse.from(activityCatalog.update(id, request.toCommand()));
  }

  @Operation(operationId = "getFinancialActivityTypes")
  @GetMapping("/types")
  public List<EnumOptionResponse> getActivityTypes() {
    return Arrays.stream(ActivityType.values())
        .map(type -> new EnumOptionResponse(type.name(), type.label))
        .toList();
  }

  @Operation(operationId = "getTaxTreatments")
  @GetMapping("/tax-treatments")
  public List<EnumOptionResponse> getTaxTreatments() {
    return Arrays.stream(TaxTreatment.values())
        .map(treatment -> new EnumOptionResponse(treatment.name(), treatment.label))
        .toList();
  }
}
