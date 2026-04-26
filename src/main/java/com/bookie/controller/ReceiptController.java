package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.ReceiptDto;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.service.PendingExpenseService;
import com.bookie.service.ReceiptParseQueueService;
import com.bookie.service.ReceiptService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
    requireConnection();
    UploadReceiptResponse response =
        receiptService.uploadReceipt(file.getOriginalFilename(), file.getBytes());
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<List<ReceiptDto>> listReceipts() {
    requireConnection();
    return ResponseEntity.ok(receiptService.listReceipts());
  }

  @GetMapping("/{itemId}/download")
  public ResponseEntity<InputStreamResource> download(@PathVariable String itemId) {
    requireConnection();
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
    requireConnection();
    String filename = receiptService.getReceiptName(itemId);
    var result = pendingExpenseService.findOrCreate(itemId, ExpenseSource.RECEIPT, filename);
    if (!result.alreadyProcessing()) {
      receiptParseQueueService.processReceipt(result.pending().getId(), itemId);
    }
    return ResponseEntity.ok(
        Map.of("id", result.pending().getId(), "status", result.pending().getStatus().name()));
  }

  @DeleteMapping("/{itemId}")
  public ResponseEntity<Void> delete(@PathVariable String itemId) {
    requireConnection();
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

  private void requireConnection() {
    if (!receiptService.isConnected()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }
  }
}
