package com.artfulbits.uniprefs;

import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.annotation.NonNull;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.artfulbits.junit.NamedDaemons;
import com.artfulbits.junit.PerformanceTests;
import com.artfulbits.junit.Sampling;
import com.artfulbits.unipref.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.runner.*;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@SuppressWarnings("NewApi,PMD")
@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 21
    , reportSdk = 18
    , manifest = "./src/test/AndroidManifest.xml"
    , constants = BuildConfig.class)
public class SharedPreferencesTests extends PerformanceTests {

  private static final String SOMETHING_TO_STORE = "something to store";

  public static final int ITERATIONS = Sampling.ITERATIONS_M;
  public static final int ITERATIONS_L = Sampling.ITERATIONS_L;
  public static final int ITERATIONS_XL = Sampling.ITERATIONS_XL;

  public static final String UNIT_TESTS_PREFS = "uniprefs.data.unit.tests.prefs";
  public static final String UNIT_TESTS_DB = "uniprefs.data.unit.tests";

  private final Set<String> TestSetValues = new HashSet<>();
  private String ExtraLongString;

  @Override
  public void configMeter() {

  }

  @Override
  public void warmUp() {
    if (null == TestSetValues || TestSetValues.isEmpty()) {
      //			CountryDao dao = new CountryDao(getContext());
      //			dao.init();
      //
      //			extracted = dao.getAll();

      TestSetValues.add("set string #1");
      TestSetValues.add("set string #2");
      TestSetValues.add("set string #3");
      TestSetValues.add("set string #4");

      final StringBuilder dumpString = new StringBuilder(0xffff * 4);

      while (dumpString.length() < 0xffff * 3) {
        //                 0         0         0         0         0         0
        dumpString.append("This is dummy string for testing the chunks logic .");
      }

      ExtraLongString = dumpString.toString();
    }
  }

  public Context getContext() {
    return RuntimeEnvironment.application;
  }

  private PreferencesUnified getPreferencesUnified() {
    return new PreferencesUnified(getContext(), UNIT_TESTS_PREFS, OrgJsonSerializer.Instance);
  }

  private SharedPreferences getDbPreferences() {
    return PreferencesToDb.newInstance(getContext(), UNIT_TESTS_DB);
  }

	/* ================================= [TESTS] ====================================== */

  @SmallTest
  public void test_00_PreferencesUnified_API() {
    final PreferencesUnified prefs = getPreferencesUnified();
    meter().beat("Instance creation");

    final Editor edit = prefs.edit();
    edit.putBoolean("boolean", true);
    edit.putFloat("float", 1.1f);
    edit.putInt("integer", 0xf0f0f0f0);
    edit.putLong("long", 0xffffffffffl);
    edit.putString("string", SOMETHING_TO_STORE);
    edit.putStringSet("stringset", TestSetValues);
    edit.apply();

    meter().beat("Edit transaction");

    // test memory TestSetValues
    assertEquals(true, prefs.getBoolean("boolean", false));
    assertEquals(1.1f, prefs.getFloat("float", 0.0f));
    assertEquals(0xf0f0f0f0, prefs.getInt("integer", 0));
    assertEquals(0xffffffffffl, prefs.getLong("long", 0));
    assertEquals(SOMETHING_TO_STORE, prefs.getString("string", ""));
    assertNotNull(prefs.getStringSet("stringset", null));

    // force commit
    prefs.edit().putLong("last_save_time", System.currentTimeMillis()).commit();
    meter().skip("Disk Commit Time");

    // TODO: done saves should be in total equal 1 (one) - this is optimal, 2 (two) - normal if device is fast.
    prefs.dump(false);

    // empty preferences
    prefs.edit().clear().commit();
  }

  @SmallTest
  public void test_01_PreferencesUnified_NewInstance() {
    PreferencesUnified.gc();

    final PreferencesUnified prefs = getPreferencesUnified();
    meter().beat("First instance creation");

    meter().loop("Create " + ITERATIONS + " instances.");
    for (int i = 0, len = ITERATIONS; i < len; i++) {
      final PreferencesUnified prefs2 = getPreferencesUnified();
      final int size = prefs2.getAll().size();
      meter().recap();
    }
    meter().unloop("new instances done.");

    // do cleanup
    prefs.edit().clear().commit();
  }

