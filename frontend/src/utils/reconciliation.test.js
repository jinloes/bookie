import { describe, expect, it } from 'vitest';
import { buildReconciliationState } from './reconciliation.js';
import { PENDING_STATUS } from '../constants.js';

describe('buildReconciliationState', () => {
  it('returns zero counts for empty inputs', () => {
    expect(buildReconciliationState([], [])).toEqual({
      linkedReceipts: [],
      unresolvedReceipts: [],
      readyPending: [],
      failedPending: [],
      processingPending: [],
      unresolvedCount: 0,
      resolvedCount: 0,
    });
  });

  it('classifies linked and unresolved receipts correctly', () => {
    const receipts = [
      { id: 'a', expenseId: 10, incomeId: null },
      { id: 'b', expenseId: null, incomeId: 7 },
      { id: 'c', expenseId: null, incomeId: null },
    ];

    const state = buildReconciliationState(receipts, []);
    expect(state.linkedReceipts.map((r) => r.id)).toEqual(['a', 'b']);
    expect(state.unresolvedReceipts.map((r) => r.id)).toEqual(['c']);
    expect(state.resolvedCount).toBe(2);
    expect(state.unresolvedCount).toBe(1);
  });

  it('classifies inbox states and unresolved totals', () => {
    const pending = [
      { id: 1, state: PENDING_STATUS.READY },
      { id: 2, state: PENDING_STATUS.FAILED },
      { id: 3, state: PENDING_STATUS.PROCESSING },
      { id: 4, state: PENDING_STATUS.QUEUED },
      { id: 5, state: PENDING_STATUS.RECEIVED },
    ];

    const state = buildReconciliationState([], pending);
    expect(state.readyPending.map((item) => item.id)).toEqual([1]);
    expect(state.failedPending.map((item) => item.id)).toEqual([2]);
    expect(state.processingPending.map((item) => item.id)).toEqual([3, 4, 5]);
    expect(state.unresolvedCount).toBe(2);
  });
});
