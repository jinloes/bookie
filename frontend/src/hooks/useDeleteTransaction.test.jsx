import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import * as api from '../api/index.js';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';
import { queryKeys } from '../queryKeys.js';

vi.mock('@mantine/modals', () => ({ modals: { openConfirmModal: vi.fn() } }));
vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));
vi.mock('../api/index.js', async (importOriginal) => ({
  ...(await importOriginal()),
  deleteTransaction: vi.fn(),
}));

import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { useDeleteTransaction } from './useDeleteTransaction.js';

const mockDeleteTransaction = vi.mocked(api.deleteTransaction);

describe('useDeleteTransaction', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  it('deletes with optimistic version and invalidates transactions and reports', async () => {
    mockDeleteTransaction.mockResolvedValue(undefined);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(
      () =>
        useDeleteTransaction({
          title: 'Delete expense',
          confirmMessage: 'Confirm delete',
          fallbackErrorMessage: 'Could not delete expense.',
        }),
      { wrapper: createQueryWrapper(queryClient) }
    );

    result.current.confirmDelete(42, [{ id: 42, version: 7 }]);
    const modalArgs = vi.mocked(modals.openConfirmModal).mock.calls[0][0];
    await act(async () => {
      await modalArgs.onConfirm();
    });

    expect(mockDeleteTransaction).toHaveBeenCalledWith(42, 7);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.transactions });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['reports'] });
  });

  it('shows a red notification when delete fails', async () => {
    mockDeleteTransaction.mockRejectedValue(new Error('Version conflict'));
    const { result } = renderHook(
      () =>
        useDeleteTransaction({
          title: 'Delete expense',
          confirmMessage: 'Confirm delete',
          fallbackErrorMessage: 'Could not delete expense.',
        }),
      { wrapper: createQueryWrapper(queryClient) }
    );

    result.current.confirmDelete(42, [{ id: 42, version: 7 }]);
    const modalArgs = vi.mocked(modals.openConfirmModal).mock.calls[0][0];
    await act(async () => {
      await modalArgs.onConfirm();
    });

    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Delete failed',
        message: 'Version conflict',
        color: 'red',
      })
    );
  });
});
