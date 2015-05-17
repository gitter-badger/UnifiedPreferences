package com.artfulbits.uniprefs.actions;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.artfulbits.uniprefs.PreferencesUnified;

import java.util.Map;

/** Remove value from the storage. */
public class RemoveValue implements PreferencesUnified.Action, PreferencesUnified.SupportsKey {
  /** Key name. */
  public final String Key;

  /**
   * Remove item from preferences.
   *
   * @param key key name
   */
  public RemoveValue(final String key) {
    Key = key;
  }

  /** {@inheritDoc} */
  @Override
  public final int getType() {
    return PreferencesUnified.Factory.TYPE_REMOVE;
  }

  /** {@inheritDoc} */
  @Override
  public String getKey() {
    return Key;
  }

  /** {@inheritDoc} */
  @Override
  public void apply(final SharedPreferences.Editor editor, @NonNull final Map<String, Object> storage) {
    storage.remove(Key);
  }
}
