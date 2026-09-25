import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js');
const mockSubmitTransactionToAgent = vi.mocked(api.submitTransactionToAgent);
const mockCreateTransaction = vi.mocked(api.createTransaction);
const mockGetFinancialActivities = vi.mocked(api.getFinancialActivities);
const mockGetFinancialCategories = vi.mocked(api.getFinancialCategories);
const mockGetCounterparties = vi.mocked(api.getCounterparties);

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
  neutralCategoryId: 120,
};
const incomeCategory = {
  id: 21,
  key: 'TUTORING_INCOME',
  label: 'Tutoring income',
  direction: 'INCOME',
  neutralCategoryId: 121,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockGetFinancialActivities.mockResolvedValue([activity]);
  mockGetCounterparties.mockResolvedValue([payer]);
  mockGetFinancialCategories.mockImplementation((direction) =>
    Promise.resolve(direction === 'INCOME' ? [incomeCategory] : [expenseCategory])
  );
  mockCreateTransaction.mockResolvedValue({ id: 41 });
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
        neutralCategoryId: 120,
        counterpartyId: 30,
        counterpartyName: 'Demo Counterparty',
        classificationAmbiguous: false,
      },
    });

    expect(mockCreateTransaction).not.toHaveBeenCalled();

    const description = screen.getByRole('textbox', { name: /description/i });
    await user.clear(description);
    await user.paste('Edited synthetic educator purchase');
    await user.click(screen.getByRole('button', { name: /save expense/i }));

    await waitFor(() => expect(mockCreateTransaction).toHaveBeenCalledTimes(1));
    expect(mockCreateTransaction).toHaveBeenCalledWith(
      expect.objectContaining({
        direction: 'EXPENSE',
        amount: 64.25,
        description: 'Edited synthetic educator purchase',
        date: '2026-02-10',
        activityId: 10,
        neutralCategoryId: 120,
        counterpartyId: 30,
      })
    );
  });

  it('keeps an income proposal unsaved until Save and saves it as an income transaction', async () => {
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
        neutralCategoryId: 121,
        counterpartyId: 30,
        counterpartyName: 'Demo Counterparty',
        classificationAmbiguous: false,
      },
    });

    expect(mockCreateTransaction).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: /save income/i }));

    await waitFor(() => expect(mockCreateTransaction).toHaveBeenCalledTimes(1));
    expect(mockCreateTransaction).toHaveBeenCalledWith(
      expect.objectContaining({
        direction: 'INCOME',
        amount: 425,
        description: 'Synthetic tutoring income',
        activityId: 10,
        neutralCategoryId: 121,
        counterpartyId: 30,
        sourceLabel: 'Demo Counterparty',
      })
    );
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
        neutralCategoryId: 120,
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

    await waitFor(() => expect(mockCreateTransaction).toHaveBeenCalledTimes(1));
    expect(mockCreateTransaction).toHaveBeenCalledWith(
      expect.objectContaining({ direction: 'INCOME', neutralCategoryId: 121 })
    );
  });
});
