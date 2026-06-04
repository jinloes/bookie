import React, { useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Anchor, Stack, Group, Text, Button, Loader } from '@mantine/core';
import { IconRefresh } from '@tabler/icons-react';
import {
  getPendingExpenses,
  getExpenseCategories,
  getProperties,
  getPayers,
} from '../api/index.js';
import { EMAIL_TYPE, EXPENSE_SOURCE, PENDING_STATUS } from '../constants.js';
import PendingItem from './PendingItem.jsx';

export default function PendingExpenses({ onSaved, onCountChange, filterType, filterSource }) {
  const queryClient = useQueryClient();
  // SSE in App.jsx invalidates ['pendingExpenses'] on every status change, so polling here is
  // redundant. If SSE is unreachable the user can still hit Refresh.
  const { data: items = [], isLoading } = useQuery({
    queryKey: ['pendingExpenses'],
    queryFn: getPendingExpenses,
  });
  const { data: categories = [] } = useQuery({
    queryKey: ['expenseCategories'],
    queryFn: getExpenseCategories,
  });
  const { data: properties = [] } = useQuery({ queryKey: ['properties'], queryFn: getProperties });
  const { data: payers = [] } = useQuery({ queryKey: ['payers'], queryFn: getPayers });

  const filteredItems = useMemo(() => {
    let result = items;
    if (filterSource) result = result.filter((i) => i.sourceType === filterSource);
    if (filterType === EMAIL_TYPE.INCOME)
      result = result.filter((i) => i.emailType === EMAIL_TYPE.INCOME);
    else if (filterType === EMAIL_TYPE.EXPENSE)
      result = result.filter((i) => i.emailType !== EMAIL_TYPE.INCOME);
    return result;
  }, [items, filterType, filterSource]);

  useEffect(() => {
    onCountChange?.(filteredItems.filter((i) => i.status === PENDING_STATUS.READY).length);
  }, [filteredItems, onCountChange]);

  const handleSaved = (pendingId, expense) => {
    queryClient.setQueryData(['pendingExpenses'], (prev) =>
      (prev ?? []).filter((i) => i.id !== pendingId)
    );
    onSaved?.(expense);
  };

  const handleDismissed = (id) => {
    queryClient.setQueryData(['pendingExpenses'], (prev) =>
      (prev ?? []).filter((i) => i.id !== id)
    );
  };

  const handlePayerCreated = (newPayer) => {
    queryClient.setQueryData(['payers'], (prev) => [...(prev ?? []), newPayer]);
  };

  const emptyMessage =
    filterSource === EXPENSE_SOURCE.RECEIPT
      ? 'No pending receipt entries'
      : filterType === EMAIL_TYPE.INCOME
        ? 'No pending income'
        : filterType === EMAIL_TYPE.EXPENSE
          ? 'No pending expenses'
          : 'No pending items';

  const contextNote =
    filterType === EMAIL_TYPE.INCOME
      ? 'Showing income items only.'
      : filterType === EMAIL_TYPE.EXPENSE
        ? 'Showing expense items only.'
        : filterSource === EXPENSE_SOURCE.RECEIPT
          ? 'Showing receipt items only.'
          : null;

  if (isLoading)
    return (
      <Stack pt="md">
        <Loader size="sm" />
      </Stack>
    );

  return (
    <Stack gap="sm" pt="md">
      <Group justify="space-between">
        {contextNote ? (
          <Text size="xs" c="dimmed">
            {contextNote}{' '}
            <Anchor component={Link} to="/inbox" size="xs">
              View all in Inbox →
            </Anchor>
          </Text>
        ) : (
          <span />
        )}
        <Button
          variant="subtle"
          size="xs"
          leftSection={<IconRefresh size={14} />}
          onClick={() => queryClient.invalidateQueries({ queryKey: ['pendingExpenses'] })}
        >
          Refresh
        </Button>
      </Group>
      {filteredItems.length === 0 ? (
        <Text c="dimmed" size="sm" ta="center" py="xl">
          {emptyMessage}
        </Text>
      ) : (
        filteredItems.map((item) => (
          <PendingItem
            key={item.id}
            item={item}
            categories={categories}
            properties={properties}
            payers={payers}
            onSaved={handleSaved}
            onDismissed={handleDismissed}
            onPayerCreated={handlePayerCreated}
          />
        ))
      )}
    </Stack>
  );
}
