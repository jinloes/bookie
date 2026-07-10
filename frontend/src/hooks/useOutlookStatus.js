import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getOutlookStatus } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

const APP_BOOT_AT = Date.now();
const DEFAULT_STARTUP_GRACE_MS = 12000;

export function useOutlookStatus({ refreshKey, startupGraceMs = DEFAULT_STARTUP_GRACE_MS } = {}) {
  const [now, setNow] = useState(() => Date.now());
  const withinStartupGrace = now - APP_BOOT_AT < startupGraceMs;

  useEffect(() => {
    if (!withinStartupGrace) {
      return undefined;
    }
    const remaining = Math.max(0, startupGraceMs - (Date.now() - APP_BOOT_AT));
    const timer = window.setTimeout(() => setNow(Date.now()), remaining);
    return () => window.clearTimeout(timer);
  }, [startupGraceMs, withinStartupGrace]);

  const query = useQuery({
    queryKey:
      refreshKey === undefined ? queryKeys.outlookStatus : [...queryKeys.outlookStatus, refreshKey],
    queryFn: getOutlookStatus,
    refetchInterval: (q) => (q.state.data?.connected === true ? false : 2000),
  });

  const connected = query.data?.connected === true;
  const hasResolvedBoolean = typeof query.data?.connected === 'boolean';
  const checking =
    !connected &&
    (query.isLoading || query.isFetching || !hasResolvedBoolean || (withinStartupGrace && !query.isError));

  const status = connected ? 'connected' : checking ? 'checking' : 'disconnected';

  return {
    ...query,
    connected,
    isChecking: status === 'checking',
    isDisconnected: status === 'disconnected',
    status,
  };
}
