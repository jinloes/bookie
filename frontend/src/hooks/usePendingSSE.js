import { useEffect, useRef } from 'react';
import { notifications } from '@mantine/notifications';
import { PENDING_STATUS } from '../constants.js';

/**
 * Subscribes to the pending-expenses SSE stream, applies an optional filter,
 * calls onUpdate for each matching event, and shows a notification when a
 * READY event arrives while the user is not already on the pending tab.
 *
 * All props (filter, notification, activeTab, onUpdate) are read through refs
 * so prop changes between renders are picked up by the next event without
 * tearing down the EventSource. The EventSource is left open on error so the
 * browser's built-in reconnection runs.
 */
export function usePendingSSE({ filter, notification, activeTab, onUpdate }) {
  const filterRef = useRef(filter);
  const notificationRef = useRef(notification);
  const activeTabRef = useRef(activeTab);
  const onUpdateRef = useRef(onUpdate);
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
      if (data.status === PENDING_STATUS.READY && activeTabRef.current !== 'pending') {
        notifications.show({ autoClose: 6000, ...notificationRef.current });
      }
    });
    return () => es.close();
  }, []);
}
