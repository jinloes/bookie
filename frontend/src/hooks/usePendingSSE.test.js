import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));
vi.mock('../api/client.js');

import { notifications } from '@mantine/notifications';
import { resolveApiUrl } from '../api/client.js';
import { usePendingSSE } from './usePendingSSE.js';

class MockEventSource {
  static instances = [];
  constructor(url) {
    this.url = url;
    this.listeners = {};
    this.closed = false;
    MockEventSource.instances.push(this);
  }
  addEventListener(type, handler) {
    this.listeners[type] = handler;
  }
  close() {
    this.closed = true;
  }
  emit(type, data) {
    this.listeners[type]?.({ data: JSON.stringify(data) });
  }
  emitRaw(type, raw) {
    this.listeners[type]?.({ data: raw });
  }
}

beforeEach(() => {
  MockEventSource.instances = [];
  global.EventSource = MockEventSource;
  vi.clearAllMocks();
});

afterEach(() => {
  delete global.EventSource;
});

describe('usePendingSSE', () => {
  it('calls onUpdate when event arrives', () => {
    const onUpdate = vi.fn();
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate }));

    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'PROCESSING' }));

    expect(onUpdate).toHaveBeenCalledWith({ state: 'PROCESSING' });
  });

  it('calls onUpdate when event passes filter', () => {
    const onUpdate = vi.fn();
    renderHook(() =>
      usePendingSSE({
        filter: (d) => d.type === 'RECEIPT',
        notification: {},
        activeTab: 'expenses',
        onUpdate,
      })
    );

    act(() =>
      MockEventSource.instances[0].emit('inbox-updated', { type: 'RECEIPT', state: 'READY' })
    );

    expect(onUpdate).toHaveBeenCalled();
  });

  it('skips onUpdate when event fails filter', () => {
    const onUpdate = vi.fn();
    renderHook(() =>
      usePendingSSE({
        filter: (d) => d.type === 'RECEIPT',
        notification: {},
        activeTab: 'expenses',
        onUpdate,
      })
    );

    act(() =>
      MockEventSource.instances[0].emit('inbox-updated', { type: 'EMAIL', state: 'READY' })
    );

    expect(onUpdate).not.toHaveBeenCalled();
  });

  it('shows notification when READY and user is not on pending tab', () => {
    const onUpdate = vi.fn();
    renderHook(() =>
      usePendingSSE({
        notification: { message: 'Done!' },
        activeTab: 'expenses',
        onUpdate,
      })
    );

    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'READY' }));

    expect(notifications.show).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'Done!', autoClose: 6000 })
    );
  });

  it('suppresses notification when user is already on pending tab', () => {
    const onUpdate = vi.fn();
    renderHook(() =>
      usePendingSSE({
        notification: { message: 'Done!' },
        activeTab: 'pending',
        onUpdate,
      })
    );

    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'READY' }));

    expect(notifications.show).not.toHaveBeenCalled();
  });

  it('does not show notification for non-READY status', () => {
    const onUpdate = vi.fn();
    renderHook(() =>
      usePendingSSE({ notification: { message: 'x' }, activeTab: 'expenses', onUpdate })
    );

    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'PROCESSING' }));

    expect(notifications.show).not.toHaveBeenCalled();
  });

  it('ignores malformed JSON', () => {
    const onUpdate = vi.fn();
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate }));

    act(() => MockEventSource.instances[0].emitRaw('inbox-updated', 'not-json'));

    expect(onUpdate).not.toHaveBeenCalled();
  });

  it('closes EventSource on unmount', () => {
    const onUpdate = vi.fn();
    const { unmount } = renderHook(() =>
      usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate })
    );

    unmount();

    expect(MockEventSource.instances[0].closed).toBe(true);
  });

  it('does not close EventSource on SSE error so browser can auto-reconnect', () => {
    const onUpdate = vi.fn();
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate }));
    const es = MockEventSource.instances[0];

    act(() => es.listeners['error']?.());

    expect(es.closed).toBe(false);
  });

  it('calls the latest onUpdate callback even after it changes between renders', () => {
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = renderHook(
      ({ cb }) => usePendingSSE({ notification: {}, activeTab: 'expenses', onUpdate: cb }),
      { initialProps: { cb: first } }
    );
    rerender({ cb: second });

    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'PROCESSING' }));

    expect(second).toHaveBeenCalled();
    expect(first).not.toHaveBeenCalled();
  });

  it('uses the latest filter on subsequent events', () => {
    const onUpdate = vi.fn();
    const { rerender } = renderHook(
      ({ f }) => usePendingSSE({ filter: f, notification: {}, activeTab: 'expenses', onUpdate }),
      { initialProps: { f: () => false } }
    );

    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'READY' }));
    expect(onUpdate).not.toHaveBeenCalled();

    rerender({ f: () => true });
    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'READY' }));
    expect(onUpdate).toHaveBeenCalledTimes(1);
  });

  it('uses the latest notification payload on subsequent READY events', () => {
    const onUpdate = vi.fn();
    const { rerender } = renderHook(
      ({ n }) => usePendingSSE({ notification: n, activeTab: 'expenses', onUpdate }),
      { initialProps: { n: { message: 'first' } } }
    );
    rerender({ n: { message: 'second' } });

    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'READY' }));

    expect(notifications.show).toHaveBeenCalledWith(expect.objectContaining({ message: 'second' }));
  });

  it('does not throw when onUpdate is omitted', () => {
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses' }));

    act(() => MockEventSource.instances[0].emit('inbox-updated', { state: 'PROCESSING' }));

    expect(notifications.show).not.toHaveBeenCalled();
  });

  it('subscribes to the unified inbox event stream', () => {
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses' }));

    expect(MockEventSource.instances[0].url).toBe('/api/v2/inbox/events');
    expect(Object.keys(MockEventSource.instances[0].listeners)).toEqual(['inbox-updated']);
  });

  it('invalidates the inbox, ledger transactions and reports when an event arrives', () => {
    const queryClient = { invalidateQueries: vi.fn() };
    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses', queryClient }));

    act(() => MockEventSource.instances[0].emit('inbox-updated', { id: 4, state: 'READY' }));

    expect(queryClient.invalidateQueries.mock.calls.map(([call]) => call.queryKey)).toEqual([
      ['inbox'],
      ['transactions'],
      ['reports'],
    ]);
  });
});

