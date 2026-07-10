import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', () => ({ useNavigate: () => mockNavigate }));

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

const mockParseReceipt = vi.fn();
vi.mock('../api/index.js', () => ({ parseReceipt: (...args) => mockParseReceipt(...args) }));

import { notifications } from '@mantine/notifications';
import { queryKeys } from '../queryKeys.js';
import { useParseReceipt } from './useParseReceipt.js';

function renderWithClient(queryClient) {
  return renderHook(() => useParseReceipt(), {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

describe('useParseReceipt', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('invalidates pendingExpenses immediately so the Review Queue reflects the new PROCESSING item', async () => {
    mockParseReceipt.mockResolvedValue({ id: 1, status: 'PROCESSING' });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);

    await act(async () => {
      await result.current.handleParseReceipt('item-1');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.pendingExpenses });
  });

  it('navigates to the Review Queue tab and shows a queued notification on success', async () => {
    mockParseReceipt.mockResolvedValue({ id: 1, status: 'PROCESSING' });
    const { result } = renderWithClient(queryClient);

    await act(async () => {
      await result.current.handleParseReceipt('item-1');
    });

    expect(mockNavigate).toHaveBeenCalledWith('/transactions/review');
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Receipt queued', color: 'blue' })
    );
  });

  it('does not invalidate pendingExpenses or navigate when the parse request fails', async () => {
    mockParseReceipt.mockRejectedValue(new Error('boom'));
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);

    await act(async () => {
      await result.current.handleParseReceipt('item-1');
    });

    expect(invalidateSpy).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Parse failed' })
    );
  });

  it('tracks parsingReceiptId while the request is in flight and clears it afterwards', async () => {
    let resolveParse;
    mockParseReceipt.mockReturnValue(
      new Promise((resolve) => {
        resolveParse = resolve;
      })
    );
    const { result } = renderWithClient(queryClient);

    let promise;
    act(() => {
      promise = result.current.handleParseReceipt('item-1');
    });
    expect(result.current.parsingReceiptId).toBe('item-1');

    await act(async () => {
      resolveParse({ id: 1, status: 'PROCESSING' });
      await promise;
    });
    expect(result.current.parsingReceiptId).toBe(null);
  });
});
