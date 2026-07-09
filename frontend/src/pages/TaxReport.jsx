import React, { useMemo, useState } from 'react';
import {
  Stack,
  Group,
  Title,
  Select,
  Text,
  Button,
  Card,
  Table,
  Divider,
  Badge,
  Loader,
  Center,
  SimpleGrid,
} from '@mantine/core';
import { IconDownload } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { getIncomes, getExpenses, getExpenseCategories, getProperties } from '../api/index.js';
import { fmtCurrency } from '../utils/formatters.js';
import { queryKeys } from '../queryKeys.js';

function downloadCsv(filename, rows) {
  const csv = rows.map((r) => r.map((c) => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export default function TaxReport() {
  const currentYear = String(new Date().getFullYear());
  const [selectedYear, setSelectedYear] = useState(currentYear);

  const { data: incomes = [], isLoading: incomesLoading } = useQuery({
    queryKey: queryKeys.incomes,
    queryFn: getIncomes,
  });
  const { data: expenses = [], isLoading: expensesLoading } = useQuery({
    queryKey: queryKeys.expenses,
    queryFn: getExpenses,
  });
  const { data: categories = [] } = useQuery({
    queryKey: queryKeys.categories,
    queryFn: getExpenseCategories,
  });
  const { data: properties = [] } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });

  const yearOptions = useMemo(() => {
    const years = new Set([
      ...incomes.map((i) => i.date?.slice(0, 4)),
      ...expenses.map((e) => e.date?.slice(0, 4)),
    ]);
    return [...years]
      .filter(Boolean)
      .sort()
      .reverse()
      .map((y) => ({ value: y, label: y }));
  }, [incomes, expenses]);

  const filteredIncomes = useMemo(
    () => incomes.filter((i) => i.date?.startsWith(selectedYear)),
    [incomes, selectedYear]
  );
  const filteredExpenses = useMemo(
    () => expenses.filter((e) => e.date?.startsWith(selectedYear)),
    [expenses, selectedYear]
  );

  // Income grouped by property
  const incomeByProperty = useMemo(() => {
    const map = new Map();
    // Include all known properties
    properties.forEach((p) => map.set(p.id, { name: p.name, total: 0 }));
    map.set(null, { name: 'No property', total: 0 });
    filteredIncomes.forEach((i) => {
      const key = i.property?.id ?? null;
      if (!map.has(key)) {
        map.set(key, { name: i.property?.name ?? 'No property', total: 0 });
      }
      map.get(key).total += i.amount ?? 0;
    });
    return [...map.entries()]
      .filter(([, v]) => v.total > 0)
      .map(([id, v]) => ({ id, ...v }))
      .sort((a, b) => b.total - a.total);
  }, [filteredIncomes, properties]);

  const totalIncome = useMemo(() => incomeByProperty.reduce((s, r) => s + r.total, 0), [incomeByProperty]);

  // Expenses grouped by Schedule E category line
  const expenseByCategory = useMemo(() => {
    const categoryMap = Object.fromEntries(categories.map((c) => [c.value, c]));
    const map = new Map();
    filteredExpenses.forEach((e) => {
      const cat = categoryMap[e.category];
      const key = e.category ?? 'OTHER';
      if (!map.has(key)) {
        map.set(key, {
          key,
          label: cat?.label ?? e.category ?? 'Other',
          scheduleELine: cat?.scheduleELine ?? 19,
          total: 0,
        });
      }
      map.get(key).total += e.amount ?? 0;
    });
    return [...map.values()].sort((a, b) => a.scheduleELine - b.scheduleELine);
  }, [filteredExpenses, categories]);

  const totalExpenses = useMemo(() => expenseByCategory.reduce((s, r) => s + r.total, 0), [expenseByCategory]);

  // Net income per property
  const netByProperty = useMemo(() => {
    const expensesByProperty = new Map();
    filteredExpenses.forEach((e) => {
      const key = e.property?.id ?? null;
      expensesByProperty.set(key, (expensesByProperty.get(key) ?? 0) + (e.amount ?? 0));
    });
    return incomeByProperty.map((r) => ({
      ...r,
      expenses: expensesByProperty.get(r.id) ?? 0,
      net: r.total - (expensesByProperty.get(r.id) ?? 0),
    }));
  }, [incomeByProperty, filteredExpenses]);

  const handleExportCsv = () => {
    const rows = [
      [`Bookie Tax Report — ${selectedYear}`],
      [],
      ['INCOME BY PROPERTY'],
      ['Property', 'Gross Income'],
      ...incomeByProperty.map((r) => [r.name, r.total.toFixed(2)]),
      ['TOTAL INCOME', totalIncome.toFixed(2)],
      [],
      ['EXPENSES BY SCHEDULE E CATEGORY'],
      ['Schedule E Line', 'Category', 'Amount'],
      ...expenseByCategory.map((r) => [r.scheduleELine, r.label, r.total.toFixed(2)]),
      ['TOTAL EXPENSES', '', totalExpenses.toFixed(2)],
      [],
      ['NET INCOME BY PROPERTY'],
      ['Property', 'Gross Income', 'Expenses', 'Net Income'],
      ...netByProperty.map((r) => [r.name, r.total.toFixed(2), r.expenses.toFixed(2), r.net.toFixed(2)]),
      ['NET INCOME', totalIncome.toFixed(2), totalExpenses.toFixed(2), (totalIncome - totalExpenses).toFixed(2)],
    ];
    downloadCsv(`bookie-schedule-e-${selectedYear}.csv`, rows);
  };

  if (incomesLoading || expensesLoading) {
    return (
      <Center h={200}>
        <Loader />
      </Center>
    );
  }

  const noData = filteredIncomes.length === 0 && filteredExpenses.length === 0;

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-start">
        <div>
          <Title order={2}>Tax Report</Title>
          <Text size="sm" c="dimmed">
            Schedule E summary — income and expenses by property and category.
          </Text>
        </div>
        <Group>
          <Select
            value={selectedYear}
            onChange={(v) => setSelectedYear(v ?? currentYear)}
            data={yearOptions.length > 0 ? yearOptions : [{ value: currentYear, label: currentYear }]}
            size="sm"
            style={{ width: 100 }}
          />
          <Button
            leftSection={<IconDownload size={16} />}
            variant="default"
            disabled={noData}
            onClick={handleExportCsv}
          >
            Export CSV
          </Button>
        </Group>
      </Group>

      {noData ? (
        <Text ta="center" c="dimmed" py="xl">
          No income or expense records for {selectedYear}.
        </Text>
      ) : (
        <>
          <SimpleGrid cols={{ base: 1, sm: 3 }}>
            <Card withBorder>
              <Text size="xs" c="dimmed" fw={600} tt="uppercase">
                Gross Income
              </Text>
              <Text size="xl" fw={800} c="green">
                {fmtCurrency(totalIncome)}
              </Text>
            </Card>
            <Card withBorder>
              <Text size="xs" c="dimmed" fw={600} tt="uppercase">
                Total Expenses
              </Text>
              <Text size="xl" fw={800} c="red">
                {fmtCurrency(totalExpenses)}
              </Text>
            </Card>
            <Card withBorder>
              <Text size="xs" c="dimmed" fw={600} tt="uppercase">
                Net Income
              </Text>
              <Text
                size="xl"
                fw={800}
                c={totalIncome - totalExpenses >= 0 ? 'green' : 'red'}
              >
                {fmtCurrency(totalIncome - totalExpenses)}
              </Text>
            </Card>
          </SimpleGrid>

          <Card withBorder p={0}>
            <Text fw={600} p="md" pb={0}>
              Income by Property
            </Text>
            <Table mt="xs">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Property</Table.Th>
                  <Table.Th style={{ textAlign: 'right' }}>Gross Income</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {incomeByProperty.length === 0 ? (
                  <Table.Tr>
                    <Table.Td colSpan={2}>
                      <Text size="sm" c="dimmed" ta="center" py="md">
                        No income in {selectedYear}
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                ) : (
                  <>
                    {incomeByProperty.map((r) => (
                      <Table.Tr key={r.id ?? 'none'}>
                        <Table.Td>{r.name}</Table.Td>
                        <Table.Td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }} fw={500} c="green">
                          {fmtCurrency(r.total)}
                        </Table.Td>
                      </Table.Tr>
                    ))}
                    <Table.Tr style={{ borderTop: '2px solid var(--mantine-color-gray-3)' }}>
                      <Table.Td fw={700}>Total</Table.Td>
                      <Table.Td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }} fw={700} c="green">
                        {fmtCurrency(totalIncome)}
                      </Table.Td>
                    </Table.Tr>
                  </>
                )}
              </Table.Tbody>
            </Table>
          </Card>

          <Card withBorder p={0}>
            <Group p="md" pb={0} justify="space-between">
              <Text fw={600}>Expenses by Schedule E Category</Text>
              <Text size="xs" c="dimmed">IRS Schedule E, Part I</Text>
            </Group>
            <Table mt="xs">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th w={80}>Line</Table.Th>
                  <Table.Th>Category</Table.Th>
                  <Table.Th style={{ textAlign: 'right' }}>Amount</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {expenseByCategory.length === 0 ? (
                  <Table.Tr>
                    <Table.Td colSpan={3}>
                      <Text size="sm" c="dimmed" ta="center" py="md">
                        No expenses in {selectedYear}
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                ) : (
                  <>
                    {expenseByCategory.map((r) => (
                      <Table.Tr key={r.key}>
                        <Table.Td c="dimmed">
                          <Badge variant="outline" size="sm" color="gray">
                            {r.scheduleELine}
                          </Badge>
                        </Table.Td>
                        <Table.Td>{r.label}</Table.Td>
                        <Table.Td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }} c="red">
                          {fmtCurrency(r.total)}
                        </Table.Td>
                      </Table.Tr>
                    ))}
                    <Table.Tr style={{ borderTop: '2px solid var(--mantine-color-gray-3)' }}>
                      <Table.Td />
                      <Table.Td fw={700}>Total</Table.Td>
                      <Table.Td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }} fw={700} c="red">
                        {fmtCurrency(totalExpenses)}
                      </Table.Td>
                    </Table.Tr>
                  </>
                )}
              </Table.Tbody>
            </Table>
          </Card>

          <Card withBorder p={0}>
            <Text fw={600} p="md" pb={0}>
              Net Income by Property
            </Text>
            <Table mt="xs">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Property</Table.Th>
                  <Table.Th style={{ textAlign: 'right' }}>Gross Income</Table.Th>
                  <Table.Th style={{ textAlign: 'right' }}>Expenses</Table.Th>
                  <Table.Th style={{ textAlign: 'right' }}>Net Income</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {netByProperty.map((r) => (
                  <Table.Tr key={r.id ?? 'none'}>
                    <Table.Td>{r.name}</Table.Td>
                    <Table.Td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }} c="green">
                      {fmtCurrency(r.total)}
                    </Table.Td>
                    <Table.Td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }} c="red">
                      {fmtCurrency(r.expenses)}
                    </Table.Td>
                    <Table.Td
                      style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                      fw={600}
                      c={r.net >= 0 ? 'green' : 'red'}
                    >
                      {fmtCurrency(r.net)}
                    </Table.Td>
                  </Table.Tr>
                ))}
                <Table.Tr style={{ borderTop: '2px solid var(--mantine-color-gray-3)' }}>
                  <Table.Td fw={700}>Total</Table.Td>
                  <Table.Td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }} fw={700} c="green">
                    {fmtCurrency(totalIncome)}
                  </Table.Td>
                  <Table.Td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }} fw={700} c="red">
                    {fmtCurrency(totalExpenses)}
                  </Table.Td>
                  <Table.Td
                    style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                    fw={700}
                    c={totalIncome - totalExpenses >= 0 ? 'green' : 'red'}
                  >
                    {fmtCurrency(totalIncome - totalExpenses)}
                  </Table.Td>
                </Table.Tr>
              </Table.Tbody>
            </Table>
          </Card>

          <Divider />
          <Text size="xs" c="dimmed">
            Schedule E line numbers correspond to IRS Schedule E (Supplemental Income and Loss), Part I.
            Consult a tax professional before filing.
          </Text>
        </>
      )}
    </Stack>
  );
}
