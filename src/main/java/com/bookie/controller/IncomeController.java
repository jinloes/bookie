package com.bookie.controller;

import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.service.IncomeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incomes")
@RequiredArgsConstructor
public class IncomeController {

  private final IncomeService incomeService;

  @GetMapping
  public List<ApiResponses.IncomeResponse> getAll() {
    return incomeService.findAll().stream().map(ApiResponses.IncomeResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ApiResponses.IncomeResponse getById(@PathVariable Long id) {
    return ApiResponses.IncomeResponse.from(incomeService.findById(id));
  }

  @PostMapping
  public ApiResponses.IncomeResponse create(@Valid @RequestBody CreateIncomeRequest req) {
    return ApiResponses.IncomeResponse.from(incomeService.create(req));
  }

  @PutMapping("/{id}")
  public ApiResponses.IncomeResponse update(
      @PathVariable Long id, @Valid @RequestBody UpdateIncomeRequest req) {
    return ApiResponses.IncomeResponse.from(incomeService.update(id, req));
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