describe('usePendingSSE in a production build', () => {
  beforeEach(() => {
    vi.stubEnv('DEV', false);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('subscribes to the event stream on the resolved backend origin', async () => {
    vi.mocked(resolveApiUrl).mockResolvedValue('http://localhost:48763/api/v2/inbox/events');

    renderHook(() => usePendingSSE({ notification: {}, activeTab: 'expenses' }));

    await waitFor(() => expect(MockEventSource.instances).toHaveLength(1));
    expect(resolveApiUrl).toHaveBeenCalledWith('/api/v2/inbox/events');
    expect(MockEventSource.instances[0].url).toBe('http://localhost:48763/api/v2/inbox/events');
  });

  it('closes the stream on unmount after the origin resolves', async () => {
    vi.mocked(resolveApiUrl).mockResolvedValue('http://localhost:48763/api/v2/inbox/events');
    const { unmount } = renderHook(() =>
      usePendingSSE({ notification: {}, activeTab: 'expenses' })
    );
    await waitFor(() => expect(MockEventSource.instances).toHaveLength(1));

    unmount();

    expect(MockEventSource.instances[0].closed).toBe(true);
  });

  it('never opens a stream when unmounted before the origin resolves', async () => {
    let resolveUrl;
    vi.mocked(resolveApiUrl).mockReturnValue(
      new Promise((resolve) => {
        resolveUrl = resolve;
      })
    );
    const { unmount } = renderHook(() =>
      usePendingSSE({ notification: {}, activeTab: 'expenses' })
    );

    unmount();
    await act(async () => {
      resolveUrl('http://localhost:48763/api/v2/inbox/events');
    });

    expect(MockEventSource.instances).toHaveLength(0);
  });
});
