import React from 'react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter } from 'react-router-dom';

vi.mock('@tanstack/react-query', () => ({
  useQuery: ({ queryKey }) => {
    const data = {
      pendingExpenses: [],
      financialActivities: [
        {
          id: 10,
          name: 'Teaching',
          owner: { id: 1, name: 'Alex' },
        },
      ],
      incomes: [
        {
          id: 1,
          amount: 999,
          date: '2026-08-15',
          description: 'Recent paycheck',
          activity: { id: 10, owner: { id: 1, name: 'Alex' } },
        },
      ],
      expenses: [],
      properties: [{}],
      payers: [{}],
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
  global.ResizeObserver =
    global.ResizeObserver ||
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
});

describe('Dashboard', () => {
  it('uses the authoritative cashflow report for headline totals', () => {
    render(
      <MemoryRouter>
        <MantineProvider>
          <Dashboard />
        </MantineProvider>
      </MemoryRouter>
    );

    expect(screen.getByText('Net Cashflow')).toBeTruthy();
    expect(screen.getAllByText('$3,000.00').length).toBeGreaterThan(0);
    expect(screen.getAllByText('$2,700.00').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Export cashflow CSV' })).toBeTruthy();
  });
});
