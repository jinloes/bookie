import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import * as api from '../api/index.js';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js', () => ({
  getCounterparties: vi.fn(),
  getCounterpartyTypes: vi.fn(),
  getCounterpartyKeywords: vi.fn(),
  createCounterparty: vi.fn(),
  updateCounterparty: vi.fn(),
  deleteCounterparty: vi.fn(),
}));
const mockGetCounterparties = vi.mocked(api.getCounterparties);

import Payers from './Payers.jsx';

describe('Counterparties', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getCounterpartyTypes).mockResolvedValue([]);
    vi.mocked(api.getCounterpartyKeywords).mockResolvedValue([]);
  });

  it('shows an error with Retry instead of the empty state when counterparties fail to load', async () => {
    mockGetCounterparties.mockRejectedValueOnce(new Error('backend down'));
    mockGetCounterparties.mockResolvedValueOnce([
      { id: 2, name: "Joe's Plumbing", type: 'COMPANY', aliases: [], accounts: [] },
    ]);

    const { user } = renderWithProviders(<Payers />);

    expect(await screen.findByText('Could not load counterparties')).toBeInTheDocument();
    expect(screen.queryByText('No counterparties yet')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText("Joe's Plumbing")).toBeInTheDocument();
    expect(mockGetCounterparties).toHaveBeenCalledTimes(2);
  });

  it('still shows the empty state when there are no counterparties', async () => {
    mockGetCounterparties.mockResolvedValue([]);

    renderWithProviders(<Payers />);

    expect(await screen.findByText('No counterparties yet')).toBeInTheDocument();
    expect(screen.queryByText('Could not load counterparties')).not.toBeInTheDocument();
  });
});
