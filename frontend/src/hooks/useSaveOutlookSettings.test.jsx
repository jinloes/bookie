import { act, renderHook } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';

vi.mock('../api/index.js');
const mockUpdateFolders = vi.mocked(api.updateOutlookFolderSettings);
const mockUpdateMove = vi.mocked(api.updateOutlookMoveSettings);

import { queryKeys } from '../queryKeys.js';
import { useSaveOutlookSettings } from './useSaveOutlookSettings.js';

describe('useSaveOutlookSettings', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  it('persists per-folder activity context and invalidates the intake feed', async () => {
    mockUpdateFolders.mockResolvedValue([]);
    mockUpdateMove.mockResolvedValue(undefined);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(useSaveOutlookSettings, {
      wrapper: createQueryWrapper(queryClient),
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
