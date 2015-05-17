package com.artfulbits.junit;

import android.support.annotation.NonNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Utility class. Creates named threads with set {@link Thread#MIN_PRIORITY} and set daemon flag. */
public class NamedDaemons implements ThreadFactory {
  /** Singleton instance. */
  public static final NamedDaemons Instance = new NamedDaemons();

  /** Prefix for thread names. */
  private static final String PREFIX = "junit-thread-pool-";
  /** Unique ID counter. */
  private static final AtomicInteger _counter = new AtomicInteger();

  /** hidden constructor. */
  private NamedDaemons() {
    // only one instance allowed
  }

  /** {@inheritDoc} */
  @NonNull
  @Override
  public Thread newThread(@NonNull final Runnable r) {
    final Thread th = new Thread(r, PREFIX + _counter.getAndIncrement());
    th.setPriority(Thread.MIN_PRIORITY);
    th.setDaemon(true);

    return th;
  }
}
