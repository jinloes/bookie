import { describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { renderHook, waitFor } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';

vi.mock('../api/index.js');

const mockGetCashflowReport = vi.mocked(api.getCashflowReport);
const mockGetScheduleEReport = vi.mocked(api.getScheduleEReport);

import { queryKeys } from '../queryKeys.js';
import { useCashflowReport, useScheduleEReport } from './useReports.js';

const wrapper = createQueryWrapper;

describe('report hooks', () => {
  it('loads cashflow by an explicit period and uses the period query key', async () => {
    const queryClient = createTestQueryClient();
    mockGetCashflowReport.mockResolvedValue({ netCashflow: 300 });
    const { result } = renderHook(
      () =>
        useCashflowReport('2026-01-01', '2026-12-31', {
          ownerId: '4',
          activityId: '10',
        }),
      {
        wrapper: wrapper(queryClient),
      }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockGetCashflowReport).toHaveBeenCalledWith('2026-01-01', '2026-12-31', '4', '10');
    expect(
      queryClient.getQueryData(queryKeys.cashflowReport('2026-01-01', '2026-12-31', '4', '10'))
    ).toEqual({ netCashflow: 300 });
  });

  it('loads the authoritative Schedule E report for a year', async () => {
    const queryClient = createTestQueryClient();
    mockGetScheduleEReport.mockResolvedValue({ year: 2026, netIncome: -200 });
    const { result } = renderHook(
      () => useScheduleEReport('2026', { ownerId: '4', activityId: '10' }),
      {
        wrapper: wrapper(queryClient),
      }
    );

    await waitFor(() => expect(result.current.data?.netIncome).toBe(-200));

    expect(mockGetScheduleEReport).toHaveBeenCalledWith('2026', '4', '10');
  });
});
