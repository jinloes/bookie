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

describe('Payers', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getCounterpartyTypes).mockResolvedValue([]);
    vi.mocked(api.getCounterpartyKeywords).mockResolvedValue([]);
  });

  it('shows an error with Retry instead of the empty state when payers fail to load', async () => {
    mockGetCounterparties.mockRejectedValueOnce(new Error('backend down'));
    mockGetCounterparties.mockResolvedValueOnce([
      { id: 2, name: "Joe's Plumbing", type: 'COMPANY', aliases: [], accounts: [] },
    ]);

    const { user } = renderWithProviders(<Payers />);

    expect(await screen.findByText('Could not load payers')).toBeInTheDocument();
    expect(screen.queryByText('No payers yet')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText("Joe's Plumbing")).toBeInTheDocument();
    expect(mockGetCounterparties).toHaveBeenCalledTimes(2);
  });

  it('still shows the empty state when there are no payers', async () => {
    mockGetCounterparties.mockResolvedValue([]);

    renderWithProviders(<Payers />);

    expect(await screen.findByText('No payers yet')).toBeInTheDocument();
    expect(screen.queryByText('Could not load payers')).not.toBeInTheDocument();
  });
});
