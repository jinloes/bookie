import React from 'react';
import { Box, Button, Center, Group, Loader, Stack, Text } from '@mantine/core';
import { IconCheck } from '@tabler/icons-react';
import { fmtCurrency } from '../utils/formatters.js';

export function PendingIncomesPanel({
  pendingLoading,
  pendingIncomes,
  handleAcceptAllPending,
  onReview,
}) {
  if (pendingLoading) {
    return (
      <Center py="xl">
        <Loader />
      </Center>
    );
  }

  if (pendingIncomes.length === 0) {
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
          onClick={handleAcceptAllPending}
        >
          Accept All ({pendingIncomes.filter((p) => p.status === 'READY').length})
        </Button>
      </Group>
      {pendingIncomes.map((p) => (
        <Box
          key={p.id}
          p="md"
          style={{
            border: '1px solid var(--mantine-color-gray-3)',
            borderRadius: 'var(--mantine-radius-md)',
          }}
        >
          <Group justify="space-between" mb="xs">
            <div>
              <Text fw={600} size="sm">
                {p.payer?.name || '—'}
              </Text>
              <Text size="xs" c="dimmed">
                {p.date} • {fmtCurrency(p.amount)}
              </Text>
            </div>
            <Button size="sm" onClick={() => onReview(p)}>
              Review
            </Button>
          </Group>
          <Text size="sm" c="dimmed" style={{ wordBreak: 'break-word' }}>
            {p.description}
          </Text>
          {p.property && (
            <Text size="xs" c="dimmed" mt={4}>
              Auto-detected property: <strong>{p.property.name}</strong>
            </Text>
          )}
        </Box>
      ))}
    </Stack>
  );
}
