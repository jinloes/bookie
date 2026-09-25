import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';

vi.mock('../api/index.js');
const mockSaveInboxItem = vi.mocked(api.saveInboxItem);

import { queryKeys } from '../queryKeys.js';
import { useSavePendingItem } from './useSavePendingItem.js';

describe('useSavePendingItem', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  function render(itemId, onSaved) {
    return renderHook(() => useSavePendingItem({ itemId, onSaved }), {
      wrapper: createQueryWrapper(queryClient),
    });
  }

  it('saves an ambiguous item in the edited direction with its source label', async () => {
    mockSaveInboxItem.mockResolvedValue({ id: 7, state: 'SAVED' });
    const onSaved = vi.fn();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = render(7, onSaved);

    await act(async () => {
      await result.current.savePendingItem({
        direction: 'INCOME',
        amount: 75,
        description: 'Synthetic ambiguous payment',
        date: '2026-08-19',
        source: 'Example Counterparty',
        payerId: 9,
        activityId: 10,
        categoryId: 11,
      });
    });

    expect(mockSaveInboxItem).toHaveBeenCalledWith(7, {
      direction: 'INCOME',
      amount: 75,
      description: 'Synthetic ambiguous payment',
      date: '2026-08-19',
      activityId: 10,
      neutralCategoryId: 11,
      counterpartyId: undefined,
      sourceLabel: 'Example Counterparty',
    });
    expect(onSaved).toHaveBeenCalledWith(7, { id: 7, state: 'SAVED' });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.transactions });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.inbox });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['reports'] });
  });

  it('saves an expense with its selected counterparty and no source label', async () => {
    mockSaveInboxItem.mockResolvedValue({ id: 8, state: 'SAVED' });
    const { result } = render(8, vi.fn());

    await act(async () => {
      await result.current.savePendingItem({
        direction: 'EXPENSE',
        amount: 48.25,
        description: 'Synthetic supplies',
        date: '2026-08-17',
        payerId: 23,
        activityId: 20,
        categoryId: 21,
      });
    });

    expect(mockSaveInboxItem).toHaveBeenCalledWith(
      8,
      expect.objectContaining({
        direction: 'EXPENSE',
        counterpartyId: 23,
        sourceLabel: undefined,
      })
    );
  });

  it('keeps the item unsaved and exposes a useful error when persistence fails', async () => {
    mockSaveInboxItem.mockRejectedValue(new Error('save failed'));
    const onSaved = vi.fn();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = render(8, onSaved);

    await act(async () => {
      await result.current.savePendingItem({
        direction: 'EXPENSE',
        amount: 48.25,
        description: 'Synthetic supplies',
        date: '2026-08-17',
        activityId: 20,
        categoryId: 21,
      });
    });

    expect(onSaved).not.toHaveBeenCalled();
    expect(invalidateSpy).not.toHaveBeenCalled();
    expect(result.current.saveError).toBe('save failed');
  });
});
