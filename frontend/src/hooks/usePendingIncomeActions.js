import { useQueryClient } from '@tanstack/react-query';
import { dismissInboxItem, saveInboxItem } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

/** Saves or dismisses Venmo-imported inbox income and synchronizes every affected cache. */
export function usePendingIncomeActions() {
  const queryClient = useQueryClient();

  const invalidatePendingIncomeCaches = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
    queryClient.invalidateQueries({ queryKey: queryKeys.financialActivities });
    queryClient.invalidateQueries({ queryKey: ['reports'] });
  };

  const savePendingIncome = async (id, form, payerId) => {
    const saved = await saveInboxItem(id, {
      direction: 'INCOME',
      amount: Number(form.amount),
      description: form.description,
      date: form.date,
      activityId: form.activityId ? Number(form.activityId) : null,
      neutralCategoryId: form.categoryId ? Number(form.categoryId) : null,
      counterpartyId: payerId ?? undefined,
      sourceLabel: form.source || undefined,
    });
    invalidatePendingIncomeCaches();
    return saved;
  };

  const deletePendingIncome = async (id) => {
    await dismissInboxItem(id);
    queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
  };

  return { savePendingIncome, deletePendingIncome };
}
