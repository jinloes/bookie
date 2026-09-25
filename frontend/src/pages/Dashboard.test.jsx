import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('@tanstack/react-query', async (importOriginal) => ({
  ...(await importOriginal()),
  useQuery: ({ queryKey }) => {
    const data = {
      inbox: [
        { id: 7, origin: 'RECEIPT', state: 'READY' },
        { id: 8, origin: 'VENMO', state: 'READY' },
        { id: 9, origin: 'OUTLOOK_EMAIL', state: 'QUEUED' },
      ],
      financialActivities: [
        {
          id: 10,
          name: 'Teaching',
          owner: { id: 1, name: 'Alex' },
        },
      ],
      transactions: [
        {
          id: 1,
          direction: 'INCOME',
          amount: 999,
          date: '2026-08-15',
          description: 'Recent paycheck',
          activity: { id: 10, owner: { id: 1, name: 'Alex' } },
          category: { id: 5, key: 'WAGES', label: 'Wages' },
          counterpartyId: 3,
          importReferences: [],
          attachments: [],
        },
        {
          id: 2,
          direction: 'EXPENSE',
          amount: 12,
          date: '2026-08-10',
          description: 'Recent supplies',
          activity: { id: 10, owner: { id: 1, name: 'Alex' } },
          category: { id: 6, key: 'SUPPLIES', label: 'Supplies' },
          importReferences: [],
          attachments: [],
        },
      ],
      properties: [{ id: 4, name: 'Oak' }],
      counterparties: [{ id: 3, name: 'Acme Payroll' }],
      receiptSettings: { folderBase: 'Receipts' },
    }[queryKey[0]];
    return { data, isLoading: false, error: null };
  },
}));

vi.mock('../hooks/useReports.js', () => ({
  useCashflowReport: () => ({
    isLoading: false,
    error: null,
    data: {
      totalIncome: 3000,
      totalExpenses: 2700,
      netCashflow: 300,
      activities: [
        {
          activity: {
            id: 10,
            name: 'Teaching',
            owner: { id: 1, name: 'Alex' },
          },
          income: 3000,
          expenses: 2700,
          netCashflow: 300,
        },
      ],
    },
  }),
}));

vi.mock('../hooks/useSessionState.js', () => ({
  useSessionState: () => [null, vi.fn()],
}));

vi.mock('../hooks/useOutlookStatus.js', () => ({
  useOutlookStatus: () => ({ status: 'connected' }),
}));

import Dashboard from './Dashboard.jsx';

describe('Dashboard', () => {
  it('uses the authoritative cashflow report for headline totals', () => {
    renderWithProviders(<Dashboard />);

    expect(screen.getByText('Net Cashflow')).toBeInTheDocument();
    expect(screen.getAllByText('$3,000.00').length).toBeGreaterThan(0);
    expect(screen.getAllByText('$2,700.00').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Export cashflow CSV' })).toBeInTheDocument();
  });

  it('lists recent ledger transactions by direction from the unified transactions query', () => {
    renderWithProviders(<Dashboard />);

    expect(screen.getByText('Recent paycheck')).toBeInTheDocument();
    expect(screen.getByText('Recent supplies')).toBeInTheDocument();
  });
});
