import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { createCounterparty, dismissInboxItem, reparseInboxItem } from '../api/index.js';
import { EXPENSE_SOURCE, PAYER_TYPE, PENDING_STATUS } from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';

/**
 * Owns Review Queue secondary mutations (dismiss, retry/re-parse, and inline counterparty
 * creation) and keeps affected React Query caches synchronized.
 */
export function usePendingItemActions({ item, onDismissed, onRetried, onPayerCreated }) {
  const queryClient = useQueryClient();
  const [retrying, setRetrying] = useState(false);
  const [creatingPayer, setCreatingPayer] = useState(false);
  const [actionError, setActionError] = useState(null);

  const dismissItem = async () => {
    try {
      await dismissInboxItem(item.id);
      queryClient.setQueryData(queryKeys.inbox, (/** @type {any[] | undefined} */ prev) =>
        prev ? prev.filter((candidate) => candidate.id !== item.id) : prev
      );
      if (item.origin === EXPENSE_SOURCE.OUTLOOK_EMAIL) {
        queryClient.invalidateQueries({ queryKey: queryKeys.outlookRentalEmailsAll });
      } else if (item.origin === EXPENSE_SOURCE.RECEIPT) {
        queryClient.invalidateQueries({ queryKey: queryKeys.receipts });
      }
      onDismissed?.(item.id);
      return true;
    } catch (err) {
      notifications.show({
        title: 'Dismiss failed',
        message: getErrorMessage(err, 'Could not dismiss this item. Please retry.'),
        color: 'red',
      });
      return false;
    }
  };

  const retryParse = async () => {
    setRetrying(true);
    setActionError(null);
    try {
      const requeued = await reparseInboxItem(item.id);
      queryClient.setQueryData(queryKeys.inbox, (/** @type {any[] | undefined} */ prev) =>
        prev
          ? prev.map((candidate) =>
              candidate.id === item.id
                ? {
                    ...candidate,
                    ...requeued,
                    state: requeued?.state ?? PENDING_STATUS.QUEUED,
                    errorMessage: null,
                  }
                : candidate
            )
          : prev
      );
      onRetried?.(item.id, requeued);
      return requeued;
    } catch (err) {
      notifications.show({
        title: 'Retry failed',
        message: getErrorMessage(err, 'Could not retry parsing. Please try again.'),
        color: 'red',
      });
      return null;
    } finally {
      setRetrying(false);
    }
  };

  const createSuggestedPayer = async (suggestedName) => {
    setCreatingPayer(true);
    setActionError(null);
    try {
      const newPayer = await createCounterparty({
        name: suggestedName,
        type: PAYER_TYPE.COMPANY,
        aliases: [],
        accounts: [],
      });
      queryClient.setQueryData(queryKeys.counterparties, (/** @type {any[] | undefined} */ prev) =>
        prev ? [...prev, newPayer] : [newPayer]
      );
      onPayerCreated?.(newPayer);
      return newPayer;
    } catch (err) {
      setActionError(getErrorMessage(err, 'Failed to create payer. Please try again.'));
      return null;
    } finally {
      setCreatingPayer(false);
    }
  };

  return {
    retrying,
    creatingPayer,
    actionError,
    clearActionError: () => setActionError(null),
    dismissItem,
    retryParse,
    createSuggestedPayer,
  };
}
