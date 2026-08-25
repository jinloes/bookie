import React, { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Group,
  NumberInput,
  Paper,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useQuery } from '@tanstack/react-query';
import { IconAlertTriangle, IconRobot, IconSend } from '@tabler/icons-react';
import {
  getFinancialActivities,
  getFinancialCategories,
  getPayers,
  submitTransactionToAgent,
} from '../api/index.js';
import { useSaveAgentProposal } from '../hooks/useSaveAgentProposal.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { todayISO } from '../utils/formatters.js';

const emptyProposal = () => ({
  direction: 'EXPENSE',
  amount: '',
  description: '',
  date: todayISO(),
  activityId: null,
  categoryId: null,
  payerId: null,
});

export default function Agent() {
  const [message, setMessage] = useState('');
  const [assistantMessage, setAssistantMessage] = useState(
    'Describe money received or spent. I will prepare an editable proposal; nothing is saved automatically.'
  );
  const [proposal, setProposal] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const { saveAgentProposal, saving, saveError } = useSaveAgentProposal();
  const form = useForm({ initialValues: emptyProposal() });

  const { data: activities = [] } = useQuery({
    queryKey: queryKeys.financialActivities,
    queryFn: getFinancialActivities,
  });
  const { data: payers = [] } = useQuery({
    queryKey: queryKeys.payers,
    queryFn: getPayers,
  });
  const { data: categories = [], isFetching: categoriesLoading } = useQuery({
    queryKey: queryKeys.financialCategories(form.values.direction, form.values.activityId),
    queryFn: () => getFinancialCategories(form.values.direction, form.values.activityId),
    enabled: Boolean(form.values.activityId),
  });

  const activityOptions = useMemo(
    () =>
      activities
        .filter((activity) => activity.active)
        .map((activity) => ({ value: String(activity.id), label: activity.name })),
    [activities]
  );
  const payerOptions = useMemo(
    () => payers.map((payer) => ({ value: String(payer.id), label: payer.name })),
    [payers]
  );
  const categoryOptions = categories.map((category) => ({
    value: String(category.id),
    label: category.label,
  }));
  const selectedActivity =
    activities.find((activity) => String(activity.id) === String(form.values.activityId)) ?? null;
  const selectedPayer =
    payers.find((payer) => String(payer.id) === String(form.values.payerId)) ?? null;

  const handleSend = async (event) => {
    event.preventDefault();
    if (!message.trim()) return;

    setSubmitting(true);
    setSubmitError(null);
    setProposal(null);
    try {
      const response = await submitTransactionToAgent(message.trim());
      const nextProposal = response?.proposedTransaction ?? null;
      setAssistantMessage(
        response?.message ??
          (nextProposal
            ? 'Review this proposal and save it if it is correct.'
            : 'I could not prepare a proposal.')
      );
      setProposal(nextProposal);
      if (nextProposal) {
        form.setValues({
          direction: nextProposal.direction ?? 'EXPENSE',
          amount: nextProposal.amount ?? '',
          description: nextProposal.description ?? '',
          date: nextProposal.date ?? todayISO(),
          activityId: nextProposal.activityId ? String(nextProposal.activityId) : null,
          categoryId: nextProposal.categoryId ? String(nextProposal.categoryId) : null,
          payerId: nextProposal.payerId ? String(nextProposal.payerId) : null,
        });
      }
      setMessage('');
    } catch (error) {
      setSubmitError(getErrorMessage(error, 'Could not prepare a transaction proposal.'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSave = async () => {
    const direction = form.values.direction;
    try {
      await saveAgentProposal({
        ...form.values,
        propertyId: selectedActivity?.property?.id ?? null,
        counterpartyName: selectedPayer?.name ?? proposal?.counterpartyName ?? '',
      });
      setProposal(null);
      form.setValues(emptyProposal());
      setAssistantMessage(
        direction === 'INCOME' ? 'Income saved after review.' : 'Expense saved after review.'
      );
    } catch {
      // The hook exposes the error and leaves the editable proposal intact.
    }
  };

  const canSave =
    Number(form.values.amount) > 0 &&
    Boolean(form.values.description?.trim()) &&
    Boolean(form.values.date) &&
    Boolean(form.values.activityId) &&
    Boolean(form.values.categoryId);

  return (
    <Stack gap="lg" maw={820} mx="auto">
      <div>
        <Group gap="xs">
          <IconRobot size={26} />
          <Title order={2}>Financial Assistant</Title>
        </Group>
        <Text c="dimmed" size="sm" mt={4}>
          Extraction creates a draft only. Review every field and use Save to create the
          transaction.
        </Text>
      </div>

      <Paper withBorder p="md">
        <Text fw={600}>Assistant</Text>
        <Text size="sm" mt={4}>
          {assistantMessage}
        </Text>
      </Paper>

      <form onSubmit={handleSend}>
        <Stack gap="sm">
          <Textarea
            label="Describe a transaction"
            placeholder="Example: Received $425 for synthetic tutoring services on 2026-02-14"
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            minRows={3}
          />
          {submitError && (
            <Text c="red" size="sm">
              {submitError}
            </Text>
          )}
          <Button
            type="submit"
            loading={submitting}
            disabled={!message.trim()}
            leftSection={<IconSend size={16} />}
            style={{ alignSelf: 'flex-start' }}
          >
            Prepare proposal
          </Button>
        </Stack>
      </form>

      {proposal && (
        <Paper withBorder p="lg">
          <Stack gap="sm">
            <Title order={3}>Review proposal</Title>
            <Text size="sm" c="dimmed">
              The assistant has not saved this transaction. Correct any field before continuing.
            </Text>

            {proposal.classificationAmbiguous && (
              <Alert
                color="yellow"
                icon={<IconAlertTriangle size={18} />}
                title="Classification needs review"
              >
                Activity or category could not be determined uniquely. Confirm both fields before
                saving.
              </Alert>
            )}

            <Group grow align="flex-start">
              <Select
                label="Direction"
                aria-label="Transaction direction"
                required
                value={form.values.direction}
                onChange={(value) => {
                  form.setFieldValue('direction', value);
                  form.setFieldValue('categoryId', null);
                }}
                data={[
                  { value: 'INCOME', label: 'Income' },
                  { value: 'EXPENSE', label: 'Expense' },
                ]}
              />
              <NumberInput
                label="Amount"
                required
                min={0}
                decimalScale={2}
                prefix="$"
                {...form.getInputProps('amount')}
              />
            </Group>

            <TextInput label="Description" required {...form.getInputProps('description')} />
            <TextInput label="Date" type="date" required {...form.getInputProps('date')} />

            <Group grow align="flex-start">
              <Select
                label="Activity"
                required
                searchable
                value={form.values.activityId}
                onChange={(value) => {
                  form.setFieldValue('activityId', value);
                  form.setFieldValue('categoryId', null);
                }}
                data={activityOptions}
                placeholder="Select activity"
              />
              <Select
                label="Category"
                aria-label="Transaction category"
                required
                searchable
                value={form.values.categoryId}
                onChange={(value) => form.setFieldValue('categoryId', value)}
                data={categoryOptions}
                disabled={!form.values.activityId}
                placeholder={
                  form.values.activityId ? 'Select category' : 'Select an activity first'
                }
                rightSection={categoriesLoading ? <Text size="xs">…</Text> : null}
              />
            </Group>

            <Group grow align="flex-start">
              <TextInput
                label="Owner"
                value={
                  selectedActivity
                    ? (selectedActivity.owner?.name ?? '')
                    : (proposal.ownerName ?? '')
                }
                readOnly
                description="Derived from the selected activity"
              />
              <TextInput
                label="Property"
                value={
                  selectedActivity
                    ? (selectedActivity.property?.name ?? '')
                    : (proposal.propertyName ?? '')
                }
                readOnly
                description="Derived from the selected activity"
              />
            </Group>

            <Select
              label={
                form.values.direction === 'INCOME'
                  ? 'Payer (optional)'
                  : 'Vendor / payee (optional)'
              }
              searchable
              clearable
              value={form.values.payerId}
              onChange={(value) => form.setFieldValue('payerId', value)}
              data={payerOptions}
              placeholder="Select a stored counterparty"
              description={
                proposal.counterpartyName
                  ? `Extracted counterparty: ${proposal.counterpartyName}`
                  : 'No counterparty was extracted'
              }
            />

            {saveError && (
              <Text c="red" size="sm">
                {saveError}
              </Text>
            )}
            <Group mt="xs">
              <Button onClick={handleSave} loading={saving} disabled={!canSave}>
                Save {form.values.direction === 'INCOME' ? 'income' : 'expense'}
              </Button>
              <Button
                variant="default"
                onClick={() => {
                  setProposal(null);
                  form.setValues(emptyProposal());
                  setAssistantMessage('Proposal discarded. Nothing was saved.');
                }}
              >
                Discard
              </Button>
            </Group>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
