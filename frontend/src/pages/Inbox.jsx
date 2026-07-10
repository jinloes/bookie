import React, { useState } from 'react';
import {
  Box,
  Button,
  Center,
  Drawer,
  Group,
  Loader,
  Select,
  Stack,
  Text,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconX } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import PendingExpenses from '../components/PendingExpenses.jsx';
import {
  acceptPendingIncome,
  getProperties,
  getPendingIncomes,
  rejectPendingIncome,
} from '../api/index.js';
import { fmtCurrency } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';
import { COLORS } from '../designTokens.js';

function PendingIncomeSection() {
  const queryClient = useQueryClient();
  const { data: pendingIncomes = [], isLoading } = useQuery({
    queryKey: queryKeys.pendingIncomes,
    queryFn: getPendingIncomes,
  });
  const { data: properties = [] } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const propertyOptions = properties.map((p) => ({ value: String(p.id), label: p.name }));

  const [reviewingId, setReviewingId] = useState(null);
  const [reviewForm, setReviewForm] = useState({ propertyId: null });

  const reviewing = pendingIncomes.find((p) => p.id === reviewingId) ?? null;

  const handleAccept = async () => {
    if (!reviewingId || !reviewing) return;
    try {
      await acceptPendingIncome(reviewingId, {
        amount: reviewing.amount,
        description: reviewing.description,
        date: reviewing.date,
        source: reviewing.source,
        propertyId: reviewForm.propertyId ? Number(reviewForm.propertyId) : null,
        payerId: reviewing.payer?.id || null,
      });
      notifications.show({ title: 'Income accepted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
      queryClient.invalidateQueries({ queryKey: queryKeys.pendingIncomes });
      queryClient.invalidateQueries({ queryKey: queryKeys.totalIncome });
      setReviewingId(null);
    } catch (err) {
      notifications.show({
        title: 'Accept failed',
        message: getErrorMessage(err, 'Could not accept pending income.'),
        color: 'red',
      });
    }
  };

  const handleReject = (id) => {
    modals.openConfirmModal({
      title: 'Reject pending income',
      children: <Text size="sm">This pending income record will be permanently deleted.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await rejectPendingIncome(id);
          queryClient.invalidateQueries({ queryKey: queryKeys.pendingIncomes });
          notifications.show({ title: 'Income rejected', color: 'green' });
        } catch (err) {
          notifications.show({
            title: 'Reject failed',
            message: getErrorMessage(err, 'Could not reject pending income.'),
            color: 'red',
          });
        }
      },
    });
  };

  if (isLoading) return <Center py="sm"><Loader size="sm" /></Center>;
  if (pendingIncomes.length === 0) return null;

  return (
    <>
      <Stack gap="xs">
        <Text fw={600} size="sm">Pending Income ({pendingIncomes.length})</Text>
        <Text size="xs" c="dimmed">
          Imported from Venmo CSV. Review and accept each record to finalize it.
        </Text>
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
                <Text fw={600} size="sm">{p.payer?.name || '—'}</Text>
                <Text size="xs" c="dimmed">
                  {p.date} • {fmtCurrency(p.amount)}
                  {p.property && <> • <strong>{p.property.name}</strong></>}
                </Text>
              </div>
              <Button
                size="sm"
                onClick={() => {
                  setReviewingId(p.id);
                  setReviewForm({ propertyId: p.property?.id ? String(p.property.id) : null });
                }}
              >
                Review
              </Button>
            </Group>
            <Text size="sm" c="dimmed" style={{ wordBreak: 'break-word' }}>{p.description}</Text>
          </Box>
        ))}
      </Stack>

      <Drawer
        opened={reviewingId !== null}
        onClose={() => setReviewingId(null)}
        title="Review Pending Income"
        position="right"
        size="lg"
        styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
      >
        {reviewing && (
          <>
            <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
              <Text size="xs" c="dimmed" style={{ fontStyle: 'italic' }}>
                Imported from Venmo CSV. Accepting will add this to finalized income records.
              </Text>
              <div>
                <Text size="sm" c="dimmed">Payer</Text>
                <Text fw={500}>{reviewing.payer?.name || '—'}</Text>
              </div>
              <div>
                <Text size="sm" c="dimmed">Date</Text>
                <Text fw={500}>{reviewing.date}</Text>
              </div>
              <div>
                <Text size="sm" c="dimmed">Amount</Text>
                <Text fw={500} c="green">+{fmtCurrency(reviewing.amount)}</Text>
              </div>
              <div>
                <Text size="sm" c="dimmed">Description</Text>
                <Text fw={500} style={{ wordBreak: 'break-word' }}>{reviewing.description}</Text>
              </div>
              <Select
                label="Property"
                description={
                  reviewing.property
                    ? 'Auto-detected from payer history — adjust if incorrect'
                    : 'No property detected — select one if applicable'
                }
                value={reviewForm.propertyId}
                onChange={(val) => setReviewForm({ propertyId: val })}
                data={propertyOptions}
                clearable
                placeholder="— None —"
              />
            </Stack>
            <Box pt="md" style={{ borderTop: `1px solid ${COLORS.BORDER}`, flexShrink: 0 }}>
              <Group justify="space-between">
                <Group>
                  <Button onClick={handleAccept} leftSection={<IconCheck size={16} />}>
                    Accept
                  </Button>
                  <Button variant="default" onClick={() => setReviewingId(null)}>
                    Cancel
                  </Button>
                </Group>
                <Button
                  variant="subtle"
                  color="red"
                  leftSection={<IconX size={16} />}
                  onClick={() => {
                    setReviewingId(null);
                    handleReject(reviewingId);
                  }}
                >
                  Reject
                </Button>
              </Group>
            </Box>
          </>
        )}
      </Drawer>
    </>
  );
}

export default function Inbox() {
  const queryClient = useQueryClient();

  const handleSaved = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
    queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
    queryClient.invalidateQueries({ queryKey: queryKeys.totalExpenses });
    queryClient.invalidateQueries({ queryKey: queryKeys.totalIncome });
  };

  return (
    <Stack gap="lg">
      <PendingIncomeSection />
      <PendingExpenses onSaved={handleSaved} onCountChange={() => {}} />
    </Stack>
  );
}
