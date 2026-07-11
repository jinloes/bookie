import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockIsTauri = vi.fn();
vi.mock('@tauri-apps/api/core', () => ({ isTauri: () => mockIsTauri() }));

const mockLoad = vi.fn();
vi.mock('@tauri-apps/plugin-store', () => ({ load: (...args) => mockLoad(...args) }));

describe('persistentStore', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
  });

  it('getPersisted returns undefined without ever loading the store when not running under Tauri', async () => {
    mockIsTauri.mockReturnValue(false);
    const { getPersisted } = await import('./persistentStore.js');

    const value = await getPersisted('some.key');

    expect(value).toBeUndefined();
    expect(mockLoad).not.toHaveBeenCalled();
  });

  it('setPersisted is a no-op without ever loading the store when not running under Tauri', async () => {
    mockIsTauri.mockReturnValue(false);
    const { setPersisted } = await import('./persistentStore.js');

    await setPersisted('some.key', 'value');

    expect(mockLoad).not.toHaveBeenCalled();
  });

  it('getPersisted reads through to the loaded store when running under Tauri', async () => {
    mockIsTauri.mockReturnValue(true);
    const mockStore = {
      get: vi.fn().mockResolvedValue('stored-value'),
      set: vi.fn(),
      delete: vi.fn(),
    };
    mockLoad.mockResolvedValue(mockStore);
    const { getPersisted } = await import('./persistentStore.js');

    const value = await getPersisted('some.key');

    expect(value).toBe('stored-value');
    expect(mockStore.get).toHaveBeenCalledWith('some.key');
  });

  it('setPersisted writes through to the loaded store when running under Tauri', async () => {
    mockIsTauri.mockReturnValue(true);
    const mockStore = { get: vi.fn(), set: vi.fn().mockResolvedValue(undefined), delete: vi.fn() };
    mockLoad.mockResolvedValue(mockStore);
    const { setPersisted } = await import('./persistentStore.js');

    await setPersisted('some.key', 'value');

    expect(mockStore.set).toHaveBeenCalledWith('some.key', 'value');
  });

  it('setPersisted deletes the key from the store when value is null', async () => {
    mockIsTauri.mockReturnValue(true);
    const mockStore = { get: vi.fn(), set: vi.fn(), delete: vi.fn().mockResolvedValue(undefined) };
    mockLoad.mockResolvedValue(mockStore);
    const { setPersisted } = await import('./persistentStore.js');

    await setPersisted('some.key', null);

    expect(mockStore.delete).toHaveBeenCalledWith('some.key');
    expect(mockStore.set).not.toHaveBeenCalled();
  });

  it('getPersisted returns undefined if the store fails to load', async () => {
    mockIsTauri.mockReturnValue(true);
    mockLoad.mockRejectedValue(new Error('boom'));
    const { getPersisted } = await import('./persistentStore.js');

    const value = await getPersisted('some.key');

    expect(value).toBeUndefined();
  });
});
