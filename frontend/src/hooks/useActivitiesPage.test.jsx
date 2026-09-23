import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { act, renderHook } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));

vi.mock('../api/index.js');
const mockGetFinancialActivities = vi.mocked(api.getFinancialActivities);
mockGetFinancialActivities.mockResolvedValue([]);
const mockGetHouseholdMembers = vi.mocked(api.getHouseholdMembers);
mockGetHouseholdMembers.mockResolvedValue([]);
const mockGetFinancialActivityTypes = vi.mocked(api.getFinancialActivityTypes);
mockGetFinancialActivityTypes.mockResolvedValue([]);
const mockGetTaxTreatments = vi.mocked(api.getTaxTreatments);
mockGetTaxTreatments.mockResolvedValue([]);
const mockCreateActivity = vi.mocked(api.createFinancialActivity);
const mockCreateMember = vi.mocked(api.createHouseholdMember);

import { queryKeys } from '../queryKeys.js';
import { useActivitiesPage } from './useActivitiesPage.js';

function renderWithClient(queryClient) {
  return renderHook(() => useActivitiesPage(), {
    wrapper: createQueryWrapper(queryClient),
  });
}

describe('useActivitiesPage', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
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
