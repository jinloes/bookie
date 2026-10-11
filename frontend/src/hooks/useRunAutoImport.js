import { useCallback, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { runAutoImport } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

/** Runs Outlook and receipt auto-import now, since on-demand mode has no import timer. */
export function useRunAutoImport() {
  const queryClient = useQueryClient();
  const [running, setRunning] = useState(false);

  const run = useCallback(async () => {
    setRunning(true);
    try {
      const { queued } = await runAutoImport();
      await queryClient.invalidateQueries({ queryKey: queryKeys.inbox });
      return queued;
    } finally {
      setRunning(false);
    }
  }, [queryClient]);

  return { run, running };
}
