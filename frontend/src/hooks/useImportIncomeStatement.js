import { useState } from 'react';
import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { importBankToInbox, importVenmoToInbox } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';

export const STATEMENT_SOURCE = {
  VENMO: 'VENMO',
  BANK: 'BANK',
};

const SOURCE_LABELS = {
  [STATEMENT_SOURCE.VENMO]: 'Venmo CSV',
  [STATEMENT_SOURCE.BANK]: 'bank statement',
};

const venmoSummary = (summary) => {
  let message = `Imported ${summary.importedRows} rows (${summary.skippedDuplicateRows} duplicates, ${summary.skippedSenderRows} sender mismatch, ${summary.skippedOutgoingRows} outgoing, ${summary.skippedInvalidRows} invalid).`;
  if (summary.propertyName) {
    message += ` Property: ${summary.propertyName}`;
  }
  return message;
};

const bankSummary = (summary) =>
  `Imported ${summary.importedRows} deposits (${summary.skippedDuplicateRows} duplicates, ${summary.skippedDebitRows} debits skipped, ${summary.skippedInvalidRows} invalid).`;

/** Import drawer state and submit for Venmo CSV and bank statement (OFX/QFX) income imports. */
export function useImportIncomeStatement() {
  const queryClient = useQueryClient();
  const [opened, setOpened] = useState(false);
  const [source, setSourceState] = useState(STATEMENT_SOURCE.VENMO);
  const [payerId, setPayerId] = useState(null);
  const [activityId, setActivityId] = useState(null);
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);

  const open = () => setOpened(true);

  const close = () => {
    setOpened(false);
    setSourceState(STATEMENT_SOURCE.VENMO);
    setPayerId(null);
    setActivityId(null);
    setFile(null);
    setError(null);
  };

  // Each source accepts a different file type, so a file picked for one never carries over.
  const setSource = (nextSource) => {
    setSourceState(nextSource);
    setFile(null);
    setError(null);
  };

  const submit = async () => {
    const label = SOURCE_LABELS[source];
    if (!file) {
      setError(`Please select a ${label} file.`);
      return;
    }

    setError(null);

    try {
      let title;
      let message;
      if (source === STATEMENT_SOURCE.BANK) {
        const summary = await importBankToInbox(file, activityId);
        title = 'Bank statement import completed';
        message = bankSummary(summary);
      } else {
        const summary = await importVenmoToInbox(file, payerId, null, activityId);
        title = 'Venmo import completed';
        message = venmoSummary(summary);
      }
      notifications.show({ title, message, color: 'green' });
      queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
      close();
    } catch (err) {
      const explicitMessage =
        err?.message && !String(err.message).startsWith('HTTP ') ? err.message : null;
      setError(explicitMessage || getErrorMessage(err, `Could not import ${label}.`));
    }
  };

  return {
    open,
    importForm: {
      opened,
      source,
      setSource,
      payerId,
      setPayerId,
      activityId,
      setActivityId,
      file,
      setFile,
      error,
      onCancel: close,
      onSubmit: submit,
    },
  };
}
