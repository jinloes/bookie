import React from 'react';
import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { usePageNavigationState } from './usePageNavigationState.js';

function renderNavigationHook(initialEntries) {
  return renderHook(() => usePageNavigationState(), {
    wrapper: ({ children }) => (
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/" element={children} />
        </Routes>
      </MemoryRouter>
    ),
  });
}

describe('usePageNavigationState', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(window.history, 'replaceState').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('surfaces pending prefill from location state and clears router state', () => {
    const prefill = { description: 'Imported item' };
    const { result } = renderNavigationHook([{ pathname: '/', state: { prefill } }]);

    expect(result.current.pendingPrefill).toEqual(prefill);
    expect(result.current.highlightId).toBeNull();
    expect(window.history.replaceState).toHaveBeenCalledWith({}, '');
  });

  it('surfaces highlight ids and clears them after the timeout', () => {
    const { result } = renderNavigationHook([{ pathname: '/', state: { highlightId: 42 } }]);

    expect(result.current.highlightId).toBe(42);
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(result.current.highlightId).toBeNull();
  });
});
