package com.artfulbits.uniprefs.storages;

/** Generic Primitives Storage interface. Very similar to {@link android.content.SharedPreferences} interface. */
@SuppressWarnings("unused")
public interface IGenericStorage {

  /* [ HELPERS ] ================================================================================================== */

  /**
   * Get the instance of the data holder.
   *
   * @return the data holder instance.
   */
  Object getDataHolder();

  /**
   * Check is storage contains value for specified key.
   *
   * @param key the key to check
   * @return true - value exists, otherwise false.
   */
  boolean contains(final String key);

  /**
   * Remove value from storage by its key.
   *
   * @param key the key of value.
   */
  void remove(final String key);

  /** Clear the storage. */
  void clear();

  /* [ GETTERS ] ================================================================================================== */

  /**
   * Gets string.
   *
   * @param key the key, identifier of value in storage.
   * @return the extracted string
   */
  String getString(final String key);

  /**
   * Gets int.
   *
   * @param key the key, identifier of value in storage.
   * @return the extracted int
   */
  int getInt(final String key);

  /**
   * Gets long.
   *
   * @param key the key, identifier of value in storage.
   * @return the extracted long
   */
  long getLong(final String key);

  /**
   * Gets boolean.
   *
   * @param key the key, identifier of value in storage.
   * @return the extracted boolean
   */
  boolean getBoolean(final String key);

  /**
   * Gets float.
   *
   * @param key the key, identifier of value in storage.
   * @return the extracted float
   */
  float getFloat(final String key);

  /* [ SETTERS ] ================================================================================================== */

  /**
   * Set boolean.
   *
   * @param key the key, identifier of value in storage.
   * @param value the value to store
   */
  void set(final String key, final boolean value);

  /**
   * Set int.
   *
   * @param key the key, identifier of value in storage.
   * @param value the value to store
   */
  void set(final String key, final int value);

  /**
   * Set long.
   *
   * @param key the key, identifier of value in storage.
   * @param value the value to store
   */
  void set(final String key, final long value);

  /**
   * Set string.
   *
   * @param key the key, identifier of value in storage.
   * @param value the value to store
   */
  void set(final String key, final String value);

  /**
   * Set float.
   *
   * @param key the key, identifier of value in storage.
   * @param value the value to store
   */
  void set(final String key, final float value);
}
