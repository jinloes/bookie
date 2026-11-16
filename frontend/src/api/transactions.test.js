import { afterEach, describe, expect, it, vi } from 'vitest';
import { counterpartyApi, propertyApi, transactionApi } from './client.js';
import {
  createCounterparty,
  createProperty,
  createTransaction,
  deleteTransaction,
  getCounterparties,
  getProperties,
  getTransactionById,
  getTransactions,
  toLedgerRow,
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
    await expect(getTransactions({ direction: 'EXPENSE', year: 2026 })).resolves.toEqual([]);
    await expect(getTransactionById('42')).resolves.toEqual({ id: 42 });

    expect(listSpy).toHaveBeenNthCalledWith(1);
    expect(listSpy).toHaveBeenNthCalledWith(2, { direction: 'EXPENSE', year: 2026 });
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

  it('maps ledger rows with neutralCategory and normalizes Set accounts to arrays', async () => {
    vi.spyOn(counterpartyApi, 'getCounterparties').mockResolvedValue([
      { id: 2, name: 'ACWD', accounts: new Set(['4109']) },
    ]);
    vi.spyOn(propertyApi, 'getProperties').mockResolvedValue([
      { id: 1, name: 'Oak St', accounts: new Set(['acct-1']) },
    ]);
    const createCpSpy = vi
      .spyOn(counterpartyApi, 'createCounterparty')
      .mockResolvedValue({ id: 3, name: 'PG&E', accounts: new Set(['999']) });
    const createPropSpy = vi
      .spyOn(propertyApi, 'createProperty')
      .mockResolvedValue({ id: 4, name: 'Elm St', accounts: new Set(['888']) });

    const [counterparty] = await getCounterparties();
    const [property] = await getProperties();
    const createdCp = await createCounterparty({
      name: 'PG&E',
      type: 'COMPANY',
      accounts: ['999'],
    });
    const createdProp = await createProperty({
      name: 'Elm St',
      address: '1 Elm St',
      type: 'SINGLE_FAMILY',
      accounts: ['888'],
    });

    expect(counterparty.accounts).toEqual(['4109']);
    expect(property.accounts).toEqual(['acct-1']);
    expect(createdCp.accounts).toEqual(['999']);
    expect(createdProp.accounts).toEqual(['888']);
    expect(createCpSpy).toHaveBeenCalledWith({
      upsertCounterpartyRequest: expect.objectContaining({ accounts: new Set(['999']) }),
    });
    expect(createPropSpy).toHaveBeenCalledWith({
      createPropertyRequest: expect.objectContaining({ accounts: new Set(['888']) }),
    });

    const row = toLedgerRow(
      {
        id: 1,
        propertyId: 1,
        counterpartyId: 2,
        category: { id: 15, key: 'UTILITIES', label: 'Utilities' },
      },
      { properties: [property], counterparties: [counterparty] }
    );
    expect(row.neutralCategory).toEqual({ id: 15, key: 'UTILITIES', label: 'Utilities' });
    expect(row).not.toHaveProperty('financialCategory');
  });
});
