import { useQuery } from '@tanstack/react-query';
import { getInboxItems } from '../api/index.js';
import { isTransientInboxState } from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { useBoundedPoll } from './useBoundedPoll.js';

const POLL_WHILE_PROCESSING_MS = 5000;

// SSE in App.jsx invalidates the inbox query on every state change, so this normally doesn't
// need to poll. But a single missed SSE event (dropped connection, reconnect race, backgrounded
// tab) would otherwise leave a transient item stuck forever with no self-healing. Poll while
// any item is still received, queued or processing as a safety net; stop once nothing is in
// flight so we don't hammer the backend during normal use. The poll is also bounded: after two
// minutes without a state change it stops so an auto-pausing database can sleep, and resumes on
// window focus or the next SSE event.
function refetchWhileProcessing(query) {
  const list = query.state.data ?? [];
  return list.some((item) => isTransientInboxState(item.state)) ? POLL_WHILE_PROCESSING_MS : false;
}

const inboxStateSignature = (items) =>
  JSON.stringify((items ?? []).map((item) => [item.id, item.state]));

/** Unified active inbox items across all intake origins (email, receipt, Venmo, bank). */
export function useInboxQuery() {
  const bounded = useBoundedPoll({ queryKey: queryKeys.inbox, signature: inboxStateSignature });
  return useQuery({
    queryKey: queryKeys.inbox,
    queryFn: getInboxItems,
    refetchInterval: bounded(refetchWhileProcessing),
  });
}
