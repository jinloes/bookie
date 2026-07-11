import React from 'react';
import { Button, Group, Stack, Text, Title } from '@mantine/core';
import { ExpensesFilters } from './ExpensesFilters.jsx';
import { ExpensesForm } from './ExpensesForm.jsx';
import { ExpensesTable } from './ExpensesTable.jsx';

export function ExpensesPageContent({ pageActions, expenseForm, filters, table }) {
  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Expenses</Title>
        <Button onClick={pageActions.openCreateForm}>+ Add Expense</Button>
      </Group>

      <Text size="sm" c="dimmed">
        Finalized expenses live here. New receipt and email items are reviewed in the Review Queue
        tab.
      </Text>

      <ExpensesForm expenseForm={expenseForm} />
      <ExpensesFilters filters={filters} />
      <ExpensesTable table={table} />
    </Stack>
  );
}
