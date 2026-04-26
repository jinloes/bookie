import React, { useMemo } from 'react'
import { Anchor, Box, Card, Center, Group, Loader, SimpleGrid, Stack, Table, Text, Alert } from '@mantine/core'
import { Link } from 'react-router-dom'
import { IconAlertCircle, IconScale, IconTrendingDown, IconTrendingUp } from '@tabler/icons-react'
import { useQuery } from '@tanstack/react-query'
import { getExpenses, getIncomes, getTotalExpenses, getTotalIncome } from '../api/index.js'
import { fmtCurrency } from '../utils/formatters.js'

function StatCard({ label, value, color, icon: Icon }) {
  return (
    <Card withBorder p="xl" radius="md" style={{ background: 'white' }}>
      <Group gap={6} mb={10}>
        <Icon size={14} style={{ color: `var(--mantine-color-${color}-5)` }} />
        <Text
          style={{
            fontSize: '0.65rem',
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '0.08em',
            color: 'var(--mantine-color-gray-5)',
          }}
        >
          {label}
        </Text>
      </Group>
      <Text
        style={{
          fontSize: '1.875rem',
          fontWeight: 800,
          fontVariantNumeric: 'tabular-nums',
          lineHeight: 1,
          color: `var(--mantine-color-${color}-7)`,
        }}
      >
        {value}
      </Text>
    </Card>
  )
}

