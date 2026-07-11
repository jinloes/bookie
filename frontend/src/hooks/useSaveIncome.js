import { useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { createIncome, updateIncome } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { createIncomeSchema } from '../validation/schemas.js';

export function useSaveIncome() {
  const queryClient = useQueryClient();

  const saveIncome = async ({ values, editing, form, setSaveError }) => {
    setSaveError(null);
    const isEditing = !!editing;
    const data = {
      amount: String(values.amount ?? ''),
      description: values.description,
      date: values.date,
      sourceType: values.source,
      propertyId: values.propertyId ? Number(values.propertyId) : null,
      payerId: values.payerId ? Number(values.payerId) : null,
    };

    try {
      createIncomeSchema.parse(data);
    } catch (validationErr) {
      const fieldErrors = {};
      validationErr.issues.forEach((issue) => {
        fieldErrors[issue.path.join('.')] = issue.message;
      });
      setSaveError('Please fix validation errors before submitting.');
      form.setErrors(fieldErrors);
      return false;
    }

    try {
      if (isEditing) {
        await updateIncome(editing, data);
      } else {
        await createIncome(data);
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
      queryClient.invalidateQueries({ queryKey: queryKeys.totalIncome });
      notifications.show({
        title: isEditing ? 'Income updated' : 'Income saved',
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
