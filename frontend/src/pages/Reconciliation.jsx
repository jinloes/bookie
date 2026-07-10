import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Group,
  Loader,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconRefresh } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getOutlookStatus, listReceipts, parseReceipt, retryPendingExpense } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { buildReconciliationState } from '../utils/reconciliation.js';
import { fmtDate } from '../utils/formatters.js';
import { usePendingExpensesQuery, usePendingIncomesQuery } from '../hooks/usePendingQueue.js';

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
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [parsingReceiptId, setParsingReceiptId] = useState(null);
  const [retryingPendingId, setRetryingPendingId] = useState(null);

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

  const handleQueueReceipt = async (itemId) => {
    setParsingReceiptId(itemId);
    try {
      await parseReceipt(itemId);
      notifications.show({ title: 'Receipt queued', color: 'blue' });
      await queryClient.invalidateQueries({ queryKey: queryKeys.pendingExpenses });
      await queryClient.invalidateQueries({ queryKey: queryKeys.receipts });
      navigate('/transactions/review');
    } catch (err) {
      notifications.show({
        title: 'Failed to queue receipt',
        message: getErrorMessage(err, 'Please retry or add the entry manually.'),
        color: 'red',
      });
    } finally {
      setParsingReceiptId(null);
    }
  };

  const handleRetryFailed = async (id) => {
    setRetryingPendingId(id);
    try {
      await retryPendingExpense(id);
      await queryClient.invalidateQueries({ queryKey: queryKeys.pendingExpenses });
    } catch (err) {
      notifications.show({
        title: 'Retry failed',
        message: getErrorMessage(err, 'Could not retry parsing.'),
        color: 'red',
      });
    } finally {
      setRetryingPendingId(null);
    }
  };

  if (loading) {
    return (
      <Stack gap="lg">
        <Title order={2}>Reconciliation</Title>
        <Loader size="sm" />
      </Stack>
    );
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
                <Button
                  size="xs"
                  variant="default"
                  loading={retryingPendingId === item.id}
                  onClick={() => handleRetryFailed(item.id)}
                >
                  Retry Parse
                </Button>
              </Group>
            ))}
          </Stack>
        )}
      </Card>

      <Card withBorder p={0}>
        <Group justify="space-between" p="md" pb={0}>
          <Text fw={600}>Unlinked receipts</Text>
          <Anchor component={Link} to="/transactions/receipts" size="sm">
            Open Receipts
          </Anchor>
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
                      <Button
                        size="xs"
                        loading={parsingReceiptId === receipt.id}
                        onClick={() => handleQueueReceipt(receipt.id)}
                      >
                        Create Entry
                      </Button>
                      <Button
                        size="xs"
                        variant="default"
                        component={Link}
                        to="/transactions/expenses"
                      >
                        Manual Entry
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
