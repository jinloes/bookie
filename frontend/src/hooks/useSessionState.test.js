import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useSessionState } from './useSessionState.js'

beforeEach(() => sessionStorage.clear())

describe('useSessionState', () => {
  it('returns defaultValue when nothing is stored', () => {
    const { result } = renderHook(() => useSessionState('test.key', null))
    expect(result.current[0]).toBeNull()
  })

  it('persists value to sessionStorage on set', () => {
    const { result } = renderHook(() => useSessionState('test.key', null))
    act(() => result.current[1]('2023'))
    expect(result.current[0]).toBe('2023')
    expect(JSON.parse(sessionStorage.getItem('test.key'))).toBe('2023')
  })

  it('removes sessionStorage entry when set to null', () => {
    sessionStorage.setItem('test.key', JSON.stringify('2023'))
    const { result } = renderHook(() => useSessionState('test.key', null))
    act(() => result.current[1](null))
    expect(result.current[0]).toBeNull()
    expect(sessionStorage.getItem('test.key')).toBeNull()
  })

  it('restores stored value on mount', () => {
    sessionStorage.setItem('test.key', JSON.stringify('2022'))
    const { result } = renderHook(() => useSessionState('test.key', null))
    expect(result.current[0]).toBe('2022')
  })

  it('works with non-string values', () => {
    const { result } = renderHook(() => useSessionState('test.obj', {}))
    act(() => result.current[1]({ year: 2024, page: 3 }))
    expect(result.current[0]).toEqual({ year: 2024, page: 3 })
    expect(JSON.parse(sessionStorage.getItem('test.obj'))).toEqual({ year: 2024, page: 3 })
  })
})
