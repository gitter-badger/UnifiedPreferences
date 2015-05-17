package com.artfulbits.uniprefs.toolbox;

import android.support.annotation.NonNull;
import android.util.Log;

import com.artfulbits.uniprefs.PreferencesUnified;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Data type converter utility class. */
public final class Types {
  /** INT. first bit. */
  public static final int INT = 1 << 1;
  /** LONG. second bit. */
  public static final int LONG = 1 << 2;
  /** FLOAT. third bit. */
  public static final int FLOAT = 1 << 3;
  /** BOOL. fourth bit. */
  public static final int BOOL = 1 << 4;
  /** STRING. fifth bit. */
  public static final int STRING = 1 << 5;
  /** SET. bit #6. */
  public static final int SET = 1 << 6;
  /** ALL possible data type bits are set. */
  public static final int ALL = INT | LONG | FLOAT | BOOL | STRING | SET;
  /** The MASK that allows to filter/cleanup the INT and leave only data type bits. */
  public static final int MASK = ~ALL;
  /** The constant DROPPED. */
  public static final int DROPPED = 0xf000;

  /** dummy string used for defining the 'chunks' without breaking the main logic. */
  private static final String CHUNKS_IN_USE = "--several-chunks-of-the-string--";
  /** Max allowed chunk size.  64 Kb. */
  private static final int CHUNK_MAX_SIZE = 0xffff;
  /**
   * Unicode string in worst cases scenario will use 2 bytes per char. So our chunk max size for strings is 32kb.
   */
  private static final int CHUNK_2BYTE_MAX = 0x8000;

  /** Hidden constructor. */
  private Types() {
    throw new AssertionError();
  }

  /* ================================= [DATA CONVERSION] ================================== */

  /**
   * Gets data type flag. Try to detect data type dynamically.
   *
   * @param value the value to check
   * @return the identified data type
   */
  public static int getDataType(final Object value) {
    if (value instanceof String) {
      return Types.STRING;
    } else if (value instanceof Integer) {
      return Types.INT;
    } else if (value instanceof Long) {
      return Types.LONG;
    } else if (value instanceof Float) {
      return Types.FLOAT;
    } else if (value instanceof Boolean) {
      return Types.BOOL;
    } else if (value instanceof Set<?>) {
      return Types.SET;
    }

    throw new IllegalArgumentException("Unexpected data type.");
  }

  /**
   * Convert to bytes array.
   *
   * @param type the type
   * @param data the data to convert
   * @return the produced bytes array
   */
  public static byte[] convertTo(final int type, final Object data) {
    if ((type & Types.ALL) > 0) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final DataOutputStream dos = new DataOutputStream(baos);

      try {
        saveData(dos, type, data);
      } catch (final Throwable ignored) {
        Log.e(PreferencesUnified.LOG_TAG, Log.getStackTraceString(ignored));
      }

      return baos.toByteArray();
    }

    return null;
  }

  /**
   * Save data. Serialize data to a byte array.
   *
   * @param dos the instance of the stream
   * @param type the type of the data
   * @param data the data instance
   * @throws IOException the iO exception
   */
  @SuppressWarnings("unchecked")
  public static void saveData(final DataOutputStream dos, final int type, final Object data) throws IOException {
    switch (type) {
      case Types.BOOL:
        dos.writeBoolean((Boolean) data);
        break;

      case Types.FLOAT:
        dos.writeFloat((Float) data);
        break;

      case Types.INT:
        dos.writeInt((Integer) data);
        break;

      case Types.LONG:
        dos.writeLong((Long) data);
        break;

      case Types.SET:
        final Set<String> set = (Set<String>) data;
        dos.writeInt(set.size());

        final Iterator<String> iterator = set.iterator();
        while (iterator.hasNext()) {
          dos.writeUTF(iterator.next());
        }

        break;

      case Types.STRING:
        final int length = ((String) data).length();
        final int chunksNeeded = length / CHUNK_MAX_SIZE;

        if (chunksNeeded > 0) {
          dos.writeUTF(CHUNKS_IN_USE);

          dos.writeInt((length / CHUNK_2BYTE_MAX) + 1);
          for (String chunk : split((String) data, 0x8000)) {
            dos.writeUTF(chunk);
          }
        } else {
          dos.writeUTF((String) data);
        }
        break;
    }

    dos.flush();
  }

  /**
   * Split long strings into set of smaller.
   *
   * @param text string to split.
   * @param sliceSize slice size.
   * @return strings collection.
   */
  @NonNull
  public static List<String> split(@NonNull final String text, final int sliceSize) {
    final List<String> textList = new ArrayList<String>();

    String aux;
    int left = -1, right = 0;
    int charsLeft = text.length();

    while (charsLeft != 0) {
      left = right;

      if (charsLeft >= sliceSize) {
        right += sliceSize;
        charsLeft -= sliceSize;
      } else {
        right = text.length();
        aux = text.substring(left, right);
        charsLeft = 0;
      }

      aux = text.substring(left, right);
      textList.add(aux);
    }

    return textList;
  }

  /**
   * Convert bytes array to a specified data type instance.
   *
   * @param type the type of the data
   * @param value the bytes for de-serialization
   * @return the instance of data
   */
  public static Object convertTo(final int type, final byte[] value) {
    if ((type & Types.ALL) > 0) {
      final DataInputStream dis = new DataInputStream(new ByteArrayInputStream(value));

      try {
        return readData(dis, type);
      } catch (final Throwable ignored) {
        Log.e(PreferencesUnified.LOG_TAG, Log.getStackTraceString(ignored));
      }
    }

    return null;
  }

  /**
   * Read data. De-serialize the data from bytes.
   *
   * @param dis the bytes input stream
   * @param type the type of data
   * @return the instance of data
   * @throws IOException the iO exception
   */
  public static Object readData(final DataInputStream dis, final int type) throws IOException {
    switch (type) {
      case Types.BOOL:
        return dis.readBoolean();

      case Types.FLOAT:
        return dis.readFloat();

      case Types.INT:
        return dis.readInt();

      case Types.LONG:
        return dis.readLong();

      case Types.SET:
        final Set<String> set = new HashSet<String>();

        for (int i = 0, len = dis.readInt(); i < len; i++) {
          set.add(dis.readUTF());
        }

        return set;

      case Types.STRING:
        final String extracted = dis.readUTF();

        if (CHUNKS_IN_USE.equals(extracted)) {
          final int chunks = dis.readInt();
          StringBuilder result = new StringBuilder(chunks * 0x8000);

          for (int i = 0; i < chunks; i++) {
            result.append(dis.readUTF());
          }

          return result.toString();
        } else {
          return extracted;
        }
    }

    return null;
  }
}
