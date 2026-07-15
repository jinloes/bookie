import { createElement, useEffect, useMemo, useState } from 'react';
import { Text } from '@mantine/core';
import { useForm } from '@mantine/form';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSessionState } from './useSessionState.js';
import { usePageNavigationState } from './usePageNavigationState.js';
import { useSaveIncome } from './useSaveIncome.js';
import { buildYearOptions, findMatchingProperty } from './transactionPageUtils.js';
import {
  deleteIncome,
  getIncomes,
  getPayers,
  getProperties,
  importVenmoIncomes,
} from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { todayISO } from '../utils/formatters.js';

const getEmptyForm = () => ({
  amount: '',
  description: '',
  date: todayISO(),
  source: '',
  propertyId: null,
  payerId: null,
});

export function useIncomesPage() {
  const queryClient = useQueryClient();
  const { saveIncome } = useSaveIncome();
  const { pendingPrefill, clearPendingPrefill, highlightId } = usePageNavigationState();
  const { data: incomes = [], isLoading: incomesLoading } = useQuery({
    queryKey: queryKeys.incomes,
    queryFn: getIncomes,
  });
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
  const [filterYear, setFilterYear] = useSessionState('incomes.filterYear', null);
  const [filterText, setFilterText] = useSessionState('incomes.filterText', '');
  const [filterPropertyId, setFilterPropertyId] = useSessionState('incomes.filterPropertyId', null);

  useEffect(() => {
    if (!pendingPrefill || !propertiesFetched) {
      return;
    }

    const matchedProperty = findMatchingProperty(properties, pendingPrefill.propertyName);

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
    clearPendingPrefill();
  }, [clearPendingPrefill, form, pendingPrefill, properties, propertiesFetched]);

  const propertyOptions = useMemo(
    () => properties.map((property) => ({ value: String(property.id), label: property.name })),
    [properties]
  );
  const payerOptions = useMemo(
    () => payers.map((payer) => ({ value: String(payer.id), label: payer.name })),
    [payers]
  );
  const yearOptions = useMemo(() => buildYearOptions(incomes), [incomes]);

  const visibleIncomes = useMemo(() => {
    let result = filterYear
      ? incomes.filter((income) => income.date?.startsWith(filterYear))
      : incomes;

    if (filterPropertyId) {
      result = result.filter(
        (income) => income.property && String(income.property.id) === filterPropertyId
      );
    }
    if (filterText) {
      const query = filterText.toLowerCase();
      result = result.filter(
        (income) =>
          income.description?.toLowerCase().includes(query) ||
          income.source?.toLowerCase().includes(query) ||
          income.payer?.name?.toLowerCase().includes(query) ||
          income.property?.name?.toLowerCase().includes(query)
      );
    }

    return result;
  }, [filterPropertyId, filterText, filterYear, incomes]);

  const openCreateForm = () => {
    form.reset();
    form.setFieldValue('date', todayISO());
    setEditing(null);
    setSaveError(null);
    setShowForm(true);
  };

  const cancelForm = () => {
    setShowForm(false);
    setEditing(null);
    setSaveError(null);
    form.reset();
  };

  const handleSubmit = async (values) => {
    const success = await saveIncome({ values, editing, form, setSaveError });

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
    });
    setEditing(income.id);
    setSaveError(null);
    setShowForm(true);
  };

  const invalidateIncomeQueries = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
    queryClient.invalidateQueries({ queryKey: queryKeys.totalIncome });
  };

  const invalidateAllIncomeQueries = () => {
    invalidateIncomeQueries();
    queryClient.invalidateQueries({ queryKey: queryKeys.pendingIncomes });
  };

  const handleDelete = (incomeId) => {
    modals.openConfirmModal({
      title: 'Delete income',
      children: createElement(
        Text,
        { size: 'sm' },
        'This income record will be permanently deleted.'
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await deleteIncome(incomeId);
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

  const openImportForm = () => {
    setShowImportForm(true);
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

  return {
    isLoading: incomesLoading,
    pageActions: {
      openCreateForm,
      openImportForm,
    },
    incomeForm: {
      opened: showForm,
      editing,
      form,
      saveError,
      onCancel: cancelForm,
      onSubmit: handleSubmit,
      propertyOptions,
      payerOptions,
    },
    importForm: {
      opened: showImportForm,
      payerId: importPayerId,
      setPayerId: setImportPayerId,
      payerOptions,
      file: importFile,
      setFile: setImportFile,
      error: importError,
      onCancel: cancelImportForm,
      onSubmit: handleImportSubmit,
    },
    filters: {
      text: filterText,
      setText: setFilterText,
      year: filterYear,
      setYear: setFilterYear,
      yearOptions,
      propertyId: filterPropertyId,
      setPropertyId: setFilterPropertyId,
      propertyOptions,
    },
    finalizedTable: {
      incomes: visibleIncomes,
      highlightId,
      activeFilters: {
        year: filterYear,
        text: filterText,
      },
      onEdit: handleEdit,
      onDelete: handleDelete,
    },
  };
}
