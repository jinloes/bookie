package com.bookie.controller;

import com.bookie.model.Expense;
import com.bookie.model.PendingExpense;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.service.PendingExpenseService;
import com.bookie.service.SseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/pending-expenses")
@RequiredArgsConstructor
public class PendingExpenseController {

  private final PendingExpenseService pendingExpenseService;
  private final SseService sseService;

  @GetMapping
  public List<PendingExpense> list() {
    return pendingExpenseService.findAll();
  }

  @GetMapping("/events")
  public SseEmitter subscribe() {
    return sseService.subscribe();
  }

  @PostMapping("/{id}/save")
  public Expense save(@PathVariable Long id, @RequestBody SavePendingExpenseRequest request) {
    return pendingExpenseService.saveAsExpense(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void dismiss(@PathVariable Long id) {
    pendingExpenseService.dismiss(id);
  }
}
