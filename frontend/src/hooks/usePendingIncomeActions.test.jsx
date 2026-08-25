import React from 'react';
import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockAcceptPendingIncome = vi.fn();
const mockRejectPendingIncome = vi.fn();
vi.mock('../api/index.js', () => ({
  acceptPendingIncome: (...args) => mockAcceptPendingIncome(...args),
  rejectPendingIncome: (...args) => mockRejectPendingIncome(...args),
}));

import { queryKeys } from '../queryKeys.js';
import { usePendingIncomeActions } from './usePendingIncomeActions.js';

describe('usePendingIncomeActions', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('saves edited pending income and invalidates finalized and pending caches', async () => {
    mockAcceptPendingIncome.mockResolvedValue({ id: 88 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(usePendingIncomeActions, {
      wrapper: ({ children }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      ),
    });

    await act(async () => {
      await result.current.savePendingIncome(
        7,
        {
          amount: 87.4,
          description: 'Synthetic reimbursement',
          date: '2026-08-18',
          source: 'North Valley Unified School District',
          activityId: '101',
          categoryId: '202',
          propertyId: null,
        },
        5
      );
    });

    expect(mockAcceptPendingIncome).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        amount: 87.4,
        activityId: 101,
        categoryId: 202,
        payerId: 5,
      })
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.pendingIncomes });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.incomes });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.totalIncome });
  });
});
