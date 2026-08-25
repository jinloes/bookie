import { useQuery } from '@tanstack/react-query';
import { getCashflowReport, getScheduleEReport } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

export function useCashflowReport(from, to, filters = {}) {
  const ownerId = filters.ownerId ?? null;
  const activityId = filters.activityId ?? null;
  return useQuery({
    queryKey: queryKeys.cashflowReport(from, to, ownerId, activityId),
    queryFn: () => getCashflowReport(from, to, ownerId, activityId),
    enabled: Boolean(from && to),
  });
}

export function useScheduleEReport(year, filters = {}) {
  const ownerId = filters.ownerId ?? null;
  const activityId = filters.activityId ?? null;
  return useQuery({
    queryKey: queryKeys.scheduleEReport(year, ownerId, activityId),
    queryFn: () => getScheduleEReport(year, ownerId, activityId),
    enabled: Boolean(year),
  });
}
