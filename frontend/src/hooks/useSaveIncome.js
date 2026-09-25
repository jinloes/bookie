// @ts-check
import { useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { createTransaction, preservedTransactionSource, updateTransaction } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { createIncomeSchema } from '../validation/schemas.js';

/** @typedef {import('../generated/api/models/CreateTransactionRequest').CreateTransactionRequest} CreateTransactionRequest */

/**
 * Validates and persists an income on the unified ledger. `editing` is the ledger row being
 * edited (or null); its version and source identity are resent with the update.
 */
export function useSaveIncome() {
  const queryClient = useQueryClient();

  const saveIncome = async ({ values, editing, form, setSaveError }) => {
    setSaveError(null);
    const isEditing = !!editing;
    const validationData = {
      amount: String(values.amount ?? ''),
      description: values.description,
      date: values.date,
      source: values.source,
      activityId: values.activityId ? Number(values.activityId) : null,
      categoryId: values.categoryId ? Number(values.categoryId) : null,
      propertyId: values.propertyId ? Number(values.propertyId) : null,
      payerId: values.payerId ? Number(values.payerId) : null,
    };

    try {
      createIncomeSchema.parse(validationData);
    } catch (validationErr) {
      const fieldErrors = {};
      validationErr.issues.forEach((issue) => {
        fieldErrors[issue.path.join('.')] = issue.message;
      });
      setSaveError('Please fix validation errors before submitting.');
      form.setErrors(fieldErrors);
      return false;
    }

    /** @type {CreateTransactionRequest} */
    const data = {
      amount: Number(validationData.amount),
      direction: 'INCOME',
      description: validationData.description,
      date: validationData.date,
      activityId: validationData.activityId,
      neutralCategoryId: validationData.categoryId,
      counterpartyId: validationData.payerId ?? undefined,
      sourceLabel: validationData.source || undefined,
      ...preservedTransactionSource(editing),
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
        title: isEditing ? 'Income updated' : 'Income saved',
        message: isEditing ? 'The income record was updated.' : 'The income record was saved.',
        color: 'green',
      });
      return true;
    } catch (err) {
      setSaveError(getErrorMessage(err, 'Could not save income. Please review fields and retry.'));
      return false;
    }
  };

  return { saveIncome };
}
