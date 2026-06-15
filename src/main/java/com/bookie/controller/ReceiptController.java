package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.ReceiptDto;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.service.PendingExpenseService;
import com.bookie.service.ReceiptParseQueueService;
import com.bookie.service.ReceiptService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
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
        receiptService.uploadReceipt(
            sanitizeUploadFilename(file.getOriginalFilename()), file.getBytes());
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
    ContentDisposition disposition =
        ContentDisposition.inline()
            .filename(StringUtils.defaultIfBlank(name, "receipt.pdf"), StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(resolveContentType(name))
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

  private String sanitizeUploadFilename(String originalFilename) {
    String cleaned =
        org.springframework.util.StringUtils.cleanPath(StringUtils.defaultString(originalFilename))
            .trim();
    String filename = org.springframework.util.StringUtils.getFilename(cleaned);
    if (StringUtils.isBlank(filename)
        || filename.contains("..")
        || filename.contains("/")
        || filename.contains("\\")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
    }
    return filename;
  }

  private MediaType resolveContentType(String name) {
    if (StringUtils.isBlank(name)) {
      return MediaType.APPLICATION_PDF;
    }
    String lower = name.toLowerCase();
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return MediaType.IMAGE_JPEG;
    }
    if (lower.endsWith(".png")) {
      return MediaType.IMAGE_PNG;
    }
    if (lower.endsWith(".gif")) {
      return MediaType.IMAGE_GIF;
    }
    return MediaType.APPLICATION_PDF;
  }
}
