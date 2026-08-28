package com.bookie.controller;

import com.bookie.intake.compatibility.api.PendingIncomeResponse;
import com.bookie.ledger.compatibility.api.IncomeResponse;
import com.bookie.ledger.compatibility.api.TotalAmountResponse;
import com.bookie.ledger.compatibility.api.VenmoIncomeImportResponse;
import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.service.IncomeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/incomes")
@RequiredArgsConstructor
public class IncomeController {

  private final IncomeService incomeService;

  @Operation(operationId = "getIncomes")
  @GetMapping
  public List<IncomeResponse> getAll() {
    // There is no pagination UI in the frontend — it always expects the complete list (used
    // for client-side year filtering, totals, and CSV export), so we return every income
    // sorted newest-first rather than truncating to a default page size.
    return incomeService.findAll().stream().map(IncomeResponse::from).toList();
  }

  @Operation(operationId = "getIncomeById")
  @GetMapping("/{id}")
  public IncomeResponse getById(@PathVariable Long id) {
    return IncomeResponse.from(incomeService.findById(id));
  }

  @Operation(operationId = "createIncome")
  @PostMapping
  public IncomeResponse create(@Valid @RequestBody CreateIncomeRequest req) {
    return IncomeResponse.from(incomeService.create(req));
  }

  @Operation(operationId = "importVenmoIncomeCsv")
  @PostMapping("/import/venmo")
  public VenmoIncomeImportResponse importVenmoCsv(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "payer", required = false) String payer,
      @RequestParam(value = "payerId", required = false) String payerId,
      @RequestParam(value = "senderName", required = false) String senderName,
      @RequestParam(value = "propertyId", required = false) String propertyId,
      @RequestParam(value = "activityId", required = false) String activityId)
      throws IOException {
    String selectedPayer = payer;
    if (selectedPayer == null) {
      selectedPayer = payerId;
    }
    if (selectedPayer == null) {
      selectedPayer = senderName;
    }
    return incomeService.importVenmoCsv(
        file.getBytes(), file.getOriginalFilename(), selectedPayer, propertyId, activityId);
  }

  @Operation(operationId = "updateIncome")
  @PutMapping("/{id}")
  public IncomeResponse update(@PathVariable Long id, @Valid @RequestBody UpdateIncomeRequest req) {
    return IncomeResponse.from(incomeService.update(id, req));
  }

  @Operation(operationId = "deleteIncome")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    incomeService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(operationId = "getIncomesTotal")
  @GetMapping("/total")
  public TotalAmountResponse getTotal() {
    return new TotalAmountResponse(incomeService.getTotalIncome());
  }

  @Operation(operationId = "getPendingIncomes")
  @GetMapping("/pending")
  public List<PendingIncomeResponse> getPending() {
    return incomeService.findAllPending().stream().map(PendingIncomeResponse::from).toList();
  }

  @Operation(operationId = "getPendingIncomeById")
  @GetMapping("/pending/{id}")
  public PendingIncomeResponse getPendingById(@PathVariable Long id) {
    return PendingIncomeResponse.from(incomeService.findPendingById(id));
  }

  @Operation(operationId = "acceptPendingIncome")
  @PostMapping("/pending/{id}/accept")
  public IncomeResponse acceptPending(
      @PathVariable Long id, @Valid @RequestBody UpdateIncomeRequest req) {
    return IncomeResponse.from(incomeService.acceptPendingIncome(id, req));
  }

  @Operation(operationId = "rejectPendingIncome")
  @DeleteMapping("/pending/{id}")
  public ResponseEntity<Void> rejectPending(@PathVariable Long id) {
    incomeService.rejectPendingIncome(id);
    return ResponseEntity.noContent().build();
  }
}
