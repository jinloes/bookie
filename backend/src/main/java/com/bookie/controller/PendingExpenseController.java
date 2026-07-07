package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.model.SavePendingIncomeRequest;
import com.bookie.service.EmailParseQueueService;
import com.bookie.service.InboxSaveOrchestrator;
import com.bookie.service.PendingExpenseService;
import com.bookie.service.ReceiptParseQueueService;
import com.bookie.service.SseService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/pending-expenses")
@RequiredArgsConstructor
public class PendingExpenseController {

  private final PendingExpenseService pendingExpenseService;
  private final InboxSaveOrchestrator orchestrator;
  private final SseService sseService;
  private final EmailParseQueueService emailParseQueueService;
  private final ReceiptParseQueueService receiptParseQueueService;

  @GetMapping
  public List<ApiResponses.PendingExpenseResponse> list() {
    return pendingExpenseService.findAll().stream()
        .map(ApiResponses.PendingExpenseResponse::from)
        .toList();
  }

  @GetMapping("/events")
  public SseEmitter subscribe() {
    return sseService.subscribe();
  }

  @PostMapping("/{id}/save")
  public ApiResponses.ExpenseResponse save(
      @PathVariable Long id, @Valid @RequestBody SavePendingExpenseRequest request) {
    return ApiResponses.ExpenseResponse.from(orchestrator.saveAsExpense(id, request));
  }

  @PostMapping("/{id}/save-income")
  public ApiResponses.IncomeResponse saveAsIncome(
      @PathVariable Long id, @Valid @RequestBody SavePendingIncomeRequest request) {
    return ApiResponses.IncomeResponse.from(orchestrator.saveAsIncome(id, request));
  }

  @PostMapping("/{id}/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void retry(@PathVariable Long id) {
    PendingExpense pending = pendingExpenseService.resetForRetry(id);
    if (pending.getSourceType() == ExpenseSource.OUTLOOK_EMAIL) {
      emailParseQueueService.processEmail(pending.getId(), pending.getSourceId());
    } else if (pending.getSourceType() == ExpenseSource.RECEIPT) {
      receiptParseQueueService.processReceipt(pending.getId(), pending.getSourceId());
    }
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void dismiss(@PathVariable Long id) {
    pendingExpenseService.dismiss(id);
  }
}
