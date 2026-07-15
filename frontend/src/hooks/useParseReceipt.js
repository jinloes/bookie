import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { parseReceipt } from '../api/index.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';

/**
 * Triggers background receipt parsing and keeps the Review Queue in sync.
 *
 * The backend creates the pending item synchronously (status PROCESSING) before parsing
 * runs in the background, so pendingExpenses must be invalidated here rather than waiting
 * for the SSE 'pending-updated' event, which only fires once parsing finishes (READY/FAILED).
 * Extracted out of Receipts.jsx so the invalidation logic is unit-testable without rendering
 * the full page.
 */
export function useParseReceipt() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [parsingReceiptId, setParsingReceiptId] = useState(null);

  const handleParseReceipt = async (itemId) => {
    setParsingReceiptId(itemId);
    try {
      await parseReceipt(itemId);
      queryClient.invalidateQueries({ queryKey: queryKeys.pendingExpenses });
      notifications.show({
        title: 'Sent to Review Queue',
        message: 'Parsing receipt — finish review in the Review Queue when it is ready',
        color: 'blue',
        autoClose: 6000,
      });
      navigate('/transactions/review');
    } catch (err) {
      notifications.show({
        title: 'Parse failed',
        message: getErrorMessage(err, 'Could not queue receipt parsing. Please try again.'),
        color: 'red',
      });
    } finally {
      setParsingReceiptId(null);
    }
  };

  return { parsingReceiptId, handleParseReceipt };
}
