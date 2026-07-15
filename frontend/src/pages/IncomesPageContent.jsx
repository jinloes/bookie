import React from 'react';
import { Anchor, Button, Group, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';
import { IncomesFilters } from './IncomesFilters.jsx';
import { IncomesForm } from './IncomesForm.jsx';
import { IncomesImportDrawer } from './IncomesImportDrawer.jsx';
import { IncomesTable } from './IncomesTable.jsx';

export function IncomesPageContent({
  pageActions,
  incomeForm,
  importForm,
  filters,
  finalizedTable,
}) {
  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Income</Title>
        <Group>
          <Button variant="default" onClick={pageActions.openImportForm}>
            Import Venmo CSV
          </Button>
          <Button onClick={pageActions.openCreateForm}>+ Add Income</Button>
        </Group>
      </Group>

      <Text size="sm" c="dimmed">
        Finalized income records live here. New imported items are finalized from the Review Queue.{' '}
        <Anchor component={Link} to="/transactions/review" size="sm">
          Open Review Queue
        </Anchor>
        .
      </Text>

      <IncomesForm incomeForm={incomeForm} />
      <IncomesImportDrawer importForm={importForm} />
      <IncomesFilters filters={filters} />
      <IncomesTable table={finalizedTable} />
    </Stack>
  );
}
