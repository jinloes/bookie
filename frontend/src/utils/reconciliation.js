import { PENDING_STATUS, isTransientInboxState } from '../constants.js';

export function buildReconciliationState(receipts = [], pendingExpenses = []) {
  const linkedReceipts = receipts.filter((r) => r.expenseId || r.incomeId);
  const unresolvedReceipts = receipts.filter((r) => !r.expenseId && !r.incomeId);
  const readyPending = pendingExpenses.filter((i) => i.state === PENDING_STATUS.READY);
  const failedPending = pendingExpenses.filter((i) => i.state === PENDING_STATUS.FAILED);
  const processingPending = pendingExpenses.filter((i) => isTransientInboxState(i.state));

  return {
    linkedReceipts,
    unresolvedReceipts,
    readyPending,
    failedPending,
    processingPending,
    unresolvedCount: unresolvedReceipts.length + readyPending.length + failedPending.length,
    resolvedCount: linkedReceipts.length,
  };
}
