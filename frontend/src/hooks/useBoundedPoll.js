import { useCallback, useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { drainIntake } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

export const BOUNDED_POLL_IDLE_MS = 2 * 60 * 1000;
export const RECOVERY_DRAIN_THROTTLE_MS = 30 * 1000;

let lastRecoveryDrainAt = Number.NEGATIVE_INFINITY;

/**
 * Asks the backend to drain intake work whose kickoff may have been lost. Every bounded poll
 * listens for focus, so the module-level throttle keeps one window focus to one request.
 */
export function requestRecoveryDrain(now = Date.now()) {
  if (now - lastRecoveryDrainAt < RECOVERY_DRAIN_THROTTLE_MS) {
    return;
  }
  lastRecoveryDrainAt = now;
  drainIntake().catch(() => {
    // Best effort: the next focus, kickoff or startup drain covers a missed one.
  });
}

export function resetRecoveryDrainThrottle() {
  lastRecoveryDrainAt = Number.NEGATIVE_INFINITY;
}

const isSseInvalidation = (event) =>
  event?.type === 'updated' &&
  event.action?.type === 'invalidate' &&
  event.query?.queryKey?.[0] === queryKeys.inbox[0];

/**
 * Bounds a database-backed backstop poll so an idle app lets an auto-pausing database sleep.
 * Polling stops once `signature(data)` has not changed for `idleMs`, and resumes (with an
 * immediate refetch) on window focus or an inbox SSE invalidation.
 *
 * Returns a `refetchInterval` wrapper: pass the interval the poll would otherwise use.
 */
export function useBoundedPoll({
  queryKey,
  signature = (data) => JSON.stringify(data ?? null),
  idleMs = BOUNDED_POLL_IDLE_MS,
  now = Date.now,
}) {
  const queryClient = useQueryClient();
  const lastSignatureRef = useRef(undefined);
  const lastChangeAtRef = useRef(now());
  const signatureRef = useRef(signature);
  const nowRef = useRef(now);
  const queryKeyRef = useRef(queryKey);
  useEffect(() => {
    signatureRef.current = signature;
    nowRef.current = now;
    queryKeyRef.current = queryKey;
  });

  const resume = useCallback(() => {
    lastChangeAtRef.current = nowRef.current();
    queryClient.refetchQueries({ queryKey: queryKeyRef.current, type: 'active' });
  }, [queryClient]);

  useEffect(() => {
    const onFocus = () => {
      requestRecoveryDrain(nowRef.current());
      resume();
    };
    window.addEventListener('focus', onFocus);
    const unsubscribe = queryClient.getQueryCache().subscribe((event) => {
      if (!isSseInvalidation(event)) {
        return;
      }
      // The invalidation already refetches the inbox; other polls need their own refetch.
      if (queryKeyRef.current?.[0] === queryKeys.inbox[0]) {
        lastChangeAtRef.current = nowRef.current();
      } else {
        resume();
      }
    });
    return () => {
      window.removeEventListener('focus', onFocus);
      unsubscribe();
    };
  }, [queryClient, resume]);

  return useCallback(
    (interval) => (query) => {
      const base = typeof interval === 'function' ? interval(query) : interval;
      const current = nowRef.current();
      const nextSignature = signatureRef.current(query.state.data);
      if (nextSignature !== lastSignatureRef.current) {
        lastSignatureRef.current = nextSignature;
        lastChangeAtRef.current = current;
      }
      if (!base) {
        return false;
      }
      return current - lastChangeAtRef.current >= idleMs ? false : base;
    },
    [idleMs]
  );
}
