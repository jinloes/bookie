import React from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const mockSubmitTransactionToAgent = vi.fn();
const mockCreateExpense = vi.fn();
const mockCreateIncome = vi.fn();
const mockGetFinancialActivities = vi.fn();
const mockGetFinancialCategories = vi.fn();
const mockGetPayers = vi.fn();

vi.mock('../api/index.js', () => ({
  submitTransactionToAgent: (...args) => mockSubmitTransactionToAgent(...args),
  createExpense: (...args) => mockCreateExpense(...args),
  createIncome: (...args) => mockCreateIncome(...args),
  getFinancialActivities: (...args) => mockGetFinancialActivities(...args),
  getFinancialCategories: (...args) => mockGetFinancialCategories(...args),
  getPayers: (...args) => mockGetPayers(...args),
}));

import Agent from './Agent.jsx';

const activity = {
  id: 10,
  name: 'Synthetic household activity',
  active: true,
  owner: { id: 1, name: 'Demo Household' },
  property: null,
};
const payer = { id: 30, name: 'Demo Counterparty' };
const expenseCategory = {
  id: 20,
  key: 'EDUCATOR_PURCHASE',
  label: 'Educator purchase',
  direction: 'EXPENSE',
};
const incomeCategory = {
  id: 21,
  key: 'TUTORING_INCOME',
  label: 'Tutoring income',
  direction: 'INCOME',
};

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
  vi.clearAllMocks();
  mockGetFinancialActivities.mockResolvedValue([activity]);
  mockGetPayers.mockResolvedValue([payer]);
  mockGetFinancialCategories.mockImplementation((direction) =>
    Promise.resolve(direction === 'INCOME' ? [incomeCategory] : [expenseCategory])
  );
  mockCreateExpense.mockResolvedValue({ id: 41 });
  mockCreateIncome.mockResolvedValue({ id: 42 });
});

function renderAgent() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <Agent />
      </QueryClientProvider>
    </MantineProvider>
  );
  return userEvent.setup();
}

async function prepareProposal(user, response) {
  mockSubmitTransactionToAgent.mockResolvedValue(response);
  fireEvent.change(screen.getByRole('textbox', { name: /describe a transaction/i }), {
    target: { value: 'Deterministic synthetic transaction' },
  });
  await user.click(screen.getByRole('button', { name: /prepare proposal/i }));
  expect(await screen.findByRole('heading', { name: /review proposal/i })).toBeTruthy();
}

describe('Agent', () => {
  it('keeps an expense proposal editable and does not persist it until explicit Save', async () => {
    const user = renderAgent();
    await prepareProposal(user, {
      message: 'Review this expense.',
      proposedTransaction: {
        direction: 'EXPENSE',
        amount: 64.25,
        description: 'Synthetic educator purchase',
        date: '2026-02-10',
        activityId: 10,
        categoryId: 20,
        payerId: 30,
        counterpartyName: 'Demo Counterparty',
        classificationAmbiguous: false,
      },
    });

    expect(mockCreateExpense).not.toHaveBeenCalled();
    expect(mockCreateIncome).not.toHaveBeenCalled();

    fireEvent.change(screen.getByRole('textbox', { name: /description/i }), {
      target: { value: 'Edited synthetic educator purchase' },
    });
    await user.click(screen.getByRole('button', { name: /save expense/i }));

    await waitFor(() => expect(mockCreateExpense).toHaveBeenCalledTimes(1));
    expect(mockCreateExpense).toHaveBeenCalledWith(
      expect.objectContaining({
        amount: 64.25,
        description: 'Edited synthetic educator purchase',
        date: '2026-02-10',
        activityId: 10,
        categoryId: 20,
        payerId: 30,
      })
    );
    expect(mockCreateIncome).not.toHaveBeenCalled();
  });

  it('keeps an income proposal unsaved until Save and uses the income endpoint', async () => {
    const user = renderAgent();
    await prepareProposal(user, {
      message: 'Review this income.',
      proposedTransaction: {
        direction: 'INCOME',
        amount: 425,
        description: 'Synthetic tutoring income',
        date: '2026-02-14',
        activityId: 10,
        categoryId: 21,
        payerId: 30,
        counterpartyName: 'Demo Counterparty',
        classificationAmbiguous: false,
      },
    });

    expect(mockCreateIncome).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: /save income/i }));

    await waitFor(() => expect(mockCreateIncome).toHaveBeenCalledTimes(1));
    expect(mockCreateIncome).toHaveBeenCalledWith(
      expect.objectContaining({
        amount: 425,
        description: 'Synthetic tutoring income',
        activityId: 10,
        categoryId: 21,
        payerId: 30,
        source: 'Demo Counterparty',
      })
    );
    expect(mockCreateExpense).not.toHaveBeenCalled();
  });

  it('allows direction correction and warns when the proposed classification is ambiguous', async () => {
    const user = renderAgent();
    await prepareProposal(user, {
      message: 'Classification needs review.',
      proposedTransaction: {
        direction: 'EXPENSE',
        amount: 80,
        description: 'Synthetic reimbursement',
        date: '2026-02-16',
        activityId: 10,
        categoryId: 20,
        classificationAmbiguous: true,
      },
    });

    expect(screen.getAllByText(/classification needs review/i).length).toBeGreaterThan(0);

    await user.click(document.querySelector('input[aria-label="Transaction direction"]'));
    await user.keyboard('{ArrowUp}{Enter}');
    await waitFor(() => expect(mockGetFinancialCategories).toHaveBeenCalledWith('INCOME', '10'));
    await user.click(document.querySelector('input[aria-label="Transaction category"]'));
    await user.keyboard('{ArrowDown}{Enter}');
    await user.click(screen.getByRole('button', { name: /save income/i }));

    await waitFor(() => expect(mockCreateIncome).toHaveBeenCalledTimes(1));
    expect(mockCreateExpense).not.toHaveBeenCalled();
  });
});
