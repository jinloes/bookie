import { useCallback, useEffect, useRef, useState } from 'react';
import { getPersisted, setPersisted } from '../utils/persistentStore.js';

/**
 * useState that persists its value in sessionStorage for the lifetime of the browser tab (or,
 * under Tauri, the current app run — sessionStorage survives hide-to-tray, only a full
 * quit/relaunch clears it). Restores the stored value on mount so filters survive navigation.
 *
 * Under Tauri, values are also mirrored to an on-disk store (see utils/persistentStore.js), so
 * a value set in a previous app run is restored once sessionStorage comes up empty after a
 * full restart. This is best-effort: if the store is unavailable (e.g. running in a plain
 * browser), the hook behaves exactly as plain sessionStorage-backed state.
 *
 * The setter is wrapped in useCallback so memo'd children that receive it as a
 * prop don't re-render every time the parent renders.
 */
export function useSessionState(key, defaultValue) {
  const [value, setValue] = useState(() => {
    try {
      const stored = sessionStorage.getItem(key);
      return stored !== null ? JSON.parse(stored) : defaultValue;
    } catch {
      return defaultValue;
    }
  });

  // Tracks whether sessionStorage already had a value at mount time — if so, this app run has
  // already established a value for `key` and we shouldn't clobber it with a stale persisted
  // one that arrives asynchronously afterward.
  const hadSessionValueRef = useRef(sessionStorage.getItem(key) !== null);

  useEffect(() => {
    if (hadSessionValueRef.current) {
      return;
    }
    let cancelled = false;
    getPersisted(key).then((persisted) => {
      if (!cancelled && persisted !== undefined) {
        setValue(persisted);
      }
    });
    return () => {
      cancelled = true;
    };
    // Only ever run once per mount — `key` is expected to be stable for a given hook instance.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const setStoredValue = useCallback(
    (newValue) => {
      setValue(newValue);
      hadSessionValueRef.current = true;
      try {
        if (newValue === null || newValue === undefined) {
          sessionStorage.removeItem(key);
        } else {
          sessionStorage.setItem(key, JSON.stringify(newValue));
        }
      } catch {
        // sessionStorage can throw (private mode, quota) — value is still tracked in React state.
      }
      setPersisted(key, newValue);
    },
    [key]
  );

  return [value, setStoredValue];
}
