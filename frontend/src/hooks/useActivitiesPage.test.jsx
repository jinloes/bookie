import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

const mockCreateActivity = vi.fn();
const mockUpdateActivity = vi.fn();
const mockCreateMember = vi.fn();
vi.mock('../api/index.js', () => ({
  getFinancialActivities: vi.fn().mockResolvedValue([]),
  getHouseholdMembers: vi.fn().mockResolvedValue([]),
  getFinancialActivityTypes: vi.fn().mockResolvedValue([]),
  getTaxTreatments: vi.fn().mockResolvedValue([]),
  createFinancialActivity: (...args) => mockCreateActivity(...args),
  updateFinancialActivity: (...args) => mockUpdateActivity(...args),
  createHouseholdMember: (...args) => mockCreateMember(...args),
  updateHouseholdMember: vi.fn(),
}));

import { queryKeys } from '../queryKeys.js';
import { useActivitiesPage } from './useActivitiesPage.js';

function renderWithClient(queryClient) {
  return renderHook(() => useActivitiesPage(), {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

describe('useActivitiesPage', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('invalidates activities after saving an activity', async () => {
    mockCreateActivity.mockResolvedValue({ id: 1, name: 'Teaching' });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);

    await act(async () => {
      await result.current.saveActivity(null, {
        name: 'Teaching',
        activityType: 'EMPLOYMENT',
        taxTreatment: 'W2',
        ownerId: 1,
        propertyId: null,
        active: true,
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.financialActivities });
  });

  it('invalidates members and activities after saving an owner', async () => {
    mockCreateMember.mockResolvedValue({ id: 1, name: 'Sam' });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);

    await act(async () => {
      await result.current.saveMember(null, { name: 'Sam', active: true });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.householdMembers });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.financialActivities });
  });
});
