import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const mockIsTauri = vi.fn();
vi.mock('@tauri-apps/api/core', () => ({ isTauri: () => mockIsTauri() }));

const mockShellOpen = vi.fn();
vi.mock('@tauri-apps/plugin-shell', () => ({ open: (...args) => mockShellOpen(...args) }));

import { openExternalUrl } from './links.js';

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
