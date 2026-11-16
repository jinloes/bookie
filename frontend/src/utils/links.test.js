import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as tauriCore from '@tauri-apps/api/core';
import * as shell from '@tauri-apps/plugin-shell';

vi.mock('@tauri-apps/api/core');
vi.mock('@tauri-apps/plugin-shell');
const mockIsTauri = vi.mocked(tauriCore.isTauri);
const mockShellOpen = vi.mocked(shell.open);

import { openExternalUrl, openOutlookConnect } from './links.js';

describe('openExternalUrl', () => {
  const originalOpen = window.open;

  beforeEach(() => {
    vi.clearAllMocks();
    window.open = vi.fn();
  });

  afterEach(() => {
    window.open = originalOpen;
  });

  it('does nothing when url is falsy', async () => {
    await openExternalUrl(null);
    expect(window.open).not.toHaveBeenCalled();
    expect(mockShellOpen).not.toHaveBeenCalled();
  });

  it('uses the shell plugin to open the url when running under Tauri', async () => {
    mockIsTauri.mockReturnValue(true);
    await openExternalUrl('https://onedrive.example.com/receipt.pdf');
    expect(mockShellOpen).toHaveBeenCalledWith('https://onedrive.example.com/receipt.pdf');
    expect(window.open).not.toHaveBeenCalled();
  });

  it('falls back to window.open when not running under Tauri', async () => {
    mockIsTauri.mockReturnValue(false);
    await openExternalUrl('https://onedrive.example.com/receipt.pdf');
    expect(window.open).toHaveBeenCalledWith(
      'https://onedrive.example.com/receipt.pdf',
      '_blank',
      'noopener,noreferrer'
    );
    expect(mockShellOpen).not.toHaveBeenCalled();
  });
});

describe('openOutlookConnect', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { assign: vi.fn(), replace: vi.fn() },
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    });
  });

  it('opens the desktop backend connect URL in the system browser under Tauri', async () => {
    mockIsTauri.mockReturnValue(true);
    const resolveApiUrl = vi
      .fn()
      .mockResolvedValue('http://localhost:48763/api/outlook/connect?client=desktop');

    await openOutlookConnect(resolveApiUrl);

    expect(resolveApiUrl).toHaveBeenCalledWith('/api/outlook/connect?client=desktop');
    expect(mockShellOpen).toHaveBeenCalledWith(
      'http://localhost:48763/api/outlook/connect?client=desktop'
    );
    expect(window.location.assign).not.toHaveBeenCalled();
  });

  it('navigates in-window in browser mode and honors replace', async () => {
    mockIsTauri.mockReturnValue(false);
    const resolveApiUrl = vi.fn();

    await openOutlookConnect(resolveApiUrl);
    expect(window.location.assign).toHaveBeenCalledWith('/api/outlook/connect');

    await openOutlookConnect(resolveApiUrl, { replace: true });
    expect(window.location.replace).toHaveBeenCalledWith('/api/outlook/connect');
    expect(resolveApiUrl).not.toHaveBeenCalled();
  });
});
