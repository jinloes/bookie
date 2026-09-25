import { act, renderHook } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';

vi.mock('../api/index.js');

const mockCreateTransaction = vi.mocked(api.createTransaction);

import { queryKeys } from '../queryKeys.js';
import { useSaveAgentProposal } from './useSaveAgentProposal.js';

function renderWithClient(queryClient) {
  return renderHook(useSaveAgentProposal, {
    wrapper: createQueryWrapper(queryClient),
  });
}

describe('useSaveAgentProposal', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  it('saves income only after the caller invokes save and invalidates ledger caches', async () => {
    mockCreateTransaction.mockResolvedValue({ id: 101 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);
    expect(mockCreateTransaction).not.toHaveBeenCalled();

    await act(async () => {
      await result.current.saveAgentProposal({
        direction: 'INCOME',
        amount: 320,
        description: 'Synthetic tutoring sessions',
        date: '2026-08-20',
        counterpartyName: 'Demo Learner',
        activityId: 12,
        categoryId: 13,
        propertyId: null,
        payerId: null,
      });
    });

    expect(mockCreateTransaction).toHaveBeenCalledWith({
      amount: 320,
      direction: 'INCOME',
      description: 'Synthetic tutoring sessions',
      date: '2026-08-20',
      activityId: 12,
      neutralCategoryId: 13,
      counterpartyId: undefined,
      sourceLabel: 'Demo Learner',
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.transactions });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.inbox });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['reports'] });
  });

  it('saves an expense with the selected activity and category', async () => {
    mockCreateTransaction.mockResolvedValue({ id: 102 });
    const { result } = renderWithClient(queryClient);

    await act(async () => {
      await result.current.saveAgentProposal({
        direction: 'EXPENSE',
        amount: 48.25,
        description: 'Synthetic classroom supplies',
        date: '2026-08-17',
        counterpartyName: 'Classroom Supply Cooperative',
        activityId: 21,
        categoryId: 22,
        propertyId: null,
        payerId: 23,
      });
    });

    expect(mockCreateTransaction).toHaveBeenCalledWith({
      amount: 48.25,
      direction: 'EXPENSE',
      description: 'Synthetic classroom supplies',
      date: '2026-08-17',
      activityId: 21,
      neutralCategoryId: 22,
      counterpartyId: 23,
      sourceLabel: undefined,
    });
  });
});
