import { useEffect, useMemo, useState } from 'react';
import { useForm } from '@mantine/form';
import { useQueryClient } from '@tanstack/react-query';
import { useSessionState } from './useSessionState.js';
import { useSaveExpense } from './useSaveExpense.js';
import { filterLedgerRows, useLedgerPage } from './useLedgerPage.js';
import { findMatchingProperty } from './transactionPageUtils.js';
import { createCounterparty, uploadReceipt } from '../api/index.js';
import { PAYER_TYPE } from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { todayISO } from '../utils/formatters.js';

const getEmptyForm = () => ({
  amount: '',
  description: '',
  date: todayISO(),
  categoryId: null,
  propertyId: null,
  payerId: null,
  sourceType: null,
  sourceId: null,
  activityId: null,
});

/** @typedef {import('../generated/api/models/UpsertCounterpartyRequest').UpsertCounterpartyRequest} UpsertCounterpartyRequest */
/** @typedef {import('../api/index.js').NormalizedCounterpartyResponse} NormalizedCounterpartyResponse */

/** @type {{ name: string, type: UpsertCounterpartyRequest['type'], aliases: string[], accounts: string[] }} */
const EMPTY_PAYER_FORM = {
  name: '',
  type: /** @type {UpsertCounterpartyRequest['type']} */ (PAYER_TYPE.COMPANY),
  aliases: [],
  accounts: [],
};

