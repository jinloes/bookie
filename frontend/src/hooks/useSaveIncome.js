// @ts-check
import { useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { createIncome, updateIncome } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { createIncomeSchema } from '../validation/schemas.js';

/** @typedef {import('../generated/api/models/CreateIncomeRequest').CreateIncomeRequest} CreateIncomeRequest */

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

    /** @type {CreateIncomeRequest} */
    const data = {
      amount: Number(validationData.amount),
      description: validationData.description,
      date: validationData.date,
      source: validationData.source,
      activityId: validationData.activityId,
      categoryId: validationData.categoryId,
      propertyId: validationData.propertyId,
      payerId: validationData.payerId,
    };

    try {
      if (isEditing) {
        await updateIncome(editing, data);
      } else {
        await createIncome(data);
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
      queryClient.invalidateQueries({ queryKey: queryKeys.totalIncome });
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
