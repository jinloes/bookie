import { useQueryClient } from '@tanstack/react-query';
import { acceptPendingIncome, rejectPendingIncome } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

/** Persists or rejects Venmo-imported income and synchronizes every affected cache. */
export function usePendingIncomeActions() {
  const queryClient = useQueryClient();

  const invalidatePendingIncomeCaches = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.pendingIncomes });
    queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
    queryClient.invalidateQueries({ queryKey: queryKeys.totalIncome });
    queryClient.invalidateQueries({ queryKey: queryKeys.financialActivities });
    queryClient.invalidateQueries({ queryKey: ['reports'] });
  };

  const savePendingIncome = async (id, form, payerId) => {
    const saved = await acceptPendingIncome(id, {
      amount: Number(form.amount),
      description: form.description,
      date: form.date,
      source: form.source,
      propertyId: form.propertyId ? Number(form.propertyId) : null,
      payerId: payerId ?? null,
      activityId: form.activityId ? Number(form.activityId) : null,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
    });
    invalidatePendingIncomeCaches();
    return saved;
  };

  const deletePendingIncome = async (id) => {
    await rejectPendingIncome(id);
    queryClient.invalidateQueries({ queryKey: queryKeys.pendingIncomes });
  };

  return { savePendingIncome, deletePendingIncome };
}
