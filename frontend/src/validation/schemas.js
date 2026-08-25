import { z } from 'zod';

const BigDecimalString = z
  .string()
  .or(z.number())
  .refine(
    (val) => {
      const num = typeof val === 'string' ? parseFloat(val) : val;
      return !isNaN(num) && num >= 0;
    },
    { message: 'Amount must be a positive number' }
  );

export const createIncomeSchema = z.object({
  amount: BigDecimalString,
  description: z.string().min(1, 'Description is required').max(255),
  date: z.string().min(1, 'Date is required'),
  source: z.string().max(255).optional(),
  activityId: z.number().positive('Activity is required'),
  categoryId: z.number().positive('Category is required'),
  payerId: z.number().positive().nullable().optional(),
  propertyId: z.number().positive().nullable().optional(),
});

export const createExpenseSchema = z.object({
  amount: BigDecimalString,
  description: z.string().min(1, 'Description is required').max(255),
  date: z.string().min(1, 'Date is required'),
  activityId: z.number().positive('Activity is required'),
  categoryId: z.number().positive('Category is required'),
  payerId: z.number().positive().nullable().optional(),
  propertyId: z.number().positive().nullable().optional(),
});

export const createPropertySchema = z.object({
  address: z.string().min(1, 'Address is required').max(255),
  nickname: z.string().max(255).optional(),
});

export const upsertPayerSchema = z.object({
  name: z.string().min(1, 'Name is required').max(255),
  accountNumbers: z.array(z.string()).optional(),
});

export function validateCreateIncome(data) {
  try {
    return createIncomeSchema.parse(data);
  } catch (error) {
    return { error: error.errors };
  }
}

export function validateCreateExpense(data) {
  try {
    return createExpenseSchema.parse(data);
  } catch (error) {
    return { error: error.errors };
  }
}

export function validateCreateProperty(data) {
  try {
    return createPropertySchema.parse(data);
  } catch (error) {
    return { error: error.errors };
  }
}

export function validateUpsertPayer(data) {
  try {
    return upsertPayerSchema.parse(data);
  } catch (error) {
    return { error: error.errors };
  }
}
