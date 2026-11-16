import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  getCounterparties,
  getFinancialActivities,
  getFinancialCategories,
  getProperties,
  getTransactions,
  toLedgerRow,
} from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { todayISO } from '../utils/formatters.js';
import { useDeleteTransaction } from './useDeleteTransaction.js';
import { usePageNavigationState } from './usePageNavigationState.js';
import { useSessionState } from './useSessionState.js';
import {
  buildActiveActivityOptions,
  buildNeutralCategoryOptions,
  buildOwnerOptions,
  buildYearOptions,
} from './transactionPageUtils.js';

/**
 * Shared ledger query, catalog option, session filter, and delete-confirmation scaffolding for
 * direction-filtered transaction pages (`Expenses` and `Incomes`).
 */
export function useLedgerPage({ direction, storagePrefix, form, deleteConfig }) {
  const navigationState = usePageNavigationState();
  const { confirmDelete } = useDeleteTransaction(deleteConfig);

  const { data: transactions = [], isLoading: transactionsLoading } = useQuery({
    queryKey: queryKeys.transactionsByDirection(direction),
    queryFn: () => getTransactions({ direction }),
  });
  const { data: properties = [], isFetched: propertiesFetched } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const { data: payers = [], isFetched: payersFetched } = useQuery({
    queryKey: queryKeys.counterparties,
    queryFn: getCounterparties,
  });
  const {
    data: activities = [],
    isFetched: activitiesFetched,
    isLoading: activitiesLoading,
  } = useQuery({
    queryKey: queryKeys.financialActivities,
    queryFn: getFinancialActivities,
  });
  const { data: compatibleCategories = [], isLoading: categoriesLoading } = useQuery({
    queryKey: queryKeys.financialCategories(direction, form.values.activityId),
    queryFn: () => getFinancialCategories(direction, form.values.activityId),
    enabled: Boolean(form.values.activityId),
  });

  const rows = useMemo(
    () =>
      transactions
        .filter((transaction) => transaction.direction === direction)
        .map((transaction) => toLedgerRow(transaction, { properties, counterparties: payers })),
    [direction, payers, properties, transactions]
  );

  const [editing, setEditing] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const [filterYear, setFilterYear] = useSessionState(`${storagePrefix}.filterYear`, null);
  const [filterText, setFilterText] = useSessionState(`${storagePrefix}.filterText`, '');
  const [filterPropertyId, setFilterPropertyId] = useSessionState(
    `${storagePrefix}.filterPropertyId`,
    null
  );
  const [filterActivityId, setFilterActivityId] = useSessionState(
    `${storagePrefix}.filterActivityId`,
    null
  );
  const [filterOwnerId, setFilterOwnerId] = useSessionState(`${storagePrefix}.filterOwnerId`, null);

  const yearOptions = useMemo(() => buildYearOptions(rows), [rows]);
  const compatibleCategoryOptions = useMemo(
    () => buildNeutralCategoryOptions(compatibleCategories),
    [compatibleCategories]
  );
  const propertyOptions = useMemo(
    () => properties.map((property) => ({ value: String(property.id), label: property.name })),
    [properties]
  );
  const activityOptions = useMemo(() => buildActiveActivityOptions(activities), [activities]);
  const ownerOptions = useMemo(() => buildOwnerOptions(activities), [activities]);
  const selectedActivity =
    activities.find((activity) => String(activity.id) === String(form.values.activityId)) ?? null;

  const openCreateForm = (onResetExtras) => {
    form.reset();
    form.setFieldValue('date', todayISO());
    setEditing(null);
    setSaveError(null);
    onResetExtras?.();
    setShowForm(true);
  };

  const cancelForm = (onResetExtras) => {
    setShowForm(false);
    setEditing(null);
    setSaveError(null);
    onResetExtras?.();
    form.reset();
  };

  const handleDelete = (rowId) => confirmDelete(rowId, rows);
  const hasCommonFilters = Boolean(
    filterText || filterYear || filterPropertyId || filterActivityId || filterOwnerId
  );
  const clearCommonFilters = () => {
    setFilterText('');
    setFilterYear(null);
    setFilterPropertyId(null);
    setFilterActivityId(null);
    setFilterOwnerId(null);
  };

  return {
    isLoading: transactionsLoading || activitiesLoading,
    categoriesLoading,
    rows,
    properties,
    propertiesFetched,
    payers,
    payersFetched,
    activities,
    activitiesFetched,
    yearOptions,
    compatibleCategoryOptions,
    propertyOptions,
    activityOptions,
    ownerOptions,
    selectedActivity,
    editing,
    setEditing,
    showForm,
    setShowForm,
    saveError,
    setSaveError,
    openCreateForm,
    cancelForm,
    handleDelete,
    navigationState,
    commonFilters: {
      text: filterText,
      setText: setFilterText,
      year: filterYear,
      setYear: setFilterYear,
      yearOptions,
      propertyId: filterPropertyId,
      setPropertyId: setFilterPropertyId,
      propertyOptions,
      activityId: filterActivityId,
      setActivityId: setFilterActivityId,
      activityOptions,
      ownerId: filterOwnerId,
      setOwnerId: setFilterOwnerId,
      ownerOptions,
      hasActiveFilters: hasCommonFilters,
      clearFilters: clearCommonFilters,
    },
  };
}

/**
 * Shared row filter used by both Expenses and Incomes pages.
 *
 * @param {any[]} rows
 * @param {{
 *   year?: string | null,
 *   propertyId?: string | null,
 *   activityId?: string | null,
 *   ownerId?: string | null,
 *   payerId?: string | null,
 *   category?: string | null,
 *   text?: string | null,
 * }} filters
 */
export function filterLedgerRows(
  rows,
  { year, propertyId, activityId, ownerId, payerId = null, category = null, text }
) {
  let result = rows;
  if (payerId) {
    result = result.filter((row) => row.payer && String(row.payer.id) === payerId);
  }
  if (year) {
    result = result.filter((row) => row.date?.startsWith(year));
  }
  if (category) {
    result = result.filter((row) => String(row.neutralCategory?.id) === category);
  }
  if (propertyId) {
    result = result.filter((row) => row.property && String(row.property.id) === propertyId);
  }
  if (activityId) {
    result = result.filter((row) => String(row.activity?.id) === activityId);
  }
  if (ownerId) {
    result = result.filter((row) => String(row.activity?.owner?.id) === ownerId);
  }
  if (text) {
    const query = text.toLowerCase();
    result = result.filter(
      (row) =>
        row.description?.toLowerCase().includes(query) ||
        row.source?.toLowerCase().includes(query) ||
        row.payer?.name?.toLowerCase().includes(query) ||
        row.property?.name?.toLowerCase().includes(query) ||
        row.category?.toLowerCase().includes(query) ||
        row.neutralCategory?.label?.toLowerCase().includes(query) ||
        row.activity?.name?.toLowerCase().includes(query) ||
        row.activity?.owner?.name?.toLowerCase().includes(query)
    );
  }
  return result;
}
