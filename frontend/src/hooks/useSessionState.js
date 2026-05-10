import { useState } from 'react'

/**
 * useState that persists its value in sessionStorage for the lifetime of the
 * browser tab. Restores the stored value on mount so filters survive navigation.
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

  const setStoredValue = (newValue) => {
    setValue(newValue)
    try {
      if (newValue === null || newValue === undefined) {
        sessionStorage.removeItem(key)
      } else {
        sessionStorage.setItem(key, JSON.stringify(newValue))
      }
    } catch {}
  }

  return [value, setStoredValue]
}
