import { isTauri } from '@tauri-apps/api/core';

const STORE_FILENAME = 'settings.json';

let storePromise = null;

/**
 * Lazily loads (and caches) the on-disk Tauri store used to persist lightweight UI
 * preferences (e.g. page filters) across full app restarts. Returns null when not running
 * under Tauri (browser dev/build) or if the store plugin fails to load for any reason —
 * callers should treat that as "persistence unavailable" and fall back to in-memory/session
 * storage only.
 */
function loadStore() {
  if (!isTauri()) {
    return Promise.resolve(null);
  }
  if (!storePromise) {
    storePromise = import('@tauri-apps/plugin-store')
      .then(({ load }) => load(STORE_FILENAME, { autoSave: true }))
      .catch(() => null);
  }
  return storePromise;
}

/** Reads a previously persisted value, or undefined if none exists / persistence is unavailable. */
export async function getPersisted(key) {
  const store = await loadStore();
  if (!store) {
    return undefined;
  }
  try {
    return await store.get(key);
  } catch {
    return undefined;
  }
}

/** Best-effort, fire-and-forget write of a value to the persisted store. */
export async function setPersisted(key, value) {
  const store = await loadStore();
  if (!store) {
    return;
  }
  try {
    if (value === null || value === undefined) {
      await store.delete(key);
    } else {
      await store.set(key, value);
    }
  } catch {
    // Persistence is best-effort — the in-memory/session value is still correct either way.
  }
}