  @LargeTest
  public void test_02_SharedPreferences_StressTest_Apply() {

    meter().loop("run " + ITERATIONS + " edit applyies.");
    for (int i = 0, len = ITERATIONS; i < len; i++) {
      final SharedPreferences prefs = getContext().getSharedPreferences(UNIT_TESTS_DB, Service.MODE_PRIVATE);
      prefs.edit().putString("" + i, SOMETHING_TO_STORE).apply();
      meter().recap();
    }
    meter().unloop("apply done.");

    final SharedPreferences prefs = getContext().getSharedPreferences(UNIT_TESTS_DB, Service.MODE_PRIVATE);
    prefs.edit().clear().commit();
    meter().skip("cleanup");
  }

  @LargeTest
  public void test_03_SharedPreferences_StressTest_Commit() {

    meter().loop("run " + ITERATIONS + " edit commits.");
    for (int i = 0, len = ITERATIONS; i < len; i++) {
      final SharedPreferences prefs = getContext().getSharedPreferences(UNIT_TESTS_DB, Service.MODE_PRIVATE);
      prefs.edit().putString("" + i, SOMETHING_TO_STORE).commit();
      meter().recap();
    }
    meter().unloop("commit done.");

    final SharedPreferences prefs = getContext().getSharedPreferences(UNIT_TESTS_DB, Service.MODE_PRIVATE);
    prefs.edit().clear().commit();
    meter().skip("cleanup");
  }

  @LargeTest
  public void test_04_PreferencesUnified_StressTest_Apply() {

    meter().loop("run " + ITERATIONS_L + " edit applyies.");
    for (int i = 0, len = ITERATIONS_L; i < len; i++) {
      final PreferencesUnified prefs = getPreferencesUnified();
      prefs.edit().putString("" + i, SOMETHING_TO_STORE).apply();
      meter().recap();
    }
    meter().unloop("apply done.");

    final PreferencesUnified prefs = getPreferencesUnified();
    prefs.edit().clear().commit();
    meter().skip("cleanup");
  }

  @LargeTest
  public void test_05_PreferencesUnified_StressTest_Commit() {

    meter().loop("run " + ITERATIONS + " edit commits.");
    for (int i = 0, len = ITERATIONS; i < len; i++) {
      final PreferencesUnified prefs = getPreferencesUnified();
      prefs.edit().putString("" + i, SOMETHING_TO_STORE).commit();
      meter().recap();
    }
    meter().unloop("commit done.");

    final PreferencesUnified prefs = getPreferencesUnified();
    prefs.edit().clear().commit();
    meter().skip("cleanup");
  }

  @SmallTest
  public void test_06_PreferencesUnified_StressTest_Full_Apply() {
    final PreferencesUnified prefs2 = getPreferencesUnified();

    meter().loop("run " + ITERATIONS_L + " edit applyies.");
    for (int i = 0, len = ITERATIONS_L; i < len; i++) {
      final PreferencesUnified prefs = getPreferencesUnified();

      final Editor edit = prefs.edit();
      edit.putBoolean("b" + i, true);
      edit.putFloat("f" + i, 1.1f);
      edit.putInt("i" + i, i);
      edit.putLong("l" + i, i);
      edit.putString("s" + i, SOMETHING_TO_STORE);
      edit.putStringSet("ss" + i, TestSetValues);
      edit.apply();

      meter().recap();
    }
    meter().unloop("apply done.");

    prefs2.dump(false);
    prefs2.edit().putLong("last_save_time", System.currentTimeMillis()).commit();
    meter().skip("Disk Commit Time");

    // empty preferences
    prefs2.edit().clear().commit();
  }

  @LargeTest
  public void test_07_PreferencesUnified_StressTest_Full_Commit() {
    Set<String> values = new HashSet<String>();
    values.add("set string #1");
    values.add("set string #2");
    values.add("set string #3");
    values.add("set string #4");

    meter().loop("run " + ITERATIONS + " edit applyies.");
    for (int i = 0, len = ITERATIONS; i < len; i++) {
      final PreferencesUnified prefs = getPreferencesUnified();

      final Editor edit = prefs.edit();
      edit.putBoolean("b" + i, true);
      edit.putFloat("f" + i, 1.1f);
      edit.putInt("i" + i, i);
      edit.putLong("l" + i, i);
      edit.putString("s" + i, SOMETHING_TO_STORE);
      edit.putStringSet("ss" + i, values);
      edit.commit();

      meter().recap();
    }
    meter().unloop("apply done.");

    final PreferencesUnified prefs = getPreferencesUnified();
    prefs.edit().clear().commit();
    meter().skip("cleanup");
  }

