package com.bookie.controller;

import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.Income;
import com.bookie.model.Property;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.service.IncomeService;
import com.bookie.service.PropertyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incomes")
@RequiredArgsConstructor
public class IncomeController {

  private final IncomeService incomeService;
  private final PropertyService propertyService;

  @GetMapping
  public List<ApiResponses.IncomeResponse> getAll() {
    return incomeService.findAll().stream().map(ApiResponses.IncomeResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ApiResponses.IncomeResponse getById(@PathVariable Long id) {
    return ApiResponses.IncomeResponse.from(incomeService.findById(id));
  }

  @PostMapping
  public ApiResponses.IncomeResponse create(@RequestBody CreateIncomeRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    var income =
        Income.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .source(req.source())
            .property(property)
            .sourceType(req.sourceType())
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .build();
    return ApiResponses.IncomeResponse.from(incomeService.save(income));
  }

  @PutMapping("/{id}")
  public ApiResponses.IncomeResponse update(
      @PathVariable Long id, @RequestBody UpdateIncomeRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    var updated =
        Income.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .source(req.source())
            .property(property)
            .build();
    return ApiResponses.IncomeResponse.from(incomeService.update(id, updated));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    incomeService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/total")
  public ApiResponses.TotalAmountResponse getTotal() {
    return new ApiResponses.TotalAmountResponse(incomeService.getTotalIncome());
  }
}
