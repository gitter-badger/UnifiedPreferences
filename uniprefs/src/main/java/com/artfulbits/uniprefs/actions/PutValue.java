package com.artfulbits.uniprefs.actions;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.artfulbits.uniprefs.PreferencesUnified;

import java.util.Map;

/** Put value into storage. */
public class PutValue implements PreferencesUnified.Action, PreferencesUnified.SupportsKey, PreferencesUnified.SupportsValue {
  /** Key name. */
  public final String Key;
  /** Updated value. */
  public final Object Value;

  /**
   * Add/Edit item in preferences.
   *
   * @param key key name.
   * @param value new value.
   */
  public PutValue(final String key, final Object value) {
    Key = key;
    Value = value;
  }

  /** {@inheritDoc} */
  @Override
  public final int getType() {
    return PreferencesUnified.Factory.TYPE_PUT;
  }

  /** {@inheritDoc} */
  @Override
  public String getKey() {
    return Key;
  }

  /** {@inheritDoc} */
  @Override
  public final Object getValue() {
    return Value;
  }

  /** {@inheritDoc} */
  @Override
  public void apply(final SharedPreferences.Editor editor, @NonNull final Map<String, Object> storage) {
    storage.put(Key, Value);
  }

}
