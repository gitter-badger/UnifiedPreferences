package com.artfulbits.uniprefs.storages;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Map;

/** Generics storage implementation that saves all values into Map&lt;String, Object&gt;. */
public class MapStorage implements IGenericStorage, ISupportMapping<MapStorage> {
  /* [ MEMBERS ] ================================================================================================== */

  /** reference on instance of low level storage. */
  private final Map<String, Object> mStorage;
  /** reference on instance of keys mapping. */
  private Map<String, String> mMapping;

  /* [ CONSTRUCTORS ] ============================================================================================= */

  /**
   * Instantiates a new Hash map facade for specified low level storage.
   *
   * @param storage the storage instance.
   */
  public MapStorage(@NonNull final Map<String, Object> storage) {
    mStorage = storage;
  }

  /* [ CONFIGURATION ] ============================================================================================ */

  /** {@inheritDoc} */
  @Override
  @NonNull
  public MapStorage setMapping(@Nullable final Map<String, String> mapping) {
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
    return mStorage;
  }

  /** {@inheritDoc} */
  @Override
  public boolean contains(@NonNull final String key) {
    return mStorage.containsKey(map(key));
  }

  /** {@inheritDoc} */
  @Override
  public void remove(@NonNull final String key) {
    mStorage.remove(map(key));
  }

  /** {@inheritDoc} */
  @Override
  public void clear() {
    mStorage.clear();
  }

  /** {@inheritDoc} */
  @Override
  public String getString(@NonNull final String key) {
    return String.valueOf(mStorage.get(map(key)));
  }

  /** {@inheritDoc} */
  @Override
  public int getInt(@NonNull final String key) {
    return ((Number) mStorage.get(map(key))).intValue();
  }

  /** {@inheritDoc} */
  @Override
  public long getLong(@NonNull final String key) {
    return ((Number) mStorage.get(map(key))).longValue();
  }

  /** {@inheritDoc} */
  @Override
  public boolean getBoolean(@NonNull final String key) {
    return (Boolean) mStorage.get(map(key));
  }

  @Override
  public float getFloat(@NonNull final String key) {
    return (Float) mStorage.get(map(key));
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final boolean value) {
    mStorage.put(map(key), value);
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final int value) {
    mStorage.put(map(key), value);
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final long value) {
    mStorage.put(map(key), value);
  }

  /** {@inheritDoc} */
  @Override
  public void set(@NonNull final String key, final String value) {
    mStorage.put(map(key), value);
  }

  @Override
  public void set(@NonNull final String key, final float value) {
    mStorage.put(map(key), value);
  }
}
