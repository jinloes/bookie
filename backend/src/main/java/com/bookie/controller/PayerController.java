package com.bookie.controller;

import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.PayerType;
import com.bookie.model.UpsertPayerRequest;
import com.bookie.service.PayerService;
import com.bookie.service.PropertyHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Arrays;
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
@RequestMapping("/api/payers")
@RequiredArgsConstructor
public class PayerController {

  private final PayerService payerService;
  private final PropertyHistoryService propertyHistoryService;

  @Operation(operationId = "getPayers")
  @GetMapping
  public List<ApiResponses.PayerResponse> getAll() {
    return payerService.findAll().stream().map(ApiResponses.PayerResponse::from).toList();
  }

  @Operation(operationId = "getPayerById")
  @GetMapping("/{id}")
  public ApiResponses.PayerResponse getById(@PathVariable Long id) {
    return ApiResponses.PayerResponse.from(payerService.findById(id));
  }

  @Operation(operationId = "createPayer")
  @PostMapping
  public ApiResponses.PayerResponse create(@RequestBody UpsertPayerRequest req) {
    return ApiResponses.PayerResponse.from(payerService.create(req));
  }

  @Operation(operationId = "updatePayer")
  @PutMapping("/{id}")
  public ApiResponses.PayerResponse update(
      @PathVariable Long id, @RequestBody UpsertPayerRequest req) {
    return ApiResponses.PayerResponse.from(payerService.update(id, req));
  }

  @Operation(operationId = "deletePayer")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    payerService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(operationId = "getPayerKeywords")
  @GetMapping("/keywords")
  public List<EmailKeywordPayerHistory> getKeywords() {
    return propertyHistoryService.getAllPayerKeywords();
  }

  @Operation(operationId = "getPayerTypes")
  @GetMapping("/types")
  public List<ApiResponses.EnumOptionResponse> getTypes() {
    return Arrays.stream(PayerType.values())
        .map(t -> new ApiResponses.EnumOptionResponse(t.name(), t.label))
        .toList();
  }
}
