// @ts-check
import { useSaveTransaction } from './useSaveTransaction.js';

export function useSaveIncome() {
  const { saveTransaction } = useSaveTransaction('INCOME');
  return { saveIncome: saveTransaction };
}
