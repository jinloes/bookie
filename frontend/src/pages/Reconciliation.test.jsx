import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import * as api from '../api/index.js';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js', () => ({
  listReceipts: vi.fn(),
  getOutlookStatus: vi.fn(),
  getInboxItems: vi.fn(),
}));
const mockListReceipts = vi.mocked(api.listReceipts);
const mockGetInboxItems = vi.mocked(api.getInboxItems);

import Reconciliation from './Reconciliation.jsx';

describe('Reconciliation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getOutlookStatus).mockResolvedValue({ connected: true });
    mockListReceipts.mockResolvedValue([]);
  });

  it('shows an error with Retry instead of a false all-clear when the inbox fails to load', async () => {
    mockGetInboxItems.mockRejectedValueOnce(new Error('backend down'));
    mockGetInboxItems.mockResolvedValue([]);

    const { user } = renderWithProviders(<Reconciliation />);

    expect(await screen.findByText('Could not load reconciliation data')).toBeInTheDocument();
    expect(screen.queryByText('No unresolved pending items.')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('No unresolved pending items.')).toBeInTheDocument();
    expect(screen.queryByText('Could not load reconciliation data')).not.toBeInTheDocument();
  });

  it('shows an error when receipts fail to load', async () => {
    mockGetInboxItems.mockResolvedValue([]);
    mockListReceipts.mockReset();
    mockListReceipts.mockRejectedValue(new Error('backend down'));

    renderWithProviders(<Reconciliation />);

    expect(await screen.findByText('Could not load reconciliation data')).toBeInTheDocument();
  });
});
