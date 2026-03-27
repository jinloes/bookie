package com.bookie.controller;

import com.bookie.model.ExpenseSource;
import com.bookie.model.ExpenseSuggestion;
import com.bookie.model.OutlookEmail;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.service.EmailParserService;
import com.bookie.service.OutlookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/outlook")
@RequiredArgsConstructor
public class OutlookController {

    private final OutlookService outlookService;
    private final EmailParserService emailParserService;

    @GetMapping("/connect")
    public RedirectView connect() {
        return new RedirectView(outlookService.getAuthorizationUrl());
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
        try {
            outlookService.handleCallback(code);
        } catch (HttpClientErrorException e) {
            log.error("Outlook token exchange failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
        return new RedirectView("/");
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("connected", outlookService.isConnected());
    }

    @GetMapping("/emails/rental")
    public OutlookEmailsPage getRentalEmails(@RequestParam(defaultValue = "0") int page) {
        return outlookService.getRentalEmails(page);
    }

    @PostMapping("/emails/{messageId}/to-expense")
    public ExpenseSuggestion convertToExpense(@PathVariable String messageId) {
        OutlookService.MessageContent message = outlookService.fetchMessageBody(messageId);
        ExpenseSuggestion suggestion = emailParserService.suggestExpenseFromEmail(message.subject(), message.body());
        return new ExpenseSuggestion(suggestion.amount(), suggestion.description(), suggestion.date(),
                suggestion.category(), suggestion.propertyName(), suggestion.payerName(), ExpenseSource.OUTLOOK_EMAIL, messageId);
    }
}