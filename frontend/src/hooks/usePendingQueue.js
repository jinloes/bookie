import { useQuery } from '@tanstack/react-query';
import { getInboxItems } from '../api/index.js';
import { EXPENSE_SOURCE, isTransientInboxState } from '../constants.js';
import { queryKeys } from '../queryKeys.js';

const POLL_WHILE_PROCESSING_MS = 5000;

// SSE in App.jsx invalidates the inbox query on every state change, so this normally doesn't
// need to poll. But a single missed SSE event (dropped connection, reconnect race, backgrounded
// tab) would otherwise leave a transient item stuck forever with no self-healing. Poll while
// any item is still received, queued or processing as a safety net; stop once nothing is in
// flight so we don't hammer the backend during normal use.
function refetchWhileProcessing(query) {
  const list = query.state.data ?? [];
  return list.some((item) => isTransientInboxState(item.state)) ? POLL_WHILE_PROCESSING_MS : false;
}

const isImportedIncome = (item) => item.origin === EXPENSE_SOURCE.VENMO;

function useInboxQuery(select) {
  return useQuery({
    queryKey: queryKeys.inbox,
    queryFn: getInboxItems,
    refetchInterval: refetchWhileProcessing,
    select,
  });
}

const selectReviewItems = (items) => items.filter((item) => !isImportedIncome(item));
const selectImportedIncomes = (items) => items.filter(isImportedIncome);

/** Parsed Outlook and receipt items awaiting review. */
export function usePendingExpensesQuery() {
  return useInboxQuery(selectReviewItems);
}

/** Imported Venmo incomes awaiting acceptance. */
export function usePendingIncomesQuery() {
  return useInboxQuery(selectImportedIncomes);
}
