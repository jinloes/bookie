import { useQuery } from '@tanstack/react-query';
import { getPendingExpenses, getPendingIncomes } from '../api/index.js';
import { PENDING_STATUS } from '../constants.js';
import { queryKeys } from '../queryKeys.js';

const POLL_WHILE_PROCESSING_MS = 5000;

// SSE in App.jsx invalidates these query keys on every status change, so this normally doesn't
// need to poll. But a single missed SSE event (dropped connection, reconnect race, backgrounded
// tab) would otherwise leave a PROCESSING item stuck forever with no self-healing. Poll while
// any item is still PROCESSING as a safety net; stop once nothing is in flight so we don't
// hammer the backend during normal (non-processing) use.
function refetchWhileProcessing(query) {
  const list = query.state.data ?? [];
  return list.some((item) => item.status === PENDING_STATUS.PROCESSING)
    ? POLL_WHILE_PROCESSING_MS
    : false;
}

export function usePendingExpensesQuery() {
  return useQuery({
    queryKey: queryKeys.pendingExpenses,
    queryFn: getPendingExpenses,
    refetchInterval: refetchWhileProcessing,
  });
}

export function usePendingIncomesQuery() {
  return useQuery({
    queryKey: queryKeys.pendingIncomes,
    queryFn: getPendingIncomes,
    refetchInterval: refetchWhileProcessing,
  });
}
