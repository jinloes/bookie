import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }))

import { notifications } from '@mantine/notifications'
import { usePendingSSE } from './usePendingSSE.js'

class MockEventSource {
  static instances = []
  constructor() {
    this.listeners = {}
    this.closed = false
    MockEventSource.instances.push(this)
  }
  addEventListener(type, handler) { this.listeners[type] = handler }
  close() { this.closed = true }
  emit(type, data) { this.listeners[type]?.({ data: JSON.stringify(data) }) }
  emitRaw(type, raw) { this.listeners[type]?.({ data: raw }) }
}

beforeEach(() => {
  MockEventSource.instances = []
  global.EventSource = MockEventSource
  vi.clearAllMocks()
})

afterEach(() => {
  delete global.EventSource
})

describe('usePendingSSE', () => {
  it('calls onUpdate when event arrives', () => {
    const onUpdate = vi.fn()
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate }))

    act(() => MockEventSource.instances[0].emit('pending-updated', { status: 'PROCESSING' }))

    expect(onUpdate).toHaveBeenCalledWith({ status: 'PROCESSING' })
  })

  it('calls onUpdate when event passes filter', () => {
    const onUpdate = vi.fn()
    renderHook(() => usePendingSSE({
      filter: d => d.type === 'RECEIPT',
      notification: {},
      activeTab: 'expenses',
      onUpdate,
    }))

    act(() => MockEventSource.instances[0].emit('pending-updated', { type: 'RECEIPT', status: 'READY' }))

    expect(onUpdate).toHaveBeenCalled()
  })

  it('skips onUpdate when event fails filter', () => {
    const onUpdate = vi.fn()
    renderHook(() => usePendingSSE({
      filter: d => d.type === 'RECEIPT',
      notification: {},
      activeTab: 'expenses',
      onUpdate,
    }))

    act(() => MockEventSource.instances[0].emit('pending-updated', { type: 'EMAIL', status: 'READY' }))

    expect(onUpdate).not.toHaveBeenCalled()
  })

  it('shows notification when READY and user is not on pending tab', () => {
    const onUpdate = vi.fn()
    renderHook(() => usePendingSSE({
      notification: { message: 'Done!' },
      activeTab: 'expenses',
      onUpdate,
    }))

    act(() => MockEventSource.instances[0].emit('pending-updated', { status: 'READY' }))

    expect(notifications.show).toHaveBeenCalledWith(expect.objectContaining({ message: 'Done!', autoClose: 6000 }))
  })

  it('suppresses notification when user is already on pending tab', () => {
    const onUpdate = vi.fn()
    renderHook(() => usePendingSSE({
      notification: { message: 'Done!' },
      activeTab: 'pending',
      onUpdate,
    }))

    act(() => MockEventSource.instances[0].emit('pending-updated', { status: 'READY' }))

    expect(notifications.show).not.toHaveBeenCalled()
  })

  it('does not show notification for non-READY status', () => {
    const onUpdate = vi.fn()
    renderHook(() => usePendingSSE({ notification: { message: 'x' }, activeTab: 'expenses', onUpdate }))

    act(() => MockEventSource.instances[0].emit('pending-updated', { status: 'PROCESSING' }))

    expect(notifications.show).not.toHaveBeenCalled()
  })

  it('ignores malformed JSON', () => {
    const onUpdate = vi.fn()
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate }))

    act(() => MockEventSource.instances[0].emitRaw('pending-updated', 'not-json'))

    expect(onUpdate).not.toHaveBeenCalled()
  })

  it('closes EventSource on unmount', () => {
    const onUpdate = vi.fn()
    const { unmount } = renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate }))

    unmount()

    expect(MockEventSource.instances[0].closed).toBe(true)
  })

  it('does not close EventSource on SSE error so browser can auto-reconnect', () => {
    const onUpdate = vi.fn()
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate }))
    const es = MockEventSource.instances[0]

    act(() => es.listeners['error']?.())

    expect(es.closed).toBe(false)
  })

  it('calls the latest onUpdate callback even after it changes between renders', () => {
    const first = vi.fn()
    const second = vi.fn()
    const { rerender } = renderHook(
      ({ cb }) => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate: cb }),
      { initialProps: { cb: first } }
    )
    rerender({ cb: second })

    act(() => MockEventSource.instances[0].emit('pending-updated', { status: 'PROCESSING' }))

    expect(second).toHaveBeenCalled()
    expect(first).not.toHaveBeenCalled()
  })
})