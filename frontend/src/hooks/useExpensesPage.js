import { createElement, useEffect, useMemo, useState } from 'react';
import { useForm } from '@mantine/form';
import { modals } from '@mantine/modals';
import { Text } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSessionState } from './useSessionState.js';
import { useSaveExpense } from './useSaveExpense.js';
import { usePageNavigationState } from './usePageNavigationState.js';
import { buildYearOptions, findMatchingProperty } from './transactionPageUtils.js';
import {
  createPayer,
  deleteExpense,
  getExpenses,
  getFinancialActivities,
  getFinancialCategories,
  getPayers,
  getProperties,
  uploadReceipt,
} from '../api/index.js';
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

const EMPTY_PAYER_FORM = { name: '', type: PAYER_TYPE.COMPANY, aliases: [], accounts: [] };

export function useExpensesPage() {
  const queryClient = useQueryClient();
  const { saveExpense } = useSaveExpense();
  const { pendingPrefill, clearPendingPrefill, highlightId } = usePageNavigationState();
  const { data: expenses = [], isLoading } = useQuery({
    queryKey: queryKeys.expenses,
    queryFn: getExpenses,
  });
  const { data: properties = [], isFetched: propertiesFetched } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const { data: payers = [], isFetched: payersFetched } = useQuery({
    queryKey: queryKeys.payers,
    queryFn: getPayers,
  });
  const {
    data: activities = [],
    isFetched: activitiesFetched,
    isLoading: activitiesLoading,
  } = useQuery({
    queryKey: queryKeys.financialActivities,
    queryFn: getFinancialActivities,
  });

  const form = useForm({
    initialValues: getEmptyForm(),
    validate: { categoryId: (value) => (!value ? 'Select a category' : null) },
  });
  const { data: compatibleCategories = [] } = useQuery({
    queryKey: queryKeys.financialCategories('EXPENSE', form.values.activityId),
    queryFn: () => getFinancialCategories('EXPENSE', form.values.activityId),
    enabled: Boolean(form.values.activityId),
  });
  const payerForm = useForm({ initialValues: EMPTY_PAYER_FORM });

  const [editing, setEditing] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [payerModalOpen, setPayerModalOpen] = useState(false);
  const [payerAccountInput, setPayerAccountInput] = useState('');
  const [uploadedReceipt, setUploadedReceipt] = useState(null);
  const [receiptUploading, setReceiptUploading] = useState(false);
  const [filterPayerId, setFilterPayerId] = useSessionState('expenses.filterPayerId', null);
  const [filterYear, setFilterYear] = useSessionState('expenses.filterYear', null);
  const [filterText, setFilterText] = useSessionState('expenses.filterText', '');
  const [filterCategory, setFilterCategory] = useSessionState('expenses.filterCategory', null);
  const [filterPropertyId, setFilterPropertyId] = useSessionState(
    'expenses.filterPropertyId',
    null
  );
  const [filterActivityId, setFilterActivityId] = useSessionState(
    'expenses.filterActivityId',
    null
  );
  const [filterOwnerId, setFilterOwnerId] = useSessionState('expenses.filterOwnerId', null);

  useEffect(() => {
    if (!pendingPrefill || !payersFetched || !propertiesFetched || !activitiesFetched) {
      return;
    }

    const matchedPayer = pendingPrefill.payerName
      ? (payers.find(
          (payer) => payer.name.toLowerCase() === pendingPrefill.payerName.toLowerCase()
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
      categoryId: pendingPrefill.financialCategory?.id
        ? String(pendingPrefill.financialCategory.id)
        : null,
      propertyId: matchedProperty ? String(matchedProperty.id) : null,
      payerId: matchedPayer ? String(matchedPayer.id) : null,
      sourceType: pendingPrefill.sourceType ?? null,
      sourceId: pendingPrefill.sourceId ?? null,
      activityId: matchedActivity ? String(matchedActivity.id) : null,
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
    clearPendingPrefill();
  }, [
    clearPendingPrefill,
    activities,
    activitiesFetched,
    form,
    payers,
    payersFetched,
    payerForm,
    pendingPrefill,
    properties,
    propertiesFetched,
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

  const openCreateForm = () => {
    form.reset();
    form.setFieldValue('date', todayISO());
    setEditing(null);
    setUploadedReceipt(null);
    setSaveError(null);
    setShowForm(true);
  };

  const handlePayerModalSave = async () => {
    const newPayer = await createPayer(payerForm.values);
    queryClient.setQueryData(queryKeys.payers, (existing = []) => [...existing, newPayer]);
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

  const cancelForm = () => {
    setShowForm(false);
    setEditing(null);
    setSaveError(null);
    setUploadedReceipt(null);
    form.reset();
  };

  const handleSubmit = async (values) => {
    const success = await saveExpense({ values, editing, uploadedReceipt, form, setSaveError });

    if (success) {
      cancelForm();
    }
  };

  const handleEdit = (expense) => {
    form.setValues({
      amount: expense.amount,
      description: expense.description,
      date: expense.date,
      categoryId: expense.financialCategory?.id ? String(expense.financialCategory.id) : null,
      propertyId: expense.property?.id ? String(expense.property.id) : null,
      payerId: expense.payer?.id ? String(expense.payer.id) : null,
      sourceType: expense.sourceType,
      sourceId: expense.sourceId,
      activityId: expense.activity?.id ? String(expense.activity.id) : null,
    });
    setEditing(expense.id);
    setUploadedReceipt(null);
    setSaveError(null);
    setShowForm(true);
  };

  const handleDelete = (expenseId) => {
    modals.openConfirmModal({
      title: 'Delete expense',
      children: createElement(
        Text,
        { size: 'sm' },
        'This expense will be permanently deleted. This action cannot be undone.'
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        await deleteExpense(expenseId);
        queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
        queryClient.invalidateQueries({ queryKey: queryKeys.totalExpenses });
        queryClient.invalidateQueries({ queryKey: ['reports'] });
      },
    });
  };

  const yearOptions = useMemo(() => buildYearOptions(expenses), [expenses]);
  const categoryOptions = useMemo(() => {
    const options = new Map();
    expenses.forEach((expense) => {
      if (expense.financialCategory?.id) {
        options.set(String(expense.financialCategory.id), expense.financialCategory.label);
      }
    });
    return [...options].map(([value, label]) => ({ value, label }));
  }, [expenses]);
  const compatibleCategoryOptions = compatibleCategories.map((category) => ({
    value: String(category.id),
    label: category.label,
  }));
  const propertyOptions = useMemo(
    () => properties.map((property) => ({ value: String(property.id), label: property.name })),
    [properties]
  );
  const activityOptions = useMemo(
    () =>
      activities
        .filter((activity) => activity.active)
        .map((activity) => ({ value: String(activity.id), label: activity.name })),
    [activities]
  );
  const ownerOptions = useMemo(() => {
    const owners = new Map();
    activities.forEach((activity) => {
      if (activity.owner?.id) {
        owners.set(String(activity.owner.id), activity.owner.name);
      }
    });
    return [...owners].map(([value, label]) => ({ value, label }));
  }, [activities]);
  const selectedActivity =
    activities.find((activity) => String(activity.id) === String(form.values.activityId)) ?? null;
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

  const visibleExpenses = useMemo(() => {
    let result = expenses;

    if (filterPayerId) {
      result = result.filter(
        (expense) => expense.payer && String(expense.payer.id) === filterPayerId
      );
    }
    if (filterYear) {
      result = result.filter((expense) => expense.date?.startsWith(filterYear));
    }
    if (filterCategory) {
      result = result.filter((expense) => String(expense.financialCategory?.id) === filterCategory);
    }
    if (filterPropertyId) {
      result = result.filter(
        (expense) => expense.property && String(expense.property.id) === filterPropertyId
      );
    }
    if (filterActivityId) {
      result = result.filter((expense) => String(expense.activity?.id) === filterActivityId);
    }
    if (filterOwnerId) {
      result = result.filter((expense) => String(expense.activity?.owner?.id) === filterOwnerId);
    }
    if (filterText) {
      const query = filterText.toLowerCase();
      result = result.filter(
        (expense) =>
          expense.description?.toLowerCase().includes(query) ||
          expense.payer?.name?.toLowerCase().includes(query) ||
          expense.property?.name?.toLowerCase().includes(query) ||
          expense.category?.toLowerCase().includes(query) ||
          expense.financialCategory?.label?.toLowerCase().includes(query) ||
          expense.activity?.name?.toLowerCase().includes(query) ||
          expense.activity?.owner?.name?.toLowerCase().includes(query)
      );
    }

    return result;
  }, [
    expenses,
    filterActivityId,
    filterCategory,
    filterOwnerId,
    filterPayerId,
    filterPropertyId,
    filterText,
    filterYear,
  ]);

  return {
    isLoading: isLoading || activitiesLoading,
    pageActions: { openCreateForm },
    expenseForm: {
      opened: showForm,
      editing,
      form,
      saveError,
      onCancel: cancelForm,
      onSubmit: handleSubmit,
      options: { payers, properties, activities },
      activityOptions,
      categoryOptions: compatibleCategoryOptions,
      selectedActivity,
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
      text: filterText,
      setText: setFilterText,
      year: filterYear,
      setYear: setFilterYear,
      yearOptions,
      category: filterCategory,
      setCategory: setFilterCategory,
      categoryOptions,
      propertyId: filterPropertyId,
      setPropertyId: setFilterPropertyId,
      propertyOptions,
      payerId: filterPayerId,
      setPayerId: setFilterPayerId,
      payerOptions,
      activityId: filterActivityId,
      setActivityId: setFilterActivityId,
      activityOptions,
      ownerId: filterOwnerId,
      setOwnerId: setFilterOwnerId,
      ownerOptions,
    },
    table: {
      expenses: visibleExpenses,
      highlightId,
      activeFilters: {
        payerId: filterPayerId,
        year: filterYear,
        text: filterText,
        activityId: filterActivityId,
        ownerId: filterOwnerId,
      },
      onEdit: handleEdit,
      onDelete: handleDelete,
    },
  };
}
