package com.artfulbits.uniprefs.storages;

/** Interface that allows to implement update operations in batches. */
@SuppressWarnings("unused")
public interface ISupportBatches {

  /**
   * Is in update mode.
   *
   * @return true - we are in update mode, otherwise false.
   */
  boolean isInUpdate();

  /** Starts 'update mode'. */
  void beginUpdate();

  /** Ends 'update mode'. */
  void endUpdate();
}
