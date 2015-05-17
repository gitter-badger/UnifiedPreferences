package com.artfulbits.uniprefs.actions;

import android.support.annotation.NonNull;

import com.artfulbits.uniprefs.PreferencesUnified;

/** Default factory which creates on request memory storage modification actions. */
public final class FactoryImpl implements PreferencesUnified.Factory {

  /** SINGLETON. instance of the default memory modification actions factory. */
  public static final FactoryImpl Instance = new FactoryImpl();

  /** Hidden constructor. */
  private FactoryImpl() {
    // do nothing
  }

  /** {@inheritDoc} */
  @NonNull
  @Override
  public PreferencesUnified.Action action(final int type, final String key, final Object value) {
    if (TYPE_CLEAR == type) {
      return new ClearAll();
    } else if (TYPE_PUT == type) {
      return new PutValue(key, value);
    } else if (TYPE_REMOVE == type) {
      return new RemoveValue(key);
    }

    throw new IllegalArgumentException("Unknown action type. Type should be one from the list: TYPE_CLEAR, " +
        "TYPE_PUT, TYPE_REMOVE.");
  }
}
