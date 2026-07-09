import { useEffect, useRef } from 'react';
import { notifications } from '@mantine/notifications';
import { PENDING_STATUS } from '../constants.js';
import { queryKeys } from '../queryKeys.js';

// Only import Tauri notification API in Tauri context; falls back gracefully in browser.
let sendNativeNotification = null;
let requestNativePermission = null;
try {
  const mod = await import('@tauri-apps/plugin-notification');
  sendNativeNotification = mod.sendNotification;
  requestNativePermission = mod.requestPermission;
} catch {
  // Running in browser dev mode — native notifications not available.
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

/**
 * Subscribes to the pending-expenses SSE stream, applies an optional filter,
 * calls onUpdate for each matching event, invalidates relevant React Query caches,
 * and shows a notification when a READY event arrives while the user is not already
 * on the pending tab.
 *
 * All props are read through refs so prop changes between renders are picked up
 * by the next event without tearing down the EventSource. The EventSource is left
 * open on error so the browser's built-in reconnection runs.
 */
export function usePendingSSE({ filter, notification, activeTab, onUpdate, queryClient }) {
  const filterRef = useRef(filter);
  const notificationRef = useRef(notification);
  const activeTabRef = useRef(activeTab);
  const onUpdateRef = useRef(onUpdate);
  const queryClientRef = useRef(queryClient);
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
    const es = new EventSource('/api/pending-expenses/events');
    es.addEventListener('pending-updated', (e) => {
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
        queryClientRef.current.invalidateQueries({ queryKey: queryKeys.pendingExpenses });
        queryClientRef.current.invalidateQueries({ queryKey: queryKeys.pendingIncomes });
        // Also invalidate the main lists since pending acceptance affects totals
        queryClientRef.current.invalidateQueries({ queryKey: queryKeys.expenses });
        queryClientRef.current.invalidateQueries({ queryKey: queryKeys.incomes });
      }
      if (data.status === PENDING_STATUS.READY && activeTabRef.current !== 'pending') {
        const n = notificationRef.current ?? {};
        showNotification(n.title ?? 'Bookie', n.message ?? 'A new item is ready to review.');
      }
    });
    return () => es.close();
  }, []);
}
