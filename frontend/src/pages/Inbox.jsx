import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Center,
  Drawer,
  Group,
  Loader,
  NumberInput,
  Select,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconX } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import PendingExpenses from '../components/PendingExpenses.jsx';
import { getFinancialActivities, getFinancialCategories, getProperties } from '../api/index.js';
import { fmtCurrency } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';
import { COLORS } from '../designTokens.js';
import { usePendingIncomesQuery } from '../hooks/usePendingQueue.js';
import { usePendingIncomeActions } from '../hooks/usePendingIncomeActions.js';

function PendingIncomeSection() {
  const { savePendingIncome, deletePendingIncome } = usePendingIncomeActions();
  const { data: pendingIncomes = [], isLoading } = usePendingIncomesQuery();
  const { data: properties = [] } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const propertyOptions = properties.map((p) => ({ value: String(p.id), label: p.name }));
  const { data: activities = [] } = useQuery({
    queryKey: queryKeys.financialActivities,
    queryFn: getFinancialActivities,
  });
  const activityOptions = activities
    .filter((activity) => activity.active)
    .map((activity) => ({ value: String(activity.id), label: activity.name }));

  const [reviewingId, setReviewingId] = useState(null);
  const [reviewForm, setReviewForm] = useState({
    amount: '',
    description: '',
    date: '',
    source: '',
    propertyId: null,
    activityId: null,
    categoryId: null,
  });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const { data: compatibleCategories = [] } = useQuery({
    queryKey: queryKeys.financialCategories('INCOME', reviewForm.activityId),
    queryFn: () => getFinancialCategories('INCOME', reviewForm.activityId),
    enabled: Boolean(reviewForm.activityId),
  });

  const reviewing = pendingIncomes.find((p) => p.id === reviewingId) ?? null;

  const handleAccept = async () => {
    if (!reviewingId || !reviewing) return;
    setSaving(true);
    setSaveError(null);
    try {
      await savePendingIncome(reviewingId, reviewForm, reviewing.payer?.id || null);
      notifications.show({ title: 'Income saved', color: 'green' });
      setReviewingId(null);
    } catch (err) {
      setSaveError(getErrorMessage(err, 'Could not save pending income.'));
    } finally {
      setSaving(false);
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
          await deletePendingIncome(id);
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

  if (isLoading)
    return (
      <Center py="sm">
        <Loader size="sm" />
      </Center>
    );
  if (pendingIncomes.length === 0) return null;

  return (
    <>
      <Stack gap="xs">
        <Text fw={600} size="sm">
          Pending Income ({pendingIncomes.length})
        </Text>
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
                <Text fw={600} size="sm">
                  {p.payer?.name || '—'}
                </Text>
                <Text size="xs" c="dimmed">
                  {p.date} • {fmtCurrency(p.amount)}
                  {p.property && (
                    <>
                      {' '}
                      • <strong>{p.property.name}</strong>
                    </>
                  )}
                </Text>
              </div>
              <Button
                size="sm"
                onClick={() => {
                  setReviewingId(p.id);
                  setSaveError(null);
                  setReviewForm({
                    amount: p.amount ?? '',
                    description: p.description ?? '',
                    date: p.date ?? '',
                    source: p.source ?? p.payer?.name ?? '',
                    propertyId: p.property?.id ? String(p.property.id) : null,
                    activityId: p.activity?.id ? String(p.activity.id) : null,
                    categoryId: p.financialCategory?.id ? String(p.financialCategory.id) : null,
                  });
                }}
              >
                Review
              </Button>
            </Group>
            <Text size="sm" c="dimmed" style={{ wordBreak: 'break-word' }}>
              {p.description}
            </Text>
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
                Imported from Venmo CSV. Nothing is finalized until you review these fields and
                choose Save income.
              </Text>
              {reviewing.classificationAmbiguous && (
                <Alert color="yellow" title="Classification needs review">
                  Confirm the activity and category before saving.
                </Alert>
              )}
              <div>
                <Text size="sm" c="dimmed">
                  Payer
                </Text>
                <Text fw={500}>{reviewing.payer?.name || '—'}</Text>
              </div>
              <Group grow>
                <NumberInput
                  label="Amount"
                  required
                  min={0}
                  decimalScale={2}
                  prefix="$"
                  value={reviewForm.amount}
                  onChange={(value) => setReviewForm((current) => ({ ...current, amount: value }))}
                />
                <TextInput
                  label="Date"
                  type="date"
                  required
                  value={reviewForm.date}
                  onChange={(event) =>
                    setReviewForm((current) => ({
                      ...current,
                      date: event.target.value,
                    }))
                  }
                />
              </Group>
              <TextInput
                label="Description"
                required
                value={reviewForm.description}
                onChange={(event) =>
                  setReviewForm((current) => ({
                    ...current,
                    description: event.target.value,
                  }))
                }
              />
              <TextInput
                label="Source"
                value={reviewForm.source}
                onChange={(event) =>
                  setReviewForm((current) => ({
                    ...current,
                    source: event.target.value,
                  }))
                }
                description="Editable statement/source label for the finalized income"
              />
              <Select
                label="Activity"
                description="Confirm which household activity owns this imported income."
                value={reviewForm.activityId}
                onChange={(value) => {
                  const activity = activities.find(
                    (candidate) => String(candidate.id) === String(value)
                  );
                  setReviewForm((current) => ({
                    ...current,
                    activityId: value,
                    propertyId: activity?.property?.id ? String(activity.property.id) : null,
                    categoryId: null,
                  }));
                }}
                data={activityOptions}
                placeholder="Select activity"
                required
              />
              <Select
                label="Category"
                value={reviewForm.categoryId}
                onChange={(value) =>
                  setReviewForm((current) => ({ ...current, categoryId: value }))
                }
                data={compatibleCategories.map((category) => ({
                  value: String(category.id),
                  label: category.label,
                }))}
                placeholder={reviewForm.activityId ? 'Select category' : 'Select an activity first'}
                disabled={!reviewForm.activityId}
                required
              />
              {reviewForm.propertyId && (
                <Select
                  label="Rental property"
                  value={reviewForm.propertyId}
                  data={propertyOptions}
                  disabled
                />
              )}
              {activities.find((activity) => String(activity.id) === String(reviewForm.activityId))
                ?.owner?.name && (
                <TextInput
                  label="Owner"
                  value={
                    activities.find(
                      (activity) => String(activity.id) === String(reviewForm.activityId)
                    )?.owner?.name ?? ''
                  }
                  readOnly
                  description="Derived from activity"
                />
              )}
              {saveError && (
                <Text c="red" size="sm">
                  {saveError}
                </Text>
              )}
            </Stack>
            <Box pt="md" style={{ borderTop: `1px solid ${COLORS.BORDER}`, flexShrink: 0 }}>
              <Group justify="space-between">
                <Group>
                  <Button
                    onClick={handleAccept}
                    leftSection={<IconCheck size={16} />}
                    loading={saving}
                    disabled={
                      !Number(reviewForm.amount) ||
                      !reviewForm.description?.trim() ||
                      !reviewForm.date ||
                      !reviewForm.activityId ||
                      !reviewForm.categoryId
                    }
                  >
                    Save income
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
    queryClient.invalidateQueries({ queryKey: ['reports'] });
  };

  return (
    <Stack gap="lg">
      <PendingIncomeSection />
      <PendingExpenses onSaved={handleSaved} onCountChange={() => {}} />
    </Stack>
  );
}
