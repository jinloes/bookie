import React from 'react';
import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockCreateExpense = vi.fn();
const mockCreateIncome = vi.fn();
vi.mock('../api/index.js', () => ({
  createExpense: (...args) => mockCreateExpense(...args),
  createIncome: (...args) => mockCreateIncome(...args),
}));

import { queryKeys } from '../queryKeys.js';
import { useSaveAgentProposal } from './useSaveAgentProposal.js';

function renderWithClient(queryClient) {
  return renderHook(useSaveAgentProposal, {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

describe('useSaveAgentProposal', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('saves income only after the caller invokes save and invalidates income caches', async () => {
    mockCreateIncome.mockResolvedValue({ id: 101 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);
    expect(mockCreateIncome).not.toHaveBeenCalled();

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

    expect(mockCreateIncome).toHaveBeenCalledWith(
      expect.objectContaining({
        amount: 320,
        source: 'Demo Learner',
        activityId: 12,
        categoryId: 13,
      })
    );
    expect(mockCreateExpense).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.incomes });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.totalIncome });
  });

  it('saves an expense with the selected activity and category', async () => {
    mockCreateExpense.mockResolvedValue({ id: 102 });
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

    expect(mockCreateExpense).toHaveBeenCalledWith(
      expect.objectContaining({
        amount: 48.25,
        activityId: 21,
        categoryId: 22,
        payerId: 23,
        sourceType: 'MANUAL',
      })
    );
    expect(mockCreateIncome).not.toHaveBeenCalled();
  });
});
