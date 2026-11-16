import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js');
const mockGetInboxItems = vi.mocked(api.getInboxItems);
const mockSaveInboxItem = vi.mocked(api.saveInboxItem);
const mockGetCounterparties = vi.mocked(api.getCounterparties);
const mockGetFinancialActivities = vi.mocked(api.getFinancialActivities);
const mockGetFinancialCategories = vi.mocked(api.getFinancialCategories);
const mockGetProperties = vi.mocked(api.getProperties);

vi.mock('@mantine/notifications', () => ({
  notifications: { show: vi.fn() },
}));

import Inbox from './Inbox.jsx';

beforeEach(() => {
  vi.clearAllMocks();
  mockGetInboxItems.mockResolvedValue([
    {
      id: 51,
      origin: 'VENMO',
      proposedDirection: 'INCOME',
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
      category: { id: 121, label: 'Tutoring income', neutralCategoryId: 121 },
      classificationAmbiguous: true,
      version: 1,
    },
  ]);
  mockGetProperties.mockResolvedValue([]);
  mockGetCounterparties.mockResolvedValue([]);
  mockGetFinancialActivities.mockResolvedValue([
    {
      id: 10,
      name: 'Synthetic tutoring activity',
      active: true,
      owner: { id: 1, name: 'Demo Household' },
    },
  ]);
  mockGetFinancialCategories.mockResolvedValue([
    { id: 121, label: 'Tutoring income', direction: 'INCOME', neutralCategoryId: 121 },
  ]);
  mockSaveInboxItem.mockResolvedValue({ id: 51, state: 'SAVED' });
});

describe('Inbox unified review queue', () => {
  it('auto-expands the first ready item, persists edits on Save Income, and advances to the next ready item', async () => {
    const secondItem = {
      id: 52,
      origin: 'VENMO',
      proposedDirection: 'INCOME',
      state: 'READY',
      amount: 210,
      description: 'Second tutoring payment',
      date: '2026-02-20',
      sourceLabel: 'Second Learner',
      counterpartyName: 'Second Learner',
      counterpartyId: 32,
      activity: {
        id: 10,
        name: 'Synthetic tutoring activity',
        owner: { id: 1, name: 'Demo Household' },
      },
      category: { id: 121, label: 'Tutoring income', neutralCategoryId: 121 },
      classificationAmbiguous: false,
      version: 1,
    };
    mockGetInboxItems
      .mockResolvedValueOnce([
        {
          id: 51,
          origin: 'VENMO',
          proposedDirection: 'INCOME',
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
          category: { id: 121, label: 'Tutoring income', neutralCategoryId: 121 },
          classificationAmbiguous: true,
          version: 1,
        },
        secondItem,
      ])
      .mockResolvedValue([secondItem]);
    const { user } = renderWithProviders(<Inbox />);

    expect(await screen.findByText('Synthetic tutoring payment')).toBeInTheDocument();
    expect(screen.getByText('Second tutoring payment')).toBeInTheDocument();
    // First READY item is already expanded without clicking its header.
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
    const source = screen.getByLabelText(/^Source$/);
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
    // Next READY item automatically expands once the first item is saved.
    await waitFor(() =>
      expect(screen.getByLabelText(/^Description/)).toHaveValue('Second tutoring payment')
    );
  });

  it('filters failed items and opens the statement import drawer from the Import menu', async () => {
    mockGetInboxItems.mockResolvedValueOnce([
      {
        id: 51,
        origin: 'VENMO',
        proposedDirection: 'INCOME',
        state: 'READY',
        amount: 425,
        description: 'Synthetic tutoring payment',
        date: '2026-02-14',
        version: 1,
      },
      {
        id: 99,
        origin: 'RECEIPT',
        subject: 'Unreadable receipt.pdf',
        state: 'FAILED',
        errorMessage: 'OCR timeout',
        version: 1,
      },
    ]);
    const { user } = renderWithProviders(<Inbox />);

    expect(await screen.findByText('Unreadable receipt.pdf')).toBeInTheDocument();
    await user.click(screen.getByText('Failed (1)'));
    expect(screen.queryByText('Synthetic tutoring payment')).not.toBeInTheDocument();
    expect(screen.getByText('Unreadable receipt.pdf')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^import$/i }));
    await user.click(
      await screen.findByRole('menuitem', { name: /income statement \(venmo \/ bank\)/i })
    );
    expect(
      await screen.findByRole('dialog', { name: /import income statement/i })
    ).toBeInTheDocument();
  });
});
