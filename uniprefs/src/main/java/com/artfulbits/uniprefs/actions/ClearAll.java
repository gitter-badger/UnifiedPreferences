package com.artfulbits.uniprefs.actions;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.artfulbits.uniprefs.PreferencesUnified;

import java.util.Map;

/** Clean the storage. */
public class ClearAll implements PreferencesUnified.Action {
  /** {@inheritDoc} */
  @Override
  public final int getType() {
    return PreferencesUnified.Factory.TYPE_CLEAR;
  }

  /** {@inheritDoc} */
  @Override
  public void apply(final SharedPreferences.Editor editor, @NonNull final Map<String, Object> storage) {
    storage.clear();
  }
}
