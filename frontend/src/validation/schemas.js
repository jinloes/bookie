import { z } from 'zod';

const BigDecimalString = z.string().or(z.number()).refine(
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
  sourceType: z.string().min(1, 'Source type is required'),
  payerId: z.string().or(z.number()).pipe(z.coerce.number().positive('Payer is required')),
  propertyId: z.string().or(z.number()).pipe(z.coerce.number().positive('Property is required')),
});

export const createExpenseSchema = z.object({
  amount: BigDecimalString,
  description: z.string().min(1, 'Description is required').max(255),
  date: z.string().min(1, 'Date is required'),
  category: z.string().min(1, 'Category is required'),
  payerId: z.string().or(z.number()).pipe(z.coerce.number().positive('Payer is required')),
  propertyId: z.string().or(z.number()).pipe(z.coerce.number().positive('Property is required')),
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
