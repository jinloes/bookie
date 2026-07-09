import { useEffect, useState } from 'react';

export function useBackendHealth(intervalMs = 5000) {
  const [status, setStatus] = useState('unknown'); // unknown, up, down

  useEffect(() => {
    const checkHealth = async () => {
      try {
        const response = await fetch('/api/health', { method: 'GET' });
        setStatus(response.ok ? 'up' : 'down');
      } catch {
        setStatus('down');
      }
    };

    checkHealth();
    const interval = setInterval(checkHealth, intervalMs);
    return () => clearInterval(interval);
  }, [intervalMs]);

  return status;
}
