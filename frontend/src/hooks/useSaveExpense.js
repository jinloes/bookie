// @ts-check
import { useSaveTransaction } from './useSaveTransaction.js';

export function useSaveExpense() {
  const { saveTransaction } = useSaveTransaction('EXPENSE');
  return { saveExpense: saveTransaction };
}
