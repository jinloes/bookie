import { act, renderHook } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import * as tauriCore from '@tauri-apps/api/core';

vi.mock('@tauri-apps/api/core');
vi.mock('../api/index.js');
const mockInvoke = vi.mocked(tauriCore.invoke);
const mockIsTauri = vi.mocked(tauriCore.isTauri);
const mockGetRestoreStatus = vi.mocked(api.getRestoreStatus);
const mockRestoreBackup = vi.mocked(api.restoreBackup);

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
    queryClient = createTestQueryClient();
  });

  const renderRestoreHook = () =>
    renderHook(useRestoreBackup, {
      wrapper: createQueryWrapper(queryClient),
    });

  it('restarts an owned backend and waits for post-start validation', async () => {
    mockIsTauri.mockReturnValue(true);
    mockRestoreBackup.mockResolvedValue(staged);
    mockInvoke.mockResolvedValue({ restarted: true, manualRestartRequired: false });
    mockGetRestoreStatus.mockResolvedValue(completed);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderRestoreHook();

    let outcome;
    await act(async () => {
      outcome = await result.current.runRestore('file-1');
    });

    expect(mockRestoreBackup).toHaveBeenCalledWith('file-1');
    expect(mockInvoke).toHaveBeenCalledWith('restart_backend_after_restore', {
      restoreId: 'restore-123',
    });
    expect(mockGetRestoreStatus).toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalled();
    expect(outcome).toEqual({ outcome: 'restored', status: completed });
    expect(result.current.isRestoring).toBe(false);
  });

  it('completes a portable restore without a restart and refreshes all data', async () => {
    const portable = {
      restoreId: null,
      state: 'POST_START_VALIDATED',
      restored: true,
      validated: true,
      restartRequired: false,
    };
    mockIsTauri.mockReturnValue(true);
    mockRestoreBackup.mockResolvedValue(portable);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderRestoreHook();

    let outcome;
    await act(async () => {
      outcome = await result.current.runRestore('file-portable');
    });

    expect(outcome).toEqual({ outcome: 'restored', status: portable });
    expect(invalidateSpy).toHaveBeenCalledWith();
    expect(mockInvoke).not.toHaveBeenCalled();
    expect(mockGetRestoreStatus).not.toHaveBeenCalled();
  });

  it('keeps a validated restore staged when the backend is not owned by Tauri', async () => {
    mockIsTauri.mockReturnValue(false);
    mockRestoreBackup.mockResolvedValue(staged);
    const { result } = renderRestoreHook();

    let outcome;
    await act(async () => {
      outcome = await result.current.runRestore('file-1');
    });

    expect(outcome).toEqual({ outcome: 'manual-restart', status: staged });
    expect(mockInvoke).not.toHaveBeenCalled();
    expect(mockGetRestoreStatus).not.toHaveBeenCalled();
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