  @LargeTest
  public void test_08_PreferencesDb_StressTest_Apply() {

    meter().loop("run " + ITERATIONS_L + " edit applyies.");
    for (int i = 0, len = ITERATIONS_L; i < len; i++) {
      final SharedPreferences prefs = getDbPreferences();
      prefs.edit().putString("" + i, SOMETHING_TO_STORE).apply();
      meter().recap();
    }
    meter().unloop("apply done.");

    final SharedPreferences prefs = getDbPreferences();
    prefs.edit().clear().commit();
    meter().skip("cleanup");
  }

  @LargeTest
  public void test_09_PreferencesDb_StressTest_Commit() {

    meter().loop("run " + ITERATIONS + " edit commits.");
    for (int i = 0, len = ITERATIONS; i < len; i++) {
      final SharedPreferences prefs = getDbPreferences();
      prefs.edit().putString("" + i, SOMETHING_TO_STORE).commit();
      meter().recap();
    }
    meter().unloop("commit done.");

    final SharedPreferences prefs = getDbPreferences();
    prefs.edit().clear().commit();
    meter().skip("cleanup");
  }

  @LargeTest
  public void test_10_PreferencesDb_StressTest_Full_Apply() {
    final SharedPreferences prefs2 = getDbPreferences();

    meter().loop("run " + ITERATIONS_XL + " edit applyies.");
    for (int i = 0, len = ITERATIONS_XL; i < len; i++) {
      final SharedPreferences prefs = getDbPreferences();

      final Editor edit = prefs.edit();
      edit.putBoolean("b" + i, true);
      edit.putFloat("f" + i, 1.1f);
      edit.putInt("i" + i, i);
      edit.putLong("l" + i, i);
      edit.putString("s" + i, SOMETHING_TO_STORE);
      edit.putStringSet("ss" + i, TestSetValues);
      edit.apply();

      meter().recap();
    }
    meter().unloop("apply done.");

    // do some debug dumps
    ((PreferencesUnified) prefs2).dump(false);
    ((PreferencesToDb) ((PreferencesUnified) prefs2).getSerializer()).dump();

    prefs2.edit().putLong("last_save_time", System.currentTimeMillis()).commit();
    meter().skip("commit all transactions.");

    Log.i(TAG, "total: " + prefs2.getAll().size() / 6);
  }

  @SmallTest
  public void test_11_DataMigration() {
    final SharedPreferences prefs = getContext().getSharedPreferences(UNIT_TESTS_DB, Service.MODE_PRIVATE);
    prefs.edit().clear().commit();
    meter().beat("initial cleanup");

    Editor edit = prefs.edit();
    for (int i = 0; i < ITERATIONS; i++) {
      edit.putBoolean("b" + i, true);
      edit.putFloat("f" + i, 1.1f);
      edit.putInt("i" + i, i);
      edit.putLong("l" + i, i);
      edit.putString("s" + i, SOMETHING_TO_STORE);
      edit.putStringSet("ss" + i, TestSetValues);
    }
    edit.commit();
    meter().skip("initial feeling");

    boolean exist = PreferencesUnified.isPreferenceExists(getContext(), UNIT_TESTS_DB);
    assertTrue("Shared preferences XML file should be on disk", exist);

    final SharedPreferences prefs2 = getDbPreferences();

    // do cleanup first
    prefs2.edit().clear().apply();
    meter().skip("initial cleanup");

    PreferencesUnified.copy(prefs, prefs2);
    meter().beat("migration");
    assertEquals(prefs.getAll().size(), prefs2.getAll().size());

    // cleanup all
    prefs2.edit().clear().commit();
    prefs.edit().clear().commit();
    meter().skip("Cleanup");
  }

