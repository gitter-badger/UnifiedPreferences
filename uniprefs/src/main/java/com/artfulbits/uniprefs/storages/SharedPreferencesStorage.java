package com.artfulbits.uniprefs.storages;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Unified facade. Facade for SharedPreferences. */
public class SharedPreferencesStorage
    implements IGenericStorage, ISupportBatches, ISupportMapping<SharedPreferencesStorage> {
  /* [ MEMBERS ] ================================================================================================== */

  /** Cached instance of the Shared Preferences. */
  private final SharedPreferences mSettings;
  /** atomic counter of locks - {@link ISupportBatches#beginUpdate()} and {@link ISupportBatches#endUpdate()}. */
  private final AtomicInteger mEditorLock = new AtomicInteger(0);
  /** reference on editor. Batch the update transaction. */
  private SharedPreferences.Editor mEditor;
  /** reference on instance of keys mapping. */
  private Map<String, String> mMapping;

  /* [ CONSTRUCTORS ] ============================================================================================= */

  /**
   * Instantiates a new Shared preferences facade.
   *
   * @param settings the preferences instance.
   */
  public SharedPreferencesStorage(final SharedPreferences settings) {
    mSettings = settings;
  }

  /** Get reference on instance of shared preferences. */
  private SharedPreferences getSettings() {
    return mSettings;
  }

  /* [ CONFIGURATION ] ============================================================================================ */

  /** {@inheritDoc} */
  @NonNull
  @Override
  public SharedPreferencesStorage setMapping(@Nullable final Map<String, String> mapping) {
    mMapping = mapping;

    return this;
  }

  /**
   * Do mapping of the proposed key.
   *
   * @param key the key to map
   * @return the new key value.
   */
  @NonNull
  protected String map(@NonNull final String key) {
    String result = key;

    if (null != mMapping) {
      result = mMapping.get(key);
    }

    return result;
  }


  /* [ PUBLIC API ] =============================================================================================== */

  /** {@inheritDoc} */
  @Override
  public Object getDataHolder() {
    return getSettings();
  }

  /** {@inheritDoc} */
  @Override
  public boolean contains(@NonNull final String key) {
    return getSettings().contains(map(key));
  }

  /** {@inheritDoc} */
  @Override
  public void remove(@NonNull final String key) {
    final SharedPreferences.Editor editor = getSettings().edit();
    editor.remove(map(key));
    editor.apply();
  }

  /** {@inheritDoc} */
  @Override
  public void clear() {
    final SharedPreferences.Editor editor = getSettings().edit();
    editor.clear();
    editor.apply();
  }

  /** {@inheritDoc} */
  @Override
  public String getString(@NonNull final String key) {
    return getSettings().getString(map(key), "");
  }

  /** {@inheritDoc} */
  @Override
  public int getInt(@NonNull final String key) {
    return getSettings().getInt(map(key), 0);
  }

  /** {@inheritDoc} */
  @Override
  public long getLong(@NonNull final String key) {
    return getSettings().getLong(map(key), 0);
  }

  /** {@inheritDoc} */
  @Override
  public boolean getBoolean(@NonNull final String key) {
    return getSettings().getBoolean(map(key), false);
  }

  @Override
  public float getFloat(@NonNull final String key) {
    return getSettings().getFloat(map(key), 0.0f);
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final boolean value) {
    beginUpdate();
    mEditor.putBoolean(map(key), value);
    endUpdate();
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final int value) {
    beginUpdate();
    mEditor.putInt(map(key), value);
    endUpdate();
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final long value) {
    beginUpdate();
    mEditor.putLong(map(key), value);
    endUpdate();
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final String value) {
    beginUpdate();
    mEditor.putString(map(key), value);
    endUpdate();
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final float value) {
    beginUpdate();
    mEditor.putFloat(map(key), value);
    endUpdate();
  }

  /* [ BATCHES SUPPORT ] ========================================================================================== */

  /** {@inheritDoc} */
  @Override
  public boolean isInUpdate() {
    return (null != mEditor);
  }

  /** {@inheritDoc} */
  @Override
  public void beginUpdate() {
    mEditorLock.incrementAndGet();

    if (null == mEditor) {
      mEditor = getSettings().edit();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void endUpdate() {
    if (isInUpdate() && 0 <= mEditorLock.decrementAndGet()) {
      mEditor.apply();
      mEditor = null;

      mEditorLock.set(0);
    }
  }
}
