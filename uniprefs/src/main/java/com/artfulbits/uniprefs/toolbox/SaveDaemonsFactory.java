package com.artfulbits.uniprefs.toolbox;

import android.support.annotation.NonNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Create thread with low priority, daemon flag and specific easy recognizable name. */
public final class SaveDaemonsFactory implements ThreadFactory {
  /** Single instance of class. */
  public static final ThreadFactory Instance = new SaveDaemonsFactory();
  /** Counter of created threads, used for getting unique ID. */
  private static final AtomicInteger _counter = new AtomicInteger();

  /** hidden constructor. */
  private SaveDaemonsFactory() {
    // only one instance allowed
  }

  /** {@inheritDoc} */
  @Override
  @NonNull
  public Thread newThread(@NonNull final Runnable r) {
    final int index = _counter.getAndIncrement();
    final Thread th = new Thread(r, "uniprefs-thread-pool-" + index);

    // set default priority to minimum, to economy CPU
    th.setPriority(Thread.MIN_PRIORITY);

    return th;
  }
}
