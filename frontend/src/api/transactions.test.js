import { afterEach, describe, expect, it, vi } from 'vitest';
import { transactionApi } from './client.js';
import {
  createTransaction,
  deleteTransaction,
  getTransactionById,
  getTransactions,
  updateTransaction,
} from './index.js';

describe('unified transaction API wrappers', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('delegates list and lookup operations to the generated client', async () => {
    const listSpy = vi.spyOn(transactionApi, 'getTransactions').mockResolvedValue([]);
    const lookupSpy = vi.spyOn(transactionApi, 'getTransactionById').mockResolvedValue({ id: 42 });

    await expect(getTransactions()).resolves.toEqual([]);
    await expect(getTransactionById('42')).resolves.toEqual({ id: 42 });

    expect(listSpy).toHaveBeenCalledWith();
    expect(lookupSpy).toHaveBeenCalledWith({ id: 42 });
  });

  it('preserves generated command parameter shapes and optimistic versions', async () => {
    const createRequest = {
      amount: 25,
      direction: 'EXPENSE',
      date: '2026-08-25',
      description: 'Ledger test',
      activityId: 3,
      neutralCategoryId: 7,
    };
    const updateRequest = { ...createRequest, version: 4 };
    const createSpy = vi.spyOn(transactionApi, 'createTransaction').mockResolvedValue({ id: 11 });
    const updateSpy = vi.spyOn(transactionApi, 'updateTransaction').mockResolvedValue({ id: 11 });
    const deleteSpy = vi.spyOn(transactionApi, 'deleteTransaction').mockResolvedValue();

    await createTransaction(createRequest);
    await updateTransaction('11', updateRequest);
    await deleteTransaction('11', 5);

    expect(createSpy).toHaveBeenCalledWith({ createTransactionRequest: createRequest });
    expect(updateSpy).toHaveBeenCalledWith({
      id: 11,
      updateTransactionRequest: updateRequest,
    });
    expect(deleteSpy).toHaveBeenCalledWith({ id: 11, version: 5 });
  });
});
