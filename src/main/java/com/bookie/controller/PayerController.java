package com.bookie.controller;

import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.service.PayerService;
import com.bookie.service.PropertyHistoryService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payers")
@RequiredArgsConstructor
public class PayerController {

  private final PayerService payerService;
  private final PropertyHistoryService propertyHistoryService;

  @GetMapping
  public List<Payer> getAll() {
    return payerService.findAll();
  }

  @GetMapping("/{id}")
  public Payer getById(@PathVariable Long id) {
    return payerService.findById(id);
  }

  @PostMapping
  public Payer create(@RequestBody Payer payer) {
    return payerService.save(payer);
  }

  @PutMapping("/{id}")
  public Payer update(@PathVariable Long id, @RequestBody Payer payer) {
    return payerService.update(id, payer);
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
  public List<Map<String, String>> getTypes() {
    return Arrays.stream(PayerType.values())
        .map(t -> Map.of("value", t.name(), "label", t.label))
        .toList();
  }
}
