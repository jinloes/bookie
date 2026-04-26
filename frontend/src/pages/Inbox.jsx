import React, { useState } from 'react'
import { Stack, Title } from '@mantine/core'
import { useQueryClient } from '@tanstack/react-query'
import { usePendingSSE } from '../hooks/usePendingSSE.js'
import PendingExpenses from '../components/PendingExpenses.jsx'

export default function Inbox() {
  const queryClient = useQueryClient()
  const [refreshKey, setRefreshKey] = useState(0)

  // Subscribe to all SSE events; suppress notifications since we're already on this page
  usePendingSSE({
    filter: () => true,
    activeTab: 'pending',
    notification: {},
    onUpdate: () => setRefreshKey(k => k + 1),
  })

  const handleSaved = () => {
    queryClient.invalidateQueries({ queryKey: ['pendingExpenses'] })
    queryClient.invalidateQueries({ queryKey: ['expenses'] })
    queryClient.invalidateQueries({ queryKey: ['incomes'] })
    queryClient.invalidateQueries({ queryKey: ['totalExpenses'] })
    queryClient.invalidateQueries({ queryKey: ['totalIncome'] })
  }

  return (
    <Stack gap="lg">
      <Title order={2}>Inbox</Title>
      <PendingExpenses
        onSaved={handleSaved}
        onCountChange={() => {}}
        refreshKey={refreshKey}
      />
    </Stack>
  )
}
