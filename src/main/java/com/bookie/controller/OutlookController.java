package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.FolderSetting;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.service.EmailParseQueueService;
import com.bookie.service.MsalTokenService;
import com.bookie.service.OutlookService;
import com.bookie.service.OutlookService.FolderInfo;
import com.bookie.service.PendingExpenseService;
import java.time.Year;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;
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
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      @RequestParam(value = "error_description", required = false) String errorDescription) {
    if (error != null) {
      log.error("Outlook authorization failed: {} - {}", error, errorDescription);
      return new RedirectView("/?outlookError=" + error);
    }
    if (!msalTokenService.validateState(state)) {
      log.warn("OAuth2 state mismatch — possible CSRF attempt");
      return new RedirectView("/?outlookError=state_mismatch");
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

  @GetMapping("/settings/move")
  public OutlookService.MoveSettings getMoveSettings() {
    return outlookService.getMoveSettings();
  }

  @PutMapping("/settings/move")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateMoveSettings(@RequestBody Map<String, Object> body) {
    boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
    String folderId = (String) body.get("folderId");
    outlookService.updateMoveSettings(enabled, folderId);
  }

  @PostMapping("/emails/{messageId}/parse")
  public Map<String, Object> parseEmail(
      @PathVariable String messageId, @RequestBody Map<String, String> body) {
    String subject = body.getOrDefault("subject", "");
    var result =
        pendingExpenseService.findOrCreate(messageId, ExpenseSource.OUTLOOK_EMAIL, subject);
    if (!result.alreadyProcessing()) {
      emailParseQueueService.processEmail(result.pending().getId(), messageId);
    }
    return Map.of("id", result.pending().getId(), "status", result.pending().getStatus().name());
  }
}
