import React from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const mockAcceptPendingIncome = vi.fn();
const mockRejectPendingIncome = vi.fn();
const mockGetFinancialActivities = vi.fn();
const mockGetFinancialCategories = vi.fn();
const mockGetProperties = vi.fn();

vi.mock('../api/index.js', () => ({
  acceptPendingIncome: (...args) => mockAcceptPendingIncome(...args),
  rejectPendingIncome: (...args) => mockRejectPendingIncome(...args),
  getFinancialActivities: (...args) => mockGetFinancialActivities(...args),
  getFinancialCategories: (...args) => mockGetFinancialCategories(...args),
  getProperties: (...args) => mockGetProperties(...args),
}));

vi.mock('../hooks/usePendingQueue.js', () => ({
  usePendingIncomesQuery: () => ({
    data: [
      {
        id: 51,
        amount: 425,
        description: 'Synthetic tutoring payment',
        date: '2026-02-14',
        source: 'Demo Learner',
        payer: { id: 31, name: 'Demo Learner' },
        activity: {
          id: 10,
          name: 'Synthetic tutoring activity',
          owner: { id: 1, name: 'Demo Household' },
        },
        financialCategory: { id: 21, label: 'Tutoring income' },
        classificationAmbiguous: true,
      },
    ],
    isLoading: false,
  }),
}));

vi.mock('../components/PendingExpenses.jsx', () => ({
  default: () => <div>Pending expense list</div>,
}));

vi.mock('@mantine/notifications', () => ({
  notifications: { show: vi.fn() },
}));

import Inbox from './Inbox.jsx';

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
  mockGetProperties.mockResolvedValue([]);
  mockGetFinancialActivities.mockResolvedValue([
    {
      id: 10,
      name: 'Synthetic tutoring activity',
      active: true,
      owner: { id: 1, name: 'Demo Household' },
    },
  ]);
  mockGetFinancialCategories.mockResolvedValue([
    { id: 21, label: 'Tutoring income', direction: 'INCOME' },
  ]);
  mockAcceptPendingIncome.mockResolvedValue({ id: 61 });
});

describe('Inbox pending income review', () => {
  it('keeps Venmo fields editable and persists edits only after Save income', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const user = userEvent.setup();
    render(
      <MantineProvider>
        <QueryClientProvider client={queryClient}>
          <Inbox />
        </QueryClientProvider>
      </MantineProvider>
    );

    await user.click(screen.getByRole('button', { name: 'Review' }));
    expect(await screen.findByText(/classification needs review/i)).toBeTruthy();
    expect(mockAcceptPendingIncome).not.toHaveBeenCalled();

    const amount = screen.getByLabelText(/^Amount/);
    fireEvent.change(amount, { target: { value: '450' } });
    const description = screen.getByLabelText(/^Description/);
    fireEvent.change(description, { target: { value: 'Edited synthetic tutoring payment' } });
    const date = screen.getByLabelText(/^Date/);
    fireEvent.change(date, { target: { value: '2026-02-15' } });
    const source = screen.getByLabelText(/^Source/);
    fireEvent.change(source, { target: { value: 'Edited Demo Learner' } });

    await user.click(screen.getByRole('button', { name: /save income/i }));

    await waitFor(() => expect(mockAcceptPendingIncome).toHaveBeenCalledTimes(1));
    expect(mockAcceptPendingIncome).toHaveBeenCalledWith(
      51,
      expect.objectContaining({
        amount: 450,
        description: 'Edited synthetic tutoring payment',
        date: '2026-02-15',
        source: 'Edited Demo Learner',
        payerId: 31,
        activityId: 10,
        categoryId: 21,
      })
    );
  });
});
