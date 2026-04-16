package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.ReceiptDto;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.service.PendingExpenseService;
import com.bookie.service.ReceiptParseQueueService;
import com.bookie.service.ReceiptService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

  private final ReceiptService receiptService;
  private final ReceiptParseQueueService receiptParseQueueService;
  private final PendingExpenseService pendingExpenseService;

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UploadReceiptResponse> upload(@RequestParam("file") MultipartFile file)
      throws IOException {
    if (!receiptService.isConnected()) {
      return ResponseEntity.status(503).build();
    }
    UploadReceiptResponse response =
        receiptService.uploadReceipt(file.getOriginalFilename(), file.getBytes());
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<List<ReceiptDto>> listReceipts() {
    if (!receiptService.isConnected()) {
      return ResponseEntity.status(503).build();
    }
    return ResponseEntity.ok(receiptService.listReceipts());
  }

  @GetMapping("/{itemId}/download")
  public ResponseEntity<InputStreamResource> download(@PathVariable String itemId) {
    if (!receiptService.isConnected()) {
      return ResponseEntity.status(503).build();
    }
    String name = receiptService.getReceiptName(itemId);
    InputStream stream = receiptService.getReceiptContent(itemId);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"" + (name != null ? name : "receipt.pdf") + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(new InputStreamResource(stream));
  }

  @PostMapping("/{itemId}/parse")
  public ResponseEntity<Map<String, Object>> parse(@PathVariable String itemId) {
    if (!receiptService.isConnected()) {
      return ResponseEntity.status(503).build();
    }

    // Return existing entry only if already processing (avoid queuing the same receipt twice).
    // READY entries are dismissed and re-parsed so the latest extraction code is always used.
    Optional<PendingExpense> existing = pendingExpenseService.findBySourceId(itemId);
    if (existing.isPresent() && existing.get().getStatus() == PendingExpenseStatus.PROCESSING) {
      PendingExpense p = existing.get();
      return ResponseEntity.ok(Map.of("id", p.getId(), "status", p.getStatus().name()));
    }
    existing.ifPresent(e -> pendingExpenseService.dismiss(e.getId()));

    String filename = receiptService.getReceiptName(itemId);
    PendingExpense pending = pendingExpenseService.create(itemId, ExpenseSource.RECEIPT, filename);
    receiptParseQueueService.processReceipt(pending.getId(), itemId);

    return ResponseEntity.ok(Map.of("id", pending.getId(), "status", "PROCESSING"));
  }

  @DeleteMapping("/{itemId}")
  public ResponseEntity<Void> delete(@PathVariable String itemId) {
    if (!receiptService.isConnected()) {
      return ResponseEntity.status(503).build();
    }
    receiptService.deleteReceipt(itemId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/settings")
  public ResponseEntity<Map<String, String>> getSettings() {
    return ResponseEntity.ok(Map.of("folderBase", receiptService.getReceiptsFolderBase()));
  }

  @PutMapping("/settings")
  public ResponseEntity<Map<String, String>> updateSettings(@RequestBody Map<String, String> body) {
    String folderBase = body.get("folderBase");
    if (folderBase == null || folderBase.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    receiptService.updateReceiptsFolderBase(folderBase.trim());
    return ResponseEntity.ok(Map.of("folderBase", folderBase.trim()));
  }
}
