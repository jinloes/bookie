package com.bookie.ledger.api;

import com.bookie.ledger.application.LedgerTransactionService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private final LedgerTransactionService transactionService;

  @Operation(operationId = "getTransactions")
  @GetMapping
  public List<TransactionResponse> getAll() {
    return transactionService.findAll().stream().map(TransactionResponse::from).toList();
  }

  @Operation(operationId = "getTransactionById")
  @GetMapping("/{id}")
  public TransactionResponse getById(@PathVariable Long id) {
    return TransactionResponse.from(transactionService.findById(id));
  }

  @Operation(operationId = "createTransaction")
  @PostMapping
  public TransactionResponse create(@Valid @RequestBody CreateTransactionRequest request) {
    return TransactionResponse.from(transactionService.create(request.toInput()));
  }

  @Operation(operationId = "updateTransaction")
  @PutMapping("/{id}")
  public TransactionResponse update(
      @PathVariable Long id, @Valid @RequestBody UpdateTransactionRequest request) {
    return TransactionResponse.from(
        transactionService.update(id, request.version(), request.toInput()));
  }

  @Operation(operationId = "deleteTransaction")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam("version") Long version) {
    transactionService.delete(id, version);
    return ResponseEntity.noContent().build();
  }
}
