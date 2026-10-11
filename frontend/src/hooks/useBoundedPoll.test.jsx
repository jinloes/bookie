import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { createQueryWrapper, createTestQueryClient } from '../test/render.jsx';
import { queryKeys } from '../queryKeys.js';
import {
  BOUNDED_POLL_IDLE_MS,
  RECOVERY_DRAIN_THROTTLE_MS,
  requestRecoveryDrain,
  resetRecoveryDrainThrottle,
  useBoundedPoll,
} from './useBoundedPoll.js';

vi.mock('../api/index.js');
const mockDrainIntake = vi.mocked(api.drainIntake);

const query = (data) => ({ state: { data } });

describe('useBoundedPoll', () => {
  let queryClient;
  let clock;

  beforeEach(() => {
    vi.clearAllMocks();
    resetRecoveryDrainThrottle();
    mockDrainIntake.mockResolvedValue(undefined);
    queryClient = createTestQueryClient();
    clock = 1_000_000;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const renderBoundedPoll = (queryKey = queryKeys.outlookStatus, options = {}) =>
    renderHook(() => useBoundedPoll({ queryKey, now: () => clock, ...options }), {
      wrapper: createQueryWrapper(queryClient),
    });

  describe('refetchInterval', () => {
    it('keeps polling at the base interval until the state is unchanged for two minutes', () => {
      const { result } = renderBoundedPoll();
      const interval = result.current(5000);

      expect(interval(query({ state: 'QUEUED' }))).toBe(5000);
      clock += BOUNDED_POLL_IDLE_MS - 1;
      expect(interval(query({ state: 'QUEUED' }))).toBe(5000);
      clock += 1;
      expect(interval(query({ state: 'QUEUED' }))).toBe(false);
    });

    it('restarts the idle window whenever the state changes', () => {
      const { result } = renderBoundedPoll();
      const interval = result.current(5000);
      interval(query({ state: 'QUEUED' }));

      clock += BOUNDED_POLL_IDLE_MS - 1000;
      expect(interval(query({ state: 'PROCESSING' }))).toBe(5000);
      clock += BOUNDED_POLL_IDLE_MS - 1;
      expect(interval(query({ state: 'PROCESSING' }))).toBe(5000);
    });

    it('delegates to an interval function and stays off when it declines to poll', () => {
      const { result } = renderBoundedPoll();
      const interval = result.current((q) => (q.state.data?.connected ? false : 2000));

      expect(interval(query({ connected: false }))).toBe(2000);
      expect(interval(query({ connected: true }))).toBe(false);
    });

    it('compares states through the supplied signature', () => {
      const { result } = renderBoundedPoll(queryKeys.inbox, {
        signature: (items) => items.map((item) => item.state).join(),
      });
      const interval = result.current(5000);
      interval(query([{ state: 'QUEUED', updatedAt: 1 }]));

      clock += BOUNDED_POLL_IDLE_MS;
      expect(interval(query([{ state: 'QUEUED', updatedAt: 2 }]))).toBe(false);
    });
  });

  describe('resume on window focus', () => {
    it('resumes polling, refetches the query and requests a recovery drain', () => {
      const refetchSpy = vi.spyOn(queryClient, 'refetchQueries').mockResolvedValue(undefined);
      const { result } = renderBoundedPoll();
      const interval = result.current(2000);
      interval(query({ connected: false }));
      clock += BOUNDED_POLL_IDLE_MS;
      expect(interval(query({ connected: false }))).toBe(false);

      act(() => {
        window.dispatchEvent(new window.Event('focus'));
      });

      expect(interval(query({ connected: false }))).toBe(2000);
      expect(refetchSpy).toHaveBeenCalledWith({
        queryKey: queryKeys.outlookStatus,
        type: 'active',
      });
      expect(mockDrainIntake).toHaveBeenCalledTimes(1);
    });

    it('stops listening after unmount', () => {
      const refetchSpy = vi.spyOn(queryClient, 'refetchQueries').mockResolvedValue(undefined);
      const { unmount } = renderBoundedPoll();
      unmount();

      window.dispatchEvent(new window.Event('focus'));

      expect(refetchSpy).not.toHaveBeenCalled();
      expect(mockDrainIntake).not.toHaveBeenCalled();
    });
  });

  describe('resume on SSE invalidation', () => {
    it('refetches a non-inbox poll when the inbox is invalidated', async () => {
      const refetchSpy = vi.spyOn(queryClient, 'refetchQueries');
      queryClient.setQueryData(queryKeys.inbox, []);
      const { result } = renderBoundedPoll();
      const interval = result.current(2000);
      interval(query({ connected: false }));
      clock += BOUNDED_POLL_IDLE_MS;
      expect(interval(query({ connected: false }))).toBe(false);

      await act(async () => {
        await queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
      });

      expect(interval(query({ connected: false }))).toBe(2000);
      expect(refetchSpy).toHaveBeenCalledWith({
        queryKey: queryKeys.outlookStatus,
        type: 'active',
      });
    });

    it('lets the invalidation refetch the inbox poll itself', async () => {
      const refetchSpy = vi.spyOn(queryClient, 'refetchQueries');
      queryClient.setQueryData(queryKeys.inbox, []);
      const { result } = renderBoundedPoll(queryKeys.inbox);
      const interval = result.current(5000);
      interval(query([]));
      clock += BOUNDED_POLL_IDLE_MS;
      expect(interval(query([]))).toBe(false);

      await act(async () => {
        await queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
      });

      expect(interval(query([]))).toBe(5000);
      // invalidateQueries makes this one refetch itself; the poll must not add a second.
      expect(refetchSpy).toHaveBeenCalledTimes(1);
    });

    it('ignores invalidations of other queries', async () => {
      queryClient.setQueryData(queryKeys.transactions, []);
      const { result } = renderBoundedPoll();
      const interval = result.current(2000);
      interval(query({ connected: false }));
      clock += BOUNDED_POLL_IDLE_MS;

      await act(async () => {
        await queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
      });

      expect(interval(query({ connected: false }))).toBe(false);
    });
  });
});

describe('requestRecoveryDrain', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetRecoveryDrainThrottle();
  });

  it('sends at most one drain per throttle window', () => {
    mockDrainIntake.mockResolvedValue(undefined);

    requestRecoveryDrain(0);
    requestRecoveryDrain(RECOVERY_DRAIN_THROTTLE_MS - 1);
    expect(mockDrainIntake).toHaveBeenCalledTimes(1);

    requestRecoveryDrain(RECOVERY_DRAIN_THROTTLE_MS);
    expect(mockDrainIntake).toHaveBeenCalledTimes(2);
  });

  it('swallows a failed drain because a later focus or startup drain recovers', async () => {
    mockDrainIntake.mockRejectedValue(new Error('offline'));

    expect(() => requestRecoveryDrain(0)).not.toThrow();
    await Promise.resolve();
    expect(mockDrainIntake).toHaveBeenCalledTimes(1);
  });
});
