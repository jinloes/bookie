import React from 'react';
import { Link } from 'react-router-dom';
import {
  Alert,
  Badge,
  Button,
  Card,
  Group,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { IconCheck, IconRefresh } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { getOutlookStatus, listReceipts } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { buildReconciliationState } from '../utils/reconciliation.js';
import { fmtDate } from '../utils/formatters.js';
import { usePendingExpensesQuery, usePendingIncomesQuery } from '../hooks/usePendingQueue.js';
import { SummaryPageSkeleton } from '../components/PageLoadingSkeleton.jsx';

function MetricCard({ label, value, color = 'gray' }) {
  return (
    <Card withBorder>
      <Text size="xs" c="dimmed" fw={600}>
        {label}
      </Text>
      <Text size="xl" fw={800} c={color}>
        {value}
      </Text>
    </Card>
  );
}

export default function Reconciliation() {
  const receiptsQuery = useQuery({
    queryKey: queryKeys.receipts,
    queryFn: listReceipts,
  });
  const pendingQuery = usePendingExpensesQuery();
  const pendingIncomeQuery = usePendingIncomesQuery();
  const outlookStatusQuery = useQuery({
    queryKey: queryKeys.outlookStatus,
    queryFn: getOutlookStatus,
  });

  const loading =
    receiptsQuery.isLoading ||
    pendingQuery.isLoading ||
    pendingIncomeQuery.isLoading ||
    outlookStatusQuery.isLoading;
  const state = buildReconciliationState(receiptsQuery.data ?? [], pendingQuery.data ?? []);
  const pendingIncomeCount = pendingIncomeQuery.data?.length ?? 0;

  const handleRefresh = async () => {
    await Promise.all([
      receiptsQuery.refetch(),
      pendingQuery.refetch(),
      outlookStatusQuery.refetch(),
    ]);
  };

  if (loading) {
    return <SummaryPageSkeleton metricCount={4} cardCount={2} rowCount={4} />;
  }

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <div>
          <Title order={2}>Reconciliation</Title>
          <Text size="sm" c="dimmed">
            Resolve imported items that are not fully matched to final expense/income records.
          </Text>
        </div>
        <Button
          variant="default"
          leftSection={<IconRefresh size={14} />}
          onClick={handleRefresh}
          loading={receiptsQuery.isFetching || pendingQuery.isFetching}
        >
          Refresh
        </Button>
      </Group>

      <SimpleGrid cols={{ base: 1, sm: 2, md: 4 }}>
        <MetricCard
          label="Unresolved total"
          value={state.unresolvedCount + pendingIncomeCount}
          color="orange"
        />
        <MetricCard label="Pending income" value={pendingIncomeCount} color="blue" />
        <MetricCard label="Failed parse items" value={state.failedPending.length} color="red" />
        <MetricCard label="Resolved receipts" value={state.resolvedCount} color="green" />
      </SimpleGrid>

      <Card withBorder>
        <Group justify="space-between" mb="xs">
          <Text fw={600}>Pending items requiring review</Text>
          <Button size="xs" variant="subtle" component={Link} to="/transactions/review">
            Open Review Queue
          </Button>
        </Group>
        {state.readyPending.length === 0 && state.failedPending.length === 0 ? (
          <Text size="sm" c="dimmed">
            No unresolved pending items.
          </Text>
        ) : (
          <Stack gap="xs">
            {state.readyPending.length > 0 && (
              <Text size="sm">
                <Badge color="blue" variant="light" mr={8}>
                  Ready
                </Badge>
                {state.readyPending.length} item{state.readyPending.length !== 1 ? 's' : ''} ready
                for final save.
              </Text>
            )}
            {state.failedPending.map((item) => (
              <Group key={item.id} justify="space-between" align="center" wrap="wrap">
                <Text size="sm">
                  <Badge color="red" variant="light" mr={8}>
                    Failed
                  </Badge>
                  {item.subject || '(no subject)'} — {item.errorMessage || 'Parsing failed'}
                </Text>
                <Button size="xs" variant="default" component={Link} to="/transactions/review">
                  Resolve in Review Queue
                </Button>
              </Group>
            ))}
          </Stack>
        )}
      </Card>

      <Card withBorder p={0}>
        <Group justify="space-between" p="md" pb={0}>
          <Text fw={600}>Unlinked receipts</Text>
          <Button size="xs" variant="subtle" component={Link} to="/transactions/receipts">
            Open Receipts
          </Button>
        </Group>
        {state.unresolvedReceipts.length === 0 ? (
          <Text size="sm" c="dimmed" p="md">
            All receipts are linked to expenses or income.
          </Text>
        ) : (
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Receipt</Table.Th>
                <Table.Th>Uploaded</Table.Th>
                <Table.Th>Action</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {state.unresolvedReceipts.map((receipt) => (
                <Table.Tr key={receipt.id}>
                  <Table.Td>{receipt.name}</Table.Td>
                  <Table.Td c="dimmed">
                    {receipt.uploadedAt ? fmtDate(receipt.uploadedAt) : '—'}
                  </Table.Td>
                  <Table.Td>
                    <Group gap="xs">
                      <Button size="xs" component={Link} to="/transactions/receipts">
                        Open in Receipts
                      </Button>
                      <Button
                        size="xs"
                        variant="default"
                        component={Link}
                        to="/transactions/review"
                      >
                        Open Review Queue
                      </Button>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>

      {state.resolvedCount > 0 && (
        <Alert icon={<IconCheck size={16} />} color="green" variant="light">
          {state.resolvedCount} receipt{state.resolvedCount !== 1 ? 's' : ''} already linked.
        </Alert>
      )}
    </Stack>
  );
}
