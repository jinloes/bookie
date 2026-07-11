import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { parseEmail } from '../api/index.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';

/**
 * Triggers background email parsing and keeps both the rental-emails list and the Review
 * Queue in sync.
 *
 * The backend creates the pending item synchronously (status PROCESSING) before parsing
 * runs in the background, so pendingExpenses must be invalidated here rather than waiting
 * for the SSE 'pending-updated' event, which only fires once parsing finishes (READY/FAILED).
 * Extracted out of RentalEmails.jsx so the invalidation logic is unit-testable without
 * rendering the full component.
 */
export function useParseEmail({ page, refreshKey, onQueued }) {
  const queryClient = useQueryClient();
  const [converting, setConverting] = useState(null);
  const [convertError, setConvertError] = useState(null);

  const handleConvert = async (email) => {
    setConverting(email.id);
    setConvertError(null);
    try {
      const result = await parseEmail(email.id, email.subject);
      // Optimistically reflect the new pending state without waiting for the next poll.
      queryClient.setQueryData(queryKeys.outlookRentalEmails(page, refreshKey), (prev) =>
        prev
          ? {
              ...prev,
              emails: prev.emails.map((e) =>
                e.id === email.id ? { ...e, pendingId: result.id } : e
              ),
            }
          : prev
      );
      queryClient.invalidateQueries({ queryKey: queryKeys.pendingExpenses });
      onQueued?.();
    } catch (err) {
      setConvertError(getErrorMessage(err, 'Failed to queue email. Please try again.'));
    } finally {
      setConverting(null);
    }
  };

  return {
    converting,
    convertError,
    handleConvert,
    clearConvertError: () => setConvertError(null),
  };
}
