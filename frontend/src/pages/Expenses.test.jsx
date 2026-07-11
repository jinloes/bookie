import React from 'react';
import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter } from 'react-router-dom';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

const mockOpenConfirmModal = vi.fn();
vi.mock('@mantine/modals', () => ({
  modals: {
    openConfirmModal: (...args) => mockOpenConfirmModal(...args),
  },
}));

const mockGetExpenses = vi.fn();
const mockCreateExpense = vi.fn();
const mockUpdateExpense = vi.fn();
const mockDeleteExpense = vi.fn();
const mockGetExpenseCategories = vi.fn();
const mockGetProperties = vi.fn();
const mockGetPayers = vi.fn();
const mockCreatePayer = vi.fn();
const mockUploadReceipt = vi.fn();

vi.mock('../api/index.js', () => ({
  getExpenses: (...args) => mockGetExpenses(...args),
  createExpense: (...args) => mockCreateExpense(...args),
  updateExpense: (...args) => mockUpdateExpense(...args),
  deleteExpense: (...args) => mockDeleteExpense(...args),
  getExpenseCategories: (...args) => mockGetExpenseCategories(...args),
  getProperties: (...args) => mockGetProperties(...args),
  getPayers: (...args) => mockGetPayers(...args),
  createPayer: (...args) => mockCreatePayer(...args),
  uploadReceipt: (...args) => mockUploadReceipt(...args),
}));

const mockGetPersisted = vi.fn();
const mockSetPersisted = vi.fn();
vi.mock('../utils/persistentStore.js', () => ({
  getPersisted: (...args) => mockGetPersisted(...args),
  setPersisted: (...args) => mockSetPersisted(...args),
}));

import { notifications } from '@mantine/notifications';
import { queryKeys } from '../queryKeys.js';
import Expenses from './Expenses.jsx';

const expenseCategories = [{ value: 'REPAIRS', label: 'Repairs', scheduleELine: 14 }];
const properties = [{ id: 1, name: 'Oak Street', address: '123 Oak Street' }];
const payers = [{ id: 2, name: "Joe's Plumbing", type: 'COMPANY' }];
const expenses = [
  {
    id: 11,
    amount: '125.00',
    description: 'Plumbing repair',
    date: '2026-03-10',
    category: 'REPAIRS',
    property: { id: 1, name: 'Oak Street' },
    payer: { id: 2, name: "Joe's Plumbing" },
    sourceType: 'MANUAL',
    sourceId: null,
  },
];

beforeAll(() => {
  window.matchMedia =
    window.matchMedia ||
    (() => ({
      matches: false,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
    }));
  window.Element.prototype.scrollTo = window.Element.prototype.scrollTo || (() => {});
  window.Element.prototype.scrollIntoView = window.Element.prototype.scrollIntoView || (() => {});
  global.ResizeObserver =
    global.ResizeObserver ||
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
});

beforeEach(() => {
  sessionStorage.clear();
  vi.clearAllMocks();
  mockGetPersisted.mockResolvedValue(undefined);
  mockGetExpenses.mockResolvedValue(expenses);
  mockGetExpenseCategories.mockResolvedValue(expenseCategories);
  mockGetProperties.mockResolvedValue(properties);
  mockGetPayers.mockResolvedValue(payers);
  mockCreateExpense.mockResolvedValue({ id: 12 });
  mockUpdateExpense.mockResolvedValue({ id: 11 });
  mockDeleteExpense.mockResolvedValue(null);
  mockCreatePayer.mockResolvedValue({ id: 9, name: 'New Payer', type: 'COMPANY' });
  mockUploadReceipt.mockResolvedValue({ receipt: { id: 'receipt-1', name: 'receipt.pdf' } });
});

function renderExpenses() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <Expenses />
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>
  );

  return { queryClient, user: userEvent.setup() };
}

function getField(scope, path) {
  const field = scope.querySelector(`[data-path="${path}"]`);
  if (!field) {
    throw new Error(`Could not find field for path ${path}`);
  }
  return field;
}

async function openDrawer(user, buttonName, dialogName) {
  await user.click(screen.getByRole('button', { name: buttonName }));
  return screen.findByRole('dialog', { name: dialogName });
}

async function selectOption(user, scope, path) {
  await user.click(getField(scope, path));
  await user.keyboard('{ArrowDown}{Enter}');
}

