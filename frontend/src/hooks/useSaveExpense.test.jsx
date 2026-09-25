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
import { useSaveExpense } from './useSaveExpense.js';

function renderWithClient(queryClient) {
  return renderHook(() => useSaveExpense(), {
    wrapper: createQueryWrapper(queryClient),
  });
}

function makeForm() {
  return { setErrors: vi.fn() };
}

const validValues = {
  amount: '100.00',
  description: 'Roof repair',
  date: '2024-01-15',
  propertyId: '1',
  payerId: '2',
  activityId: '10',
  categoryId: '20',
  sourceType: null,
  sourceId: null,
};

const editedRow = {
  id: 5,
  version: 3,
  importReferences: [{ origin: 'OUTLOOK_EMAIL', externalId: 'message-1' }],
  attachments: [{ externalId: 'file-1', fileName: 'invoice.pdf', sha256: 'a'.repeat(64) }],
};

describe('useSaveExpense', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  async function save(overrides = {}) {
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();
    let success;
    await act(async () => {
      success = await result.current.saveExpense({
        values: validValues,
        editing: null,
        uploadedReceipt: null,
        form,
        setSaveError,
        ...overrides,
      });
    });
    return { success, form, setSaveError };
  }

  it('creates an expense transaction with the neutral category, activity and counterparty', async () => {
    mockCreateTransaction.mockResolvedValue({ id: 1 });

    await save();

    expect(mockCreateTransaction).toHaveBeenCalledWith({
      amount: 100,
      direction: 'EXPENSE',
      description: 'Roof repair',
      date: '2024-01-15',
      activityId: 10,
      neutralCategoryId: 20,
      counterpartyId: 2,
      origin: undefined,
      externalId: undefined,
      attachmentExternalId: undefined,
      attachmentFileName: undefined,
      attachmentSha256: undefined,
    });
    expect(mockCreateTransaction.mock.calls[0][0].sourceLabel).toBeUndefined();
  });

  it('updates the edited row with its version and keeps its import identity and attachment', async () => {
    mockUpdateTransaction.mockResolvedValue({ id: 5 });

    await save({ editing: editedRow });

    expect(mockUpdateTransaction).toHaveBeenCalledWith(
      5,
      expect.objectContaining({
        version: 3,
        direction: 'EXPENSE',
        origin: 'OUTLOOK_EMAIL',
        externalId: 'message-1',
        attachmentExternalId: 'file-1',
        attachmentFileName: 'invoice.pdf',
        attachmentSha256: 'a'.repeat(64),
      })
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
      expect.objectContaining({ title: 'Expense saved', color: 'green' })
    );
  });

  it('sets field errors and does not call the API when validation fails', async () => {
    const { success, form, setSaveError } = await save({
      values: { ...validValues, categoryId: null },
    });

    expect(success).toBe(false);
    expect(mockCreateTransaction).not.toHaveBeenCalled();
    expect(form.setErrors).toHaveBeenCalled();
    expect(setSaveError).toHaveBeenCalledWith('Please fix validation errors before submitting.');
  });

  it('surfaces an API error via setSaveError and returns false when the save request fails', async () => {
    mockCreateTransaction.mockRejectedValue(new Error('boom'));

    const { success, setSaveError } = await save();

    expect(success).toBe(false);
    expect(setSaveError).toHaveBeenCalledWith('boom');
  });

  it('records an uploaded receipt as a RECEIPT-origin attachment', async () => {
    mockCreateTransaction.mockResolvedValue({ id: 1 });

    await save({ uploadedReceipt: { itemId: 'onedrive-1', fileName: 'receipt.pdf' } });

    expect(mockCreateTransaction).toHaveBeenCalledWith(
      expect.objectContaining({
        origin: 'RECEIPT',
        attachmentExternalId: 'onedrive-1',
        attachmentFileName: 'receipt.pdf',
      })
    );
  });

  it('omits the counterparty when none is selected', async () => {
    mockCreateTransaction.mockResolvedValue({ id: 1 });

    await save({ values: { ...validValues, payerId: null } });

    expect(mockCreateTransaction.mock.calls[0][0].counterpartyId).toBeUndefined();
  });
});
