import { useEffect, useRef } from 'react'
import { notifications } from '@mantine/notifications'

/**
 * Subscribes to the pending-expenses SSE stream, applies an optional filter,
 * calls onUpdate for each matching event, and shows a notification when a
 * READY event arrives while the user is not already on the pending tab.
 *
 * The EventSource is left open on error so the browser's built-in reconnection
 * runs. Closing on error would permanently drop the stream until remount.
 */
export function usePendingSSE({ filter, notification, activeTab, onUpdate }) {
  const activeTabRef = useRef(activeTab)
  useEffect(() => { activeTabRef.current = activeTab }, [activeTab])

  const onUpdateRef = useRef(onUpdate)
  useEffect(() => { onUpdateRef.current = onUpdate }, [onUpdate])

  useEffect(() => {
    const es = new EventSource('/api/pending-expenses/events')
    es.addEventListener('pending-updated', (e) => {
      let data
      try { data = JSON.parse(e.data) } catch { return }
      if (filter && !filter(data)) return
      onUpdateRef.current(data)
      if (data.status === 'READY' && activeTabRef.current !== 'pending') {
        notifications.show({ autoClose: 6000, ...notification })
      }
    })
    return () => es.close()
  }, [])
}
