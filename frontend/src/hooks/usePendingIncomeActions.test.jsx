import { act, renderHook } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';

vi.mock('../api/index.js');
const mockAcceptPendingIncome = vi.mocked(api.acceptPendingIncome);

import { queryKeys } from '../queryKeys.js';
import { usePendingIncomeActions } from './usePendingIncomeActions.js';

describe('usePendingIncomeActions', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  it('saves edited pending income and invalidates finalized and pending caches', async () => {
    mockAcceptPendingIncome.mockResolvedValue({ id: 88 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(usePendingIncomeActions, {
      wrapper: createQueryWrapper(queryClient),
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
