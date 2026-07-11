import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

const mockCreateIncome = vi.fn();
const mockUpdateIncome = vi.fn();
vi.mock('../api/index.js', () => ({
  createIncome: (...args) => mockCreateIncome(...args),
  updateIncome: (...args) => mockUpdateIncome(...args),
}));

import { notifications } from '@mantine/notifications';
import { queryKeys } from '../queryKeys.js';
import { useSaveIncome } from './useSaveIncome.js';

function renderWithClient(queryClient) {
  return renderHook(() => useSaveIncome(), {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

function makeForm() {
  return { setErrors: vi.fn() };
}

const validValues = {
  amount: '1400.00',
  description: 'July rent',
  date: '2026-07-01',
  source: 'Venmo',
  propertyId: '1',
  payerId: '2',
};

describe('useSaveIncome', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('sends flat propertyId/payerId fields when creating', async () => {
    mockCreateIncome.mockResolvedValue({ id: 1 });
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    await act(async () => {
      await result.current.saveIncome({
        values: validValues,
        editing: null,
        form,
        setSaveError,
      });
    });

    expect(mockCreateIncome).toHaveBeenCalledWith(
      expect.objectContaining({ propertyId: 1, payerId: 2, sourceType: 'Venmo' })
    );
    const sentData = mockCreateIncome.mock.calls[0][0];
    expect(sentData.property).toBeUndefined();
    expect(sentData.payer).toBeUndefined();
  });

  it('calls updateIncome with the editing id when editing is set', async () => {
    mockUpdateIncome.mockResolvedValue({ id: 5 });
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    await act(async () => {
      await result.current.saveIncome({
        values: validValues,
        editing: 5,
        form,
        setSaveError,
      });
    });

    expect(mockUpdateIncome).toHaveBeenCalledWith(5, expect.objectContaining({ propertyId: 1 }));
    expect(mockCreateIncome).not.toHaveBeenCalled();
  });

  it('invalidates income totals and shows a success notification on save', async () => {
    mockCreateIncome.mockResolvedValue({ id: 1 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    const success = await act(async () =>
      result.current.saveIncome({
        values: validValues,
        editing: null,
        form,
        setSaveError,
      })
    );

    expect(success).toBe(true);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.incomes });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.totalIncome });
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Income saved', color: 'green' })
    );
  });

  it('sets field errors and skips the API when validation fails', async () => {
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    const success = await act(async () =>
      result.current.saveIncome({
        values: { ...validValues, amount: '' },
        editing: null,
        form,
        setSaveError,
      })
    );

    expect(success).toBe(false);
    expect(mockCreateIncome).not.toHaveBeenCalled();
    expect(form.setErrors).toHaveBeenCalled();
    expect(setSaveError).toHaveBeenCalledWith('Please fix validation errors before submitting.');
  });

  it('surfaces API errors via setSaveError', async () => {
    mockCreateIncome.mockRejectedValue(new Error('boom'));
    const { result } = renderWithClient(queryClient);
    const form = makeForm();
    const setSaveError = vi.fn();

    const success = await act(async () =>
      result.current.saveIncome({
        values: validValues,
        editing: null,
        form,
        setSaveError,
      })
    );

    expect(success).toBe(false);
    expect(setSaveError).toHaveBeenCalledWith('boom');
  });
});
