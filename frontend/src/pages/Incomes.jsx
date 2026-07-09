import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Stack,
  Group,
  Title,
  Button,
  Drawer,
  Box,
  TextInput,
  NumberInput,
  Select,
  Table,
  Text,
  Loader,
  Center,
  ActionIcon,
  ScrollArea,
  FileInput,
  Tabs,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { IconPencil, IconTrash, IconSearch, IconCheck, IconX } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSessionState } from '../hooks/useSessionState.js';
import {
  getIncomes,
  createIncome,
  updateIncome,
  deleteIncome,
  getProperties,
  getPayers,
  importVenmoIncomes,
  getPendingIncomes,
  acceptPendingIncome,
  rejectPendingIncome,
} from '../api/index.js';
import { fmtCurrency, todayISO } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';

const getEmptyForm = () => ({
  amount: '',
  description: '',
  date: todayISO(),
  source: '',
  propertyId: null,
  payerId: null,
});

export default function Incomes() {
  const queryClient = useQueryClient();
  const { data: incomes = [], isLoading: incomesLoading } = useQuery({
    queryKey: ['incomes'],
    queryFn: getIncomes,
  });
  const { data: pendingIncomes = [], isLoading: pendingLoading } = useQuery({
    queryKey: ['pendingIncomes'],
    queryFn: getPendingIncomes,
  });
  const { data: properties = [], isFetched: propertiesFetched } = useQuery({
    queryKey: ['properties'],
    queryFn: getProperties,
  });
  const { data: payers = [] } = useQuery({
    queryKey: ['payers'],
    queryFn: getPayers,
  });

  const form = useForm({ initialValues: getEmptyForm() });
  const [editing, setEditing] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [showImportForm, setShowImportForm] = useState(false);
  const [importPayerId, setImportPayerId] = useState(null);
  const [importFile, setImportFile] = useState(null);
  const [importError, setImportError] = useState(null);
  const [pendingPrefill, setPendingPrefill] = useState(null);
  const [highlightId, setHighlightId] = useState(null);
  const [reviewingPendingId, setReviewingPendingId] = useState(null);
  const [reviewingForm, setReviewingForm] = useState({ propertyId: null });
  const highlightTimerRef = useRef(null);
  const [filterYear, setFilterYear] = useSessionState('incomes.filterYear', null);
  const [filterText, setFilterText] = useSessionState('incomes.filterText', '');
  const [filterPropertyId, setFilterPropertyId] = useSessionState('incomes.filterPropertyId', null);
  const location = useLocation();

  const propertyOptions = useMemo(
    () => properties.map((p) => ({ value: String(p.id), label: p.name })),
    [properties]
  );
  const payerOptions = useMemo(
    () => payers.map((p) => ({ value: String(p.id), label: p.name })),
    [payers]
  );

  const yearOptions = useMemo(() => {
    const years = [...new Set(incomes.map((i) => i.date?.slice(0, 4)).filter(Boolean))]
      .sort()
      .reverse();
    return years.map((y) => ({ value: y, label: y }));
  }, [incomes]);

  const visibleIncomes = useMemo(() => {
    let result = filterYear ? incomes.filter((i) => i.date?.startsWith(filterYear)) : incomes;
    if (filterPropertyId)
      result = result.filter((i) => i.property && String(i.property.id) === filterPropertyId);
    if (filterText) {
      const q = filterText.toLowerCase();
      result = result.filter(
        (i) =>
          i.description?.toLowerCase().includes(q) ||
          i.source?.toLowerCase().includes(q) ||
          i.payer?.name?.toLowerCase().includes(q) ||
          i.property?.name?.toLowerCase().includes(q)
      );
    }
    return result;
  }, [incomes, filterYear, filterPropertyId, filterText]);

  useEffect(() => {
    const { prefill, highlightId: hid } = location.state || {};
    if (hid) {
      setHighlightId(hid);
      window.history.replaceState({}, '');
      highlightTimerRef.current = setTimeout(() => setHighlightId(null), 3000);
      return () => clearTimeout(highlightTimerRef.current);
    }
    if (prefill) {
      setPendingPrefill(prefill);
      window.history.replaceState({}, '');
    }
  }, [location.state]);

  useEffect(() => () => clearTimeout(highlightTimerRef.current), []);

  useEffect(() => {
    if (!pendingPrefill || !propertiesFetched) return;
    const suggestedPropLower = pendingPrefill.propertyName?.trim().toLowerCase();
    const matchedProperty = suggestedPropLower
      ? (properties.find((p) => p.name.toLowerCase() === suggestedPropLower) ??
        properties.find(
          (p) =>
            p.address?.toLowerCase().includes(suggestedPropLower) ||
            suggestedPropLower.includes(p.address?.toLowerCase() ?? '')
        ) ??
        null)
      : null;
    form.setValues({
      amount: pendingPrefill.amount ?? '',
      description: pendingPrefill.description ?? '',
      date: pendingPrefill.date ?? todayISO(),
      source: pendingPrefill.payerName ?? '',
      propertyId: matchedProperty ? String(matchedProperty.id) : null,
      payerId: null,
    });
    setEditing(null);
    setShowForm(true);
    setPendingPrefill(null);
  }, [pendingPrefill, propertiesFetched, properties]);

  const handleSubmit = async (values) => {
    setSaveError(null);
    const data = {
      // Send amount as a string so the backend BigDecimal parses an exact decimal.
      amount: String(values.amount ?? ''),
      description: values.description,
      date: values.date,
      source: values.source,
      propertyId: values.propertyId ? Number(values.propertyId) : null,
      payerId: values.payerId ? Number(values.payerId) : null,
    };
    try {
      if (editing) await updateIncome(editing, data);
      else await createIncome(data);
      notifications.show({ title: editing ? 'Income updated' : 'Income saved', color: 'green' });
      form.reset();
      form.setFieldValue('date', todayISO());
      setEditing(null);
      setShowForm(false);
      queryClient.invalidateQueries({ queryKey: ['incomes'] });
      queryClient.invalidateQueries({ queryKey: ['totalIncome'] });
    } catch (err) {
      setSaveError(getErrorMessage(err, 'Could not save income. Please review fields and retry.'));
    }
  };

  const handleEdit = (income) => {
    form.setValues({
      amount: income.amount,
      description: income.description,
      date: income.date,
      source: income.source || '',
      propertyId: income.property?.id ? String(income.property.id) : null,
      payerId: income.payer?.id ? String(income.payer.id) : null,
    });
    setEditing(income.id);
    setShowForm(true);
  };

  const handleDelete = (id) => {
    modals.openConfirmModal({
      title: 'Delete income',
      children: <Text size="sm">This income record will be permanently deleted.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await deleteIncome(id);
          queryClient.invalidateQueries({ queryKey: ['incomes'] });
          queryClient.invalidateQueries({ queryKey: ['totalIncome'] });
        } catch (err) {
          notifications.show({
            title: 'Delete failed',
            message: getErrorMessage(err, 'Could not delete income.'),
            color: 'red',
          });
        }
      },
    });
  };

  const cancelForm = () => {
    setShowForm(false);
    setEditing(null);
    setSaveError(null);
    form.reset();
  };

  const cancelImportForm = () => {
    setShowImportForm(false);
    setImportPayerId(null);
    setImportFile(null);
    setImportError(null);
  };

  const handleImportSubmit = async () => {
    if (!importFile) {
      setImportError('Please select a Venmo CSV file.');
      return;
    }
    setImportError(null);
    try {
      const summary = await importVenmoIncomes(importFile, importPayerId, null);
      let message = `Imported ${summary.importedRows} rows (${summary.skippedDuplicateRows} duplicates, ${summary.skippedSenderRows} sender mismatch, ${summary.skippedOutgoingRows} outgoing, ${summary.skippedInvalidRows} invalid).`;
      if (summary.propertyName) {
        message += ` Property: ${summary.propertyName}`;
      }
      notifications.show({
        title: 'Venmo import completed',
        message,
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['incomes'] });
      queryClient.invalidateQueries({ queryKey: ['pendingIncomes'] });
      queryClient.invalidateQueries({ queryKey: ['totalIncome'] });
      cancelImportForm();
    } catch (err) {
      const explicitMessage =
        err?.message && !String(err.message).startsWith('HTTP ') ? err.message : null;
      setImportError(explicitMessage || getErrorMessage(err, 'Could not import Venmo CSV.'));
    }
  };

  const handleAcceptPending = async () => {
    if (!reviewingPendingId) return;
    const pending = pendingIncomes.find((p) => p.id === reviewingPendingId);
    if (!pending) return;
    try {
      await acceptPendingIncome(reviewingPendingId, {
        amount: pending.amount,
        description: pending.description,
        date: pending.date,
        source: pending.source,
        propertyId: reviewingForm.propertyId ? Number(reviewingForm.propertyId) : null,
        payerId: pending.payer?.id || null,
      });
      notifications.show({ title: 'Income accepted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['incomes'] });
      queryClient.invalidateQueries({ queryKey: ['pendingIncomes'] });
      queryClient.invalidateQueries({ queryKey: ['totalIncome'] });
      setReviewingPendingId(null);
      setReviewingForm({ propertyId: null });
    } catch (err) {
      notifications.show({
        title: 'Accept failed',
        message: getErrorMessage(err, 'Could not accept pending income.'),
        color: 'red',
      });
    }
  };

  const handleAcceptAllPending = async () => {
    const readyItems = pendingIncomes.filter((p) => p.status === 'READY');
    if (readyItems.length === 0) return;
    modals.openConfirmModal({
      title: 'Accept all pending income',
      children: (
        <Text size="sm">
          Accept all {readyItems.length} pending income records? Each will use its auto-detected
          property (if any).
        </Text>
      ),
      labels: { confirm: 'Accept All', cancel: 'Cancel' },
      onConfirm: async () => {
        let succeeded = 0;
        let failed = 0;
        for (const p of readyItems) {
          try {
            await acceptPendingIncome(p.id, {
              amount: p.amount,
              description: p.description,
              date: p.date,
              source: p.source,
              propertyId: p.property?.id || null,
              payerId: p.payer?.id || null,
            });
            succeeded++;
          } catch {
            failed++;
          }
        }
        queryClient.invalidateQueries({ queryKey: ['incomes'] });
        queryClient.invalidateQueries({ queryKey: ['pendingIncomes'] });
        queryClient.invalidateQueries({ queryKey: ['totalIncome'] });
        notifications.show({
          title: failed === 0 ? 'All accepted' : `${succeeded} accepted, ${failed} failed`,
          color: failed === 0 ? 'green' : 'orange',
        });
      },
    });
  };

  const handleRejectPending = (id) => {
    modals.openConfirmModal({
      title: 'Reject pending income',
      children: <Text size="sm">This pending income record will be permanently deleted.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await rejectPendingIncome(id);
          queryClient.invalidateQueries({ queryKey: ['pendingIncomes'] });
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

  if (incomesLoading)
    return (
      <Center h={200}>
        <Loader />
      </Center>
    );

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Income</Title>
        <Group>
          <Button variant="default" onClick={() => setShowImportForm(true)}>
            Import Venmo CSV
          </Button>
          <Button
            onClick={() => {
              form.reset();
              form.setFieldValue('date', todayISO());
              setEditing(null);
              setShowForm(true);
            }}
          >
            + Add Income
          </Button>
        </Group>
      </Group>

      <Text size="sm" c="dimmed">
        Finalized income records live here. New email and receipt items are reviewed in Inbox.
      </Text>

      <Drawer
        opened={showForm}
        onClose={cancelForm}
        title={editing ? 'Edit Income' : 'New Income'}
        position="right"
        size="lg"
        styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
      >
        <form
          onSubmit={form.onSubmit(handleSubmit)}
          style={{ display: 'flex', flexDirection: 'column', flex: 1 }}
        >
          <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
            <Group grow>
              <NumberInput
                label="Amount"
                {...form.getInputProps('amount')}
                min={0}
                decimalScale={2}
                prefix="$"
                required
              />
              <TextInput label="Description" {...form.getInputProps('description')} required />
            </Group>
            <Group grow>
              <TextInput label="Date" type="date" {...form.getInputProps('date')} required />
              <TextInput label="Source" {...form.getInputProps('source')} />
            </Group>
            <Group grow>
              <Select
                label="Property"
                {...form.getInputProps('propertyId')}
                data={propertyOptions}
                clearable
                placeholder="— None —"
              />
              <Select
                label="Payer"
                {...form.getInputProps('payerId')}
                data={payerOptions}
                clearable
                searchable
                placeholder="— None —"
              />
            </Group>
            {saveError && (
              <Text c="red" size="sm">
                {saveError}
              </Text>
            )}
          </Stack>
          <Box
            pt="md"
            style={{ borderTop: '1px solid var(--mantine-color-gray-2)', flexShrink: 0 }}
          >
            <Group>
              <Button type="submit">Save</Button>
              <Button variant="default" onClick={cancelForm}>
                Cancel
              </Button>
            </Group>
          </Box>
        </form>
      </Drawer>

      <Drawer
        opened={showImportForm}
        onClose={cancelImportForm}
        title="Import Venmo CSV"
        position="right"
        size="md"
      >
        <Stack gap="sm">
          <Text size="sm" c="dimmed">
            Property will be auto-detected from payer history. Optional: select a payer to import only payments received from that payer.
          </Text>
          <Select
            label="Payer filter (optional)"
            value={importPayerId}
            onChange={setImportPayerId}
            data={payerOptions}
            clearable
            searchable
            placeholder="All senders"
          />
          <FileInput
            label="Venmo CSV file"
            value={importFile}
            onChange={setImportFile}
            accept=".csv,text/csv"
            clearable
          />
          {importError && (
            <Text c="red" size="sm">
              {importError}
            </Text>
          )}
          <Group pt="sm">
            <Button onClick={handleImportSubmit}>Import</Button>
            <Button variant="default" onClick={cancelImportForm}>
              Cancel
            </Button>
          </Group>
        </Stack>
      </Drawer>

      <Tabs defaultValue="finalized">
        <Tabs.List>
          <Tabs.Tab value="finalized">Finalized ({visibleIncomes.length})</Tabs.Tab>
          <Tabs.Tab value="pending">Pending ({pendingIncomes.length})</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="finalized" pt="md">
          <Group mb="sm" gap="xs">
            <TextInput
              placeholder="Search income…"
              value={filterText}
              onChange={(e) => setFilterText(e.target.value)}
              leftSection={<IconSearch size={14} />}
              size="xs"
              style={{ width: 200 }}
            />
            <Select
              placeholder="All years"
              data={yearOptions}
              value={filterYear}
              onChange={setFilterYear}
              clearable
              size="xs"
              style={{ width: 110 }}
            />
            {propertyOptions.length > 0 && (
              <Select
                placeholder="All properties"
                value={filterPropertyId}
                onChange={setFilterPropertyId}
                data={propertyOptions}
                clearable
                size="xs"
                style={{ width: 160 }}
              />
            )}
          </Group>
          <ScrollArea>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th w={90}>Date</Table.Th>
                  <Table.Th>Description</Table.Th>
                  <Table.Th w={130}>Source</Table.Th>
                  <Table.Th w={150}>Payer</Table.Th>
                  <Table.Th w={150}>Property</Table.Th>
                  <Table.Th w={110} style={{ textAlign: 'right' }}>
                    Amount
                  </Table.Th>
                  <Table.Th w={72}>Actions</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {visibleIncomes.length === 0 ? (
                  <Table.Tr>
                    <Table.Td colSpan={7}>
                      <Text ta="center" c="dimmed" py="xl" size="sm">
                        {filterYear || filterText
                          ? 'No income records match the current filters'
                          : 'No income records yet. Import a Venmo CSV or add income manually.'}
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                ) : (
                  visibleIncomes.map((i) => (
                    <Table.Tr
                      key={i.id}
                      style={{
                        background: highlightId === i.id ? 'var(--mantine-color-yellow-0)' : undefined,
                        transition: 'background 0.5s',
                      }}
                    >
                      <Table.Td c="dimmed">{i.date}</Table.Td>
                      <Table.Td>{i.description}</Table.Td>
                      <Table.Td c="dimmed">{i.source || '—'}</Table.Td>
                      <Table.Td c="dimmed">{i.payer?.name || '—'}</Table.Td>
                      <Table.Td c="dimmed">{i.property?.name || '—'}</Table.Td>
                      <Table.Td
                        fw={600}
                        c="green"
                        style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                      >
                        +{fmtCurrency(i.amount)}
                      </Table.Td>
                      <Table.Td>
                        <Group gap="xs">
                          <ActionIcon variant="subtle" color="gray" onClick={() => handleEdit(i)}>
                            <IconPencil size={16} />
                          </ActionIcon>
                          <ActionIcon variant="subtle" color="red" onClick={() => handleDelete(i.id)}>
                            <IconTrash size={16} />
                          </ActionIcon>
                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  ))
                )}
              </Table.Tbody>
            </Table>
          </ScrollArea>
        </Tabs.Panel>

        <Tabs.Panel value="pending" pt="md">
          {pendingLoading ? (
            <Center py="xl">
              <Loader />
            </Center>
          ) : pendingIncomes.length === 0 ? (
            <Text ta="center" c="dimmed" py="xl" size="sm">
              No pending income records
            </Text>
          ) : (
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
                    <Button
                      size="sm"
                      onClick={() => {
                        setReviewingPendingId(p.id);
                        setReviewingForm({ propertyId: p.property?.id ? String(p.property.id) : null });
                      }}
                    >
                      Review
                    </Button>
                  </Group>
                  <Text size="sm" c="dimmed" style={{ wordBreak: 'break-word' }}>{p.description}</Text>
                  {p.property && (
                    <Text size="xs" c="dimmed" mt={4}>
                      Auto-detected property: <strong>{p.property.name}</strong>
                    </Text>
                  )}
                </Box>
              ))}
            </Stack>
          )}
        </Tabs.Panel>
      </Tabs>

      <Drawer
        opened={reviewingPendingId !== null}
        onClose={() => {
          setReviewingPendingId(null);
          setReviewingForm({ propertyId: null });
        }}
        title="Review Pending Income"
        position="right"
        size="lg"
        styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
      >
        {reviewingPendingId && (
          <>
            {(() => {
              const pending = pendingIncomes.find((p) => p.id === reviewingPendingId);
              return (
                <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
                  <Text size="xs" c="dimmed" style={{ fontStyle: 'italic' }}>
                    Imported from Venmo CSV. Accepting will add this to finalized income records.
                  </Text>
                  <div>
                    <Text size="sm" c="dimmed">Payer</Text>
                    <Text fw={500}>{pending?.payer?.name || '—'}</Text>
                  </div>
                  <div>
                    <Text size="sm" c="dimmed">Date</Text>
                    <Text fw={500}>{pending?.date}</Text>
                  </div>
                  <div>
                    <Text size="sm" c="dimmed">Amount</Text>
                    <Text fw={500} c="green">+{fmtCurrency(pending?.amount)}</Text>
                  </div>
                  <div>
                    <Text size="sm" c="dimmed">Description</Text>
                    <Text fw={500} style={{ wordBreak: 'break-word' }}>{pending?.description}</Text>
                  </div>
                  <Select
                    label="Property"
                    description={pending?.property ? 'Auto-detected from payer history — adjust if incorrect' : 'No property detected — select one if applicable'}
                    value={reviewingForm.propertyId}
                    onChange={(val) => setReviewingForm({ propertyId: val })}
                    data={propertyOptions}
                    clearable
                    placeholder="— None —"
                  />
                </Stack>
              );
            })()}
            <Box pt="md" style={{ borderTop: '1px solid var(--mantine-color-gray-2)', flexShrink: 0 }}>
              <Group justify="space-between">
                <Group>
                  <Button onClick={handleAcceptPending} leftSection={<IconCheck size={16} />}>
                    Accept
                  </Button>
                  <Button
                    variant="default"
                    onClick={() => {
                      setReviewingPendingId(null);
                      setReviewingForm({ propertyId: null });
                    }}
                  >
                    Cancel
                  </Button>
                </Group>
                <Button
                  variant="subtle"
                  color="red"
                  leftSection={<IconX size={16} />}
                  onClick={() => {
                    setReviewingPendingId(null);
                    setReviewingForm({ propertyId: null });
                    handleRejectPending(reviewingPendingId);
                  }}
                >
                  Reject
                </Button>
              </Group>
            </Box>
          </>
        )}
      </Drawer>
    </Stack>
  );
}
