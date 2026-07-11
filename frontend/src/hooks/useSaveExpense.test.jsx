import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

const mockCreateExpense = vi.fn();
const mockUpdateExpense = vi.fn();
vi.mock('../api/index.js', () => ({
  createExpense: (...args) => mockCreateExpense(...args),
  updateExpense: (...args) => mockUpdateExpense(...args),
}));

import { notifications } from '@mantine/notifications';
import { queryKeys } from '../queryKeys.js';
import { useSaveExpense } from './useSaveExpense.js';

function renderWithClient(queryClient) {
  return renderHook(() => useSaveExpense(), {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

function makeForm() {
  return { setErrors: vi.fn() };
}

const validValues = {
  amount: '100.00',
  description: 'Roof repair',
  date: '2024-01-15',
  category: 'REPAIRS',
  propertyId: '1',
  payerId: '2',
  sourceType: null,
  sourceId: null,
};

describe('useSaveExpense', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('sends flat propertyId/payerId fields (not nested property/payer objects) when creating', async () => {
    mockCreateExpense.mockResolvedValue({ id: 1 });
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    await act(async () => {
      await result.current.saveExpense({
        values: validValues,
        editing: null,
        uploadedReceipt: null,
        form,
        setSaveError,
      });
    });

    expect(mockCreateExpense).toHaveBeenCalledWith(
      expect.objectContaining({ propertyId: 1, payerId: 2 })
    );
    const sentData = mockCreateExpense.mock.calls[0][0];
    expect(sentData.property).toBeUndefined();
    expect(sentData.payer).toBeUndefined();
  });

  it('calls updateExpense with the editing id when editing is set', async () => {
    mockUpdateExpense.mockResolvedValue({ id: 5 });
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    await act(async () => {
      await result.current.saveExpense({
        values: validValues,
        editing: 5,
        uploadedReceipt: null,
        form,
        setSaveError,
      });
    });

    expect(mockUpdateExpense).toHaveBeenCalledWith(5, expect.objectContaining({ propertyId: 1 }));
    expect(mockCreateExpense).not.toHaveBeenCalled();
  });

  it('invalidates the expenses and totalExpenses queries and shows a success notification on save', async () => {
    mockCreateExpense.mockResolvedValue({ id: 1 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    const success = await act(async () =>
      result.current.saveExpense({
        values: validValues,
        editing: null,
        uploadedReceipt: null,
        form,
        setSaveError,
      })
    );

    expect(success).toBe(true);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.expenses });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.totalExpenses });
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Expense saved', color: 'green' })
    );
  });

  it('sets field errors and does not call the API when validation fails', async () => {
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    const success = await act(async () =>
      result.current.saveExpense({
        values: { ...validValues, propertyId: null, payerId: null },
        editing: null,
        uploadedReceipt: null,
        form,
        setSaveError,
      })
    );

    expect(success).toBe(false);
    expect(mockCreateExpense).not.toHaveBeenCalled();
    expect(form.setErrors).toHaveBeenCalled();
    expect(setSaveError).toHaveBeenCalledWith('Please fix validation errors before submitting.');
  });

  it('surfaces an API error via setSaveError and returns false when the save request fails', async () => {
    mockCreateExpense.mockRejectedValue(new Error('boom'));
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    const success = await act(async () =>
      result.current.saveExpense({
        values: validValues,
        editing: null,
        uploadedReceipt: null,
        form,
        setSaveError,
      })
    );

    expect(success).toBe(false);
    expect(setSaveError).toHaveBeenCalledWith('boom');
  });

  it('overrides sourceType and includes receipt fields when uploadedReceipt is set', async () => {
    mockCreateExpense.mockResolvedValue({ id: 1 });
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    await act(async () => {
      await result.current.saveExpense({
        values: validValues,
        editing: null,
        uploadedReceipt: { itemId: 'onedrive-1', fileName: 'receipt.pdf' },
        form,
        setSaveError,
      });
    });

    expect(mockCreateExpense).toHaveBeenCalledWith(
      expect.objectContaining({
        receiptOneDriveId: 'onedrive-1',
        receiptFileName: 'receipt.pdf',
        sourceType: 'RECEIPT',
      })
    );
  });
});
