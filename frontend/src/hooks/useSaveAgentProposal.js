import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { createTransaction } from '../api/index.js';
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
    const income = form.direction === INCOME;

    try {
      const created = await createTransaction({
        amount: Number(form.amount),
        direction: income ? INCOME : 'EXPENSE',
        description: form.description,
        date: form.date,
        activityId: form.activityId ? Number(form.activityId) : null,
        neutralCategoryId: form.categoryId ? Number(form.categoryId) : null,
        counterpartyId: form.payerId ? Number(form.payerId) : undefined,
        sourceLabel: income ? form.counterpartyName || undefined : undefined,
      });

      queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
      queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
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
