import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { createQueryWrapper, createTestQueryClient } from '../test/render.jsx';
import { useRunAutoImport } from './useRunAutoImport.js';

vi.mock('../api/index.js');

describe('useRunAutoImport', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  const renderRun = () =>
    renderHook(useRunAutoImport, { wrapper: createQueryWrapper(queryClient) });

  it('runs auto-import, refreshes the inbox and returns the queued count', async () => {
    vi.mocked(api.runAutoImport).mockResolvedValue({ queued: 3 });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderRun();

    let queued;
    await act(async () => {
      queued = await result.current.run();
    });

    expect(queued).toBe(3);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['inbox'] });
    expect(result.current.running).toBe(false);
  });

  it('clears the running flag when the import fails', async () => {
    vi.mocked(api.runAutoImport).mockRejectedValue(new Error('offline'));
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderRun();

    await act(async () => {
      await expect(result.current.run()).rejects.toThrow('offline');
    });

    expect(invalidateSpy).not.toHaveBeenCalled();
    expect(result.current.running).toBe(false);
  });
});
