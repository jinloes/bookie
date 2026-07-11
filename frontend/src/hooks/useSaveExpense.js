import { useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { createExpense, updateExpense } from '../api/index.js';
import { getErrorMessage } from '../utils/errors.js';
import { createExpenseSchema } from '../validation/schemas.js';
import { EXPENSE_SOURCE } from '../constants.js';
import { queryKeys } from '../queryKeys.js';

/**
 * Validates, persists, and syncs the cache for an expense create/update.
 *
 * Extracted out of Expenses.jsx so the create/update + invalidation logic is unit-testable
 * without rendering the full page (see frontend/AGENTS.md mutation-handler-extraction rule).
 *
 * Returns true on success (caller should close/reset the form) and false on failure
 * (caller should keep the form open so the user can fix errors).
 */
export function useSaveExpense() {
  const queryClient = useQueryClient();

  const saveExpense = async ({ values, editing, uploadedReceipt, form, setSaveError }) => {
    setSaveError(null);
    const isEditing = !!editing;

    // Validate data structure for schema
    const validationData = {
      amount: String(values.amount ?? ''),
      description: values.description,
      date: values.date,
      category: values.category,
      propertyId: values.propertyId ? Number(values.propertyId) : null,
      payerId: values.payerId ? Number(values.payerId) : null,
    };

    // Validate before sending to API
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

    // Build the data object for the API
    const data = {
      amount: validationData.amount,
      description: values.description,
      date: values.date,
      category: values.category,
      propertyId: validationData.propertyId,
      payerId: validationData.payerId,
      sourceType: values.sourceType,
      sourceId: values.sourceId,
      ...(uploadedReceipt
        ? {
            receiptOneDriveId: uploadedReceipt.itemId,
            receiptFileName: uploadedReceipt.fileName,
            sourceType: EXPENSE_SOURCE.RECEIPT,
          }
        : {}),
    };

    try {
      if (isEditing) {
        await updateExpense(editing, data);
      } else {
        await createExpense(data);
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
      queryClient.invalidateQueries({ queryKey: queryKeys.totalExpenses });
      notifications.show({
        title: isEditing ? 'Expense updated' : 'Expense saved',
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