export default function Dashboard() {
  const { data: totalIncomeData, isLoading: l1 } = useQuery({ queryKey: ['totalIncome'], queryFn: getTotalIncome })
  const { data: totalExpensesData, isLoading: l2 } = useQuery({ queryKey: ['totalExpenses'], queryFn: getTotalExpenses })
  const { data: incomes = [], isLoading: l3, error } = useQuery({ queryKey: ['incomes'], queryFn: getIncomes })
  const { data: expenses = [], isLoading: l4 } = useQuery({ queryKey: ['expenses'], queryFn: getExpenses })

  const totalIncome = totalIncomeData?.total || 0
  const totalExpenses = totalExpensesData?.total || 0

  const recentIncomes = useMemo(() => incomes.slice(0, 5), [incomes])
  const recentExpenses = useMemo(() => expenses.slice(0, 5), [expenses])

  const propertyBreakdown = useMemo(() => {
    const map = {}
    incomes.forEach(i => {
      const key = i.property?.name || 'Unassigned'
      if (!map[key]) map[key] = { income: 0, expenses: 0 }
      map[key].income += Number(i.amount) || 0
    })
    expenses.forEach(e => {
      const key = e.property?.name || 'Unassigned'
      if (!map[key]) map[key] = { income: 0, expenses: 0 }
      map[key].expenses += Number(e.amount) || 0
    })
    return Object.entries(map)
      .map(([name, { income, expenses: exp }]) => ({ name, income, expenses: exp, net: income - exp }))
      .sort((a, b) => b.income - a.income)
  }, [incomes, expenses])

  const monthlyData = useMemo(() => {
    const currentYear = String(new Date().getFullYear())
    const map = {}
    incomes.filter(i => i.date?.startsWith(currentYear)).forEach(i => {
      const month = i.date?.slice(0, 7)
      if (!month) return
      if (!map[month]) map[month] = { income: 0, expenses: 0 }
      map[month].income += Number(i.amount) || 0
    })
    expenses.filter(e => e.date?.startsWith(currentYear)).forEach(e => {
      const month = e.date?.slice(0, 7)
      if (!month) return
      if (!map[month]) map[month] = { income: 0, expenses: 0 }
      map[month].expenses += Number(e.amount) || 0
    })
    return Object.entries(map)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([month, vals]) => ({
        month,
        ...vals,
        net: vals.income - vals.expenses,
        label: new Date(month + '-01').toLocaleDateString('en-US', { month: 'short' }),
      }))
  }, [incomes, expenses])

  const maxMonthlyValue = useMemo(
    () => Math.max(...monthlyData.flatMap(m => [m.income, m.expenses]), 1),
    [monthlyData]
  )

  if (l1 || l2 || l3 || l4) return <Center h={200}><Loader /></Center>
  if (error) return <Alert icon={<IconAlertCircle size={16} />} color="red" title="Error">{error.message}</Alert>

  const net = (totalIncome - totalExpenses).toFixed(2)
  const netPositive = Number(net) >= 0

  return (
    <Stack gap="xl">
      <SimpleGrid cols={3}>
        <StatCard label="Total Income" value={fmtCurrency(totalIncome)} color="green" icon={IconTrendingUp} />
        <StatCard label="Total Expenses" value={fmtCurrency(totalExpenses)} color="red" icon={IconTrendingDown} />
        <StatCard
          label="Net Income"
          value={`${netPositive ? '+' : ''}${fmtCurrency(net)}`}
          color={netPositive ? 'violet' : 'orange'}
          icon={IconScale}
        />
      </SimpleGrid>

      <SimpleGrid cols={2}>
        <Card withBorder p="lg" radius="md" style={{ background: 'white' }}>
          <Group justify="space-between" mb="md">
            <Text fw={600} size="sm">Recent Income</Text>
            <Anchor component={Link} to="/incomes" size="xs" c="dimmed">View all →</Anchor>
          </Group>
          {recentIncomes.length === 0 ? (
            <Text size="sm" c="dimmed">No income yet</Text>
          ) : (
            <Stack gap={0}>
              {recentIncomes.map(i => (
                <Group
                  key={i.id}
                  justify="space-between"
                  py={8}
                  style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}
                >
                  <Box>
                    <Text size="sm">{i.description}</Text>
                    <Text size="xs" c="dimmed">{i.date}</Text>
                  </Box>
                  <Text
                    size="sm"
                    fw={600}
                    c="green"
                    style={{ fontVariantNumeric: 'tabular-nums' }}
                  >
                    +{fmtCurrency(i.amount)}
                  </Text>
                </Group>
              ))}
            </Stack>
          )}
        </Card>

        <Card withBorder p="lg" radius="md" style={{ background: 'white' }}>
          <Group justify="space-between" mb="md">
            <Text fw={600} size="sm">Recent Expenses</Text>
            <Anchor component={Link} to="/expenses" size="xs" c="dimmed">View all →</Anchor>
          </Group>
          {recentExpenses.length === 0 ? (
            <Text size="sm" c="dimmed">No expenses yet</Text>
          ) : (
            <Stack gap={0}>
              {recentExpenses.map(e => (
                <Group
                  key={e.id}
                  justify="space-between"
                  py={8}
                  style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}
                >
                  <Box>
                    <Text size="sm">{e.description}</Text>
                    <Text size="xs" c="dimmed">{e.date}</Text>
                  </Box>
                  <Text
                    size="sm"
                    fw={600}
                    c="red"
                    style={{ fontVariantNumeric: 'tabular-nums' }}
                  >
                    -{fmtCurrency(e.amount)}
                  </Text>
                </Group>
              ))}
            </Stack>
          )}
        </Card>
      </SimpleGrid>

      {monthlyData.length > 0 && (
        <Card withBorder p="lg" radius="md" style={{ background: 'white' }}>
          <Text fw={600} size="sm" mb="md">{new Date().getFullYear()} Monthly Breakdown</Text>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th w={50}>Month</Table.Th>
                <Table.Th>Income</Table.Th>
                <Table.Th>Expenses</Table.Th>
                <Table.Th style={{ textAlign: 'right' }}>Net</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {monthlyData.map(m => (
                <Table.Tr key={m.month}>
                  <Table.Td c="dimmed" fw={500}>{m.label}</Table.Td>
                  <Table.Td>
                    <Group gap="xs" wrap="nowrap">
                      <Box style={{ width: 80, flexShrink: 0 }}>
                        <Box
                          style={{
                            width: `${(m.income / maxMonthlyValue) * 100}%`,
                            minWidth: 2,
                            height: 6,
                            background: 'var(--mantine-color-green-4)',
                            borderRadius: 3,
                          }}
                        />
                      </Box>
                      <Text size="sm" c="green" fw={500} style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {fmtCurrency(m.income)}
                      </Text>
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <Group gap="xs" wrap="nowrap">
                      <Box style={{ width: 80, flexShrink: 0 }}>
                        <Box
                          style={{
                            width: `${(m.expenses / maxMonthlyValue) * 100}%`,
                            minWidth: 2,
                            height: 6,
                            background: 'var(--mantine-color-red-4)',
                            borderRadius: 3,
                          }}
                        />
                      </Box>
                      <Text size="sm" c="red" fw={500} style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {fmtCurrency(m.expenses)}
                      </Text>
                    </Group>
                  </Table.Td>
                  <Table.Td
                    fw={600}
                    c={m.net >= 0 ? 'violet' : 'orange'}
                    style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                  >
                    {m.net >= 0 ? '+' : ''}{fmtCurrency(m.net)}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Card>
      )}

      {propertyBreakdown.length > 0 && (
        <Card withBorder p="lg" radius="md" style={{ background: 'white' }}>
          <Text fw={600} size="sm" mb="md">By Property</Text>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Property</Table.Th>
                <Table.Th style={{ textAlign: 'right' }}>Income</Table.Th>
                <Table.Th style={{ textAlign: 'right' }}>Expenses</Table.Th>
                <Table.Th style={{ textAlign: 'right' }}>Net</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {propertyBreakdown.map(p => (
                <Table.Tr key={p.name}>
                  <Table.Td fw={500}>{p.name}</Table.Td>
                  <Table.Td
                    c="green"
                    fw={500}
                    style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                  >
                    {fmtCurrency(p.income)}
                  </Table.Td>
                  <Table.Td
                    c="red"
                    fw={500}
                    style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                  >
                    {fmtCurrency(p.expenses)}
                  </Table.Td>
                  <Table.Td
                    fw={600}
                    c={p.net >= 0 ? 'violet' : 'orange'}
                    style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                  >
                    {p.net >= 0 ? '+' : ''}{fmtCurrency(p.net)}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Card>
      )}
    </Stack>
  )
}
