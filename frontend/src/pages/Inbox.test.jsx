import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js');
const mockSaveInboxItem = vi.mocked(api.saveInboxItem);
const mockGetFinancialActivities = vi.mocked(api.getFinancialActivities);
const mockGetFinancialCategories = vi.mocked(api.getFinancialCategories);
const mockGetProperties = vi.mocked(api.getProperties);

vi.mock('../hooks/usePendingQueue.js', () => ({
  usePendingIncomesQuery: () => ({
    data: [
      {
        id: 51,
        origin: 'VENMO',
        state: 'READY',
        amount: 425,
        description: 'Synthetic tutoring payment',
        date: '2026-02-14',
        sourceLabel: 'Demo Learner',
        counterpartyName: 'Demo Learner',
        counterpartyId: 31,
        activity: {
          id: 10,
          name: 'Synthetic tutoring activity',
          owner: { id: 1, name: 'Demo Household' },
        },
        category: { id: 21, label: 'Tutoring income', neutralCategoryId: 121 },
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
    { id: 21, label: 'Tutoring income', direction: 'INCOME', neutralCategoryId: 121 },
  ]);
  mockSaveInboxItem.mockResolvedValue({ id: 51, state: 'SAVED' });
});

describe('Inbox pending income review', () => {
  it('keeps Venmo fields editable and persists edits only after Save income', async () => {
    const { user } = renderWithProviders(<Inbox />);

    await user.click(screen.getByRole('button', { name: 'Review' }));
    expect(await screen.findByText(/classification needs review/i)).toBeInTheDocument();
    expect(mockSaveInboxItem).not.toHaveBeenCalled();

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

    await waitFor(() => expect(mockSaveInboxItem).toHaveBeenCalledTimes(1));
    expect(mockSaveInboxItem).toHaveBeenCalledWith(51, {
      direction: 'INCOME',
      amount: 450,
      description: 'Edited synthetic tutoring payment',
      date: '2026-02-15',
      sourceLabel: 'Edited Demo Learner',
      counterpartyId: 31,
      activityId: 10,
      neutralCategoryId: 121,
    });
  });
});
