// @ts-check
import { jsx } from 'react/jsx-runtime';
import { Text } from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { deleteTransaction } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';

/**
 * Shared mutation hook for deleting a finalized ledger transaction (expense or income),
 * invalidating ledger/report caches, and notifying on failure.
 *
 * @param {{
 *   title: string,
 *   confirmMessage: string,
 *   fallbackErrorMessage: string,
 * }} options
 */
export function useDeleteTransaction({ title, confirmMessage, fallbackErrorMessage }) {
  const queryClient = useQueryClient();

  /**
   * @param {number} transactionId
   * @param {Array<{ id?: number, version?: number }>} rows
   */
  const confirmDelete = (transactionId, rows) => {
    modals.openConfirmModal({
      title,
      children: jsx(Text, { size: 'sm', children: confirmMessage }),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          const row = rows.find((candidate) => candidate.id === transactionId);
          await deleteTransaction(transactionId, row?.version);
          queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
          queryClient.invalidateQueries({ queryKey: ['reports'] });
        } catch (err) {
          notifications.show({
            title: 'Delete failed',
            message: getErrorMessage(err, fallbackErrorMessage),
            color: 'red',
          });
        }
      },
    });
  };

  return { confirmDelete };
}
