import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { saveInboxItem } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';

/** Saves an edited Review Queue item as one ledger transaction in the user-selected direction. */
export function useSavePendingItem({ itemId, onSaved }) {
  const queryClient = useQueryClient();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const savePendingItem = async (form) => {
    setSaving(true);
    setSaveError(null);
    const income = form.direction === 'INCOME';
    try {
      const saved = await saveInboxItem(itemId, {
        direction: income ? 'INCOME' : 'EXPENSE',
        amount: Number(form.amount ?? 0),
        description: form.description,
        date: form.date,
        activityId: form.activityId ? Number(form.activityId) : null,
        neutralCategoryId: form.categoryId ? Number(form.categoryId) : null,
        counterpartyId: !income && form.payerId ? Number(form.payerId) : undefined,
        sourceLabel: income ? form.source || form.counterpartyName || undefined : undefined,
      });
      queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
      queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      onSaved(itemId, saved);
      return true;
    } catch (error) {
      setSaveError(
        getErrorMessage(error, 'Could not save this item. Please check the fields and retry.')
      );
      return false;
    } finally {
      setSaving(false);
    }
  };

  return {
    saving,
    saveError,
    savePendingItem,
    clearSaveError: () => setSaveError(null),
  };
}
