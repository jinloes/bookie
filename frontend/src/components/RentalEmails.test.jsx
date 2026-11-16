import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import * as api from '../api/index.js';
import { renderWithProviders } from '../test/render.jsx';
import RentalEmails from './RentalEmails.jsx';

vi.mock('../api/index.js');
vi.mock('../hooks/useOutlookStatus.js', () => ({
  useOutlookStatus: () => ({ connected: true, isChecking: false, isDisconnected: false }),
}));

const mockGetOutlookRentalEmails = vi.mocked(api.getOutlookRentalEmails);
const mockGetFinancialActivities = vi.mocked(api.getFinancialActivities);

describe('RentalEmails', () => {
  const currentYear = new Date().getFullYear();

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetFinancialActivities.mockResolvedValue([]);
  });

  it('requests the selected year when the year filter changes', async () => {
    mockGetOutlookRentalEmails.mockResolvedValue({ emails: [], page: 0, hasMore: false });
    const { user } = renderWithProviders(<RentalEmails onQueued={vi.fn()} refreshKey={0} />);

    await waitFor(() => expect(mockGetOutlookRentalEmails).toHaveBeenCalledWith(0, currentYear));

    await user.click(screen.getByRole('combobox', { name: 'Email year' }));
    await user.click(await screen.findByRole('option', { name: String(currentYear - 1) }));

    await waitFor(() =>
      expect(mockGetOutlookRentalEmails).toHaveBeenCalledWith(0, currentYear - 1)
    );
  });

  it('does not poll repeatedly when queued emails are already READY', async () => {
    mockGetOutlookRentalEmails.mockResolvedValue({
      emails: [
        {
          id: 'msg-ready',
          subject: 'Electric bill',
          sender: 'Utility',
          receivedAt: `${currentYear}-02-01T10:00:00Z`,
          preview: 'Amount due',
          inboxItemId: 12,
          inboxState: 'READY',
        },
      ],
      page: 0,
      hasMore: false,
    });

    const { queryClient } = renderWithProviders(<RentalEmails onQueued={vi.fn()} refreshKey={0} />);
    expect(await screen.findByText('Electric bill')).toBeInTheDocument();

    const query = queryClient
      .getQueryCache()
      .find({ queryKey: ['outlookRentalEmails', 0, 0, currentYear] });
    const intervalFn = query?.observers[0]?.options?.refetchInterval;
    expect(typeof intervalFn === 'function' ? intervalFn(query) : intervalFn).toBe(false);
  });
});
