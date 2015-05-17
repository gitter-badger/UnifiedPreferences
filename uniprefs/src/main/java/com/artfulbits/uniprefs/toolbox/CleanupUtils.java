package com.artfulbits.uniprefs.toolbox;

import android.util.Log;

import com.artfulbits.uniprefs.PreferencesUnified;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channel;

/**
 * Cleanup Utilities used for simplifying cleanup logic in methods, hide try/catches and etc.
 *
 * @author Oleksandr Kucherenko
 */
public final class CleanupUtils {
  /**
   * Destroy Channel instance.
   *
   * @param channel instance of the channel.
   */
  public static void destroy(final Channel channel) {
    if (null != channel) {
      try {
        channel.close();
      } catch (final Throwable ignored) {
        Log.i(PreferencesUnified.LOG_TAG, ignored.getMessage());
      }
    }
  }

  /**
   * Close input stream gracefully.
   *
   * @param is instance of the input stream.
   */
  public static void destroy(final InputStream is) {
    if (null != is) {
      try {
        is.close();
      } catch (final IOException ignored) {
        Log.i(PreferencesUnified.LOG_TAG, ignored.getMessage());
      }
    }
  }
}
