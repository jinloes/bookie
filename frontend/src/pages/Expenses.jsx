import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Center, Loader, Text } from '@mantine/core';
import { useForm } from '@mantine/form';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSessionState } from '../hooks/useSessionState.js';
import {
  getExpenses,
  createExpense,
  updateExpense,
  deleteExpense,
  getExpenseCategories,
  getProperties,
  getPayers,
  createPayer,
  uploadReceipt,
} from '../api/index.js';
import { todayISO } from '../utils/formatters.js';
import { EXPENSE_SOURCE, PAYER_TYPE } from '../constants.js';
import { getErrorMessage } from '../utils/errors.js';
import { createExpenseSchema } from '../validation/schemas.js';
import { queryKeys } from '../queryKeys.js';
import { ExpensesPageContent } from './ExpensesPageContent.jsx';

const HIGHLIGHT_MS = 3000;

const getEmptyForm = () => ({
  amount: '',
  description: '',
  date: todayISO(),
  category: null,
  propertyId: null,
  payerId: null,
  sourceType: null,
  sourceId: null,
});
const EMPTY_PAYER_FORM = { name: '', type: PAYER_TYPE.COMPANY, aliases: [], accounts: [] };

export default function Expenses() {
  const queryClient = useQueryClient();
  const { data: expenses = [], isLoading } = useQuery({
    queryKey: queryKeys.expenses,
    queryFn: getExpenses,
  });
  const { data: categories = [] } = useQuery({
    queryKey: queryKeys.categories,
    queryFn: getExpenseCategories,
  });
  const { data: properties = [], isFetched: propertiesFetched } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const { data: payers = [], isFetched: payersFetched } = useQuery({
    queryKey: queryKeys.payers,
    queryFn: getPayers,
  });

  const form = useForm({
    initialValues: getEmptyForm(),
    validate: { category: (v) => (!v ? 'Select a category' : null) },
  });
  const payerForm = useForm({ initialValues: EMPTY_PAYER_FORM });

  const [editing, setEditing] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [pendingPrefill, setPendingPrefill] = useState(null);
  const [payerModalOpen, setPayerModalOpen] = useState(false);
  const [payerAccountInput, setPayerAccountInput] = useState('');
  const [uploadedReceipt, setUploadedReceipt] = useState(null);
  const [receiptUploading, setReceiptUploading] = useState(false);
  const [highlightId, setHighlightId] = useState(null);
  const highlightTimerRef = useRef(null);
  const [filterPayerId, setFilterPayerId] = useSessionState('expenses.filterPayerId', null);
  const [filterYear, setFilterYear] = useSessionState('expenses.filterYear', null);
  const [filterText, setFilterText] = useSessionState('expenses.filterText', '');
  const [filterCategory, setFilterCategory] = useSessionState('expenses.filterCategory', null);
  const [filterPropertyId, setFilterPropertyId] = useSessionState('expenses.filterPropertyId', null);
  const location = useLocation();

  useEffect(() => {
    const { prefill, highlightId: hid } = location.state || {};
    if (hid) {
      setHighlightId(hid);
      window.history.replaceState({}, '');
      highlightTimerRef.current = setTimeout(() => setHighlightId(null), HIGHLIGHT_MS);
    } else if (prefill) {
      setPendingPrefill(prefill);
      window.history.replaceState({}, '');
    }
    // Always return the same cleanup so a re-run of the effect (or unmount) clears any
    // pending timer the previous run scheduled.
    return () => {
      if (highlightTimerRef.current) {
        clearTimeout(highlightTimerRef.current);
        highlightTimerRef.current = null;
      }
    };
  }, [location.state]);

  useEffect(() => {
    if (!pendingPrefill || !payersFetched || !propertiesFetched) return;
    const matchedPayer = pendingPrefill.payerName
      ? (payers.find((p) => p.name.toLowerCase() === pendingPrefill.payerName.toLowerCase()) ??
        null)
      : null;
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
      category: pendingPrefill.category ?? null,
      propertyId: matchedProperty ? String(matchedProperty.id) : null,
      payerId: matchedPayer ? String(matchedPayer.id) : null,
      sourceType: pendingPrefill.sourceType ?? null,
      sourceId: pendingPrefill.sourceId ?? null,
    });
    if (pendingPrefill.payerName && !matchedPayer) {
      payerForm.setValues({
        name: pendingPrefill.payerName,
        type: PAYER_TYPE.COMPANY,
        aliases: [],
        accounts: pendingPrefill.accountNumbers || [],
      });
      setPayerModalOpen(true);
    }
    setEditing(null);
    setShowForm(true);
    setPendingPrefill(null);
  }, [pendingPrefill, payersFetched, propertiesFetched, payers, properties]);

  const openPayerModal = () => {
    payerForm.reset();
    setPayerModalOpen(true);
  };

  const openCreateForm = () => {
    form.reset();
    form.setFieldValue('date', todayISO());
    setEditing(null);
    setUploadedReceipt(null);
    setShowForm(true);
  };

  const handlePayerModalSave = async () => {
    const newPayer = await createPayer(payerForm.values);
    queryClient.setQueryData(['payers'], (old = []) => [...old, newPayer]);
    form.setFieldValue('payerId', String(newPayer.id));
    setPayerModalOpen(false);
    payerForm.reset();
    setPayerAccountInput('');
  };

  const addPayerAccount = () => {
    const trimmed = payerAccountInput.trim();
    if (!trimmed || payerForm.values.accounts.includes(trimmed)) return;
    payerForm.insertListItem('accounts', trimmed);
    setPayerAccountInput('');
  };

  const handleReceiptUpload = async (file) => {
    if (!file) return;
    setReceiptUploading(true);
    setSaveError(null);
    try {
      const dot = file.name.lastIndexOf('.');
      const base = dot >= 0 ? file.name.slice(0, dot) : file.name;
      const ext = dot >= 0 ? file.name.slice(dot) : '';
      const date = form.values.date || todayISO();
      const renamedFile = new File([file], `${base}_${date}${ext}`, { type: file.type });
      const result = await uploadReceipt(renamedFile);
      setUploadedReceipt({ itemId: result.receipt.id, fileName: result.receipt.name });
    } catch (err) {
      setSaveError(getErrorMessage(err, 'Receipt upload failed. Please try again.'));
    } finally {
      setReceiptUploading(false);
    }
  };

  const cancelForm = () => {
    setShowForm(false);
    setEditing(null);
    setSaveError(null);
    setUploadedReceipt(null);
    form.reset();
  };

  const handleSubmit = async (values) => {
    setSaveError(null);
    const isEditing = !!editing;

    // Validate data structure for schema
    const validationData = {
      amount: String(values.amount ?? ''),
      description: values.description,
      date: values.date,
      category: values.category,
      propertyId: values.propertyId ? Number(values.propertyId) : null,
      payerId: values.payerId ? Number(values.payerId) : null,
    };

    // Validate before sending to API
    try {
      createExpenseSchema.parse(validationData);
    } catch (validationErr) {
      const fieldErrors = {};
      validationErr.errors.forEach((err) => {
        const field = err.path.join('.');
        fieldErrors[field] = err.message;
      });
      setSaveError('Please fix validation errors before submitting.');
      form.setErrors(fieldErrors);
      return;
    }

    // Build the data object for the API
    const data = {
      amount: validationData.amount,
      description: values.description,
      date: values.date,
      category: values.category,
      property: values.propertyId ? { id: Number(values.propertyId) } : null,
      payer: values.payerId ? { id: Number(values.payerId) } : null,
      sourceType: values.sourceType,
      sourceId: values.sourceId,
      ...(uploadedReceipt
        ? {
            receiptOneDriveId: uploadedReceipt.itemId,
            receiptFileName: uploadedReceipt.fileName,
            sourceType: EXPENSE_SOURCE.RECEIPT,
          }
        : {}),
    };
    try {
      if (isEditing) await updateExpense(editing, data);
      else await createExpense(data);
      cancelForm();
      queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
      queryClient.invalidateQueries({ queryKey: queryKeys.totalExpenses });
      notifications.show({
        title: isEditing ? 'Expense updated' : 'Expense saved',
        color: 'green',
      });
    } catch (err) {
      setSaveError(getErrorMessage(err, 'Could not save expense. Please review fields and retry.'));
    }
  };

  const handleEdit = (expense) => {
    form.setValues({
      amount: expense.amount,
      description: expense.description,
      date: expense.date,
      category: expense.category,
      propertyId: expense.property?.id ? String(expense.property.id) : null,
      payerId: expense.payer?.id ? String(expense.payer.id) : null,
      sourceType: expense.sourceType,
      sourceId: expense.sourceId,
    });
    setEditing(expense.id);
    setShowForm(true);
  };

  const handleDelete = (id) => {
    modals.openConfirmModal({
      title: 'Delete expense',
      children: (
        <Text size="sm">
          This expense will be permanently deleted. This action cannot be undone.
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        await deleteExpense(id);
        queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
        queryClient.invalidateQueries({ queryKey: queryKeys.totalExpenses });
      },
    });
  };

  const yearOptions = useMemo(() => {
    const years = [...new Set(expenses.map((e) => e.date?.slice(0, 4)).filter(Boolean))]
      .sort()
      .reverse();
    return years.map((y) => ({ value: y, label: y }));
  }, [expenses]);

  const categoryOptions = useMemo(
    () => categories.map((c) => ({ value: c.value, label: c.label })),
    [categories]
  );

  const propertyFilterOptions = useMemo(
    () => properties.map((p) => ({ value: String(p.id), label: p.name })),
    [properties]
  );

  const payerOptions = useMemo(
    () =>
      expenses
        .filter((e) => e.payer)
        .reduce((acc, e) => {
          if (!acc.some((o) => o.value === String(e.payer.id))) {
            acc.push({ value: String(e.payer.id), label: e.payer.name });
          }
          return acc;
        }, [])
        .sort((a, b) => a.label.localeCompare(b.label)),
    [expenses]
  );

  const visibleExpenses = useMemo(() => {
    let result = expenses;
    if (filterPayerId)
      result = result.filter((e) => e.payer && String(e.payer.id) === filterPayerId);
    if (filterYear) result = result.filter((e) => e.date?.startsWith(filterYear));
    if (filterCategory) result = result.filter((e) => e.category === filterCategory);
    if (filterPropertyId)
      result = result.filter((e) => e.property && String(e.property.id) === filterPropertyId);
    if (filterText) {
      const q = filterText.toLowerCase();
      result = result.filter(
        (e) =>
          e.description?.toLowerCase().includes(q) ||
          e.payer?.name?.toLowerCase().includes(q) ||
          e.property?.name?.toLowerCase().includes(q) ||
          e.category?.toLowerCase().includes(q)
      );
    }
    return result;
  }, [expenses, filterPayerId, filterYear, filterCategory, filterPropertyId, filterText]);

  if (isLoading)
    return (
      <Center h={200}>
        <Loader />
      </Center>
    );

  return (
    <ExpensesPageContent
      openCreateForm={openCreateForm}
      showForm={showForm}
      cancelForm={cancelForm}
      editing={editing}
      form={form}
      handleSubmit={handleSubmit}
      payers={payers}
      properties={properties}
      categories={categories}
      openPayerModal={openPayerModal}
      payerModalOpen={payerModalOpen}
      setPayerModalOpen={setPayerModalOpen}
      payerForm={payerForm}
      payerAccountInput={payerAccountInput}
      setPayerAccountInput={setPayerAccountInput}
      addPayerAccount={addPayerAccount}
      handlePayerModalSave={handlePayerModalSave}
      uploadedReceipt={uploadedReceipt}
      setUploadedReceipt={setUploadedReceipt}
      handleReceiptUpload={handleReceiptUpload}
      receiptUploading={receiptUploading}
      saveError={saveError}
      filterText={filterText}
      setFilterText={setFilterText}
      yearOptions={yearOptions}
      filterYear={filterYear}
      setFilterYear={setFilterYear}
      categoryOptions={categoryOptions}
      filterCategory={filterCategory}
      setFilterCategory={setFilterCategory}
      propertyFilterOptions={propertyFilterOptions}
      filterPropertyId={filterPropertyId}
      setFilterPropertyId={setFilterPropertyId}
      payerOptions={payerOptions}
      filterPayerId={filterPayerId}
      setFilterPayerId={setFilterPayerId}
      visibleExpenses={visibleExpenses}
      highlightId={highlightId}
      handleEdit={handleEdit}
      handleDelete={handleDelete}
    />
  );
}
