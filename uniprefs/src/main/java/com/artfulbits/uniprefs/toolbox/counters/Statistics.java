package com.artfulbits.uniprefs.toolbox.counters;

import java.util.concurrent.atomic.AtomicInteger;

/** Statistics collecting class. */
public final class Statistics {
  /** Quantity of done save operations. */
  public final AtomicInteger DoneSaves = new AtomicInteger();
  /** Quantity of rescheduled save operations. */
  public final AtomicInteger RescheduledSaves = new AtomicInteger();
  /** Quantity of skipped save operations. */
  public final AtomicInteger SkippedSaves = new AtomicInteger();
  /** Quantity of set into queue save operations. */
  public final AtomicInteger SetSaves = new AtomicInteger();
  /** Quantity of executed memory commits. */
  public final AtomicInteger MemoryCommits = new AtomicInteger();
  /** Quantity of executed apply(). */
  public final AtomicInteger Applies = new AtomicInteger();
  /** Quantity of executed commit(). */
  public final AtomicInteger Commits = new AtomicInteger();
}
