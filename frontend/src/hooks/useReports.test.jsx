import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const mockGetCashflowReport = vi.fn();
const mockGetScheduleEReport = vi.fn();

vi.mock('../api/index.js', () => ({
  getCashflowReport: (...args) => mockGetCashflowReport(...args),
  getScheduleEReport: (...args) => mockGetScheduleEReport(...args),
}));

import { queryKeys } from '../queryKeys.js';
import { useCashflowReport, useScheduleEReport } from './useReports.js';

function wrapper(queryClient) {
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('report hooks', () => {
  it('loads cashflow by an explicit period and uses the period query key', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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
