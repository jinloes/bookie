package com.bookie.catalog.counterparty.api;

import com.bookie.catalog.api.EnumOptionResponse;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
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

  private final CounterpartyCatalog counterpartyCatalog;

  @Operation(operationId = "getPayers")
  @GetMapping
  public List<PayerResponse> getAll() {
    return counterpartyCatalog.findAll().stream().map(PayerResponse::from).toList();
  }

  @Operation(operationId = "getPayerById")
  @GetMapping("/{id}")
  public PayerResponse getById(@PathVariable Long id) {
    return PayerResponse.from(counterpartyCatalog.findById(id));
  }

  @Operation(operationId = "createPayer")
  @PostMapping
  public PayerResponse create(@RequestBody UpsertPayerRequest request) {
    return PayerResponse.from(counterpartyCatalog.create(request.toCommand()));
  }

  @Operation(operationId = "updatePayer")
  @PutMapping("/{id}")
  public PayerResponse update(@PathVariable Long id, @RequestBody UpsertPayerRequest request) {
    return PayerResponse.from(counterpartyCatalog.update(id, request.toCommand()));
  }

  @Operation(operationId = "deletePayer")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    counterpartyCatalog.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(operationId = "getPayerTypes")
  @GetMapping("/types")
  public List<EnumOptionResponse> getTypes() {
    return Arrays.stream(PayerType.values())
        .map(t -> new EnumOptionResponse(t.name(), t.label))
        .toList();
  }
}
