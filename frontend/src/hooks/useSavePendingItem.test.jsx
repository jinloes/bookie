import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockSavePendingExpense = vi.fn();
const mockSavePendingIncome = vi.fn();
vi.mock('../api/index.js', () => ({
  savePendingExpense: (...args) => mockSavePendingExpense(...args),
  savePendingIncome: (...args) => mockSavePendingIncome(...args),
}));

import { useSavePendingItem } from './useSavePendingItem.js';

describe('useSavePendingItem', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uses the edited direction when saving an ambiguous item', async () => {
    mockSavePendingIncome.mockResolvedValue({ id: 44 });
    const onSaved = vi.fn();
    const { result } = renderHook(() => useSavePendingItem({ itemId: 7, onSaved }));

    await act(async () => {
      await result.current.savePendingItem({
        direction: 'INCOME',
        amount: 75,
        description: 'Synthetic ambiguous payment',
        date: '2026-08-19',
        source: 'Example Counterparty',
        activityId: 10,
        categoryId: 11,
      });
    });

    expect(mockSavePendingIncome).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        amount: 75,
        activityId: 10,
        categoryId: 11,
      })
    );
    expect(mockSavePendingExpense).not.toHaveBeenCalled();
    expect(onSaved).toHaveBeenCalledWith(7, { id: 44 });
  });

  it('keeps the item unsaved and exposes a useful error when persistence fails', async () => {
    mockSavePendingExpense.mockRejectedValue(new Error('save failed'));
    const onSaved = vi.fn();
    const { result } = renderHook(() => useSavePendingItem({ itemId: 8, onSaved }));

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
    expect(result.current.saveError).toBe('save failed');
  });
});
