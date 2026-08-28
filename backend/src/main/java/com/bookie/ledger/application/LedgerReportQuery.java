package com.bookie.ledger.application;

import java.time.LocalDate;

public interface LedgerReportQuery {

  LedgerReportData.CashflowTotals cashflow(LocalDate from, LocalDate to);

  LedgerReportData.ScheduleETotals scheduleE(LocalDate from, LocalDate to);
}
