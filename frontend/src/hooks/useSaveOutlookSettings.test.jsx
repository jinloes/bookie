import React from 'react';
import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockUpdateFolders = vi.fn();
const mockUpdateMove = vi.fn();
vi.mock('../api/index.js', () => ({
  updateOutlookFolderSettings: (...args) => mockUpdateFolders(...args),
  updateOutlookMoveSettings: (...args) => mockUpdateMove(...args),
}));

import { queryKeys } from '../queryKeys.js';
import { useSaveOutlookSettings } from './useSaveOutlookSettings.js';

describe('useSaveOutlookSettings', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('persists per-folder activity context and invalidates the intake feed', async () => {
    mockUpdateFolders.mockResolvedValue([]);
    mockUpdateMove.mockResolvedValue(undefined);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(useSaveOutlookSettings, {
      wrapper: ({ children }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      ),
    });
    const folderSettings = [{ folderId: 'employment', expandSubfolders: true, activityId: 42 }];

    await act(async () => {
      await result.current.saveOutlookSettings({
        folderSettings,
        moveEnabled: false,
        moveDestinationFolderId: null,
      });
    });

    expect(mockUpdateFolders).toHaveBeenCalledWith(folderSettings);
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: queryKeys.outlookFolderSettings,
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['outlookRentalEmails'] });
  });
});
