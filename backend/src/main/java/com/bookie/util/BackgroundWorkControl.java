package com.bookie.util;

import java.time.Duration;

/** Controls durable background intake work from modules that cannot depend on intake. */
public interface BackgroundWorkControl {

  /**
   * Starts or extends processing of due intake work, recovering any work whose kickoff was lost.
   * Does nothing while writes are frozen.
   */
  void requestDrain();

  /**
   * Processes due and bound intake work, even while writes are frozen, until none remains.
   *
   * @return {@code false} if work still remained when the timeout elapsed
   */
  boolean drainUntilIdle(Duration timeout);

  /**
   * Returns unfinished jobs bound to engine executions to the unbound queue, so they run again
   * after a restore replaced the rows the engine knew about.
   */
  int releaseEngineBindings();
}
