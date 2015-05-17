package com.artfulbits.uniprefs;

import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Internal memory storage for shared preferences. <p> <i>Note: instance of object is used for save in background
 * synchronization.</i> </p>
 */
/* package */ final class Storage {
  /**
   * Sync object for data modifications. Used: {@link SharedPreferences.Editor#commit()}, {@link
   * SharedPreferences.Editor#apply()} .
   */
  public final Object ModifySync = new Object();
  /** Memory storage. Guarded by ModifySync object. */
  public final Map<String, Object> Objects = new HashMap<>();
  /** Modification version of the objects map. */
  public final AtomicInteger Version = new AtomicInteger();
  /**
   * Shutdown hook that guaranty us that data changes will be saved before apps will be shutdown. Value update guarded
   * by 'this'.
   */
  @Nullable
  public Thread Shutdown;
}
