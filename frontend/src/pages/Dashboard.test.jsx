import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

const currentYear = new Date().getFullYear();
const mockUseCashflowReport = vi.fn(() => ({
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
}));

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
          date: `${currentYear}-08-15`,
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
          date: `${currentYear}-08-10`,
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
  useCashflowReport: (...args) => mockUseCashflowReport(...args),
}));

vi.mock('../hooks/useSessionState.js', () => ({
  useSessionState: () => [null, vi.fn()],
}));

vi.mock('../hooks/useOutlookStatus.js', () => ({
  useOutlookStatus: () => ({ status: 'connected' }),
}));

import Dashboard, { buildMonthlyBreakdown } from './Dashboard.jsx';

describe('Dashboard', () => {
  it('scopes headline totals to the selected period and includes all ready inbox origins in pending count', () => {
    renderWithProviders(<Dashboard />);

    expect(mockUseCashflowReport).toHaveBeenCalledWith(
      `${currentYear}-01-01`,
      `${currentYear}-12-31`,
      { ownerId: null, activityId: null }
    );
    expect(screen.getByText('Net Cashflow')).toBeInTheDocument();
    expect(screen.getAllByText('$3,000.00').length).toBeGreaterThan(0);
    expect(screen.getAllByText('$2,700.00').length).toBeGreaterThan(0);
    expect(screen.getByText(/2 items are ready to review/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export cashflow CSV' })).toBeInTheDocument();
  });

  it('lists recent ledger transactions by direction from the unified transactions query', () => {
    renderWithProviders(<Dashboard />);

    expect(screen.getByText('Recent paycheck')).toBeInTheDocument();
    expect(screen.getByText('Recent supplies')).toBeInTheDocument();
  });

  it('zero-fills elapsed months and formats month labels in local time without UTC shift', () => {
    const rows = buildMonthlyBreakdown(
      [{ date: '2026-03-01', amount: 1200 }],
      [{ date: '2026-01-15', amount: 300 }],
      2026,
      2026,
      4
    );

    expect(rows.map((r) => r.label)).toEqual(['Jan', 'Feb', 'Mar', 'Apr']);
    expect(rows.map((r) => r.income)).toEqual([0, 0, 1200, 0]);
    expect(rows.map((r) => r.expenses)).toEqual([300, 0, 0, 0]);
  });
});
