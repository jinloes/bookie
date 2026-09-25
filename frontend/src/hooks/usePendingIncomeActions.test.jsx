import { act, renderHook } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';

vi.mock('../api/index.js');
const mockSaveInboxItem = vi.mocked(api.saveInboxItem);
const mockDismissInboxItem = vi.mocked(api.dismissInboxItem);

import { queryKeys } from '../queryKeys.js';
import { usePendingIncomeActions } from './usePendingIncomeActions.js';

describe('usePendingIncomeActions', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  function render() {
    return renderHook(usePendingIncomeActions, { wrapper: createQueryWrapper(queryClient) });
  }

  it('saves edited imported income as an inbox income and invalidates ledger caches', async () => {
    mockSaveInboxItem.mockResolvedValue({ id: 7, state: 'SAVED' });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = render();

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

    expect(mockSaveInboxItem).toHaveBeenCalledWith(7, {
      direction: 'INCOME',
      amount: 87.4,
      description: 'Synthetic reimbursement',
      date: '2026-08-18',
      activityId: 101,
      neutralCategoryId: 202,
      counterpartyId: 5,
      sourceLabel: 'North Valley Unified School District',
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.inbox });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.transactions });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['reports'] });
  });

  it('dismisses imported income through the inbox and refreshes the inbox', async () => {
    mockDismissInboxItem.mockResolvedValue(undefined);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = render();

    await act(async () => {
      await result.current.deletePendingIncome(9);
    });

    expect(mockDismissInboxItem).toHaveBeenCalledWith(9);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.inbox });
  });
});
