import React from 'react';
import { Badge, Box, Button, Card, Group, Stack, Text } from '@mantine/core';
import { COLORS } from '../designTokens.js';
import { fmtCurrency } from '../utils/formatters.js';
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
  const count = finalizedTable.incomes.length;
  const totalCount = finalizedTable.totalCount ?? count;
  const totalAmount = finalizedTable.totalAmount ?? 0;

  return (
    <Stack gap="md">
      <IncomesForm incomeForm={incomeForm} />
      <IncomesImportDrawer importForm={importForm} />

      <Card withBorder p={0}>
        <Box p="md" style={{ borderBottom: `1px solid ${COLORS.BORDER}` }}>
          <Stack gap="sm">
            <Group justify="space-between" align="center" wrap="wrap">
              <Group gap="xs">
                <Badge color="gray" variant="light">
                  {filters.hasActiveFilters
                    ? `${count} of ${totalCount} income records`
                    : `${count} income record${count === 1 ? '' : 's'}`}
                </Badge>
                {count > 0 && (
                  <Text size="sm" fw={600} c="green" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    +{fmtCurrency(totalAmount)}
                  </Text>
                )}
              </Group>
              <Group gap="xs">
                <Button size="sm" variant="default" onClick={pageActions.openImportForm}>
                  Import statement
                </Button>
                <Button size="sm" onClick={pageActions.openCreateForm}>
                  + Add Income
                </Button>
              </Group>
            </Group>
            <IncomesFilters filters={filters} />
          </Stack>
        </Box>
        <IncomesTable table={finalizedTable} />
      </Card>
    </Stack>
  );
}
