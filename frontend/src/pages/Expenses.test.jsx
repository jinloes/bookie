import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as api from '../api/index.js';
import * as persistentStore from '../utils/persistentStore.js';
import { screen, waitFor, within } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

vi.mock('@mantine/modals', () => ({ modals: { openConfirmModal: vi.fn() } }));
import { modals } from '@mantine/modals';
const mockOpenConfirmModal = vi.mocked(modals.openConfirmModal);

vi.mock('../api/index.js');
const mockGetExpenses = vi.mocked(api.getExpenses);
const mockCreateExpense = vi.mocked(api.createExpense);
const mockUpdateExpense = vi.mocked(api.updateExpense);
const mockDeleteExpense = vi.mocked(api.deleteExpense);
const mockGetFinancialActivities = vi.mocked(api.getFinancialActivities);
const mockGetFinancialCategories = vi.mocked(api.getFinancialCategories);
const mockGetProperties = vi.mocked(api.getProperties);
const mockGetPayers = vi.mocked(api.getPayers);
const mockCreatePayer = vi.mocked(api.createPayer);
const mockUploadReceipt = vi.mocked(api.uploadReceipt);

vi.mock('../utils/persistentStore.js');
const mockGetPersisted = vi.mocked(persistentStore.getPersisted);

import { notifications } from '@mantine/notifications';
import { queryKeys } from '../queryKeys.js';
import Expenses from './Expenses.jsx';

const properties = [{ id: 1, name: 'Oak Street', address: '123 Oak Street' }];
const payers = [{ id: 2, name: "Joe's Plumbing", type: 'COMPANY' }];
const activities = [
  {
    id: 10,
    name: 'Oak Street rental',
    active: true,
    owner: { id: 1, name: 'Household' },
    property: { id: 1, name: 'Oak Street' },
  },
];
const financialCategories = [{ id: 20, key: 'REPAIRS', label: 'Repairs', direction: 'EXPENSE' }];
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
    activity: activities[0],
    financialCategory: financialCategories[0],
  },
];

beforeEach(() => {
  sessionStorage.clear();
  vi.clearAllMocks();
  mockGetPersisted.mockResolvedValue(undefined);
  mockGetExpenses.mockResolvedValue(expenses);
  mockGetFinancialActivities.mockResolvedValue(activities);
  mockGetFinancialCategories.mockResolvedValue(financialCategories);
  mockGetProperties.mockResolvedValue(properties);
  mockGetPayers.mockResolvedValue(payers);
  mockCreateExpense.mockResolvedValue({ id: 12 });
  mockUpdateExpense.mockResolvedValue({ id: 11 });
  mockDeleteExpense.mockResolvedValue(null);
  mockCreatePayer.mockResolvedValue({ id: 9, name: 'New Payer', type: 'COMPANY' });
  mockUploadReceipt.mockResolvedValue({ receipt: { id: 'receipt-1', name: 'receipt.pdf' } });
});

function renderExpenses() {
  return renderWithProviders(<Expenses />);
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
  await user.click(getField(scope, 'amount'));
  await user.paste(values.amount);
  await user.click(getField(scope, 'description'));
  await user.paste(values.description);
  await user.clear(getField(scope, 'date'));
  await user.paste(values.date);
  await selectOption(user, scope, 'payerId');
  const activityField = scope.querySelector('input[placeholder="Select activity"]');
  if (!activityField) throw new Error('Could not find activity selector');
  await user.click(activityField);
  await user.keyboard('{ArrowDown}{Enter}');
  await waitFor(() => expect(mockGetFinancialCategories).toHaveBeenCalledWith('EXPENSE', '10'));
  await selectOption(user, scope, 'categoryId');
}

describe('Expenses', () => {
  it('renders the current expense list', async () => {
    renderExpenses();

    expect(await screen.findByText('Plumbing repair')).toBeInTheDocument();
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
        amount: 250,
        description: 'Water heater repair',
        date: '2026-04-15',
        propertyId: 1,
        payerId: 2,
        activityId: 10,
        categoryId: 20,
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
    const description = getField(dialog, 'description');
    await user.clear(description);
    await user.paste('Updated plumbing repair');
    await user.click(within(dialog).getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockUpdateExpense).toHaveBeenCalledTimes(1));
    expect(mockUpdateExpense).toHaveBeenCalledWith(
      11,
      expect.objectContaining({
        description: 'Updated plumbing repair',
        propertyId: 1,
        payerId: 2,
        activityId: 10,
        categoryId: 20,
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

    expect(await within(dialog).findByText('Expense save failed')).toBeInTheDocument();
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