export function useExpensesPage() {
  const queryClient = useQueryClient();
  const { saveExpense } = useSaveExpense();
  const form = useForm({
    initialValues: getEmptyForm(),
    validate: { categoryId: (value) => (!value ? 'Select a category' : null) },
  });
  const payerForm = useForm({ initialValues: EMPTY_PAYER_FORM });

  const ledger = useLedgerPage({
    direction: 'EXPENSE',
    storagePrefix: 'expenses',
    form,
    deleteConfig: {
      title: 'Delete expense',
      confirmMessage: 'This expense will be permanently deleted. This action cannot be undone.',
      fallbackErrorMessage: 'Could not delete expense.',
    },
  });

  const {
    rows: expenses,
    properties,
    propertiesFetched,
    payers,
    payersFetched,
    activities,
    activitiesFetched,
    setEditing,
    setShowForm,
    setSaveError,
    navigationState: { pendingPrefill, clearPendingPrefill, highlightId },
    commonFilters,
  } = ledger;

  const [payerModalOpen, setPayerModalOpen] = useState(false);
  const [payerAccountInput, setPayerAccountInput] = useState('');
  const [uploadedReceipt, setUploadedReceipt] = useState(null);
  const [receiptUploading, setReceiptUploading] = useState(false);
  const [filterPayerId, setFilterPayerId] = useSessionState('expenses.filterPayerId', null);
  const [filterCategory, setFilterCategory] = useSessionState('expenses.filterCategory', null);

  useEffect(() => {
    if (!pendingPrefill || !payersFetched || !propertiesFetched || !activitiesFetched) {
      return;
    }

    const matchedPayer = pendingPrefill.counterpartyName
      ? (payers.find(
          (payer) => payer.name.toLowerCase() === pendingPrefill.counterpartyName.toLowerCase()
        ) ?? null)
      : null;
    const matchedProperty = findMatchingProperty(properties, pendingPrefill.propertyName);
    const matchedActivity =
      activities.find((activity) => String(activity.id) === String(pendingPrefill.activity?.id)) ??
      activities.find(
        (activity) =>
          matchedProperty && String(activity.property?.id) === String(matchedProperty.id)
      ) ??
      null;

    form.setValues({
      amount: pendingPrefill.amount ?? '',
      description: pendingPrefill.description ?? '',
      date: pendingPrefill.date ?? todayISO(),
      categoryId: pendingPrefill.category?.neutralCategoryId
        ? String(pendingPrefill.category.neutralCategoryId)
        : null,
      propertyId: matchedProperty ? String(matchedProperty.id) : null,
      payerId: matchedPayer ? String(matchedPayer.id) : null,
      sourceType: pendingPrefill.origin ?? null,
      sourceId: pendingPrefill.sourceId ?? null,
      activityId: matchedActivity ? String(matchedActivity.id) : null,
    });

    if (pendingPrefill.counterpartyName && !matchedPayer) {
      payerForm.setValues({
        name: pendingPrefill.counterpartyName,
        type: /** @type {UpsertCounterpartyRequest['type']} */ (PAYER_TYPE.COMPANY),
        aliases: [],
        accounts: pendingPrefill.accountNumbers || [],
      });
      setPayerModalOpen(true);
    }

    setEditing(null);
    setShowForm(true);
    clearPendingPrefill();
  }, [
    activities,
    activitiesFetched,
    clearPendingPrefill,
    form,
    payerForm,
    payers,
    payersFetched,
    pendingPrefill,
    properties,
    propertiesFetched,
    setEditing,
    setShowForm,
  ]);

  const closePayerModal = () => {
    setPayerModalOpen(false);
    setPayerAccountInput('');
  };

  const openPayerModal = () => {
    payerForm.reset();
    setPayerAccountInput('');
    setPayerModalOpen(true);
  };

  const openCreateForm = () => ledger.openCreateForm(() => setUploadedReceipt(null));
  const cancelForm = () => ledger.cancelForm(() => setUploadedReceipt(null));

  const handlePayerModalSave = async () => {
    const newPayer = await createCounterparty(payerForm.values);
    queryClient.setQueryData(
      queryKeys.counterparties,
      /** @param {NormalizedCounterpartyResponse[] | undefined} existing */
      (existing = []) => [...existing, newPayer]
    );
    form.setFieldValue('payerId', String(newPayer.id));
    closePayerModal();
    payerForm.reset();
  };

  const addPayerAccount = () => {
    const trimmedAccount = payerAccountInput.trim();
    if (!trimmedAccount || payerForm.values.accounts.includes(trimmedAccount)) {
      return;
    }
    payerForm.insertListItem('accounts', trimmedAccount);
    setPayerAccountInput('');
  };

  const handleReceiptUpload = async (file) => {
    if (!file) {
      return;
    }
    setReceiptUploading(true);
    setSaveError(null);
    try {
      const dotIndex = file.name.lastIndexOf('.');
      const baseName = dotIndex >= 0 ? file.name.slice(0, dotIndex) : file.name;
      const extension = dotIndex >= 0 ? file.name.slice(dotIndex) : '';
      const date = form.values.date || todayISO();
      const renamedFile = new File([file], `${baseName}_${date}${extension}`, { type: file.type });
      const result = await uploadReceipt(renamedFile);
      setUploadedReceipt({ itemId: result.receipt.id, fileName: result.receipt.name });
    } catch (err) {
      setSaveError(getErrorMessage(err, 'Receipt upload failed. Please try again.'));
    } finally {
      setReceiptUploading(false);
    }
  };

  const handleSubmit = async (values) => {
    const success = await saveExpense({
      values,
      editing: ledger.editing,
      uploadedReceipt,
      form,
      setSaveError,
    });
    if (success) {
      cancelForm();
    }
  };

  const handleEdit = (expense) => {
    form.setValues({
      amount: expense.amount,
      description: expense.description,
      date: expense.date,
      categoryId: expense.neutralCategory?.id ? String(expense.neutralCategory.id) : null,
      propertyId: expense.property?.id ? String(expense.property.id) : null,
      payerId: expense.payer?.id ? String(expense.payer.id) : null,
      sourceType: expense.sourceType,
      sourceId: expense.sourceId,
      activityId: expense.activity?.id ? String(expense.activity.id) : null,
    });
    setEditing(expense);
    setUploadedReceipt(null);
    setSaveError(null);
    setShowForm(true);
  };

  const categoryOptions = useMemo(() => {
    const options = new Map();
    expenses.forEach((expense) => {
      if (expense.neutralCategory?.id) {
        options.set(String(expense.neutralCategory.id), expense.neutralCategory.label);
      }
    });
    return [...options].map(([value, label]) => ({ value, label }));
  }, [expenses]);

  const payerOptions = useMemo(
    () =>
      expenses
        .filter((expense) => expense.payer)
        .reduce((options, expense) => {
          if (!options.some((option) => option.value === String(expense.payer.id))) {
            options.push({ value: String(expense.payer.id), label: expense.payer.name });
          }
          return options;
        }, [])
        .sort((left, right) => left.label.localeCompare(right.label)),
    [expenses]
  );

  const visibleExpenses = useMemo(
    () =>
      filterLedgerRows(expenses, {
        payerId: filterPayerId,
        year: commonFilters.year,
        category: filterCategory,
        propertyId: commonFilters.propertyId,
        activityId: commonFilters.activityId,
        ownerId: commonFilters.ownerId,
        text: commonFilters.text,
      }),
    [
      commonFilters.activityId,
      commonFilters.ownerId,
      commonFilters.propertyId,
      commonFilters.text,
      commonFilters.year,
      expenses,
      filterCategory,
      filterPayerId,
    ]
  );

  return {
    isLoading: ledger.isLoading,
    pageActions: { openCreateForm },
    expenseForm: {
      opened: ledger.showForm,
      editing: ledger.editing,
      form,
      saveError: ledger.saveError,
      onCancel: cancelForm,
      onSubmit: handleSubmit,
      options: { payers, properties, activities },
      activityOptions: ledger.activityOptions,
      categoryOptions: ledger.compatibleCategoryOptions,
      selectedActivity: ledger.selectedActivity,
      receipt: {
        uploadedReceipt,
        setUploadedReceipt,
        onUpload: handleReceiptUpload,
        uploading: receiptUploading,
      },
      payerModal: {
        opened: payerModalOpen,
        open: openPayerModal,
        close: closePayerModal,
        form: payerForm,
        accountInput: payerAccountInput,
        setAccountInput: setPayerAccountInput,
        onAddAccount: addPayerAccount,
        onSave: handlePayerModalSave,
      },
    },
    filters: {
      ...commonFilters,
      category: filterCategory,
      setCategory: setFilterCategory,
      categoryOptions,
      payerId: filterPayerId,
      setPayerId: setFilterPayerId,
      payerOptions,
      hasActiveFilters: Boolean(commonFilters.hasActiveFilters || filterCategory || filterPayerId),
      clearFilters: () => {
        commonFilters.clearFilters();
        setFilterCategory(null);
        setFilterPayerId(null);
      },
    },
    table: {
      expenses: visibleExpenses,
      totalCount: expenses.length,
      totalAmount: visibleExpenses.reduce((sum, row) => sum + Number(row.amount || 0), 0),
      highlightId,
      activeFilters: {
        payerId: filterPayerId,
        category: filterCategory,
        year: commonFilters.year,
        text: commonFilters.text,
        propertyId: commonFilters.propertyId,
        activityId: commonFilters.activityId,
        ownerId: commonFilters.ownerId,
      },
      onEdit: handleEdit,
      onDelete: ledger.handleDelete,
    },
  };
}
