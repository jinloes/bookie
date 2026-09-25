import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as api from '../api/index.js';
import { renderHook, act } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

vi.mock('../api/index.js', async (importOriginal) => ({
  ...(await importOriginal()),
  createTransaction: vi.fn(),
  updateTransaction: vi.fn(),
}));
const mockCreateTransaction = vi.mocked(api.createTransaction);
const mockUpdateTransaction = vi.mocked(api.updateTransaction);

import { notifications } from '@mantine/notifications';
import { queryKeys } from '../queryKeys.js';
import { useSaveIncome } from './useSaveIncome.js';

const validValues = {
  amount: '1500.00',
  description: 'January rent',
  date: '2024-01-01',
  source: 'Tenant A',
  propertyId: '1',
  payerId: '2',
  activityId: '10',
  categoryId: '30',
};

describe('useSaveIncome', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  async function save(overrides = {}) {
    const { result } = renderHook(() => useSaveIncome(), {
      wrapper: createQueryWrapper(queryClient),
    });
    const form = { setErrors: vi.fn() };
    const setSaveError = vi.fn();
    let success;
    await act(async () => {
      success = await result.current.saveIncome({
        values: validValues,
        editing: null,
        form,
        setSaveError,
        ...overrides,
      });
    });
    return { success, form, setSaveError };
  }

  it('creates an income transaction carrying its source label and counterparty', async () => {
    mockCreateTransaction.mockResolvedValue({ id: 1 });

    await save();

    expect(mockCreateTransaction).toHaveBeenCalledWith(
      expect.objectContaining({
        amount: 1500,
        direction: 'INCOME',
        description: 'January rent',
        date: '2024-01-01',
        activityId: 10,
        neutralCategoryId: 30,
        counterpartyId: 2,
        sourceLabel: 'Tenant A',
      })
    );
  });

  it('omits an empty source label and counterparty', async () => {
    mockCreateTransaction.mockResolvedValue({ id: 1 });

    await save({ values: { ...validValues, source: '', payerId: null, propertyId: null } });

    const sent = mockCreateTransaction.mock.calls[0][0];
    expect(sent.sourceLabel).toBeUndefined();
    expect(sent.counterpartyId).toBeUndefined();
  });

  it('updates the edited row with its version and preserved import identity', async () => {
    mockUpdateTransaction.mockResolvedValue({ id: 5 });

    await save({
      editing: {
        id: 5,
        version: 2,
        importReferences: [{ origin: 'VENMO', externalId: 'venmo-1' }],
        attachments: [],
      },
    });

    expect(mockUpdateTransaction).toHaveBeenCalledWith(
      5,
      expect.objectContaining({ version: 2, origin: 'VENMO', externalId: 'venmo-1' })
    );
    expect(mockCreateTransaction).not.toHaveBeenCalled();
  });

  it('invalidates transactions, the inbox and reports and shows a success notification', async () => {
    mockCreateTransaction.mockResolvedValue({ id: 1 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { success } = await save();

    expect(success).toBe(true);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.transactions });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.inbox });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['reports'] });
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Income saved', color: 'green' })
    );
  });

  it('sets field errors and skips the API when validation fails', async () => {
    const { success, form, setSaveError } = await save({
      values: { ...validValues, activityId: null },
    });

    expect(success).toBe(false);
    expect(mockCreateTransaction).not.toHaveBeenCalled();
    expect(form.setErrors).toHaveBeenCalled();
    expect(setSaveError).toHaveBeenCalledWith('Please fix validation errors before submitting.');
  });

  it('surfaces API errors via setSaveError', async () => {
    mockCreateTransaction.mockRejectedValue(new Error('boom'));

    const { success, setSaveError } = await save();

    expect(success).toBe(false);
    expect(setSaveError).toHaveBeenCalledWith('boom');
  });
});
