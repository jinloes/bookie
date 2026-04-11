package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.FolderSetting;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.service.EmailParseQueueService;
import com.bookie.service.MsalTokenService;
import com.bookie.service.OutlookService;
import com.bookie.service.OutlookService.FolderInfo;
import com.bookie.service.PendingExpenseService;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
@RestController
@RequestMapping("/api/outlook")
@RequiredArgsConstructor
public class OutlookController {

  private final OutlookService outlookService;
  private final MsalTokenService msalTokenService;
  private final PendingExpenseService pendingExpenseService;
  private final EmailParseQueueService emailParseQueueService;

  @GetMapping("/connect")
  public RedirectView connect() {
    return new RedirectView(msalTokenService.getAuthorizationUrl());
  }

  @GetMapping("/callback")
  public RedirectView callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String error,
      @RequestParam(value = "error_description", required = false) String errorDescription) {
    if (error != null) {
      log.error("Outlook authorization failed: {} - {}", error, errorDescription);
      return new RedirectView("/?outlookError=" + error);
    }
    msalTokenService.handleCallback(code);
    return new RedirectView("/");
  }

  @GetMapping("/status")
  public Map<String, Boolean> status() {
    return Map.of("connected", msalTokenService.isConnected());
  }

  @GetMapping("/emails/rental")
  public OutlookEmailsPage getRentalEmails(
      @RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer year) {
    return outlookService.getRentalEmails(page, year != null ? year : Year.now().getValue());
  }

  @GetMapping("/folders/available")
  public List<FolderInfo> getAvailableFolders() {
    return outlookService.getAvailableFolders();
  }

  @GetMapping("/settings/folders")
  public List<FolderSetting> getConfiguredFolderSettings() {
    return outlookService.getConfiguredFolderSettings();
  }

  @PutMapping("/settings/folders")
  public List<FolderSetting> updateConfiguredFolderSettings(
      @RequestBody Map<String, List<FolderSetting>> body) {
    List<FolderSetting> folderSettings = body.getOrDefault("folderSettings", List.of());
    outlookService.updateConfiguredFolderSettings(folderSettings);
    return folderSettings;
  }

  @PostMapping("/emails/{messageId}/parse")
  public Map<String, Object> parseEmail(
      @PathVariable String messageId, @RequestBody Map<String, String> body) {
    Optional<PendingExpense> existing = pendingExpenseService.findBySourceId(messageId);
    // Return existing entry if already queued or ready; dismiss and re-queue if previously failed
    if (existing.isPresent() && existing.get().getStatus() != PendingExpenseStatus.FAILED) {
      PendingExpense e = existing.get();
      return Map.of("id", e.getId(), "status", e.getStatus().name());
    }
    existing.ifPresent(failed -> pendingExpenseService.dismiss(failed.getId()));
    String subject = body.getOrDefault("subject", "");
    PendingExpense pending =
        pendingExpenseService.create(messageId, ExpenseSource.OUTLOOK_EMAIL, subject);
    emailParseQueueService.processEmail(pending.getId(), messageId);
    return Map.of("id", pending.getId(), "status", pending.getStatus().name());
  }
}
