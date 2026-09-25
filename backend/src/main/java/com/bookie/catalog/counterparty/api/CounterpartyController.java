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
@RequestMapping("/api/counterparties")
@RequiredArgsConstructor
public class CounterpartyController {

  private final CounterpartyCatalog counterpartyCatalog;

  @Operation(operationId = "getCounterparties")
  @GetMapping
  public List<CounterpartyResponse> getAll() {
    return counterpartyCatalog.findAll().stream().map(CounterpartyResponse::from).toList();
  }

  @Operation(operationId = "getCounterpartyById")
  @GetMapping("/{id}")
  public CounterpartyResponse getById(@PathVariable Long id) {
    return CounterpartyResponse.from(counterpartyCatalog.findById(id));
  }

  @Operation(operationId = "createCounterparty")
  @PostMapping
  public CounterpartyResponse create(@RequestBody UpsertCounterpartyRequest request) {
    return CounterpartyResponse.from(counterpartyCatalog.create(request.toCommand()));
  }

  @Operation(operationId = "updateCounterparty")
  @PutMapping("/{id}")
  public CounterpartyResponse update(
      @PathVariable Long id, @RequestBody UpsertCounterpartyRequest request) {
    return CounterpartyResponse.from(counterpartyCatalog.update(id, request.toCommand()));
  }

  @Operation(operationId = "deleteCounterparty")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    counterpartyCatalog.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(operationId = "getCounterpartyTypes")
  @GetMapping("/types")
  public List<EnumOptionResponse> getTypes() {
    return Arrays.stream(CounterpartyTypeDto.values())
        .map(type -> new EnumOptionResponse(type.name(), type.label))
        .toList();
  }
}
