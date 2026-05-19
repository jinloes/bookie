import { useCallback, useState } from 'react'

/**
 * useState that persists its value in sessionStorage for the lifetime of the
 * browser tab. Restores the stored value on mount so filters survive navigation.
 * The setter is wrapped in useCallback so memo'd children that receive it as a
 * prop don't re-render every time the parent renders.
 */
export function useSessionState(key, defaultValue) {
  const [value, setValue] = useState(() => {
    try {
      const stored = sessionStorage.getItem(key)
      return stored !== null ? JSON.parse(stored) : defaultValue
    } catch {
      return defaultValue
    }
  })

  const setStoredValue = useCallback((newValue) => {
    setValue(newValue)
    try {
      if (newValue === null || newValue === undefined) {
        sessionStorage.removeItem(key)
      } else {
        sessionStorage.setItem(key, JSON.stringify(newValue))
      }
    } catch {
      // sessionStorage can throw (private mode, quota) — value is still tracked in React state.
    }
  }, [key])

  return [value, setStoredValue]
}
