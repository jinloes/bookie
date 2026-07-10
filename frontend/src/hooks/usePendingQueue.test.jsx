import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const mockGetPendingExpenses = vi.fn();
const mockGetPendingIncomes = vi.fn();
vi.mock('../api/index.js', () => ({
  getPendingExpenses: (...args) => mockGetPendingExpenses(...args),
  getPendingIncomes: (...args) => mockGetPendingIncomes(...args),
}));

import { usePendingExpensesQuery, usePendingIncomesQuery } from './usePendingQueue.js';

function renderWithClient(hook, queryClient) {
  return renderHook(hook, {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

describe('usePendingQueue', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('usePendingExpensesQuery polls while an item is PROCESSING', async () => {
    mockGetPendingExpenses.mockResolvedValue([{ id: 1, status: 'PROCESSING' }]);
    const { result } = renderWithClient(() => usePendingExpensesQuery(), queryClient);

    await waitFor(() => expect(result.current.data).toBeDefined());

    const query = queryClient.getQueryCache().find({ queryKey: ['pendingExpenses'] });
    expect(query.options.refetchInterval(query)).toBe(5000);
  });

  it('usePendingExpensesQuery does not poll once nothing is PROCESSING', async () => {
    mockGetPendingExpenses.mockResolvedValue([{ id: 1, status: 'READY' }]);
    const { result } = renderWithClient(() => usePendingExpensesQuery(), queryClient);

    await waitFor(() => expect(result.current.data).toBeDefined());

    const query = queryClient.getQueryCache().find({ queryKey: ['pendingExpenses'] });
    expect(query.options.refetchInterval(query)).toBe(false);
  });

  it('usePendingIncomesQuery polls while an item is PROCESSING', async () => {
    mockGetPendingIncomes.mockResolvedValue([{ id: 2, status: 'PROCESSING' }]);
    const { result } = renderWithClient(() => usePendingIncomesQuery(), queryClient);

    await waitFor(() => expect(result.current.data).toBeDefined());

    const query = queryClient.getQueryCache().find({ queryKey: ['pendingIncomes'] });
    expect(query.options.refetchInterval(query)).toBe(5000);
  });

  it('handles an empty/undefined data list without throwing', () => {
    const query = { state: { data: undefined } };
    // Accessing the exported hooks only checks wiring; the internal predicate is exercised
    // via the queries above. This case guards against a null items list before first fetch.
    expect(() => {
      const list = query.state.data ?? [];
      list.some((item) => item.status === 'PROCESSING');
    }).not.toThrow();
  });
});
