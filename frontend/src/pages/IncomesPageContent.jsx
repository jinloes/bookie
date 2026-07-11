import React from 'react';
import { Button, Group, Stack, Tabs, Text, Title } from '@mantine/core';
import { IncomesFilters } from './IncomesFilters.jsx';
import { IncomesForm } from './IncomesForm.jsx';
import { IncomesImportDrawer } from './IncomesImportDrawer.jsx';
import { IncomesTable } from './IncomesTable.jsx';
import { PendingIncomeReviewDrawer } from './PendingIncomeReviewDrawer.jsx';
import { PendingIncomesPanel } from './PendingIncomesPanel.jsx';

export function IncomesPageContent({
  pageActions,
  incomeForm,
  importForm,
  filters,
  finalizedTable,
  pendingReview,
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
        Finalized income records live here. New email and receipt items are reviewed in the Review
        Queue tab.
      </Text>

      <IncomesForm incomeForm={incomeForm} />
      <IncomesImportDrawer importForm={importForm} />

      <Tabs defaultValue="finalized">
        <Tabs.List>
          <Tabs.Tab value="finalized">Finalized ({finalizedTable.incomes.length})</Tabs.Tab>
          <Tabs.Tab value="pending">Pending ({pendingReview.pendingIncomes.length})</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="finalized" pt="md">
          <IncomesFilters filters={filters} />
          <IncomesTable table={finalizedTable} />
        </Tabs.Panel>

        <Tabs.Panel value="pending" pt="md">
          <PendingIncomesPanel pendingReview={pendingReview} />
        </Tabs.Panel>
      </Tabs>

      <PendingIncomeReviewDrawer pendingReview={pendingReview} />
    </Stack>
  );
}
