import React, { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Stack, Group, Text, Button, Loader, SegmentedControl, Select } from '@mantine/core';
import { IconRefresh } from '@tabler/icons-react';
import { getCounterparties, getFinancialActivities, getProperties } from '../api/index.js';
import { EXPENSE_SOURCE, PENDING_STATUS } from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { useInboxQuery } from '../hooks/usePendingQueue.js';
import PendingItem from './PendingItem.jsx';

const ORIGIN_OPTIONS = [
  { value: EXPENSE_SOURCE.OUTLOOK_EMAIL, label: 'Outlook email' },
  { value: EXPENSE_SOURCE.RECEIPT, label: 'Receipt' },
  { value: 'VENMO', label: 'Venmo CSV' },
  { value: 'BANK', label: 'Bank statement' },
];

export default function InboxQueue({ onSaved, toolbarActions = null }) {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [originFilter, setOriginFilter] = useState(null);
  const { data: items = [], isLoading } = useInboxQuery();
  const { data: properties = [] } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const { data: payers = [] } = useQuery({
    queryKey: queryKeys.counterparties,
    queryFn: getCounterparties,
  });
  const { data: activities = [] } = useQuery({
    queryKey: queryKeys.financialActivities,
    queryFn: getFinancialActivities,
  });

  const counts = useMemo(() => {
    let ready = 0;
    let failed = 0;
    items.forEach((item) => {
      if (item.state === PENDING_STATUS.READY) ready += 1;
      else if (item.state === PENDING_STATUS.FAILED) failed += 1;
    });
    return { all: items.length, ready, failed };
  }, [items]);

  const visibleItems = useMemo(
    () =>
      items.filter((item) => {
        if (statusFilter === 'READY' && item.state !== PENDING_STATUS.READY) return false;
        if (statusFilter === 'FAILED' && item.state !== PENDING_STATUS.FAILED) return false;
        if (originFilter && item.origin !== originFilter) return false;
        return true;
      }),
    [items, originFilter, statusFilter]
  );

  const firstReadyId = useMemo(
    () => visibleItems.find((item) => item.state === PENDING_STATUS.READY)?.id ?? null,
    [visibleItems]
  );

  const handleSaved = (inboxItemId, savedItem) => {
    queryClient.setQueryData(queryKeys.inbox, (/** @type {any[] | undefined} */ prev) =>
      (prev ?? []).filter((i) => i.id !== inboxItemId)
    );
    onSaved?.(savedItem);
  };

  const handleDismissed = (id) => {
    queryClient.setQueryData(queryKeys.inbox, (/** @type {any[] | undefined} */ prev) =>
      (prev ?? []).filter((i) => i.id !== id)
    );
  };

  const handlePayerCreated = (newPayer) => {
    queryClient.setQueryData(queryKeys.counterparties, (/** @type {any[] | undefined} */ prev) => [
      ...(prev ?? []),
      newPayer,
    ]);
  };

  const handleRetried = (id, requeued) => {
    queryClient.setQueryData(queryKeys.inbox, (/** @type {any[] | undefined} */ prev) =>
      (prev ?? []).map((item) =>
        item.id === id
          ? {
              ...item,
              ...requeued,
              state: requeued?.state ?? PENDING_STATUS.QUEUED,
              errorMessage: null,
            }
          : item
      )
    );
  };

  if (isLoading)
    return (
      <Stack pt="md">
        <Loader size="sm" />
      </Stack>
    );

  const hasFilters = statusFilter !== 'ALL' || Boolean(originFilter);

  return (
    <Stack gap="sm">
      <Group justify="space-between" align="center" wrap="wrap" gap="xs">
        <Group gap="xs" wrap="wrap">
          <SegmentedControl
            size="xs"
            aria-label="Filter queue by status"
            value={statusFilter}
            onChange={setStatusFilter}
            data={[
              { value: 'ALL', label: `All (${counts.all})` },
              { value: 'READY', label: `Ready (${counts.ready})` },
              { value: 'FAILED', label: `Failed (${counts.failed})` },
            ]}
          />
          <Select
            size="xs"
            aria-label="Filter queue by source"
            placeholder="All sources"
            clearable
            data={ORIGIN_OPTIONS}
            value={originFilter}
            onChange={setOriginFilter}
            style={{ width: 165 }}
          />
        </Group>
        <Group gap="xs" wrap="wrap">
          {toolbarActions}
          <Button
            variant="subtle"
            size="xs"
            leftSection={<IconRefresh size={14} />}
            onClick={() => queryClient.invalidateQueries({ queryKey: queryKeys.inbox })}
          >
            Refresh
          </Button>
        </Group>
      </Group>
      {visibleItems.length === 0 ? (
        <Text c="dimmed" size="sm" ta="center" py="xl">
          {items.length === 0 || !hasFilters
            ? 'No items in the Review Queue'
            : 'No items match the current Review Queue filters'}
        </Text>
      ) : (
        visibleItems.map((item) => (
          <PendingItem
            key={item.id}
            item={item}
            properties={properties}
            payers={payers}
            activities={activities}
            defaultExpanded={item.id === firstReadyId}
            onSaved={handleSaved}
            onDismissed={handleDismissed}
            onPayerCreated={handlePayerCreated}
            onRetried={handleRetried}
          />
        ))
      )}
    </Stack>
  );
}
