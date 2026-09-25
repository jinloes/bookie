// @ts-check
import { useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { createTransaction, preservedTransactionSource, updateTransaction } from '../api/index.js';
import { getErrorMessage } from '../utils/errors.js';
import { createExpenseSchema } from '../validation/schemas.js';
import { EXPENSE_SOURCE } from '../constants.js';
import { queryKeys } from '../queryKeys.js';

/** @typedef {import('../generated/api/models/CreateTransactionRequest').CreateTransactionRequest} CreateTransactionRequest */

/**
 * Validates, persists, and syncs the cache for an expense create/update on the unified ledger.
 *
 * Extracted out of Expenses.jsx so the create/update + invalidation logic is unit-testable
 * without rendering the full page (see frontend/AGENTS.md mutation-handler-extraction rule).
 * `editing` is the ledger row being edited (or null); its version and source identity are
 * resent so an edit neither conflicts nor drops the original import reference or attachment.
 *
 * Returns true on success (caller should close/reset the form) and false on failure
 * (caller should keep the form open so the user can fix errors).
 */
export function useSaveExpense() {
  const queryClient = useQueryClient();

  const saveExpense = async ({ values, editing, uploadedReceipt, form, setSaveError }) => {
    setSaveError(null);
    const isEditing = !!editing;

    const validationData = {
      amount: String(values.amount ?? ''),
      description: values.description,
      date: values.date,
      activityId: values.activityId ? Number(values.activityId) : null,
      categoryId: values.categoryId ? Number(values.categoryId) : null,
      propertyId: values.propertyId ? Number(values.propertyId) : null,
      payerId: values.payerId ? Number(values.payerId) : null,
    };

    try {
      createExpenseSchema.parse(validationData);
    } catch (validationErr) {
      const fieldErrors = {};
      // zod v4 exposes issues via `.issues` (the `.errors` alias from v3 no longer exists).
      validationErr.issues.forEach((err) => {
        const field = err.path.join('.');
        fieldErrors[field] = err.message;
      });
      setSaveError('Please fix validation errors before submitting.');
      form.setErrors(fieldErrors);
      return false;
    }

    /** @type {CreateTransactionRequest} */
    const data = {
      amount: Number(validationData.amount),
      direction: 'EXPENSE',
      description: values.description,
      date: values.date,
      activityId: validationData.activityId,
      neutralCategoryId: validationData.categoryId,
      counterpartyId: validationData.payerId ?? undefined,
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
        title: isEditing ? 'Expense updated' : 'Expense saved',
        message: isEditing ? 'The expense record was updated.' : 'The expense record was saved.',
        color: 'green',
      });
      return true;
    } catch (err) {
      setSaveError(getErrorMessage(err, 'Could not save expense. Please review fields and retry.'));
      return false;
    }
  };

  return { saveExpense };
}
