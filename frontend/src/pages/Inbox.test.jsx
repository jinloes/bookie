import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js');
const mockAcceptPendingIncome = vi.mocked(api.acceptPendingIncome);
const mockGetFinancialActivities = vi.mocked(api.getFinancialActivities);
const mockGetFinancialCategories = vi.mocked(api.getFinancialCategories);
const mockGetProperties = vi.mocked(api.getProperties);

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
    const { user } = renderWithProviders(<Inbox />);

    await user.click(screen.getByRole('button', { name: 'Review' }));
    expect(await screen.findByText(/classification needs review/i)).toBeInTheDocument();
    expect(mockAcceptPendingIncome).not.toHaveBeenCalled();

    const amount = screen.getByLabelText(/^Amount/);
    await user.clear(amount);
    await user.paste('450');
    const description = screen.getByLabelText(/^Description/);
    await user.clear(description);
    await user.paste('Edited synthetic tutoring payment');
    const date = screen.getByLabelText(/^Date/);
    await user.clear(date);
    await user.paste('2026-02-15');
    const source = screen.getByLabelText(/^Source/);
    await user.clear(source);
    await user.paste('Edited Demo Learner');

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
