import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import * as api from '../api/index.js';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js', () => ({
  getProperties: vi.fn(),
  getPropertyTypes: vi.fn(),
  getPropertyKeywords: vi.fn(),
  createProperty: vi.fn(),
  updateProperty: vi.fn(),
  deleteProperty: vi.fn(),
}));
const mockGetProperties = vi.mocked(api.getProperties);

import Properties from './Properties.jsx';

describe('Properties', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getPropertyTypes).mockResolvedValue([]);
    vi.mocked(api.getPropertyKeywords).mockResolvedValue([]);
  });

  it('shows an error with Retry instead of the empty state when properties fail to load', async () => {
    mockGetProperties.mockRejectedValueOnce(new Error('backend down'));
    mockGetProperties.mockResolvedValueOnce([
      { id: 1, name: 'Oak Street', address: '123 Oak', type: 'SINGLE_FAMILY', accounts: [] },
    ]);

    const { user } = renderWithProviders(<Properties />);

    expect(await screen.findByText('Could not load properties')).toBeInTheDocument();
    expect(screen.queryByText('No properties yet')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('Oak Street')).toBeInTheDocument();
    expect(mockGetProperties).toHaveBeenCalledTimes(2);
  });

  it('still shows the empty state when there are no properties', async () => {
    mockGetProperties.mockResolvedValue([]);

    renderWithProviders(<Properties />);

    expect(await screen.findByText('No properties yet')).toBeInTheDocument();
    expect(screen.queryByText('Could not load properties')).not.toBeInTheDocument();
  });
});
