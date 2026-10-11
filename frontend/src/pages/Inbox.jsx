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
import { IconCheck, IconRefresh, IconX } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import PendingExpenses from '../components/PendingExpenses.jsx';
import { getFinancialActivities, getFinancialCategories, getProperties } from '../api/index.js';
import { fmtCurrency } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';
import { COLORS } from '../designTokens.js';
import { importedIncomeSourceLabel } from '../constants.js';
import { usePendingIncomesQuery } from '../hooks/usePendingQueue.js';
import { usePendingIncomeActions } from '../hooks/usePendingIncomeActions.js';
import { useRunAutoImport } from '../hooks/useRunAutoImport.js';

// Ledger saves carry neutral categories, so each compatible financial category is offered by the
// neutral category it maps to.
function neutralCategoryOptions(categories) {
  const options = new Map();
  categories
    .filter((category) => category.neutralCategoryId)
    .forEach((category) => {
      const value = String(category.neutralCategoryId);
      if (!options.has(value)) {
        options.set(value, category.label);
      }
    });
  return [...options].map(([value, label]) => ({ value, label }));
}

function PendingIncomeSection() {
  const { savePendingIncome, deletePendingIncome } = usePendingIncomeActions();
  const { data: pendingIncomes = [], isLoading } = usePendingIncomesQuery();
  const { data: properties = [] } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const propertyOptions = properties.map((p) => ({ value: String(p.id), label: p.name }));
  const propertyIdNamed = (name) => {
    const property = properties.find((candidate) => candidate.name === name);
    return property ? String(property.id) : null;
  };
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
  const importedFrom = [
    ...new Set(pendingIncomes.map((p) => importedIncomeSourceLabel(p.origin))),
  ].join(' and ');

  const handleAccept = async () => {
    if (!reviewingId || !reviewing) return;
    setSaving(true);
    setSaveError(null);
    try {
      await savePendingIncome(reviewingId, reviewForm, reviewing.counterpartyId || null);
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
          Imported from {importedFrom}. Review and accept each record to finalize it.
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
                  {p.counterpartyName || '—'}
                </Text>
                <Text size="xs" c="dimmed">
                  {p.date} • {fmtCurrency(p.amount)}
                  {p.propertyName && (
                    <>
                      {' '}
                      • <strong>{p.propertyName}</strong>
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
                    source: p.sourceLabel ?? p.counterpartyName ?? '',
                    propertyId: propertyIdNamed(p.propertyName),
                    activityId: p.activity?.id ? String(p.activity.id) : null,
                    categoryId: p.category?.neutralCategoryId
                      ? String(p.category.neutralCategoryId)
                      : null,
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
                Imported from {importedIncomeSourceLabel(reviewing.origin)}. Nothing is finalized
                until you review these fields and choose Save income.
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
                <Text fw={500}>{reviewing.counterpartyName || '—'}</Text>
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
                data={neutralCategoryOptions(compatibleCategories)}
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

function CheckForNewItemsButton() {
  const { run, running } = useRunAutoImport();

  const handleClick = async () => {
    try {
      const queued = await run();
      notifications.show({
        title: queued > 0 ? `Found ${queued} new item${queued === 1 ? '' : 's'}` : 'No new items',
        message: queued > 0 ? 'They will appear here once parsed.' : undefined,
        color: 'green',
      });
    } catch (err) {
      notifications.show({
        title: 'Check failed',
        message: getErrorMessage(err, 'Could not check for new items.'),
        color: 'red',
      });
    }
  };

  return (
    <Group justify="flex-end">
      <Button
        variant="light"
        leftSection={<IconRefresh size={16} />}
        loading={running}
        onClick={handleClick}
      >
        Check for new items
      </Button>
    </Group>
  );
}

export default function Inbox() {
  const queryClient = useQueryClient();

  const handleSaved = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
    queryClient.invalidateQueries({ queryKey: ['reports'] });
  };

  return (
    <Stack gap="lg">
      <CheckForNewItemsButton />
      <PendingIncomeSection />
      <PendingExpenses onSaved={handleSaved} onCountChange={() => {}} />
    </Stack>
  );
}
