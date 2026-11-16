import { useEffect, useMemo } from 'react';
import { useForm } from '@mantine/form';
import { useSaveIncome } from './useSaveIncome.js';
import { useImportIncomeStatement } from './useImportIncomeStatement.js';
import { filterLedgerRows, useLedgerPage } from './useLedgerPage.js';
import { findMatchingProperty } from './transactionPageUtils.js';
import { todayISO } from '../utils/formatters.js';

const getEmptyForm = () => ({
  amount: '',
  description: '',
  date: todayISO(),
  source: '',
  propertyId: null,
  payerId: null,
  activityId: null,
  categoryId: null,
});

export function useIncomesPage() {
  const { saveIncome } = useSaveIncome();
  const importStatement = useImportIncomeStatement();
  const form = useForm({ initialValues: getEmptyForm() });

  const ledger = useLedgerPage({
    direction: 'INCOME',
    storagePrefix: 'incomes',
    form,
    deleteConfig: {
      title: 'Delete income',
      confirmMessage: 'This income record will be permanently deleted.',
      fallbackErrorMessage: 'Could not delete income.',
    },
  });

  const {
    rows: incomes,
    properties,
    propertiesFetched,
    payers,
    activities,
    activitiesFetched,
    setEditing,
    setShowForm,
    setSaveError,
    navigationState: { pendingPrefill, clearPendingPrefill, highlightId },
    commonFilters,
  } = ledger;

  useEffect(() => {
    if (!pendingPrefill || !propertiesFetched || !activitiesFetched) {
      return;
    }

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
      source: pendingPrefill.counterpartyName ?? '',
      propertyId: matchedProperty ? String(matchedProperty.id) : null,
      payerId: null,
      activityId: matchedActivity ? String(matchedActivity.id) : null,
      categoryId: pendingPrefill.category?.neutralCategoryId
        ? String(pendingPrefill.category.neutralCategoryId)
        : null,
    });
    setEditing(null);
    setShowForm(true);
    clearPendingPrefill();
  }, [
    activities,
    activitiesFetched,
    clearPendingPrefill,
    form,
    pendingPrefill,
    properties,
    propertiesFetched,
    setEditing,
    setShowForm,
  ]);

  const payerOptions = useMemo(
    () => payers.map((payer) => ({ value: String(payer.id), label: payer.name })),
    [payers]
  );

  const visibleIncomes = useMemo(
    () =>
      filterLedgerRows(incomes, {
        year: commonFilters.year,
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
      incomes,
    ]
  );

  const openCreateForm = () => ledger.openCreateForm();
  const cancelForm = () => ledger.cancelForm();

  const handleSubmit = async (values) => {
    const success = await saveIncome({
      values,
      editing: ledger.editing,
      form,
      setSaveError,
    });
    if (success) {
      form.reset();
      form.setFieldValue('date', todayISO());
      setEditing(null);
      setShowForm(false);
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
      activityId: income.activity?.id ? String(income.activity.id) : null,
      categoryId: income.neutralCategory?.id ? String(income.neutralCategory.id) : null,
    });
    setEditing(income);
    setSaveError(null);
    setShowForm(true);
  };

  return {
    isLoading: ledger.isLoading,
    pageActions: {
      openCreateForm,
      openImportForm: importStatement.open,
    },
    incomeForm: {
      opened: ledger.showForm,
      editing: ledger.editing,
      form,
      saveError: ledger.saveError,
      onCancel: cancelForm,
      onSubmit: handleSubmit,
      propertyOptions: ledger.propertyOptions,
      payerOptions,
      activityOptions: ledger.activityOptions,
      activities,
      selectedActivity: ledger.selectedActivity,
      categoryOptions: ledger.compatibleCategoryOptions,
      categoriesLoading: ledger.categoriesLoading,
    },
    importForm: {
      ...importStatement.importForm,
      payerOptions,
      activityOptions: ledger.activityOptions,
    },
    filters: commonFilters,
    finalizedTable: {
      incomes: visibleIncomes,
      totalCount: incomes.length,
      totalAmount: visibleIncomes.reduce((sum, row) => sum + Number(row.amount || 0), 0),
      highlightId,
      activeFilters: {
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
