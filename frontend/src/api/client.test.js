import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

vi.mock('@tauri-apps/api/core', () => ({ invoke: vi.fn() }));

import { invoke } from '@tauri-apps/api/core';

// client.js caches the resolved origin per module instance, so each test loads a fresh copy.
async function loadResolveApiUrl() {
  const { resolveApiUrl } = await import('./client.js');
  return resolveApiUrl;
}

beforeEach(() => {
  vi.resetModules();
  vi.mocked(invoke).mockReset();
});

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('resolveApiUrl', () => {
  it('keeps API paths relative in dev so the Vite proxy serves them', async () => {
    const resolveApiUrl = await loadResolveApiUrl();

    await expect(resolveApiUrl('/api/health')).resolves.toBe('/api/health');
    expect(invoke).not.toHaveBeenCalled();
  });

  it('prefixes the backend URL reported by Tauri in production builds', async () => {
    vi.stubEnv('DEV', false);
    vi.mocked(invoke).mockResolvedValue('http://localhost:51234');
    const resolveApiUrl = await loadResolveApiUrl();

    await expect(resolveApiUrl('/api/health')).resolves.toBe('http://localhost:51234/api/health');
    expect(invoke).toHaveBeenCalledWith('get_backend_url');
  });

  it('falls back to the default backend port when the Tauri command fails', async () => {
    vi.stubEnv('DEV', false);
    vi.mocked(invoke).mockRejectedValue(new Error('not running inside Tauri'));
    const resolveApiUrl = await loadResolveApiUrl();

    await expect(resolveApiUrl('/api/v2/inbox/events')).resolves.toBe(
      'http://localhost:48763/api/v2/inbox/events'
    );
  });
});
