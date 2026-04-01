package com.bookie.controller;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.ExpenseSource;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.service.EmailParserService;
import com.bookie.service.MsalTokenService;
import com.bookie.service.OutlookService;
import com.bookie.service.ParseSessionContext;
import com.bookie.service.PayerService;
import com.bookie.service.PropertyHistoryService;
import java.time.Year;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
@RestController
@RequestMapping("/api/outlook")
@RequiredArgsConstructor
public class OutlookController {

  private final OutlookService outlookService;
  private final MsalTokenService msalTokenService;
  private final EmailParserService emailParserService;
  private final PropertyHistoryService propertyHistoryService;
  private final ParseSessionContext parseSessionContext;
  private final PayerService payerService;

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

  @PostMapping("/emails/{messageId}/parse")
  public EmailSuggestion parseEmail(@PathVariable String messageId) {
    OutlookService.MessageContent message = outlookService.fetchMessageBody(messageId);
    EmailSuggestion suggestion =
        emailParserService.suggestFromEmail(
            message.subject(), message.body(), message.receivedDate());
    propertyHistoryService.storeKeywords(messageId, suggestion.keywords());
    autoSaveAliases(suggestion.payerName());
    return suggestion.toBuilder()
        .sourceType(ExpenseSource.OUTLOOK_EMAIL)
        .sourceId(messageId)
        .build();
  }

  private void autoSaveAliases(String payerName) {
    if (!StringUtils.hasText(payerName)) {
      return;
    }
    List<String> unrecognized = parseSessionContext.getUnrecognizedAliases();
    unrecognized.forEach(alias -> payerService.addAliasIfAbsent(payerName, alias));
  }
}
