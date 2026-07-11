import React from 'react';
import { Box, Button, Center, Group, Loader, Stack, Text } from '@mantine/core';
import { IconCheck } from '@tabler/icons-react';
import { fmtCurrency } from '../utils/formatters.js';

export function PendingIncomesPanel({ pendingReview }) {
  if (pendingReview.loading) {
    return (
      <Center py="xl">
        <Loader />
      </Center>
    );
  }

  if (pendingReview.pendingIncomes.length === 0) {
    return (
      <Text ta="center" c="dimmed" py="xl" size="sm">
        No pending income records
      </Text>
    );
  }

  return (
    <Stack gap="md">
      <Group justify="flex-end">
        <Button
          size="xs"
          variant="light"
          color="green"
          leftSection={<IconCheck size={14} />}
          onClick={pendingReview.onAcceptAll}
        >
          Accept All (
          {pendingReview.pendingIncomes.filter((item) => item.status === 'READY').length})
        </Button>
      </Group>
      {pendingReview.pendingIncomes.map((pendingIncome) => (
        <Box
          key={pendingIncome.id}
          p="md"
          style={{
            border: '1px solid var(--mantine-color-gray-3)',
            borderRadius: 'var(--mantine-radius-md)',
          }}
        >
          <Group justify="space-between" mb="xs">
            <div>
              <Text fw={600} size="sm">
                {pendingIncome.payer?.name || '—'}
              </Text>
              <Text size="xs" c="dimmed">
                {pendingIncome.date} • {fmtCurrency(pendingIncome.amount)}
              </Text>
            </div>
            <Button size="sm" onClick={() => pendingReview.onOpenReview(pendingIncome)}>
              Review
            </Button>
          </Group>
          <Text size="sm" c="dimmed" style={{ wordBreak: 'break-word' }}>
            {pendingIncome.description}
          </Text>
          {pendingIncome.property && (
            <Text size="xs" c="dimmed" mt={4}>
              Auto-detected property: <strong>{pendingIncome.property.name}</strong>
            </Text>
          )}
        </Box>
      ))}
    </Stack>
  );
}
