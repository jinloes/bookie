import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';

const HIGHLIGHT_MS = 3000;

export function usePageNavigationState() {
  const location = useLocation();
  const [pendingPrefill, setPendingPrefill] = useState(null);
  const [highlightId, setHighlightId] = useState(null);
  const highlightTimerRef = useRef(null);

  useEffect(() => {
    const { prefill, highlightId: nextHighlightId } = location.state || {};

    if (nextHighlightId) {
      setHighlightId(nextHighlightId);
      window.history.replaceState({}, '');
      highlightTimerRef.current = setTimeout(() => setHighlightId(null), HIGHLIGHT_MS);
    } else if (prefill) {
      setPendingPrefill(prefill);
      window.history.replaceState({}, '');
    }

    return () => {
      if (highlightTimerRef.current) {
        clearTimeout(highlightTimerRef.current);
        highlightTimerRef.current = null;
      }
    };
  }, [location.state]);

  const clearPendingPrefill = useCallback(() => {
    setPendingPrefill(null);
  }, []);

  return { pendingPrefill, clearPendingPrefill, highlightId };
}
