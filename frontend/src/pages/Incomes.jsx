import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Center, Loader, Text } from '@mantine/core';
import { useForm } from '@mantine/form';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
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
  acceptPendingIncome,
  rejectPendingIncome,
} from '../api/index.js';
import { todayISO } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';
import { createIncomeSchema } from '../validation/schemas.js';
import { queryKeys } from '../queryKeys.js';
import { usePendingIncomesQuery } from '../hooks/usePendingQueue.js';
import { IncomesPageContent } from './IncomesPageContent.jsx';

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
    queryKey: queryKeys.incomes,
    queryFn: getIncomes,
  });
  const { data: pendingIncomes = [], isLoading: pendingLoading } = usePendingIncomesQuery();
  const { data: properties = [], isFetched: propertiesFetched } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const { data: payers = [] } = useQuery({
    queryKey: queryKeys.payers,
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
    if (filterPropertyId) {
      result = result.filter((i) => i.property && String(i.property.id) === filterPropertyId);
    }
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

  const openCreateForm = () => {
    form.reset();
    form.setFieldValue('date', todayISO());
    setEditing(null);
    setShowForm(true);
  };

  const closePendingReview = () => {
    setReviewingPendingId(null);
    setReviewingForm({ propertyId: null });
  };

  const openPendingReview = (pendingIncome) => {
    setReviewingPendingId(pendingIncome.id);
    setReviewingForm({
      propertyId: pendingIncome.property?.id ? String(pendingIncome.property.id) : null,
    });
  };

  const invalidateIncomeQueries = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
    queryClient.invalidateQueries({ queryKey: queryKeys.totalIncome });
  };

  const invalidateAllIncomeQueries = () => {
    invalidateIncomeQueries();
    queryClient.invalidateQueries({ queryKey: queryKeys.pendingIncomes });
  };

  const handleSubmit = async (values) => {
    setSaveError(null);
    const data = {
      // Send amount as a string so the backend BigDecimal parses an exact decimal.
      amount: String(values.amount ?? ''),
      description: values.description,
      date: values.date,
      sourceType: values.source,
      propertyId: values.propertyId ? Number(values.propertyId) : null,
      payerId: values.payerId ? Number(values.payerId) : null,
    };

    // Validate data before sending to API
    try {
      createIncomeSchema.parse(data);
    } catch (validationErr) {
      const fieldErrors = {};
      // zod v4 exposes issues via `.issues` (the `.errors` alias from v3 no longer exists).
      validationErr.issues.forEach((err) => {
        const field = err.path.join('.');
        fieldErrors[field] = err.message;
      });
      setSaveError('Please fix validation errors before submitting.');
      form.setErrors(fieldErrors);
      return;
    }

    try {
      if (editing) await updateIncome(editing, data);
      else await createIncome(data);
      notifications.show({ title: editing ? 'Income updated' : 'Income saved', color: 'green' });
      form.reset();
      form.setFieldValue('date', todayISO());
      setEditing(null);
      setShowForm(false);
      invalidateIncomeQueries();
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
          invalidateIncomeQueries();
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
      notifications.show({ title: 'Venmo import completed', message, color: 'green' });
      invalidateAllIncomeQueries();
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
      invalidateAllIncomeQueries();
      closePendingReview();
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
        invalidateAllIncomeQueries();
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

  if (incomesLoading) {
    return (
      <Center h={200}>
        <Loader />
      </Center>
    );
  }

  return (
    <IncomesPageContent
      setShowImportForm={setShowImportForm}
      openCreateForm={openCreateForm}
      showForm={showForm}
      cancelForm={cancelForm}
      editing={editing}
      form={form}
      handleSubmit={handleSubmit}
      propertyOptions={propertyOptions}
      payerOptions={payerOptions}
      saveError={saveError}
      showImportForm={showImportForm}
      cancelImportForm={cancelImportForm}
      importPayerId={importPayerId}
      setImportPayerId={setImportPayerId}
      importFile={importFile}
      setImportFile={setImportFile}
      importError={importError}
      handleImportSubmit={handleImportSubmit}
      visibleIncomes={visibleIncomes}
      pendingIncomes={pendingIncomes}
      filterText={filterText}
      setFilterText={setFilterText}
      yearOptions={yearOptions}
      filterYear={filterYear}
      setFilterYear={setFilterYear}
      filterPropertyId={filterPropertyId}
      setFilterPropertyId={setFilterPropertyId}
      highlightId={highlightId}
      handleEdit={handleEdit}
      handleDelete={handleDelete}
      pendingLoading={pendingLoading}
      handleAcceptAllPending={handleAcceptAllPending}
      openPendingReview={openPendingReview}
      reviewingPendingId={reviewingPendingId}
      closePendingReview={closePendingReview}
      reviewingForm={reviewingForm}
      setReviewingForm={setReviewingForm}
      handleAcceptPending={handleAcceptPending}
      handleRejectPending={handleRejectPending}
    />
  );
}