  @LargeTest
  public void test_12_MultiThread_HeavyLoad() {
    final AtomicInteger counter = new AtomicInteger();

    Runnable run = new Runnable() {
      @Override
      public void run() {
        final SharedPreferences prefs = getDbPreferences();

        Log.i(TAG, "In Thread 'load', data size: " + prefs.getAll().size() +
            ", thread: " + Thread.currentThread().getName() +
            ", counter: " + counter.decrementAndGet());
      }
    };

    Runnable runUpdate = new Runnable() {
      @Override
      public void run() {
        final SharedPreferences prefs = getDbPreferences();

        for (int i = 0; i < ITERATIONS; i++) {
          final Editor edit = prefs.edit();
          edit.putBoolean("b" + i, true);
          edit.putFloat("f" + i, 1.1f);
          edit.putInt("i" + i, i);
          edit.putLong("l" + i, i);
          edit.putString("s" + i, SOMETHING_TO_STORE);
          edit.putStringSet("ss" + i, TestSetValues);
          edit.apply();
        }

        Log.i(TAG, "In Thread 'update', data size: " + prefs.getAll().size() +
            ", thread: " + Thread.currentThread().getName() +
            ", counter: " + counter.decrementAndGet());
      }
    };

    final ExecutorService pool = Executors.newCachedThreadPool(NamedDaemons.Instance);

    // schedule execution of the tasks
    for (int i = 0; i < ITERATIONS; i++) {
      counter.addAndGet(2);

      pool.execute(run);
      pool.execute(runUpdate);
    }

    while (counter.get() > 0) {
      try {
        Thread.sleep(1 /*sec*/ * 1000 /*millis*/);
      } catch (final InterruptedException ignored) {
        // run next cycle
      }
    }
  }

  @SmallTest
  public void test_13_HugeStrings_ChunkLogic_Save() {
    final SharedPreferences prefs = getDbPreferences();
    prefs.edit().putString("chunked-string", ExtraLongString).commit();

    // expected - no failures!
  }

  @SmallTest
  public void test_14_HugeStrings_ChunkLogic_Load() {
    final SharedPreferences prefs = getDbPreferences();
    String data = prefs.getString("chunked-string", "");

    assertEquals(ExtraLongString, data);
  }

	/* ================================= [NESTED CLASSES DECLARATIONS] ====================================== */

  /** Serialize to/from JSON with use of standard {@link org.json.JSONObject}. */
  private static final class OrgJsonSerializer
      implements PreferencesUnified.Serialization {
    /** Singleton instance. */
    public static final PreferencesUnified.Serialization Instance = new OrgJsonSerializer();
    /** Default encoding. */
    private final static Charset UTF8 = Charset.forName("UTF-8");

    private OrgJsonSerializer() {
      // do nothing
    }

    @Override
    public byte[] serialize(final Map<String, ?> data) {
      final String blob = new org.json.JSONObject(data).toString();

      return blob.getBytes(UTF8);
    }

    @Override
    public Map<String, ?> deserialize(final byte[] data) {
      if (null != data) {
        try {
          final String json = new String(data, UTF8);
          final JSONObject extracted = (JSONObject) new JSONTokener(json).nextValue();

          return toMap(extracted);
        } catch (final Throwable ignored) {

        }
      }

      return null;
    }

    @NonNull
    public static Map<String, Object> toMap(@NonNull final JSONObject object) throws JSONException {
      final Map<String, Object> map = new HashMap<>();

      final Iterator<String> keysItr = object.keys();
      while (keysItr.hasNext()) {
        final String key = keysItr.next();
        Object value = object.get(key);

        if (value instanceof JSONArray) {
          value = toList((JSONArray) value);
        } else if (value instanceof JSONObject) {
          value = toMap((JSONObject) value);
        }

        map.put(key, value);
      }

      return map;
    }

    @NonNull
    public static List<Object> toList(@NonNull final JSONArray array) throws JSONException {
      final List<Object> list = new ArrayList<>();

      for (int i = 0, len = array.length(); i < len; i++) {
        Object value = array.get(i);

        if (value instanceof JSONArray) {
          value = toList((JSONArray) value);
        } else if (value instanceof JSONObject) {
          value = toMap((JSONObject) value);
        }

        list.add(value);
      }

      return list;
    }
  }

}
