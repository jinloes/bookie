import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js');
const mockSubmitTransactionToAgent = vi.mocked(api.submitTransactionToAgent);
const mockCreateExpense = vi.mocked(api.createExpense);
const mockCreateIncome = vi.mocked(api.createIncome);
const mockGetFinancialActivities = vi.mocked(api.getFinancialActivities);
const mockGetFinancialCategories = vi.mocked(api.getFinancialCategories);
const mockGetPayers = vi.mocked(api.getPayers);

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
  return renderWithProviders(<Agent />).user;
}

async function prepareProposal(user, response) {
  mockSubmitTransactionToAgent.mockResolvedValue(response);
  await user.click(screen.getByRole('textbox', { name: /describe a transaction/i }));
  await user.paste('Deterministic synthetic transaction');
  await user.click(screen.getByRole('button', { name: /prepare proposal/i }));
  expect(await screen.findByRole('heading', { name: /review proposal/i })).toBeInTheDocument();
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

    const description = screen.getByRole('textbox', { name: /description/i });
    await user.clear(description);
    await user.paste('Edited synthetic educator purchase');
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
