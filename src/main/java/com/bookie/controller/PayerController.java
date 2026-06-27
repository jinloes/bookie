package com.bookie.controller;

import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.service.PayerService;
import com.bookie.service.PropertyHistoryService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payers")
@RequiredArgsConstructor
public class PayerController {

  private final PayerService payerService;
  private final PropertyHistoryService propertyHistoryService;

  public record PayerUpsertRequest(
      String name, PayerType type, List<String> aliases, List<String> accounts) {}

  @GetMapping
  public List<ApiResponses.PayerResponse> getAll() {
    return payerService.findAll().stream().map(ApiResponses.PayerResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ApiResponses.PayerResponse getById(@PathVariable Long id) {
    return ApiResponses.PayerResponse.from(payerService.findById(id));
  }

  @PostMapping
  public ApiResponses.PayerResponse create(@RequestBody PayerUpsertRequest request) {
    var payer =
        Payer.builder()
            .name(request.name())
            .type(request.type())
            .aliases(request.aliases() != null ? request.aliases() : List.of())
            .accounts(
                request.accounts() != null ? new HashSet<>(request.accounts()) : new HashSet<>())
            .build();
    return ApiResponses.PayerResponse.from(payerService.save(payer));
  }

  @PutMapping("/{id}")
  public ApiResponses.PayerResponse update(
      @PathVariable Long id, @RequestBody PayerUpsertRequest request) {
    var payer =
        Payer.builder()
            .name(request.name())
            .type(request.type())
            .aliases(request.aliases() != null ? request.aliases() : List.of())
            .accounts(
                request.accounts() != null ? new HashSet<>(request.accounts()) : new HashSet<>())
            .build();
    return ApiResponses.PayerResponse.from(payerService.update(id, payer));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    payerService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/keywords")
  public List<EmailKeywordPayerHistory> getKeywords() {
    return propertyHistoryService.getAllPayerKeywords();
  }

  @GetMapping("/types")
  public List<ApiResponses.EnumOptionResponse> getTypes() {
    return Arrays.stream(PayerType.values())
        .map(t -> new ApiResponses.EnumOptionResponse(t.name(), t.label))
        .toList();
  }
}
