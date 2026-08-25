package com.bookie.controller;

import com.bookie.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;

  @Operation(operationId = "getCashflowReport")
  @GetMapping("/cashflow")
  public ApiResponses.CashflowSummaryResponse cashflow(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) Long activityId) {
    return reportService.cashflow(from, to, ownerId, activityId);
  }

  @Operation(operationId = "getScheduleEReport")
  @GetMapping("/schedule-e")
  public ApiResponses.ScheduleEReportResponse scheduleE(
      @RequestParam int year,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) Long activityId) {
    return reportService.scheduleE(year, ownerId, activityId);
  }
}
