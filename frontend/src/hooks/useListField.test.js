import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useListField } from './useListField.js'

describe('useListField', () => {
  it('adds trimmed item to the list', () => {
    const setForm = vi.fn()
    const { result } = renderHook(() => useListField('aliases', { aliases: ['existing'] }, setForm))

    act(() => result.current.setInput('  new  '))
    act(() => result.current.add())

    const updater = setForm.mock.calls[0][0]
    expect(updater({ aliases: ['existing'] })).toEqual({ aliases: ['existing', 'new'] })
  })

  it('clears input after successful add', () => {
    const setForm = vi.fn()
    const { result } = renderHook(() => useListField('aliases', { aliases: [] }, setForm))

    act(() => result.current.setInput('bar'))
    act(() => result.current.add())

    expect(result.current.input).toBe('')
  })

  it('does not add when input is blank', () => {
    const setForm = vi.fn()
    const { result } = renderHook(() => useListField('aliases', { aliases: [] }, setForm))

    act(() => result.current.add())

    expect(setForm).not.toHaveBeenCalled()
  })

  it('does not add duplicate item', () => {
    const setForm = vi.fn()
    const { result } = renderHook(() => useListField('aliases', { aliases: ['foo'] }, setForm))

    act(() => result.current.setInput('foo'))
    act(() => result.current.add())

    expect(setForm).not.toHaveBeenCalled()
  })

  it('removes item from list', () => {
    const setForm = vi.fn()
    const { result } = renderHook(() => useListField('aliases', { aliases: ['a', 'b'] }, setForm))

    act(() => result.current.remove('a'))

    const updater = setForm.mock.calls[0][0]
    expect(updater({ aliases: ['a', 'b'] })).toEqual({ aliases: ['b'] })
  })

  it('reset clears input without touching the list', () => {
    const setForm = vi.fn()
    const { result } = renderHook(() => useListField('aliases', { aliases: [] }, setForm))

    act(() => result.current.setInput('something'))
    act(() => result.current.reset())

    expect(result.current.input).toBe('')
    expect(setForm).not.toHaveBeenCalled()
  })

  it('handles missing list key gracefully on add', () => {
    const setForm = vi.fn()
    const { result } = renderHook(() => useListField('aliases', {}, setForm))

    act(() => result.current.setInput('x'))
    act(() => result.current.add())

    const updater = setForm.mock.calls[0][0]
    expect(updater({})).toEqual({ aliases: ['x'] })
  })

  it('handles missing list key gracefully on remove', () => {
    const setForm = vi.fn()
    const { result } = renderHook(() => useListField('aliases', {}, setForm))

    act(() => result.current.remove('x'))

    const updater = setForm.mock.calls[0][0]
    expect(updater({})).toEqual({ aliases: [] })
  })
})