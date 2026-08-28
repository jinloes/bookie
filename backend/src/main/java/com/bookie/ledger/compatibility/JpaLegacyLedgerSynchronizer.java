package com.bookie.ledger.compatibility;

import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.application.LegacyTransactionSnapshot;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.model.Expense;
import com.bookie.model.Income;
import com.bookie.model.TransactionDirection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class JpaLegacyLedgerSynchronizer implements LegacyLedgerSynchronizer {

  private final LedgerTransactionService ledgerTransactionService;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void synchronize(Income income) {
    ledgerTransactionService.synchronizeLegacy(
        LegacyTransactionSnapshot.builder()
            .key(new LegacyTransactionKey(LegacyTransactionTable.INCOMES, income.getId()))
            .amount(income.getAmount())
            .direction(TransactionDirection.INCOME)
            .date(income.getDate())
            .description(income.getDescription())
            .activityId(income.getActivity().getId())
            .legacyCategoryId(income.getFinancialCategory().getId())
            .legacyCounterpartyId(income.getPayer() == null ? null : income.getPayer().getId())
            .origin(income.getSourceType() == null ? null : income.getSourceType().name())
            .externalId(income.getSourceId())
            .sourceLabel(income.getSource())
            .attachmentStorageProvider(
                income.getReceiptOneDriveId() == null && income.getReceiptFileName() == null
                    ? null
                    : "ONEDRIVE")
            .attachmentExternalId(income.getReceiptOneDriveId())
            .attachmentFileName(income.getReceiptFileName())
            .build());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void synchronize(Expense expense) {
    ledgerTransactionService.synchronizeLegacy(
        LegacyTransactionSnapshot.builder()
            .key(new LegacyTransactionKey(LegacyTransactionTable.EXPENSES, expense.getId()))
            .amount(expense.getAmount())
            .direction(TransactionDirection.EXPENSE)
            .date(expense.getDate())
            .description(expense.getDescription())
            .activityId(expense.getActivity().getId())
            .legacyCategoryId(expense.getFinancialCategory().getId())
            .legacyCounterpartyId(expense.getPayer() == null ? null : expense.getPayer().getId())
            .legacyExpenseCategory(expense.getCategory())
            .origin(expense.getSourceType() == null ? null : expense.getSourceType().name())
            .externalId(expense.getSourceId())
            .attachmentStorageProvider(
                expense.getReceiptOneDriveId() == null && expense.getReceiptFileName() == null
                    ? null
                    : "ONEDRIVE")
            .attachmentExternalId(expense.getReceiptOneDriveId())
            .attachmentFileName(expense.getReceiptFileName())
            .build());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void tombstoneIncome(Long incomeId) {
    ledgerTransactionService.tombstoneLegacy(
        new LegacyTransactionKey(LegacyTransactionTable.INCOMES, incomeId));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void tombstoneExpense(Long expenseId) {
    ledgerTransactionService.tombstoneLegacy(
        new LegacyTransactionKey(LegacyTransactionTable.EXPENSES, expenseId));
  }
}
