import React from 'react';
import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  getRestoreStatus: vi.fn(),
  invoke: vi.fn(),
  isTauri: vi.fn(),
  restoreBackup: vi.fn(),
}));

vi.mock('@tauri-apps/api/core', () => ({
  invoke: (...args) => mocks.invoke(...args),
  isTauri: () => mocks.isTauri(),
}));

vi.mock('../api/index.js', () => ({
  getRestoreStatus: (...args) => mocks.getRestoreStatus(...args),
  restoreBackup: (...args) => mocks.restoreBackup(...args),
}));

import { useRestoreBackup, waitForRestoreCompletion } from './useRestoreBackup.js';

const staged = {
  restoreId: 'restore-123',
  state: 'VALIDATED',
  restored: false,
  validated: true,
  restartRequired: true,
};

const completed = {
  ...staged,
  state: 'POST_START_VALIDATED',
  restored: true,
  restartRequired: false,
};

describe('useRestoreBackup', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  const renderRestoreHook = () =>
    renderHook(useRestoreBackup, {
      wrapper: ({ children }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      ),
    });

  it('restarts an owned backend and waits for post-start validation', async () => {
    mocks.isTauri.mockReturnValue(true);
    mocks.restoreBackup.mockResolvedValue(staged);
    mocks.invoke.mockResolvedValue({ restarted: true, manualRestartRequired: false });
    mocks.getRestoreStatus.mockResolvedValue(completed);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderRestoreHook();

    let outcome;
    await act(async () => {
      outcome = await result.current.runRestore('file-1');
    });

    expect(mocks.restoreBackup).toHaveBeenCalledWith('file-1');
    expect(mocks.invoke).toHaveBeenCalledWith('restart_backend_after_restore', {
      restoreId: 'restore-123',
    });
    expect(mocks.getRestoreStatus).toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalled();
    expect(outcome).toEqual({ outcome: 'restored', status: completed });
    expect(result.current.isRestoring).toBe(false);
  });

  it('keeps a validated restore staged when the backend is not owned by Tauri', async () => {
    mocks.isTauri.mockReturnValue(false);
    mocks.restoreBackup.mockResolvedValue(staged);
    const { result } = renderRestoreHook();

    let outcome;
    await act(async () => {
      outcome = await result.current.runRestore('file-1');
    });

    expect(outcome).toEqual({ outcome: 'manual-restart', status: staged });
    expect(mocks.invoke).not.toHaveBeenCalled();
    expect(mocks.getRestoreStatus).not.toHaveBeenCalled();
  });
});

describe('waitForRestoreCompletion', () => {
  it('retries transient disconnects while the backend restarts', async () => {
    const loadStatus = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('fetch failed'))
      .mockResolvedValueOnce({ ...staged, state: 'SHADOW_ACTIVATED' })
      .mockResolvedValueOnce(completed);
    const wait = vi.fn().mockResolvedValue(undefined);

    const result = await waitForRestoreCompletion('restore-123', {
      loadStatus,
      wait,
      attempts: 3,
      intervalMs: 0,
    });

    expect(result).toEqual(completed);
    expect(wait).toHaveBeenCalledTimes(2);
  });

  it('reports that the original database was reactivated after a failed candidate', async () => {
    const loadStatus = vi.fn().mockResolvedValue({
      ...staged,
      state: 'ROLLED_BACK',
      message: 'Retained live database restored',
    });

    await expect(
      waitForRestoreCompletion('restore-123', {
        loadStatus,
        wait: vi.fn(),
        attempts: 1,
        intervalMs: 0,
      })
    ).rejects.toThrow('retained database was reactivated');
  });
});
