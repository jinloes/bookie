import { useEffect, useRef } from 'react'
import { notifications } from '@mantine/notifications'

/**
 * Subscribes to the pending-expenses SSE stream, applies an optional filter,
 * calls onUpdate for each matching event, and shows a notification when a
 * READY event arrives while the user is not already on the pending tab.
 */
export function usePendingSSE({ filter, notification, activeTab, onUpdate }) {
  const activeTabRef = useRef(activeTab)
  useEffect(() => { activeTabRef.current = activeTab }, [activeTab])

  useEffect(() => {
    const es = new EventSource('/api/pending-expenses/events')
    es.addEventListener('pending-updated', (e) => {
      let data
      try { data = JSON.parse(e.data) } catch { return }
      if (filter && !filter(data)) return
      onUpdate(data)
      if (data.status === 'READY' && activeTabRef.current !== 'pending') {
        notifications.show({ autoClose: 6000, ...notification })
      }
    })
    es.addEventListener('error', () => es.close())
    return () => es.close()
  }, [])
}