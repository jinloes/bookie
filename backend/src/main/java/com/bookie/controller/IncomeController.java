package com.bookie.controller;

import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.service.IncomeService;
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

  @GetMapping
  public List<ApiResponses.IncomeResponse> getAll() {
    // There is no pagination UI in the frontend — it always expects the complete list (used
    // for client-side year filtering, totals, and CSV export), so we return every income
    // sorted newest-first rather than truncating to a default page size.
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

  @PostMapping("/import/venmo")
  public ApiResponses.VenmoIncomeImportResponse importVenmoCsv(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "payer", required = false) String payer,
      @RequestParam(value = "payerId", required = false) String payerId,
      @RequestParam(value = "senderName", required = false) String senderName,
      @RequestParam(value = "propertyId", required = false) String propertyId)
      throws IOException {
    String selectedPayer = payer;
    if (selectedPayer == null) {
      selectedPayer = payerId;
    }
    if (selectedPayer == null) {
      selectedPayer = senderName;
    }
    return incomeService.importVenmoCsv(
        file.getBytes(), file.getOriginalFilename(), selectedPayer, propertyId);
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

  @GetMapping("/pending")
  public List<ApiResponses.PendingIncomeResponse> getPending() {
    return incomeService.findAllPending().stream()
        .map(ApiResponses.PendingIncomeResponse::from)
        .toList();
  }

  @GetMapping("/pending/{id}")
  public ApiResponses.PendingIncomeResponse getPendingById(@PathVariable Long id) {
    return ApiResponses.PendingIncomeResponse.from(incomeService.findPendingById(id));
  }

  @PostMapping("/pending/{id}/accept")
  public ApiResponses.IncomeResponse acceptPending(
      @PathVariable Long id, @Valid @RequestBody UpdateIncomeRequest req) {
    return ApiResponses.IncomeResponse.from(incomeService.acceptPendingIncome(id, req));
  }

  @DeleteMapping("/pending/{id}")
  public ResponseEntity<Void> rejectPending(@PathVariable Long id) {
    incomeService.rejectPendingIncome(id);
    return ResponseEntity.noContent().build();
  }
}
