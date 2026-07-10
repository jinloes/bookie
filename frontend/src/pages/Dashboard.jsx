import React, { useMemo } from 'react';
import {
  Anchor,
  Box,
  Card,
  Group,
  SimpleGrid,
  Skeleton,
  Stack,
  Table,
  Text,
  Alert,
} from '@mantine/core';
import { Link } from 'react-router-dom';
import {
  IconAlertCircle,
  IconInbox,
  IconScale,
  IconTrendingDown,
  IconTrendingUp,
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import {
  getExpenses,
  getIncomes,
  getPayers,
  getProperties,
  getReceiptSettings,
  getTotalExpenses,
  getTotalIncome,
  getPendingExpenses,
} from '../api/index.js';
import { fmtCurrency, sumByKey } from '../utils/formatters.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { useOutlookStatus } from '../hooks/useOutlookStatus.js';

function StatCard({ label, value, color, icon: Icon, loading }) {
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
            color: 'var(--mantine-color-gray-7)',
          }}
        >
          {label}
        </Text>
      </Group>
      {loading ? (
        <Skeleton height={30} width="60%" />
      ) : (
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
      )}
    </Card>
  );
}

export default function Dashboard() {
  const { data: pendingExpenses = [] } = useQuery({
    queryKey: queryKeys.pendingExpenses,
    queryFn: getPendingExpenses,
  });
  const { data: totalIncomeData, isLoading: l1 } = useQuery({
    queryKey: queryKeys.totalIncome,
    queryFn: getTotalIncome,
  });
  const { data: totalExpensesData, isLoading: l2 } = useQuery({
    queryKey: queryKeys.totalExpenses,
    queryFn: getTotalExpenses,
  });
  const {
    data: incomes = [],
    isLoading: l3,
    error,
  } = useQuery({ queryKey: queryKeys.incomes, queryFn: getIncomes });
  const { data: expenses = [], isLoading: l4 } = useQuery({
    queryKey: queryKeys.expenses,
    queryFn: getExpenses,
  });
  const { data: properties = [] } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const { data: payers = [] } = useQuery({
    queryKey: queryKeys.payers,
    queryFn: getPayers,
  });
  const outlookStatus = useOutlookStatus();
  const { data: receiptSettings } = useQuery({
    queryKey: queryKeys.receiptSettings,
    queryFn: getReceiptSettings,
  });

  const recentIncomes = [...(incomes ?? [])].sort((a, b) => (b.date ?? '').localeCompare(a.date ?? '')).slice(0, 5);
  const recentExpenses = [...(expenses ?? [])].sort((a, b) => (b.date ?? '').localeCompare(a.date ?? '')).slice(0, 5);

  const propertyBreakdown = useMemo(() => {
    const incomeByName = sumByKey(
      incomes,
      (i) => i.property?.name || 'Unassigned',
      (i) => i.amount
    );
    const expensesByName = sumByKey(
      expenses,
      (e) => e.property?.name || 'Unassigned',
      (e) => e.amount
    );
    const names = new Set([...incomeByName.keys(), ...expensesByName.keys()]);
    return [...names]
      .map((name) => {
        const income = incomeByName.get(name) ?? 0;
        const expensesAmt = expensesByName.get(name) ?? 0;
        return { name, income, expenses: expensesAmt, net: income - expensesAmt };
      })
      .sort((a, b) => b.income - a.income);
  }, [incomes, expenses]);

  const monthlyData = useMemo(() => {
    const currentYear = String(new Date().getFullYear());
    const monthKey = (r) => (r.date?.startsWith(currentYear) ? r.date.slice(0, 7) : null);
    const incomeByMonth = sumByKey(incomes, monthKey, (i) => i.amount);
    const expensesByMonth = sumByKey(expenses, monthKey, (e) => e.amount);
    const months = new Set([...incomeByMonth.keys(), ...expensesByMonth.keys()]);
    return [...months].sort().map((month) => {
      const income = incomeByMonth.get(month) ?? 0;
      const expensesAmt = expensesByMonth.get(month) ?? 0;
      return {
        month,
        income,
        expenses: expensesAmt,
        net: income - expensesAmt,
        label: new Date(month + '-01').toLocaleDateString('en-US', { month: 'short' }),
      };
    });
  }, [incomes, expenses]);

  const maxMonthlyValue = useMemo(
    () => monthlyData.reduce((m, x) => Math.max(m, x.income, x.expenses), 1),
    [monthlyData]
  );
  const pendingCount = pendingExpenses.filter((i) => i.status === 'READY').length;
  const setupItems = [
    outlookStatus.status === 'disconnected' ? { label: 'Connect Outlook', to: '/settings' } : null,
    receiptSettings?.folderBase ? null : { label: 'Set receipt folder', to: '/settings' },
    properties.length > 0 ? null : { label: 'Create a property', to: '/properties' },
    payers.length > 0 ? null : { label: 'Create a payer', to: '/payers' },
  ].filter(Boolean);

  if (error)
    return (
      <Alert icon={<IconAlertCircle size={16} />} color="red" title="Error">
        {getErrorMessage(error, 'Could not load dashboard data.')}
      </Alert>
    );

  const totalIncomeVal = totalIncomeData?.total ?? 0;
  const totalExpensesVal = totalExpensesData?.total ?? 0;
  const net = (totalIncomeVal - totalExpensesVal).toFixed(2);
  const netPositive = Number(net) >= 0;

  return (
    <Stack gap="xl">
      {setupItems.length > 0 && (
        <Alert icon={<IconInbox size={16} />} color="blue" title="Finish setup" variant="light">
          <Stack gap={4}>
            <Text size="sm">Bookie works best after a quick setup pass. Do these next:</Text>
            <Group gap="sm">
              {setupItems.map((item) => (
                <Anchor key={item.label} component={Link} to={item.to} size="sm">
                  {item.label}
                </Anchor>
              ))}
            </Group>
          </Stack>
        </Alert>
      )}

      {pendingCount > 0 && (
        <Alert color="orange" variant="light" title="Pending items ready">
          <Text size="sm">
            {pendingCount} item{pendingCount !== 1 ? 's' : ''} are ready to review in the Review Queue tab.{' '}
            <Anchor component={Link} to="/transactions/review" size="sm">
              Open Review Queue
            </Anchor>
          </Text>
        </Alert>
      )}

      <SimpleGrid cols={{ base: 1, md: 3 }}>
        <StatCard
          label="Total Income"
          value={fmtCurrency(totalIncomeVal)}
          color="green"
          icon={IconTrendingUp}
          loading={l1}
        />
        <StatCard
          label="Total Expenses"
          value={fmtCurrency(totalExpensesVal)}
          color="red"
          icon={IconTrendingDown}
          loading={l2}
        />
        <StatCard
          label="Net Income"
          value={`${netPositive ? '+' : ''}${fmtCurrency(net)}`}
          color={netPositive ? 'violet' : 'orange'}
          icon={IconScale}
          loading={l1 || l2}
        />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <Card withBorder p="lg" radius="md" style={{ background: 'white' }}>
          <Group justify="space-between" mb="md">
            <Text fw={600} size="sm">
              Recent Income
            </Text>
            <Anchor component={Link} to="/transactions/income" size="xs" c="dimmed">
              View all →
            </Anchor>
          </Group>
          {l3 ? (
            <Stack gap={8}>
              {[1, 2, 3].map((n) => (
                <Group key={n} justify="space-between" py={8} style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                  <Box style={{ flex: 1 }}>
                    <Skeleton height={14} width="60%" mb={4} />
                    <Skeleton height={11} width="30%" />
                  </Box>
                  <Skeleton height={14} width={60} />
                </Group>
              ))}
            </Stack>
          ) : recentIncomes.length === 0 ? (
            <Text size="sm" c="dimmed">
              No income yet. <Anchor component={Link} to="/transactions/income" size="sm">Add income →</Anchor>
            </Text>
          ) : (
            <Stack gap={0}>
              {recentIncomes.map((i) => (
                <Group
                  key={i.id}
                  justify="space-between"
                  py={8}
                  style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}
                >
                  <Box>
                    <Text size="sm">{i.description}</Text>
                    <Text size="xs" c="dimmed">
                      {i.date}
                    </Text>
                  </Box>
                  <Text size="sm" fw={600} c="green" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    +{fmtCurrency(i.amount)}
                  </Text>
                </Group>
              ))}
            </Stack>
          )}
        </Card>

        <Card withBorder p="lg" radius="md" style={{ background: 'white' }}>
          <Group justify="space-between" mb="md">
            <Text fw={600} size="sm">
              Recent Expenses
            </Text>
            <Anchor component={Link} to="/transactions/expenses" size="xs" c="dimmed">
              View all →
            </Anchor>
          </Group>
          {l4 ? (
            <Stack gap={8}>
              {[1, 2, 3].map((n) => (
                <Group key={n} justify="space-between" py={8} style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                  <Box style={{ flex: 1 }}>
                    <Skeleton height={14} width="60%" mb={4} />
                    <Skeleton height={11} width="30%" />
                  </Box>
                  <Skeleton height={14} width={60} />
                </Group>
              ))}
            </Stack>
          ) : recentExpenses.length === 0 ? (
            <Text size="sm" c="dimmed">
              No expenses yet. <Anchor component={Link} to="/transactions/expenses" size="sm">Add expense →</Anchor>
            </Text>
          ) : (
            <Stack gap={0}>
              {recentExpenses.map((e) => (
                <Group
                  key={e.id}
                  justify="space-between"
                  py={8}
                  style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}
                >
                  <Box>
                    <Text size="sm">{e.description}</Text>
                    <Text size="xs" c="dimmed">
                      {e.date}
                    </Text>
                  </Box>
                  <Text size="sm" fw={600} c="red" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    -{fmtCurrency(e.amount)}
                  </Text>
                </Group>
              ))}
            </Stack>
          )}
        </Card>
      </SimpleGrid>

      <Card withBorder p="lg" radius="md" style={{ background: 'white' }}>
        <Text fw={600} size="sm" mb="md">
          {new Date().getFullYear()} Monthly Breakdown
        </Text>
        {monthlyData.length === 0 ? (
          <Text size="sm" c="dimmed" ta="center" py="lg">
            Add income or expenses to see this year's monthly breakdown.
          </Text>
        ) : (
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
              {monthlyData.map((m) => (
                <Table.Tr key={m.month}>
                  <Table.Td c="dimmed" fw={500}>
                    {m.label}
                  </Table.Td>
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
                      <Text
                        size="sm"
                        c="green"
                        fw={500}
                        style={{ fontVariantNumeric: 'tabular-nums' }}
                      >
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
                      <Text
                        size="sm"
                        c="red"
                        fw={500}
                        style={{ fontVariantNumeric: 'tabular-nums' }}
                      >
                        {fmtCurrency(m.expenses)}
                      </Text>
                    </Group>
                  </Table.Td>
                  <Table.Td
                    fw={600}
                    c={m.net >= 0 ? 'violet' : 'orange'}
                    style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                  >
                    {m.net >= 0 ? '+' : ''}
                    {fmtCurrency(m.net)}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>

      <Card withBorder p="lg" radius="md" style={{ background: 'white' }}>
        <Text fw={600} size="sm" mb="md">
          By Property
        </Text>
        {propertyBreakdown.length === 0 ? (
          <Text size="sm" c="dimmed" ta="center" py="lg">
            Assign income and expenses to a property to see a breakdown here.
          </Text>
        ) : (
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
              {propertyBreakdown.map((p) => (
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
                    {p.net >= 0 ? '+' : ''}
                    {fmtCurrency(p.net)}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>
    </Stack>
  );
}
