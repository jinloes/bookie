import { useCallback, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { invoke, isTauri } from '@tauri-apps/api/core';
import { getRestoreStatus, restoreBackup } from '../api/index.js';

const COMPLETED_STATE = 'POST_START_VALIDATED';
const ROLLED_BACK_STATE = 'ROLLED_BACK';
const FAILURE_STATES = new Set(['FAILED']);
const POLL_ATTEMPTS = 240;
const POLL_INTERVAL_MS = 500;

const sleep = (milliseconds) =>
  new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });

function restoreFailure(status) {
  if (status.state === ROLLED_BACK_STATE) {
    return new Error(
      `Restore failed post-start validation; the retained database was reactivated. ${status.message || ''}`.trim()
    );
  }
  if (FAILURE_STATES.has(status.state)) {
    return new Error(status.message || `Restore entered unsafe state ${status.state}.`);
  }
  return null;
}

export async function waitForRestoreCompletion(
  restoreId,
  {
    loadStatus = getRestoreStatus,
    wait = sleep,
    attempts = POLL_ATTEMPTS,
    intervalMs = POLL_INTERVAL_MS,
  } = {}
) {
  let lastConnectionError;
  let rollbackRequired = false;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const status = await loadStatus();
      if (status?.restoreId && status.restoreId !== restoreId) {
        throw new Error(
          `Restore status belongs to ${status.restoreId}, not the staged restore ${restoreId}.`
        );
      }
      const failure = status ? restoreFailure(status) : null;
      if (failure) {
        throw failure;
      }
      if (status?.state === COMPLETED_STATE && status.restored && status.validated) {
        return status;
      }
      rollbackRequired ||= status?.state === 'ROLLBACK_REQUIRED';
      lastConnectionError = null;
    } catch (error) {
      if (
        error?.message?.startsWith('Restore status belongs to') ||
        error?.message?.startsWith('Restore failed post-start') ||
        error?.message?.startsWith('Restore entered unsafe')
      ) {
        throw error;
      }
      lastConnectionError = error;
    }
    if (attempt + 1 < attempts) {
      await wait(intervalMs);
    }
  }

  const detail = lastConnectionError?.message ? ` Last error: ${lastConnectionError.message}` : '';
  if (rollbackRequired) {
    throw new Error(
      `Post-start validation failed and rollback is required. Restart the backend again to reactivate the retained database.${detail}`
    );
  }
  throw new Error(`Timed out waiting for post-start restore validation.${detail}`);
}

export function useRestoreBackup() {
  const queryClient = useQueryClient();
  const [restoringId, setRestoringId] = useState(null);

  const confirmRestore = useCallback(
    async (restoreId) => {
      const completed = await waitForRestoreCompletion(restoreId);
      await queryClient.invalidateQueries();
      return completed;
    },
    [queryClient]
  );

  const runRestore = useCallback(
    async (fileId) => {
      setRestoringId(fileId);
      try {
        const staged = await restoreBackup(fileId);
        if (
          staged?.state !== 'VALIDATED' ||
          !staged.validated ||
          !staged.restartRequired ||
          !staged.restoreId
        ) {
          throw new Error('Restore was not safely validated and staged for activation.');
        }

        if (!isTauri()) {
          return { outcome: 'manual-restart', status: staged };
        }

        const restart = await invoke('restart_backend_after_restore', {
          restoreId: staged.restoreId,
        });
        if (!restart?.restarted) {
          if (restart?.manualRestartRequired) {
            return {
              outcome: 'manual-restart',
              status: staged,
              message: restart.message,
            };
          }
          throw new Error(restart?.message || 'The backend could not be restarted safely.');
        }

        const completed = await confirmRestore(staged.restoreId);
        return { outcome: 'restored', status: completed };
      } finally {
        setRestoringId(null);
      }
    },
    [confirmRestore]
  );

  return {
    confirmRestore,
    runRestore,
    restoringId,
    isRestoring: restoringId !== null,
  };
}
