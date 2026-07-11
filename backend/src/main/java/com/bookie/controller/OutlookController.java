package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.FolderSetting;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.service.EmailParseQueueService;
import com.bookie.service.MsalTokenService;
import com.bookie.service.OutlookService;
import com.bookie.service.OutlookService.FolderInfo;
import com.bookie.service.PendingExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Year;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
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

  @Operation(operationId = "connectOutlook")
  @GetMapping("/connect")
  public RedirectView connect() {
    return new RedirectView(msalTokenService.getAuthorizationUrl());
  }

  @Operation(operationId = "outlookCallback")
  @GetMapping("/callback")
  @ResponseBody
  public String callback(
      HttpServletResponse response,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      @RequestParam(value = "error_description", required = false) String errorDescription) {
    response.setContentType("text/html; charset=UTF-8");

    if (error != null) {
      log.error("Outlook authorization failed: {} - {}", error, errorDescription);
      return redirectHtml("http://localhost:5173/?outlookError=" + error);
    }
    if (!msalTokenService.validateState(state)) {
      log.warn("OAuth2 state mismatch — possible CSRF attempt");
      return redirectHtml("http://localhost:5173/?outlookError=state_mismatch");
    }
    msalTokenService.handleCallback(code);
    return redirectHtml("http://localhost:5173/settings");
  }

  private String redirectHtml(String path) {
    return """
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="utf-8">
            <meta http-equiv="refresh" content="0; url=%s">
            <title>Redirecting...</title>
          </head>
          <body>
            <p>Redirecting to <a href="%s">Bookie Settings</a>...</p>
            <script>
              window.location.href = '%s';
            </script>
          </body>
        </html>
        """
        .formatted(path, path, path);
  }

  @Operation(operationId = "getOutlookStatus")
  @GetMapping("/status")
  public Map<String, Boolean> status() {
    return Map.of("connected", msalTokenService.isConnected());
  }

  @Operation(operationId = "getOutlookRentalEmails")
  @GetMapping("/emails/rental")
  public OutlookEmailsPage getRentalEmails(
      @RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer year) {
    return outlookService.getRentalEmails(page, year != null ? year : Year.now().getValue());
  }

  @Operation(operationId = "getOutlookEmailContent")
  @GetMapping("/emails/{messageId}/content")
  public OutlookService.MessageContent getEmailContent(@PathVariable String messageId) {
    return outlookService.fetchMessageBody(messageId);
  }

  @Operation(operationId = "getOutlookAvailableFolders")
  @GetMapping("/folders/available")
  public List<FolderInfo> getAvailableFolders() {
    return outlookService.getAvailableFolders();
  }

  @Operation(operationId = "getOutlookFolderSettings")
  @GetMapping("/settings/folders")
  public List<FolderSetting> getConfiguredFolderSettings() {
    return outlookService.getConfiguredFolderSettings();
  }

  @Operation(operationId = "updateOutlookFolderSettings")
  @PutMapping("/settings/folders")
  public List<FolderSetting> updateConfiguredFolderSettings(
      @RequestBody Map<String, List<FolderSetting>> body) {
    List<FolderSetting> folderSettings = body.getOrDefault("folderSettings", List.of());
    outlookService.updateConfiguredFolderSettings(folderSettings);
    return folderSettings;
  }

  @Operation(operationId = "getOutlookMoveSettings")
  @GetMapping("/settings/move")
  public OutlookService.MoveSettings getMoveSettings() {
    return outlookService.getMoveSettings();
  }

  @Operation(operationId = "updateOutlookMoveSettings")
  @PutMapping("/settings/move")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateMoveSettings(@RequestBody Map<String, Object> body) {
    boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
    String folderId = (String) body.get("folderId");
    outlookService.updateMoveSettings(enabled, folderId);
  }

  @Operation(operationId = "parseOutlookEmail")
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
