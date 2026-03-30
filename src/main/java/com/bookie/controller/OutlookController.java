package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.ExpenseSuggestion;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.service.EmailParserService;
import com.bookie.service.MsalTokenService;
import com.bookie.service.OutlookService;
import com.bookie.service.PropertyHistoryService;
import java.time.Year;
import java.util.Map;
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
  private final EmailParserService emailParserService;
  private final PropertyHistoryService propertyHistoryService;

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

  @PostMapping("/emails/{messageId}/to-expense")
  public ExpenseSuggestion convertToExpense(@PathVariable String messageId) {
    OutlookService.MessageContent message = outlookService.fetchMessageBody(messageId);
    ExpenseSuggestion suggestion =
        emailParserService.suggestExpenseFromEmail(
            message.subject(), message.body(), message.receivedDate());
    propertyHistoryService.storeKeywords(messageId, suggestion.keywords());
    return ExpenseSuggestion.builder()
        .amount(suggestion.amount())
        .description(suggestion.description())
        .date(suggestion.date())
        .category(suggestion.category())
        .propertyName(suggestion.propertyName())
        .payerName(suggestion.payerName())
        .sourceType(ExpenseSource.OUTLOOK_EMAIL)
        .sourceId(messageId)
        .build();
  }
}
