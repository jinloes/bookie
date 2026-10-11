package com.bookie.util;

/**
 * Whether the cutover write freeze is on. While frozen, background sources of new writes (intake
 * kickoffs, the automatic relay and auto-import) do nothing.
 */
public interface WriteFreezeState {

  boolean isFrozen();
}
