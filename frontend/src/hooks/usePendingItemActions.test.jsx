import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { notifications } from '@mantine/notifications';
import * as api from '../api/index.js';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';
import { EXPENSE_SOURCE, PENDING_STATUS } from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { usePendingItemActions } from './usePendingItemActions.js';

vi.mock('../api/index.js');
vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

const mockDismissInboxItem = vi.mocked(api.dismissInboxItem);
const mockReparseInboxItem = vi.mocked(api.reparseInboxItem);
const mockCreateCounterparty = vi.mocked(api.createCounterparty);

describe('usePendingItemActions', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  it('removes a dismissed email item from inbox cache and invalidates Outlook email queries', async () => {
    mockDismissInboxItem.mockResolvedValue(undefined);
    queryClient.setQueryData(queryKeys.inbox, [
      { id: 1, origin: EXPENSE_SOURCE.OUTLOOK_EMAIL },
      { id: 2, origin: EXPENSE_SOURCE.RECEIPT },
    ]);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const onDismissed = vi.fn();

    const { result } = renderHook(
      () =>
        usePendingItemActions({
          item: { id: 1, origin: EXPENSE_SOURCE.OUTLOOK_EMAIL },
          onDismissed,
        }),
      { wrapper: createQueryWrapper(queryClient) }
    );

    await act(async () => {
      await result.current.dismissItem();
    });

    expect(mockDismissInboxItem).toHaveBeenCalledWith(1);
    expect(queryClient.getQueryData(queryKeys.inbox)).toEqual([
      { id: 2, origin: EXPENSE_SOURCE.RECEIPT },
    ]);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.outlookRentalEmailsAll });
    expect(onDismissed).toHaveBeenCalledWith(1);
  });

  it('invalidates receipt queries when dismissing a receipt inbox item', async () => {
    mockDismissInboxItem.mockResolvedValue(undefined);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(
      () =>
        usePendingItemActions({
          item: { id: 2, origin: EXPENSE_SOURCE.RECEIPT },
        }),
      { wrapper: createQueryWrapper(queryClient) }
    );

    await act(async () => {
      await result.current.dismissItem();
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.receipts });
  });

  it('notifies and preserves cache when dismiss fails', async () => {
    mockDismissInboxItem.mockRejectedValue(new Error('network down'));
    queryClient.setQueryData(queryKeys.inbox, [{ id: 1, origin: EXPENSE_SOURCE.RECEIPT }]);

    const { result } = renderHook(
      () =>
        usePendingItemActions({
          item: { id: 1, origin: EXPENSE_SOURCE.RECEIPT },
        }),
      { wrapper: createQueryWrapper(queryClient) }
    );

    let dismissed;
    await act(async () => {
      dismissed = await result.current.dismissItem();
    });

    expect(dismissed).toBe(false);
    expect(queryClient.getQueryData(queryKeys.inbox)).toHaveLength(1);
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Dismiss failed', color: 'red' })
    );
  });

  it('updates inbox cache state and clears errorMessage on retryParse', async () => {
    mockReparseInboxItem.mockResolvedValue({ id: 5, state: PENDING_STATUS.QUEUED });
    queryClient.setQueryData(queryKeys.inbox, [
      { id: 5, state: PENDING_STATUS.FAILED, errorMessage: 'old error' },
    ]);
    const onRetried = vi.fn();

    const { result } = renderHook(
      () =>
        usePendingItemActions({
          item: { id: 5, origin: EXPENSE_SOURCE.RECEIPT },
          onRetried,
        }),
      { wrapper: createQueryWrapper(queryClient) }
    );

    await act(async () => {
      await result.current.retryParse();
    });

    expect(queryClient.getQueryData(queryKeys.inbox)).toEqual([
      { id: 5, state: PENDING_STATUS.QUEUED, errorMessage: null },
    ]);
    expect(onRetried).toHaveBeenCalledWith(5, { id: 5, state: PENDING_STATUS.QUEUED });
  });

  it('appends newly created counterparty to cache and sets actionError on failure', async () => {
    mockCreateCounterparty
      .mockResolvedValueOnce({ id: 99, name: 'Utility Co' })
      .mockRejectedValueOnce(new Error('duplicate name'));
    queryClient.setQueryData(queryKeys.counterparties, [{ id: 1, name: 'Existing' }]);
    const onPayerCreated = vi.fn();

    const { result } = renderHook(
      () =>
        usePendingItemActions({
          item: { id: 5, origin: EXPENSE_SOURCE.OUTLOOK_EMAIL },
          onPayerCreated,
        }),
      { wrapper: createQueryWrapper(queryClient) }
    );

    await act(async () => {
      await result.current.createSuggestedPayer('Utility Co');
    });

    expect(queryClient.getQueryData(queryKeys.counterparties)).toEqual([
      { id: 1, name: 'Existing' },
      { id: 99, name: 'Utility Co' },
    ]);
    expect(onPayerCreated).toHaveBeenCalledWith({ id: 99, name: 'Utility Co' });

    await act(async () => {
      await result.current.createSuggestedPayer('Utility Co');
    });
    expect(result.current.actionError).toBe('duplicate name');

    act(() => {
      result.current.clearActionError();
    });
    expect(result.current.actionError).toBeNull();
  });
});
