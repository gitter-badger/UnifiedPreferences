package com.artfulbits.uniprefs.toolbox;

/** Raised when required reschedule of the save operation required. */
public final class RescheduleException extends RuntimeException {
  public RescheduleException(final String message) {
    super(message);
  }
}
