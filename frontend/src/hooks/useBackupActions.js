import { useCallback, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  confirmBackupKey,
  createPreMigrationBackup,
  createRollbackBackup,
  getBackupStatus,
  liftWriteFreeze,
  revealBackupKey,
} from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

// Nested under the backups key so invalidating the backup list also refreshes the status.
export const BACKUP_STATUS_KEY = [...queryKeys.backups, 'status'];

/**
 * Backup-page state that depends on the database vendor: the freeze flag, the encryption-key
 * confirmation, and the pre-migration, rollback and lift-freeze actions.
 */
export function useBackupActions() {
  const queryClient = useQueryClient();
  const statusQuery = useQuery({ queryKey: BACKUP_STATUS_KEY, queryFn: getBackupStatus });
  const [pending, setPending] = useState(null);
  const [revealedKey, setRevealedKey] = useState(null);

  const run = useCallback(
    async (name, action) => {
      setPending(name);
      try {
        const result = await action();
        await queryClient.invalidateQueries({ queryKey: queryKeys.backups });
        return result;
      } finally {
        setPending(null);
      }
    },
    [queryClient]
  );

  const createPreMigration = useCallback(
    () => run('preMigration', createPreMigrationBackup),
    [run]
  );

  const prepareRollback = useCallback(() => run('rollback', createRollbackBackup), [run]);

  // A lifted freeze re-enables writes everywhere, so every cached query may be stale.
  const liftFreeze = useCallback(async () => {
    setPending('liftFreeze');
    try {
      await liftWriteFreeze();
      await queryClient.invalidateQueries();
    } finally {
      setPending(null);
    }
  }, [queryClient]);

  const revealKey = useCallback(async () => {
    setPending('revealKey');
    try {
      const { key } = await revealBackupKey();
      setRevealedKey(key);
      return key;
    } finally {
      setPending(null);
    }
  }, []);

  const confirmKey = useCallback(async () => {
    await run('confirmKey', confirmBackupKey);
    setRevealedKey(null);
  }, [run]);

  return {
    status: statusQuery.data ?? null,
    statusError: statusQuery.error ?? null,
    pending,
    revealedKey,
    createPreMigration,
    prepareRollback,
    liftFreeze,
    revealKey,
    confirmKey,
  };
}
