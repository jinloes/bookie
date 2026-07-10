import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const mockParseEmail = vi.fn();
vi.mock('../api/index.js', () => ({ parseEmail: (...args) => mockParseEmail(...args) }));

import { queryKeys } from '../queryKeys.js';
import { useParseEmail } from './useParseEmail.js';

function renderWithClient(queryClient, props) {
  return renderHook(() => useParseEmail(props), {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

describe('useParseEmail', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('invalidates pendingExpenses immediately so the Review Queue reflects the new PROCESSING item', async () => {
    mockParseEmail.mockResolvedValue({ id: 42 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient, { page: 0, refreshKey: 0 });

    await act(async () => {
      await result.current.handleConvert({ id: 'email-1', subject: 'Rent' });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.pendingExpenses });
  });

  it('optimistically sets the pendingId on the matching email in the cached list', async () => {
    mockParseEmail.mockResolvedValue({ id: 42 });
    const cacheKey = queryKeys.outlookRentalEmails(0, 0);
    queryClient.setQueryData(cacheKey, {
      emails: [
        { id: 'email-1', subject: 'Rent' },
        { id: 'email-2', subject: 'Other' },
      ],
    });
    const { result } = renderWithClient(queryClient, { page: 0, refreshKey: 0 });

    await act(async () => {
      await result.current.handleConvert({ id: 'email-1', subject: 'Rent' });
    });

    expect(queryClient.getQueryData(cacheKey)).toEqual({
      emails: [
        { id: 'email-1', subject: 'Rent', pendingId: 42 },
        { id: 'email-2', subject: 'Other' },
      ],
    });
  });

  it('calls onQueued after a successful convert', async () => {
    mockParseEmail.mockResolvedValue({ id: 42 });
    const onQueued = vi.fn();
    const { result } = renderWithClient(queryClient, { page: 0, refreshKey: 0, onQueued });

    await act(async () => {
      await result.current.handleConvert({ id: 'email-1', subject: 'Rent' });
    });

    expect(onQueued).toHaveBeenCalled();
  });

  it('sets convertError and does not invalidate pendingExpenses when the request fails', async () => {
    mockParseEmail.mockRejectedValue(new Error('boom'));
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const onQueued = vi.fn();
    const { result } = renderWithClient(queryClient, { page: 0, refreshKey: 0, onQueued });

    await act(async () => {
      await result.current.handleConvert({ id: 'email-1', subject: 'Rent' });
    });

    expect(invalidateSpy).not.toHaveBeenCalled();
    expect(onQueued).not.toHaveBeenCalled();
    expect(result.current.convertError).toBe('boom');
  });

  it('tracks converting state while the request is in flight and clears it afterwards', async () => {
    let resolveParse;
    mockParseEmail.mockReturnValue(
      new Promise((resolve) => {
        resolveParse = resolve;
      })
    );
    const { result } = renderWithClient(queryClient, { page: 0, refreshKey: 0 });

    let promise;
    act(() => {
      promise = result.current.handleConvert({ id: 'email-1', subject: 'Rent' });
    });
    expect(result.current.converting).toBe('email-1');

    await act(async () => {
      resolveParse({ id: 42 });
      await promise;
    });
    expect(result.current.converting).toBe(null);
  });

  it('exposes clearConvertError to reset the error state', async () => {
    mockParseEmail.mockRejectedValue(new Error('boom'));
    const { result } = renderWithClient(queryClient, { page: 0, refreshKey: 0 });

    await act(async () => {
      await result.current.handleConvert({ id: 'email-1', subject: 'Rent' });
    });
    expect(result.current.convertError).toBe('boom');

    act(() => {
      result.current.clearConvertError();
    });
    expect(result.current.convertError).toBe(null);
  });
});
