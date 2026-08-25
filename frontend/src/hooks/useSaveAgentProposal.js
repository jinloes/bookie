import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { createExpense, createIncome } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';

const INCOME = 'INCOME';

/**
 * Persists an edited Agent proposal only after the proposal card's explicit Save action.
 * Classification fields are sent by ID so the backend revalidates activity/category compatibility.
 */
export function useSaveAgentProposal() {
  const queryClient = useQueryClient();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const saveAgentProposal = async (form) => {
    setSaving(true);
    setSaveError(null);
    const common = {
      amount: Number(form.amount),
      description: form.description,
      date: form.date,
      propertyId: form.propertyId ? Number(form.propertyId) : null,
      payerId: form.payerId ? Number(form.payerId) : null,
      activityId: form.activityId ? Number(form.activityId) : null,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
    };

    try {
      const created =
        form.direction === INCOME
          ? await createIncome({
              ...common,
              source: form.counterpartyName || '',
            })
          : await createExpense({
              ...common,
              sourceType: 'MANUAL',
            });

      if (form.direction === INCOME) {
        queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
        queryClient.invalidateQueries({ queryKey: queryKeys.totalIncome });
      } else {
        queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
        queryClient.invalidateQueries({ queryKey: queryKeys.totalExpenses });
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.financialActivities });
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      return created;
    } catch (error) {
      setSaveError(
        getErrorMessage(error, 'Could not save this proposal. Review the fields and retry.')
      );
      throw error;
    } finally {
      setSaving(false);
    }
  };

  return { saveAgentProposal, saving, saveError };
}
