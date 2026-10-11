import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../api/index.js';
import { createQueryWrapper, createTestQueryClient } from '../test/render.jsx';
import { BACKUP_STATUS_KEY, useBackupActions } from './useBackupActions.js';

vi.mock('../api/index.js');

const status = { vendor: 'sqlserver', frozen: false, keyConfirmed: false };

describe('useBackupActions', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
    vi.mocked(api.getBackupStatus).mockResolvedValue(status);
  });

  const renderActions = () =>
    renderHook(useBackupActions, { wrapper: createQueryWrapper(queryClient) });

  it('nests the status key under the backups key', () => {
    expect(BACKUP_STATUS_KEY).toEqual(['backups', 'status']);
  });

  it('loads the backup status', async () => {
    const { result } = renderActions();

    await waitFor(() => expect(result.current.status).toEqual(status));
    expect(result.current.statusError).toBeNull();
  });

  describe('createPreMigration', () => {
    it('creates the backup and invalidates the backup queries', async () => {
      const preMigration = { fileName: 'pre-migration.bookiebak' };
      vi.mocked(api.createPreMigrationBackup).mockResolvedValue(preMigration);
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
      const { result } = renderActions();

      let returned;
      await act(async () => {
        returned = await result.current.createPreMigration();
      });

      expect(returned).toEqual(preMigration);
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['backups'] });
      expect(result.current.pending).toBeNull();
    });

    it('clears the pending flag when the backup fails', async () => {
      vi.mocked(api.createPreMigrationBackup).mockRejectedValue(new Error('key not confirmed'));
      const { result } = renderActions();

      await act(async () => {
        await expect(result.current.createPreMigration()).rejects.toThrow('key not confirmed');
      });

      expect(result.current.pending).toBeNull();
    });
  });

  describe('prepareRollback', () => {
    it('creates the rollback backup and invalidates the backup queries', async () => {
      vi.mocked(api.createRollbackBackup).mockResolvedValue({ fileName: 'rollback.sql' });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
      const { result } = renderActions();

      await act(async () => {
        await result.current.prepareRollback();
      });

      expect(api.createRollbackBackup).toHaveBeenCalled();
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['backups'] });
    });
  });

  describe('liftFreeze', () => {
    it('lifts the freeze and invalidates every query', async () => {
      vi.mocked(api.liftWriteFreeze).mockResolvedValue(undefined);
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
      const { result } = renderActions();

      await act(async () => {
        await result.current.liftFreeze();
      });

      expect(api.liftWriteFreeze).toHaveBeenCalled();
      expect(invalidateSpy).toHaveBeenCalledWith();
      expect(result.current.pending).toBeNull();
    });

    it('does not invalidate when lifting fails', async () => {
      vi.mocked(api.liftWriteFreeze).mockRejectedValue(new Error('no'));
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
      const { result } = renderActions();

      await act(async () => {
        await expect(result.current.liftFreeze()).rejects.toThrow('no');
      });

      expect(invalidateSpy).not.toHaveBeenCalled();
      expect(result.current.pending).toBeNull();
    });
  });

  describe('revealKey and confirmKey', () => {
    it('shows the key once and hides it after confirmation', async () => {
      vi.mocked(api.revealBackupKey).mockResolvedValue({ key: 'secret-key' });
      vi.mocked(api.confirmBackupKey).mockResolvedValue(undefined);
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
      const { result } = renderActions();

      await act(async () => {
        await result.current.revealKey();
      });
      expect(result.current.revealedKey).toBe('secret-key');

      await act(async () => {
        await result.current.confirmKey();
      });

      expect(api.confirmBackupKey).toHaveBeenCalled();
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['backups'] });
      expect(result.current.revealedKey).toBeNull();
    });

    it('keeps no key when revealing fails', async () => {
      vi.mocked(api.revealBackupKey).mockRejectedValue(new Error('already confirmed'));
      const { result } = renderActions();

      await act(async () => {
        await expect(result.current.revealKey()).rejects.toThrow('already confirmed');
      });

      expect(result.current.revealedKey).toBeNull();
      expect(result.current.pending).toBeNull();
    });
  });
});
