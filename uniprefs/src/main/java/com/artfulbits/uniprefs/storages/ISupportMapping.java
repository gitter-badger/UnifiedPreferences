package com.artfulbits.uniprefs.storages;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Map;

/** Implement interface if storage supports mapping of keys. */
@SuppressWarnings("unused")
public interface ISupportMapping<T> {
  /**
   * Sets mapping for keys.
   *
   * @param mapping instance of the mapping map.
   * @return this instance of implementer for chained configurations.
   */
  @NonNull
  T setMapping(@Nullable final Map<String, String> mapping);
}
