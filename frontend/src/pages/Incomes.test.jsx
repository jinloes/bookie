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

const mockUsePendingIncomesQuery = vi.fn();
vi.mock('../hooks/usePendingQueue.js', () => ({
  usePendingIncomesQuery: () => mockUsePendingIncomesQuery(),
}));

const mockGetIncomes = vi.fn();
const mockCreateIncome = vi.fn();
const mockUpdateIncome = vi.fn();
const mockDeleteIncome = vi.fn();
const mockGetProperties = vi.fn();
const mockGetPayers = vi.fn();
const mockGetFinancialActivities = vi.fn();
const mockGetFinancialCategories = vi.fn();
const mockImportVenmoIncomes = vi.fn();
const mockAcceptPendingIncome = vi.fn();
const mockRejectPendingIncome = vi.fn();

vi.mock('../api/index.js', () => ({
  getIncomes: (...args) => mockGetIncomes(...args),
  createIncome: (...args) => mockCreateIncome(...args),
  updateIncome: (...args) => mockUpdateIncome(...args),
  deleteIncome: (...args) => mockDeleteIncome(...args),
  getProperties: (...args) => mockGetProperties(...args),
  getPayers: (...args) => mockGetPayers(...args),
  getFinancialActivities: (...args) => mockGetFinancialActivities(...args),
  getFinancialCategories: (...args) => mockGetFinancialCategories(...args),
  importVenmoIncomes: (...args) => mockImportVenmoIncomes(...args),
  acceptPendingIncome: (...args) => mockAcceptPendingIncome(...args),
  rejectPendingIncome: (...args) => mockRejectPendingIncome(...args),
}));

const mockGetPersisted = vi.fn();
const mockSetPersisted = vi.fn();
vi.mock('../utils/persistentStore.js', () => ({
  getPersisted: (...args) => mockGetPersisted(...args),
  setPersisted: (...args) => mockSetPersisted(...args),
}));

import { notifications } from '@mantine/notifications';
import { queryKeys } from '../queryKeys.js';
import Incomes from './Incomes.jsx';

const properties = [{ id: 1, name: 'Oak Street', address: '123 Oak Street' }];
const payers = [{ id: 2, name: 'Jane Tenant' }];
const activities = [
  {
    id: 10,
    name: 'Oak Street rental',
    active: true,
    owner: { id: 1, name: 'Household' },
    property: { id: 1, name: 'Oak Street' },
  },
];
const financialCategories = [
  { id: 20, key: 'RENTAL_INCOME', label: 'Rental Income', direction: 'INCOME' },
];
const incomes = [
  {
    id: 21,
    amount: '1400.00',
    description: 'July rent',
    date: '2026-07-01',
    source: 'Venmo',
    property: { id: 1, name: 'Oak Street' },
    payer: { id: 2, name: 'Jane Tenant' },
    activity: activities[0],
    financialCategory: financialCategories[0],
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
  mockUsePendingIncomesQuery.mockReturnValue({ data: [], isLoading: false });
  mockGetIncomes.mockResolvedValue(incomes);
  mockGetProperties.mockResolvedValue(properties);
  mockGetPayers.mockResolvedValue(payers);
  mockGetFinancialActivities.mockResolvedValue(activities);
  mockGetFinancialCategories.mockResolvedValue(financialCategories);
  mockCreateIncome.mockResolvedValue({ id: 22 });
  mockUpdateIncome.mockResolvedValue({ id: 21 });
  mockDeleteIncome.mockResolvedValue(null);
  mockImportVenmoIncomes.mockResolvedValue({ importedRows: 0 });
  mockAcceptPendingIncome.mockResolvedValue(null);
  mockRejectPendingIncome.mockResolvedValue(null);
});

function renderIncomes() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <Incomes />
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

async function fillIncomeForm(user, scope, values) {
  await user.clear(getField(scope, 'amount'));
  await user.type(getField(scope, 'amount'), values.amount);
  await user.clear(getField(scope, 'description'));
  await user.type(getField(scope, 'description'), values.description);
  await user.clear(getField(scope, 'date'));
  await user.type(getField(scope, 'date'), values.date);
  await user.clear(getField(scope, 'source'));
  await user.type(getField(scope, 'source'), values.source);
  const activityField = scope.querySelector('input[placeholder="Select activity"]');
  if (!activityField) throw new Error('Could not find activity selector');
  await user.click(activityField);
  await user.keyboard('{ArrowDown}{Enter}');
  await waitFor(() => expect(mockGetFinancialCategories).toHaveBeenCalledWith('INCOME', '10'));
  await selectOption(user, scope, 'categoryId');
  await selectOption(user, scope, 'payerId');
}

