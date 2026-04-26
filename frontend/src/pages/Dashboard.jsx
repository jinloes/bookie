import React, { useMemo } from 'react'
import { SimpleGrid, Card, Text, Group, Stack, Loader, Center, Title, ThemeIcon, Alert } from '@mantine/core'
import { IconTrendingUp, IconTrendingDown, IconScale, IconAlertCircle } from '@tabler/icons-react'
import { useQuery } from '@tanstack/react-query'
import { getTotalIncome, getTotalExpenses, getIncomes, getExpenses } from '../api/index.js'
import { fmtCurrency } from '../utils/formatters.js'

export default function Dashboard() {
  const { data: totalIncomeData, isLoading: l1 } = useQuery({ queryKey: ['totalIncome'], queryFn: getTotalIncome })
  const { data: totalExpensesData, isLoading: l2 } = useQuery({ queryKey: ['totalExpenses'], queryFn: getTotalExpenses })
  const { data: incomes = [], isLoading: l3, error } = useQuery({ queryKey: ['incomes'], queryFn: getIncomes })
  const { data: expenses = [], isLoading: l4 } = useQuery({ queryKey: ['expenses'], queryFn: getExpenses })

  const totalIncome = totalIncomeData?.total || 0
  const totalExpenses = totalExpensesData?.total || 0
  const recentIncomes = useMemo(() => incomes.slice(-5).reverse(), [incomes])
  const recentExpenses = useMemo(() => expenses.slice(-5).reverse(), [expenses])

  if (l1 || l2 || l3 || l4) return <Center h={200}><Loader /></Center>
  if (error) return <Alert icon={<IconAlertCircle size={16} />} color="red" title="Error">{error.message}</Alert>

  const net = (totalIncome - totalExpenses).toFixed(2)
  const netPositive = Number(net) >= 0

  return (
    <Stack gap="xl">
      <Title order={2}>Dashboard</Title>

      <SimpleGrid cols={3}>
        <Card withBorder p="lg" style={{ borderLeft: '4px solid var(--mantine-color-green-5)' }}>
          <Group justify="space-between" mb="xs">
            <Text size="sm" c="dimmed">Total Income</Text>
            <ThemeIcon color="green" variant="light" size="sm"><IconTrendingUp size={14} /></ThemeIcon>
          </Group>
          <Text fw={700} size="xl" c="green">{fmtCurrency(totalIncome)}</Text>
        </Card>

        <Card withBorder p="lg" style={{ borderLeft: '4px solid var(--mantine-color-red-5)' }}>
          <Group justify="space-between" mb="xs">
            <Text size="sm" c="dimmed">Total Expenses</Text>
            <ThemeIcon color="red" variant="light" size="sm"><IconTrendingDown size={14} /></ThemeIcon>
          </Group>
          <Text fw={700} size="xl" c="red">{fmtCurrency(totalExpenses)}</Text>
        </Card>

        <Card withBorder p="lg" style={{ borderLeft: `4px solid var(--mantine-color-${netPositive ? 'blue' : 'orange'}-5)` }}>
          <Group justify="space-between" mb="xs">
            <Text size="sm" c="dimmed">Net Income</Text>
            <ThemeIcon color={netPositive ? 'blue' : 'orange'} variant="light" size="sm"><IconScale size={14} /></ThemeIcon>
          </Group>
          <Text fw={700} size="xl" c={netPositive ? 'blue' : 'orange'}>
            {netPositive ? '+' : ''}{fmtCurrency(net)}
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
                  <Text size="sm" fw={600} c="green">+{fmtCurrency(i.amount)}</Text>
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
                  <Text size="sm" fw={600} c="red">-{fmtCurrency(e.amount)}</Text>
                </Group>
              ))}
            </Stack>
          )}
        </Card>
      </SimpleGrid>
    </Stack>
  )
}
