import React, { useEffect, useState } from 'react'
import { SimpleGrid, Card, Text, Group, Stack, Loader, Center, Title, ThemeIcon } from '@mantine/core'
import { IconTrendingUp, IconTrendingDown, IconScale } from '@tabler/icons-react'
import { getTotalIncome, getTotalExpenses, getIncomes, getExpenses } from '../api/index.js'
import RentalEmails from '../components/RentalEmails.jsx'

const fmt = (n) => `$${Number(n).toFixed(2)}`

export default function Dashboard() {
  const [totalIncome, setTotalIncome] = useState(0)
  const [totalExpenses, setTotalExpenses] = useState(0)
  const [recentIncomes, setRecentIncomes] = useState([])
  const [recentExpenses, setRecentExpenses] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([getTotalIncome(), getTotalExpenses(), getIncomes(), getExpenses()])
      .then(([inc, exp, incomes, expenses]) => {
        setTotalIncome(inc.total || 0)
        setTotalExpenses(exp.total || 0)
        setRecentIncomes(incomes.slice(-5).reverse())
        setRecentExpenses(expenses.slice(-5).reverse())
      })
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <Center h={200}><Loader /></Center>

  const net = (totalIncome - totalExpenses).toFixed(2)
  const netPositive = Number(net) >= 0

  return (
    <Stack gap="xl">
      <Title order={2}>Dashboard</Title>
      <RentalEmails />

      <SimpleGrid cols={3}>
        <Card withBorder p="lg" style={{ borderLeft: '4px solid var(--mantine-color-green-5)' }}>
          <Group justify="space-between" mb="xs">
            <Text size="sm" c="dimmed">Total Income</Text>
            <ThemeIcon color="green" variant="light" size="sm"><IconTrendingUp size={14} /></ThemeIcon>
          </Group>
          <Text fw={700} size="xl" c="green">{fmt(totalIncome)}</Text>
        </Card>

        <Card withBorder p="lg" style={{ borderLeft: '4px solid var(--mantine-color-red-5)' }}>
          <Group justify="space-between" mb="xs">
            <Text size="sm" c="dimmed">Total Expenses</Text>
            <ThemeIcon color="red" variant="light" size="sm"><IconTrendingDown size={14} /></ThemeIcon>
          </Group>
          <Text fw={700} size="xl" c="red">{fmt(totalExpenses)}</Text>
        </Card>

        <Card withBorder p="lg" style={{ borderLeft: `4px solid var(--mantine-color-${netPositive ? 'blue' : 'orange'}-5)` }}>
          <Group justify="space-between" mb="xs">
            <Text size="sm" c="dimmed">Net Income</Text>
            <ThemeIcon color={netPositive ? 'blue' : 'orange'} variant="light" size="sm"><IconScale size={14} /></ThemeIcon>
          </Group>
          <Text fw={700} size="xl" c={netPositive ? 'blue' : 'orange'}>
            {netPositive ? '+' : ''}{fmt(net)}
          </Text>
        </Card>
      </SimpleGrid>

      <SimpleGrid cols={2}>
        <Card withBorder p="lg">
          <Text fw={600} mb="md">Recent Income</Text>
          {recentIncomes.length === 0 ? (
            <Text size="sm" c="dimmed">No income yet</Text>
          ) : (
            <Stack gap={4}>
              {recentIncomes.map(i => (
                <Group key={i.id} justify="space-between" py={6} style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                  <Text size="sm">{i.description}</Text>
                  <Text size="sm" fw={600} c="green">+{fmt(i.amount)}</Text>
                </Group>
              ))}
            </Stack>
          )}
        </Card>

        <Card withBorder p="lg">
          <Text fw={600} mb="md">Recent Expenses</Text>
          {recentExpenses.length === 0 ? (
            <Text size="sm" c="dimmed">No expenses yet</Text>
          ) : (
            <Stack gap={4}>
              {recentExpenses.map(e => (
                <Group key={e.id} justify="space-between" py={6} style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                  <Text size="sm">{e.description}</Text>
                  <Text size="sm" fw={600} c="red">-{fmt(e.amount)}</Text>
                </Group>
              ))}
            </Stack>
          )}
        </Card>
      </SimpleGrid>
    </Stack>
  )
}