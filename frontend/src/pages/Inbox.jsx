import React from 'react'
import { Stack, Title } from '@mantine/core'
import { useQueryClient } from '@tanstack/react-query'
import PendingExpenses from '../components/PendingExpenses.jsx'

export default function Inbox() {
  const queryClient = useQueryClient()

  const handleSaved = () => {
    queryClient.invalidateQueries({ queryKey: ['expenses'] })
    queryClient.invalidateQueries({ queryKey: ['incomes'] })
    queryClient.invalidateQueries({ queryKey: ['totalExpenses'] })
    queryClient.invalidateQueries({ queryKey: ['totalIncome'] })
  }

  return (
    <Stack gap="lg">
      <Title order={2}>Inbox</Title>
      <PendingExpenses onSaved={handleSaved} onCountChange={() => {}} />
    </Stack>
  )
}
