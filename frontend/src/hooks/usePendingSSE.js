import { useEffect, useRef } from 'react';
import { notifications } from '@mantine/notifications';
import { isTauri } from '@tauri-apps/api/core';
import { PENDING_STATUS } from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { resolveApiUrl } from '../api/client.js';

// Only import Tauri notification API in Tauri context; falls back gracefully in browser.
let sendNativeNotification = null;
let requestNativePermission = null;
if (isTauri()) {
  try {
    const mod = await import('@tauri-apps/plugin-notification');
    sendNativeNotification = mod.sendNotification;
    requestNativePermission = mod.requestPermission;
  } catch {
    // Running in browser dev mode — native notifications not available.
  }
}

async function showNotification(title, body) {
  if (sendNativeNotification) {
    try {
      if (requestNativePermission) {
        await requestNativePermission();
      }
      sendNativeNotification({ title, body });
      return;
    } catch {
      // Fall through to in-app notification.
    }
  }
  notifications.show({ title, message: body, autoClose: 6000 });
}

const INITIAL_RETRY_MS = 1000;
const MAX_RETRY_MS = 15000;
const EVENT_SOURCE_CLOSED = 2;

/**
 * Subscribes to the inbox SSE stream, applies an optional filter,
 * calls onUpdate for each matching event, invalidates relevant React Query caches,
 * and shows a notification when a READY event arrives while the user is not already
 * on the pending tab.
 *
 * All props are read through refs so prop changes between renders are picked up
 * by the next event without tearing down the EventSource. Transient network drops
 * use the browser's built-in reconnection (`readyState === CONNECTING`); terminal
 * closes (`readyState === CLOSED`, e.g. a dev-proxy 502 while the backend is still
 * booting) are re-opened with bounded backoff or immediately when `reconnectTrigger`
 * signals that the backend is healthy.
 */
export function usePendingSSE({
  filter = undefined,
  notification,
  activeTab,
  onUpdate,
  queryClient,
  reconnectTrigger = null,
}) {
  const filterRef = useRef(filter);
  const notificationRef = useRef(notification);
  const activeTabRef = useRef(activeTab);
  const onUpdateRef = useRef(onUpdate);
  const queryClientRef = useRef(queryClient);
  const reconnectRef = useRef(null);
  useEffect(() => {
    filterRef.current = filter;
  }, [filter]);
  useEffect(() => {
    notificationRef.current = notification;
  }, [notification]);
  useEffect(() => {
    activeTabRef.current = activeTab;
  }, [activeTab]);
  useEffect(() => {
    onUpdateRef.current = onUpdate;
  }, [onUpdate]);
  useEffect(() => {
    queryClientRef.current = queryClient;
  }, [queryClient]);

  useEffect(() => {
    /** @type {EventSource | null} */
    let es = null;
    /** @type {ReturnType<typeof setTimeout> | null} */
    let retryTimer = null;
    let retryDelay = INITIAL_RETRY_MS;
    let cancelled = false;

    const clearRetryTimer = () => {
      if (retryTimer !== null) {
        clearTimeout(retryTimer);
        retryTimer = null;
      }
    };

    const open = (url) => {
      if (cancelled) return;
      es?.close();
      const current = new EventSource(url);
      es = current;

      current.addEventListener('open', () => {
        retryDelay = INITIAL_RETRY_MS;
        queryClientRef.current?.invalidateQueries({ queryKey: queryKeys.inbox });
      });

      current.addEventListener('inbox-updated', (e) => {
        let data;
        try {
          data = JSON.parse(e.data);
        } catch {
          return;
        }
        if (filterRef.current && !filterRef.current(data)) return;
        if (typeof onUpdateRef.current === 'function') {
          onUpdateRef.current(data);
        }
        if (queryClientRef.current) {
          queryClientRef.current.invalidateQueries({ queryKey: queryKeys.inbox });
          // Ledger transactions and reports only change when an inbox item is saved.
          if (data.state === PENDING_STATUS.SAVED) {
            queryClientRef.current.invalidateQueries({ queryKey: queryKeys.transactions });
            queryClientRef.current.invalidateQueries({ queryKey: ['reports'] });
          }
        }
        if (data.state === PENDING_STATUS.READY && activeTabRef.current !== 'pending') {
          const n = notificationRef.current ?? {};
          showNotification(n.title ?? 'Bookie', n.message ?? 'A new item is ready to review.');
        }
      });

      current.addEventListener('error', () => {
        if (cancelled || current !== es) return;
        // Browsers permanently close EventSource (`readyState === 2`) on non-200 HTTP responses
        // such as a Vite proxy 502 before Spring Boot finishes starting.
        if (current.readyState === EVENT_SOURCE_CLOSED) {
          current.close();
          es = null;
          clearRetryTimer();
          const delay = retryDelay;
          retryDelay = Math.min(MAX_RETRY_MS, retryDelay * 2);
          retryTimer = setTimeout(() => {
            retryTimer = null;
            connect();
          }, delay);
        }
      });
    };

    const connect = () => {
      if (cancelled) return;
      clearRetryTimer();
      // Dev stays synchronous behind the Vite proxy; production needs the resolved backend origin.
      if (import.meta.env.DEV) {
        open('/api/v2/inbox/events');
      } else {
        resolveApiUrl('/api/v2/inbox/events').then((url) => {
          if (!cancelled) open(url);
        });
      }
    };

    reconnectRef.current = () => {
      if (cancelled) return;
      if (!es || es.readyState === EVENT_SOURCE_CLOSED || retryTimer !== null) {
        retryDelay = INITIAL_RETRY_MS;
        connect();
      }
    };

    connect();

    return () => {
      cancelled = true;
      reconnectRef.current = null;
      clearRetryTimer();
      es?.close();
    };
  }, []);

  useEffect(() => {
    if (reconnectTrigger) {
      reconnectRef.current?.();
    }
  }, [reconnectTrigger]);
}