async function fillExpenseForm(user, scope, values) {
  await user.clear(getField(scope, 'amount'));
  await user.type(getField(scope, 'amount'), values.amount);
  await user.clear(getField(scope, 'description'));
  await user.type(getField(scope, 'description'), values.description);
  await user.clear(getField(scope, 'date'));
  await user.type(getField(scope, 'date'), values.date);
  await selectOption(user, scope, 'payerId');
  await selectOption(user, scope, 'propertyId');
  await selectOption(user, scope, 'category');
}

describe('Expenses', () => {
  it('renders the current expense list', async () => {
    renderExpenses();

    expect(await screen.findByText('Plumbing repair')).toBeTruthy();
    expect(screen.getAllByText('Oak Street').length).toBeGreaterThan(0);
    expect(screen.getAllByText("Joe's Plumbing").length).toBeGreaterThan(0);
    expect(mockGetExpenses).toHaveBeenCalledTimes(1);
  });

  it('creates an expense with flat propertyId and payerId fields', async () => {
    const { queryClient, user } = renderExpenses();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await screen.findByText('Plumbing repair');
    const dialog = await openDrawer(user, /\+ add expense/i, /new expense/i);

    await fillExpenseForm(user, dialog, {
      amount: '250',
      description: 'Water heater repair',
      date: '2026-04-15',
      payerOption: "Joe's Plumbing (Company)",
      propertyOption: 'Oak Street',
      categoryOption: 'Line 14 — Repairs',
    });

    await user.click(within(dialog).getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockCreateExpense).toHaveBeenCalledTimes(1));
    expect(mockCreateExpense).toHaveBeenCalledWith(
      expect.objectContaining({
        amount: '250',
        description: 'Water heater repair',
        date: '2026-04-15',
        category: 'REPAIRS',
        propertyId: 1,
        payerId: 2,
      })
    );
    const sentExpense = mockCreateExpense.mock.calls[0][0];
    expect(sentExpense.property).toBeUndefined();
    expect(sentExpense.payer).toBeUndefined();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.expenses });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.totalExpenses });
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Expense saved', color: 'green' })
    );
  });

  it('updates an existing expense using the nested property and payer ids from the row', async () => {
    const { user } = renderExpenses();

    const editButton = await screen.findByRole('button', {
      name: /edit expense plumbing repair/i,
    });
    await user.click(editButton);

    const dialog = await screen.findByRole('dialog', { name: /edit expense/i });
    await user.clear(getField(dialog, 'description'));
    await user.type(getField(dialog, 'description'), 'Updated plumbing repair');
    await user.click(within(dialog).getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockUpdateExpense).toHaveBeenCalledTimes(1));
    expect(mockUpdateExpense).toHaveBeenCalledWith(
      11,
      expect.objectContaining({
        description: 'Updated plumbing repair',
        propertyId: 1,
        payerId: 2,
        category: 'REPAIRS',
      })
    );
    const sentExpense = mockUpdateExpense.mock.calls[0][1];
    expect(sentExpense.property).toBeUndefined();
    expect(sentExpense.payer).toBeUndefined();
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Expense updated', color: 'green' })
    );
  });

  it('shows the API error when creating an expense fails', async () => {
    mockCreateExpense.mockRejectedValueOnce(new Error('Expense save failed'));

    const { user } = renderExpenses();

    await screen.findByText('Plumbing repair');
    const dialog = await openDrawer(user, /\+ add expense/i, /new expense/i);

    await fillExpenseForm(user, dialog, {
      amount: '175',
      description: 'Leaky faucet',
      date: '2026-04-20',
      payerOption: "Joe's Plumbing (Company)",
      propertyOption: 'Oak Street',
      categoryOption: 'Line 14 — Repairs',
    });

    await user.click(within(dialog).getByRole('button', { name: /^save$/i }));

    expect(await within(dialog).findByText('Expense save failed')).toBeTruthy();
    expect(notifications.show).not.toHaveBeenCalled();
  });

  it('deletes an expense after confirmation and invalidates the expense queries', async () => {
    const { queryClient, user } = renderExpenses();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const deleteButton = await screen.findByRole('button', {
      name: /delete expense plumbing repair/i,
    });
    await user.click(deleteButton);

    expect(mockOpenConfirmModal).toHaveBeenCalledTimes(1);
    const modalConfig = mockOpenConfirmModal.mock.calls[0][0];

    await modalConfig.onConfirm();

    await waitFor(() => expect(mockDeleteExpense).toHaveBeenCalledWith(11));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.expenses });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.totalExpenses });
  });
});
