package com.artfulbits.uniprefs.toolbox.counters;

import java.util.concurrent.atomic.AtomicInteger;

/** Diagnostics statistics. */
public final class DbStatistics {
  /** Quantity of created commits objects. */
  public final AtomicInteger CommitsCreated = new AtomicInteger();
  /** Quantity of the DB updates. */
  public final AtomicInteger DbUpdates = new AtomicInteger();
  /** Quantity of the Serialize method calls. */
  public final AtomicInteger Serialize = new AtomicInteger();
}
