package com.artfulbits.uniprefs.toolbox;

import android.support.annotation.NonNull;

import com.artfulbits.uniprefs.PreferencesUnified;

import java.util.HashMap;
import java.util.Map;

/** Equal to serialization to '/dev/null'. Empty stub for preventing class failing. */
public final class NullSerialization implements PreferencesUnified.Serialization {
  /** SINGLETON. Instance of the NULL serializer. */
  public static final PreferencesUnified.Serialization Instance = new NullSerialization();

  /** Hidden constructor. */
  private NullSerialization() {
    // do nothing
  }

  /** {@inheritDoc} */
  @NonNull
  @Override
  public byte[] serialize(final Map<String, ?> data) {
    return new byte[]{};
  }

  /** {@inheritDoc} */
  @NonNull
  @Override
  public Map<String, Object> deserialize(final byte[] data) {
    return new HashMap<>();
  }
}
