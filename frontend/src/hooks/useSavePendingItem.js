import { useState } from 'react';
import { savePendingExpense, savePendingIncome } from '../api/index.js';
import { getErrorMessage } from '../utils/errors.js';

/** Saves an edited Review Queue item as the user-selected direction. */
export function useSavePendingItem({ itemId, onSaved }) {
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const savePendingItem = async (form) => {
    setSaving(true);
    setSaveError(null);
    const common = {
      amount: Number(form.amount ?? 0),
      description: form.description,
      date: form.date,
      propertyId: form.propertyId ? Number(form.propertyId) : null,
      activityId: form.activityId ? Number(form.activityId) : null,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
    };
    try {
      const saved =
        form.direction === 'INCOME'
          ? await savePendingIncome(itemId, {
              ...common,
              source: form.source || form.counterpartyName || '',
            })
          : await savePendingExpense(itemId, {
              ...common,
              payerId: form.payerId ? Number(form.payerId) : null,
            });
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
