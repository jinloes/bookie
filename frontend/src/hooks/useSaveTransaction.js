// @ts-check
import { useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { createTransaction, preservedTransactionSource, updateTransaction } from '../api/index.js';
import { EXPENSE_SOURCE } from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { validateTransactionForm } from './transactionPageUtils.js';

/** @typedef {import('../generated/api/models/CreateTransactionRequest').CreateTransactionRequest} CreateTransactionRequest */

/**
 * Shared create/update + cache-invalidation hook for both Expenses and Incomes on the unified
 * ledger.
 *
 * @param {'EXPENSE' | 'INCOME'} direction
 */
export function useSaveTransaction(direction) {
  const queryClient = useQueryClient();
  const noun = direction === 'INCOME' ? 'Income' : 'Expense';
  const lowerNoun = noun.toLowerCase();

  const saveTransaction = async ({
    values,
    editing,
    uploadedReceipt = null,
    form,
    setSaveError,
  }) => {
    setSaveError(null);
    const isEditing = !!editing;
    const validation = validateTransactionForm(values);

    if (!validation.valid) {
      setSaveError('Please fix validation errors before submitting.');
      form.setErrors(validation.errors);
      return false;
    }

    const { parsed } = validation;

    /** @type {CreateTransactionRequest} */
    const data = {
      amount: parsed.amount,
      direction,
      description: parsed.description,
      date: parsed.date,
      activityId: parsed.activityId,
      neutralCategoryId: parsed.categoryId,
      counterpartyId: parsed.payerId,
      ...(direction === 'INCOME' ? { sourceLabel: parsed.source } : {}),
      ...preservedTransactionSource(editing),
      ...(uploadedReceipt
        ? {
            origin: /** @type {CreateTransactionRequest['origin']} */ (EXPENSE_SOURCE.RECEIPT),
            attachmentExternalId: uploadedReceipt.itemId,
            attachmentFileName: uploadedReceipt.fileName,
          }
        : {}),
    };

    try {
      if (isEditing) {
        await updateTransaction(editing.id, { ...data, version: editing.version });
      } else {
        await createTransaction(data);
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
      queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
      queryClient.invalidateQueries({ queryKey: queryKeys.financialActivities });
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      notifications.show({
        title: isEditing ? `${noun} updated` : `${noun} saved`,
        message: isEditing
          ? `The ${lowerNoun} record was updated.`
          : `The ${lowerNoun} record was saved.`,
        color: 'green',
      });
      return true;
    } catch (err) {
      setSaveError(
        getErrorMessage(err, `Could not save ${lowerNoun}. Please review fields and retry.`)
      );
      return false;
    }
  };

  return { saveTransaction };
}
