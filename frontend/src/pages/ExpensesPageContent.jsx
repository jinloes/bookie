import React from 'react';
import { Badge, Box, Button, Card, Group, Stack, Text } from '@mantine/core';
import { COLORS } from '../designTokens.js';
import { fmtCurrency } from '../utils/formatters.js';
import { ExpensesFilters } from './ExpensesFilters.jsx';
import { ExpensesForm } from './ExpensesForm.jsx';
import { ExpensesTable } from './ExpensesTable.jsx';

export function ExpensesPageContent({ pageActions, expenseForm, filters, table }) {
  const count = table.expenses.length;
  const totalCount = table.totalCount ?? count;
  const totalAmount = table.totalAmount ?? 0;

  return (
    <Stack gap="md">
      <ExpensesForm expenseForm={expenseForm} />

      <Card withBorder p={0}>
        <Box p="md" style={{ borderBottom: `1px solid ${COLORS.BORDER}` }}>
          <Stack gap="sm">
            <Group justify="space-between" align="center" wrap="wrap">
              <Group gap="xs">
                <Badge color="gray" variant="light">
                  {filters.hasActiveFilters
                    ? `${count} of ${totalCount} expenses`
                    : `${count} expense${count === 1 ? '' : 's'}`}
                </Badge>
                {count > 0 && (
                  <Text size="sm" fw={600} c="red" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    -{fmtCurrency(totalAmount)}
                  </Text>
                )}
              </Group>
              <Button size="sm" onClick={pageActions.openCreateForm}>
                + Add Expense
              </Button>
            </Group>
            <ExpensesFilters filters={filters} />
          </Stack>
        </Box>
        <ExpensesTable table={table} />
      </Card>
    </Stack>
  );
}