describe('Incomes', () => {
  it('renders the current income list', async () => {
    renderIncomes();

    expect(await screen.findByText('July rent')).toBeTruthy();
    expect(screen.getByText('Venmo')).toBeTruthy();
    expect(screen.getAllByText('Oak Street').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Jane Tenant').length).toBeGreaterThan(0);
  });

  it('creates an income with flat propertyId and payerId fields', async () => {
    const { queryClient, user } = renderIncomes();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await screen.findByText('July rent');
    const dialog = await openDrawer(user, /\+ add income/i, /new income/i);

    await fillIncomeForm(user, dialog, {
      amount: '1450',
      description: 'August rent',
      date: '2026-08-01',
      source: 'Venmo',
      propertyOption: 'Oak Street',
      payerOption: 'Jane Tenant',
    });

    await user.click(within(dialog).getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockCreateIncome).toHaveBeenCalledTimes(1));
    expect(mockCreateIncome).toHaveBeenCalledWith(
      expect.objectContaining({
        amount: 1450,
        description: 'August rent',
        date: '2026-08-01',
        source: 'Venmo',
        propertyId: 1,
        payerId: 2,
        activityId: 10,
        categoryId: 20,
      })
    );
    const sentIncome = mockCreateIncome.mock.calls[0][0];
    expect(sentIncome.property).toBeUndefined();
    expect(sentIncome.payer).toBeUndefined();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.incomes });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.totalIncome });
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Income saved', color: 'green' })
    );
  });

  it('updates an existing income using the nested property and payer ids from the row', async () => {
    const { user } = renderIncomes();

    const editButton = await screen.findByRole('button', {
      name: /edit income july rent/i,
    });
    await user.click(editButton);

    const dialog = await screen.findByRole('dialog', { name: /edit income/i });
    await user.clear(getField(dialog, 'description'));
    await user.type(getField(dialog, 'description'), 'July rent corrected');
    await user.click(within(dialog).getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockUpdateIncome).toHaveBeenCalledTimes(1));
    expect(mockUpdateIncome).toHaveBeenCalledWith(
      21,
      expect.objectContaining({
        description: 'July rent corrected',
        source: 'Venmo',
        propertyId: 1,
        payerId: 2,
        activityId: 10,
        categoryId: 20,
      })
    );
    const sentIncome = mockUpdateIncome.mock.calls[0][1];
    expect(sentIncome.property).toBeUndefined();
    expect(sentIncome.payer).toBeUndefined();
    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Income updated', color: 'green' })
    );
  });

  it('shows the API error when creating an income fails', async () => {
    mockCreateIncome.mockRejectedValueOnce(new Error('Income save failed'));

    const { user } = renderIncomes();

    await screen.findByText('July rent');
    const dialog = await openDrawer(user, /\+ add income/i, /new income/i);

    await fillIncomeForm(user, dialog, {
      amount: '1500',
      description: 'September rent',
      date: '2026-09-01',
      source: 'Venmo',
      propertyOption: 'Oak Street',
      payerOption: 'Jane Tenant',
    });

    await user.click(within(dialog).getByRole('button', { name: /^save$/i }));

    expect(await within(dialog).findByText('Income save failed')).toBeTruthy();
    expect(notifications.show).not.toHaveBeenCalled();
  });

  it('deletes an income after confirmation and invalidates the income queries', async () => {
    const { queryClient, user } = renderIncomes();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const deleteButton = await screen.findByRole('button', {
      name: /delete income july rent/i,
    });
    await user.click(deleteButton);

    expect(mockOpenConfirmModal).toHaveBeenCalledTimes(1);
    const modalConfig = mockOpenConfirmModal.mock.calls[0][0];

    await modalConfig.onConfirm();

    await waitFor(() => expect(mockDeleteIncome).toHaveBeenCalledWith(21));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.incomes });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.totalIncome });
  });

  it('forwards the selected activity context with a Venmo import', async () => {
    const { user } = renderIncomes();
    await screen.findByText('July rent');
    const dialog = await openDrawer(user, /import venmo csv/i, /import venmo csv/i);

    await user.click(within(dialog).getByLabelText(/activity context/i));
    await user.keyboard('{ArrowDown}{Enter}');
    const file = new File(['synthetic,venmo,data'], 'synthetic-venmo.csv', {
      type: 'text/csv',
    });
    await user.upload(dialog.querySelector('input[type="file"]'), file);
    await user.click(within(dialog).getByRole('button', { name: /^import$/i }));

    await waitFor(() => expect(mockImportVenmoIncomes).toHaveBeenCalledTimes(1));
    expect(mockImportVenmoIncomes).toHaveBeenCalledWith(file, null, null, '10');
  });
});
