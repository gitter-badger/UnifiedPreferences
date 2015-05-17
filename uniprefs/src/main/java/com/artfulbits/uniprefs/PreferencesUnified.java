package com.artfulbits.uniprefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.artfulbits.uniprefs.actions.FactoryImpl;
import com.artfulbits.uniprefs.toolbox.CleanupUtils;
import com.artfulbits.uniprefs.toolbox.NullSerialization;
import com.artfulbits.uniprefs.toolbox.RescheduleException;
import com.artfulbits.uniprefs.toolbox.SaveDaemonsFactory;
import com.artfulbits.uniprefs.toolbox.Types;
import com.artfulbits.uniprefs.toolbox.counters.Statistics;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Shared preferences which implements abstraction for simple replacement of persistence storage. Easily storage can be
 * switched to DB, JSON or XML. Used callback pattern for class logic adjustment.<br/> <br/> Main features:<br/> <ol>
 * <li>Memory 'storage' pool. Creation of new instance of SharedPreferences is cheap.</li> <li>'Storage' pool based on
 * weak references. Polite to memory usage.</li> <li>Save of storage in background thread only.</li> <li>'Storage' can
 * have only one save thread in a process.</li> <li>Save threads change own priority based on thread queue size.</li>
 * <li>Storage serialization can be replaced by any implementation you prefer: DB, JSON, etc.</li> <li>Actions Factory -
 * allows to customize deeply modifications of the preferences.</li> <li>Implemented CommitsListener - allows to monitor
 * in memory commit transaction.</li> <li>Custom names for save background threads. Easy to identify who created the
 * thread and control it lifetime.</li> <li>Save to Disk Thread Pools max size is polite to device resources. CPU Cores
 * count influence on Max number of available threads in thread pool.</li> <li>Merged save transactions, multiple Apply
 * calls merged into one save to disk operation.</li> <li></li> </ol>
 *
 * @author Oleksandr Kucherenko
 * @version 1.0 beta
 * @see <a href="http://goo.gl/xQwxaG">v1.4.2 / org.holoeverywhere.internal._SharedPreferencesImpl_JSON</a>
 * @see <a href="http://goo.gl/kAaZvX">v4.4.2_r1 / android.app.SharedPreferencesImpl</a>
 * @see <a href="http://goo.gl/o9AvfI">v1.5_r4 / android.app.ApplicationContext$SharedPreferencesImpl</a>
 */
public final class PreferencesUnified
    implements SharedPreferences, Runnable {

  /** Logging tag used by this class for tracking the activities. */
  public static final String LOG_TAG = "uniprefs";
  /** Default write buffer size. */
  private final static int BUFFER_SIZE = 32 * 1024;
  /** Number of CPUs on board. */
  private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

  /**
   * Global cache of the preferences. Used for pointing all shared preferences on the same instance of Objects map and
   * version counter.
   * <p/>
   * Now strong references instead of weak. Weak references cannot be used for caching.
   */
  private final static Map<String, Storage> sPool = new HashMap<>();
  /** Statistics calculations. */
  /* package */ final static Statistics sStats = new Statistics();

	/* ==================================== [MEMBERS] ====================================== */

  /** Reference on application context. */
  @NonNull
  private final Context mContext;
  /** File name used for preferences storing. */
  @NonNull
  private final File mFileName;
  /** Preferences storage directory. */
  @NonNull
  private final File mStorageDir;
  /** Memory storage of the shared preferences. */
  @Nullable
  /* package */ final Storage mStorage;

	/* ================================= [CONSTRUCTOR] ===================================== */

  /**
   * Create instance of the shared preferences for provided context and file name.
   *
   * @param context application context
   * @param filePath file name of the preferences storage
   * @param serializer instance of the storage to use
   */
  public PreferencesUnified(@NonNull final Context context, @NonNull final String filePath,
                            @NonNull final Serialization serializer) {
    final ApplicationInfo appInfo = context.getApplicationInfo();

    mContext = context;
    mStorageDir = new File(appInfo.dataDir + "/shared_prefs");
    mFileName = new File(mStorageDir, filePath);

    setSerializer(serializer);

    final String key = mFileName.getAbsolutePath();
    boolean loadingNeeded = false;

    // temporary storage for preventing GC storage instance destroy
    Storage storageTmp;

    // singleton initialization of the data, 'double check' pattern used
    if (null == (storageTmp = sPool.get(key))) {
      synchronized (sPool) {
        if (null == (storageTmp = sPool.get(key))) {
          // we are the first, create shared storage for all others instances
          sPool.put(key, storageTmp = new Storage());

          // tell that we are waiting for data loading
          loadingNeeded = true;
        }
      }
    }

    // use shared settings for very fast initialization
    mStorage = storageTmp;

    initializeDirs();

    // try load the data
    if (loadingNeeded) {
      initializeData();
    }

    waitDataInitialization();
  }

  /**
   * Is preference file with provided name exists or not.
   *
   * @param context application context.
   * @param filePath the preferences file path.
   * @return true - file exists, otherwise false.
   */
  public static boolean isPreferenceExists(@NonNull final Context context, @NonNull final String filePath) {
    final File directory = new File(context.getApplicationInfo().dataDir + "/shared_prefs");
    final File toCheck = new File(directory, filePath + ".xml");

    return (toCheck.exists() && toCheck.length() > 0);
  }

  /**
   * Copy shared preferences of one instance to another.
   *
   * @param from source instance.
   * @param to destination instance.
   */
  @SuppressLint("NewApi")
  public static void copy(@Nullable final SharedPreferences from, @Nullable final SharedPreferences to) {
    if (null != from && null != to) {
      Map<String, ?> values = from.getAll();

      if (null != values && !values.isEmpty()) {
        final Editor editor = to.edit();

        for (Entry<String, ?> entry : values.entrySet()) {

          switch (Types.getDataType(entry.getValue())) {
            case Types.BOOL:
              editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
              break;

            case Types.FLOAT:
              editor.putFloat(entry.getKey(), (Float) entry.getValue());
              break;

            case Types.INT:
              editor.putInt(entry.getKey(), (Integer) entry.getValue());
              break;

            case Types.LONG:
              editor.putLong(entry.getKey(), (Long) entry.getValue());
              break;

            case Types.STRING:
              editor.putString(entry.getKey(), (String) entry.getValue());
              break;

            case Types.SET:
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                editor.putStringSet(entry.getKey(), (Set<String>) entry.getValue());
              }
              break;
          }
        }

        editor.apply();
      }
    }
  }

	/* ================================= [INITIALIZATION] ================================== */

  /** Create folders for shared preferences storage. */
  private void initializeDirs() {
    // create directory if needed, double check pattern used
    if (!mStorageDir.exists()) {
      synchronized (PreferencesUnified.class) {
        if (!mStorageDir.exists()) {
          if (!mStorageDir.mkdirs()) {
            Log.e(LOG_TAG, "Impossible to create directories for preferences.");
          }
        }
      }
    }
  }

  /**
   * Load settings from disk storage.
   *
   * @return true - success, otherwise false.
   */
  private boolean initializeData() {
    boolean result = false;
    byte[] data = null;

    if (mFileName.exists()) {
      data = new byte[(int) mFileName.length()];

      FileInputStream is = null;
      FileChannel channel = null;

      try {
        is = new FileInputStream(mFileName);

        channel = is.getChannel();
        channel.read(ByteBuffer.wrap(data));
        channel.close();

        is.close();
      } catch (@NonNull final Throwable ignored) {
        // ignore all
      } finally {
        CleanupUtils.destroy(channel);
        CleanupUtils.destroy(is);
      }
    }

    try {
      // this is the LONGEST operation in loading algorithm
      final Map<String, ?> values = getSerializer().deserialize(data);

      // override values
      if (null != values && values.size() > 0) {
        synchronized (mStorage.ModifySync) {
          mStorage.Objects.putAll(values);
        }
      }

      result = true;
    } catch (@NonNull final Throwable ignored) {
      Log.e(LOG_TAG, Log.getStackTraceString(ignored));
    } finally {
      // after recovering the data increment the storage value
      mStorage.Version.incrementAndGet();

      // notify all wait's that version is updated
      synchronized (mStorage.Version) {
        mStorage.Version.notifyAll();
      }
    }

    return result;
  }

  /** Wait till loading process is done. Version of the storage should be 1 or greater. */
  private void waitDataInitialization() {
    // wait for the loading operation, it should increase version to 1
    while (mStorage.Version.get() == 0) {
      synchronized (mStorage.Version) {
        try {
          // we are doing waiting with timeout due to unknown thread which calls us,
          // that maybe a MAIN thread. So in any case we should try to not block the thread in waiting if
          // possible.
          mStorage.Version.wait(16 /* 16 is millis for one video frame in 60fps UI */);
        } catch (@NonNull final InterruptedException ignored) {
          // do nothing, this is most likely the shutdown of the process
          break;
        }
      }
    }
  }

	/* ================================= [IMPLEMENTATION] ================================== */

  /** {@inheritDoc} */
  @Override
  public Map<String, ?> getAll() {
    synchronized (mStorage.ModifySync) {
      return Collections.unmodifiableMap(mStorage.Objects);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getString(final String key, final String defValue) {
    synchronized (mStorage.ModifySync) {
      if (mStorage.Objects.containsKey(key)) {
        return String.valueOf(mStorage.Objects.get(key));
      }
    }

    return defValue;
  }

  /** {@inheritDoc} */
  @SuppressWarnings("unchecked")
  @Override
  public Set<String> getStringSet(final String key, final Set<String> defValues) {
    synchronized (mStorage.ModifySync) {
      if (mStorage.Objects.containsKey(key)) {
        final Object item = (mStorage.Objects.get(key));

        if (item instanceof Set<?>) {
          return (Set<String>) item;
        }
      }
    }

    return defValues;
  }

  /** {@inheritDoc} */
  @Override
  public int getInt(final String key, final int defValue) {
    synchronized (mStorage.ModifySync) {
      if (mStorage.Objects.containsKey(key)) {
        final Object item = (mStorage.Objects.get(key));

        if (item instanceof Number) {
          return ((Number) item).intValue();
        }
      }
    }

    return defValue;
  }

  /** {@inheritDoc} */
  @Override
  public long getLong(final String key, final long defValue) {
    synchronized (mStorage.ModifySync) {
      if (mStorage.Objects.containsKey(key)) {
        final Object item = (mStorage.Objects.get(key));

        if (item instanceof Number) {
          return ((Number) item).longValue();
        }
      }
    }

    return defValue;
  }

  /** {@inheritDoc} */
  @Override
  public float getFloat(final String key, final float defValue) {
    synchronized (mStorage.ModifySync) {
      if (mStorage.Objects.containsKey(key)) {
        final Object item = (mStorage.Objects.get(key));

        if (item instanceof Number) {
          return ((Number) item).floatValue();
        }
      }
    }

    return defValue;
  }

  /** {@inheritDoc} */
  @Override
  public boolean getBoolean(final String key, final boolean defValue) {
    synchronized (mStorage.ModifySync) {
      if (mStorage.Objects.containsKey(key)) {
        final Object item = (mStorage.Objects.get(key));

        if (item instanceof Boolean) {
          return (Boolean) item;
        }
      }
    }

    return defValue;
  }

  /** {@inheritDoc} */
  @Override
  public boolean contains(final String key) {
    synchronized (mStorage.ModifySync) {
      return mStorage.Objects.containsKey(key);
    }
  }

  /** {@inheritDoc} */
  @NonNull
  @Override
  public Editor edit() {
    return new EditorImpl(this);
  }

	/* ================================= [SCHEDULED SAVE] ================================== */

  /** Global queue of save to disk requests for shared preferences. */
  private final static UniqueQueue sQueue = new UniqueQueue();

  /** Save files thread pool. Limit it to the COREs on dives. */
  private final static ThreadPoolExecutor sThreadPool = new ThreadPoolExecutor(0, CPU_COUNT, 30L,
      TimeUnit.SECONDS, sQueue, SaveDaemonsFactory.Instance);

  /** Schedule a save operation in thread pool. */
  private void scheduleCommitToDisk() {
    sThreadPool.execute(this);
  }

  /**
   * Forces Sync save to disk.
   *
   * @return <code>true</code> on success, otherwise <code>false</code>.
   */
  private boolean forceCommitToDisk() {
    synchronized (mStorage) {
      scheduleCommitToDisk();

      // TODO: how to confirm that thread does not already complete own job???

      // wait for confirmation of save thread complete
      try {
        mStorage.wait();
      } catch (@NonNull final InterruptedException ignored) {
        return false;
      }
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public final void run() {
    boolean noError = saveToDisk(mStorage, mStorageDir, mFileName, getSerializer());

    // in case of failure reschedule save operation
    if (!noError && !sThreadPool.isShutdown()) {
      sStats.RescheduledSaves.incrementAndGet();

      sThreadPool.execute(this);
    }

    // notify all waiters about done save operation
    if (noError) {
      synchronized (mStorage) {
        mStorage.notifyAll();
      }

      sStats.DoneSaves.incrementAndGet();
    }
  }

  /**
   * Do serialization to the disk of the current shared preference data content. It always happens in <b>background
   * thread</b>.
   *
   * @return <code>true</code> - save done, otherwise <code>false</code>.
   */
  private static boolean saveToDisk(@NonNull final Storage storage, final File dir, @NonNull final File file,
                                    @NonNull final Serialization serializer) {
    final int version = storage.Version.get();

    final long timestamp = System.nanoTime();
    final File bakFile = new File(dir, file.getName() + ".bak");
    final File tmpFile = new File(dir, timestamp + "-" + file.getName() + ".temp");

    // adjust priority base on our load
    Thread.currentThread().setPriority(suggestThreadPriority());

    boolean noError = true;

    // create a new file with data
    try {
      // get read-only version of data for synchronization
      final Map<String, Object> toSave = Collections.unmodifiableMap(storage.Objects);

      // serialize finally
      final byte[] data = serializer.serialize(toSave);

      // version watchdog, async modifications possible. serialize is a long operation.
      if (storage.Version.get() != version) {
        throw new RescheduleException("Collection modified during serialization. Reschedule is needed.");
        // throw new RuntimeException("Reschedule of synchronization job is required. Expected version does
        // not match current.");
      }

      if (null == data || 0 == data.length) {
        // empty data means drop of the file on disk to us
        tmpFile.delete();
      } else {
        final FileOutputStream fos = new FileOutputStream(tmpFile);
        final BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);

        bos.write(data);
        bos.flush();
        bos.close(); // that will close FOS too
      }
    } catch (@NonNull final Throwable ignored) {
      noError = false;

      if (!(ignored instanceof RescheduleException)) {
        Log.e(LOG_TAG, Log.getStackTraceString(ignored));
      }
    }

    // drop old backup version, if exists
    if (noError && bakFile.exists()) {
      noError = bakFile.delete();
    }

    // rename current data file to *.bak
    if (noError && file.exists()) {
      // we assume that rename is a atomic operation
      noError = file.renameTo(bakFile);
    }

    // rename new file to a current filename
    if (noError && tmpFile.exists()) {
      synchronized (storage) {
        noError = tmpFile.renameTo(file);

        // remove our shutdown guard
        if (null != storage.Shutdown) {
          Runtime.getRuntime().removeShutdownHook(storage.Shutdown);
          storage.Shutdown = null;
        }
      }
    }

    // final cleanup
    if (tmpFile.exists()) {
      tmpFile.delete();
    }

    return noError;
  }

  /**
   * Based on how full is our processing queue we adjusting background thread priority.
   *
   * @return suggested priority for thread.
   */
  private static int suggestThreadPriority() {
    final int size = sQueue.size();
    int priority = Thread.MIN_PRIORITY;

    if (size > 4) {
      // we are too slow possible lost of data
      priority = Thread.MAX_PRIORITY;
    } else if (size > CPU_COUNT) {
      // we are in normal state, but should hurry
      priority = Thread.NORM_PRIORITY;
    } else {
      // we are fine. Leave priority with MIN priority.
    }

    return priority;
  }

	/* ================================= [FACTORY SUPPORT] ================================= */

  /** Modify actions factory instance. */
  @NonNull
  private Factory mFactory = FactoryImpl.Instance;

  /** Serialization instance. */
  @NonNull
  private Serialization mSerializer = NullSerialization.Instance;

  /**
   * Get instance of the SharedPreferences actions factory.
   *
   * @return instance of the factory.
   */
  @NonNull
  public Factory getFactory() {
    return mFactory;
  }

  /**
   * Change factory of the SharedPreferences actions.
   *
   * @param factory instance of the factory. NULL reset factory to default instance.
   */
  public void setFactory(@Nullable final Factory factory) {
    if (null == factory) {
      mFactory = FactoryImpl.Instance;
    } else {
      mFactory = factory;
    }
  }

  /**
   * Get instance of the serializer used for storing preferences into file.
   *
   * @return instance of the serializer implementor.
   */
  @NonNull
  public Serialization getSerializer() {
    return mSerializer;
  }

  /**
   * Set serializer instance.
   *
   * @param serializer instance of the serializer.
   */
  public void setSerializer(@Nullable final Serialization serializer) {
    if (null == serializer) {
      mSerializer = NullSerialization.Instance;
    } else {
      mSerializer = serializer;
    }
  }

	/* ================================== [LISTENERS] ====================================== */

  private final WeakHashMap<CommitListener, Object> mCommitListeners = new WeakHashMap<>();
  /** Listener's storage. */
  private final WeakHashMap<OnSharedPreferenceChangeListener, Object> mListeners = new WeakHashMap<>();
  /** Listeners empty value. */
  private final static Object sEmpty = new Object();

  /** {@inheritDoc} */
  @Override
  public void registerOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener) {
    mListeners.put(listener, sEmpty);
  }

  /** {@inheritDoc} */
  @Override
  public void unregisterOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener) {
    mListeners.remove(listener);
  }

  /**
   * Register commit listener.
   *
   * @param listener instance that implements listener interface.
   */
  public void registerOnCommitListener(final CommitListener listener) {
    mCommitListeners.put(listener, sEmpty);
  }

  /**
   * Remove listener from list of commits listeners.
   *
   * @param listener instance to remove.
   */
  @SuppressWarnings("unused")
  public void unregisterOnCommitListener(final CommitListener listener) {
    mCommitListeners.remove(listener);
  }

  /**
   * Notify listeners about keys changes. Batch updates.
   *
   * @param keys collection of updated keys.
   */
  private void notifyChangeListeners(@Nullable final List<String> keys) {
    if (null != keys && !keys.isEmpty() && !mListeners.isEmpty()) {
      for (String key : keys) {
        if (null != key) {
          notifyChangeListeners(key);
        }
      }
    }
  }

  /**
   * Notify listeners about preferences updates.
   *
   * @param key modified keys.
   */
  private void notifyChangeListeners(final String key) {
    if (!mListeners.isEmpty()) {
      for (final OnSharedPreferenceChangeListener listener : mListeners.keySet()) {
        if (null != listener) {
          listener.onSharedPreferenceChanged(this, key);
        }
      }
    }
  }

  /**
   * Raise event/callback {@link CommitListener#onCommitEnd(android.content.SharedPreferences.Editor)}.
   *
   * @param editor instance that raising it.
   */
  private void notifyCommitListeners(final Editor editor) {
    notifyCommitListeners(editor, null);
  }

  /**
   * Raise event/callback {@link CommitListener#onCommitStart(android.content.SharedPreferences.Editor, Queue)} if
   * provided two not null parameters, otherwise will be raise {@link CommitListener#onCommitStart(android.content.SharedPreferences.Editor,
   * Queue)}.
   *
   * @param editor instance of the Editor that raise event.
   * @param actions queue of actions for commit.
   */
  private void notifyCommitListeners(final Editor editor, @Nullable final Queue<Action> actions) {
    if (!mCommitListeners.isEmpty()) {
      for (final CommitListener listener : mCommitListeners.keySet()) {
        if (null != listener) {
          if (null == actions) {
            listener.onCommitEnd(editor);
          } else {
            listener.onCommitStart(editor, actions);
          }
        }
      }
    }
  }

	/* ================================= [DIAGNOSTICS] ===================================== */

  /** Do storage's pool cleanup. */
  public static void gc() {
    sPool.clear();
  }

  /** Dump diagnostic information to the logcat. */
  public void dump(final boolean fullDump) {
    Log.d(LOG_TAG, "Current Save Path: " + mFileName.getAbsolutePath());
    Log.d(LOG_TAG, "Memory Pool Size: " + sPool.size());

    if (fullDump) {
      for (String key : sPool.keySet()) {
        Log.v(LOG_TAG, "--> key: " + key);
      }
    }

    Log.d(LOG_TAG, "Changed Version: " + mStorage.Version.get());
    Log.d(LOG_TAG, "Saves DONE: " + sStats.DoneSaves.get());
    Log.d(LOG_TAG, "Saves RESCHEDULED: " + sStats.RescheduledSaves.get());
    Log.d(LOG_TAG, "Saves SKIPPED: " + sStats.SkippedSaves.get());
    Log.d(LOG_TAG, "Saves SCHEDULED: " + sStats.SetSaves.get());
    Log.d(LOG_TAG, "Data map size: " + mStorage.Objects.size());
    Log.d(LOG_TAG, "Apply's: " + sStats.Applies.get());
    Log.d(LOG_TAG, "Commit's: " + sStats.Commits.get());
    Log.d(LOG_TAG, "Memory Commit's: " + sStats.MemoryCommits.get());

    if (fullDump) {
      for (Entry<String, Object> entry : mStorage.Objects.entrySet()) {
        Log.v(LOG_TAG, "--> key: " + entry.getKey() + ", value: " + String.valueOf(entry.getValue()));
      }
    }

    Log.d(LOG_TAG, "Thread Pool Max: " + CPU_COUNT);
    Log.d(LOG_TAG, "Thread Pool Queue Size: " + sQueue.size());

    Log.d(LOG_TAG, "Attached Change Listeners: " + mListeners.size());
    Log.d(LOG_TAG, "Attached Commits Listeners: " + mCommitListeners.size());
    Log.d(LOG_TAG, "Attached Factory: " + getFactory().getClass().getName());
    Log.d(LOG_TAG, "Attached Serializer: " + getSerializer().getClass().getName());
  }

	/* ========================== [NESTED CLASSES DECLARATIONS] ============================ */

  /** Utility interface. Inherit this interface if Action supports Key identifier. */
  public interface SupportsKey {
    /**
     * Get key name.
     *
     * @return key name, otherwise <code>null</code>.
     */
    public String getKey();
  }

  /** Utility interface. Inherit this interface if Action supports Value storage. */
  public interface SupportsValue {
    /**
     * Get value.
     *
     * @return stored value.
     */
    public Object getValue();
  }

  /** Modification Action of the shared preferences. */
  public interface Action {
    /**
     * Get Action type integer identifier.
     *
     * @return type of action identifier. One from list: {@link Factory#TYPE_PUT}, {@link Factory#TYPE_REMOVE} or {@link
     * Factory#TYPE_CLEAR}.
     */
    public int getType();

    /**
     * Request update of the provided storage.
     *
     * @param storage instance of the storage that requesting update.
     */
    void apply(final Editor editor, final Map<String, Object> storage);
  }

  /** Modification actions factory. Known only three types of actions. */
  public interface Factory {
    public static final int TYPE_PUT = 1;
    public static final int TYPE_REMOVE = 2;
    public static final int TYPE_CLEAR = 4;

    /**
     * Get instance of the action.
     *
     * @param type type of action, one from constants: {@link Factory#TYPE_CLEAR}, {@link Factory#TYPE_PUT} or {@link
     * Factory#TYPE_REMOVE}.
     * @param key key name
     * @param value new value
     * @return instance of the class responsible for Memory storage updated.
     */
    @NonNull
    Action action(final int type, final String key, final Object value);
  }

  /** Serialization interface. Should be used for converting data to byte array which we persist. */
  public interface Serialization {
    /**
     * Serialize provided collection of data to byte array.<br/> Always executed in <b>background thread</b>!
     * Implementation of the serialization can throw any runtime exception, that is a signal to the Preferences storage
     * algorithm that required rescheduling of the save operation.
     *
     * @param data data to serialize. Always 'not null'.
     * @return Serialization results. Return <code>null</code> is no file storage is needed (used DB or any other global
     * storage).
     */
    byte[] serialize(final Map<String, ?> data);

    /**
     * De-serialize data from byte array to collection of values.<br/> Always executed in <b>background thread</b>!
     *
     * @param data byte array to de-serialize. Can be <code>null</code>.
     * @return extracted collection of values.
     */
    Map<String, ?> deserialize(final byte[] data);
  }

  /**
   * Listener interface that allows to capture commits to the memory from class side. <p> <i>Note: Editors without
   * actions will not raise commits and will be skipped by Editor logic.</i> </p>
   */
  public interface CommitListener {
    /**
     * On commit operation started. Please keep code in callback as fast as possible. Callback raise inside the LOCK
     * section.
     *
     * @param editor instance that starts commit.
     * @param actions queue of actions that will be committed.
     */
    void onCommitStart(final Editor editor, final Queue<Action> actions);

    /**
     * On done commit to the memory. Please keep code in callback as fast as possible. Callback raise inside the LOCK
     * section.
     *
     * @param editor instance that finish own commit.
     */
    void onCommitEnd(final Editor editor);
  }

  /** Implementation of the Shared Preferences Editor with Commit, Apply and Notifications. */
  private static final class EditorImpl implements SharedPreferences.Editor {
    /** Reference on preferences storage. */
    @Nullable
    private final Storage mStorage;
    /** Reference on actions factory. It cannot be changed during editor existence. */
    @Nullable
    private final Factory mFactory;
    /** Reference on parent instance. */
    private final PreferencesUnified mParent;
    /** Queue of actions done with use of editor. Default preallocation is 16. */
    private final Queue<Action> mActions = new ArrayDeque<Action>();

    /**
     * Construct the editor.
     *
     * @param parent instance of the shared preferences.
     */
    private EditorImpl(final PreferencesUnified parent) {
      mParent = parent;
      mStorage = mParent.mStorage;
      mFactory = mParent.getFactory();
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Editor putString(final String key, final String value) {
      mActions.offer(mFactory.action(Factory.TYPE_PUT, key, value));
      return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Editor putStringSet(final String key, final Set<String> values) {
      mActions.offer(mFactory.action(Factory.TYPE_PUT, key, values));
      return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Editor putInt(final String key, final int value) {
      mActions.offer(mFactory.action(Factory.TYPE_PUT, key, value));
      return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Editor putLong(final String key, final long value) {
      mActions.offer(mFactory.action(Factory.TYPE_PUT, key, value));
      return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Editor putFloat(final String key, final float value) {
      mActions.offer(mFactory.action(Factory.TYPE_PUT, key, value));
      return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Editor putBoolean(final String key, final boolean value) {
      mActions.offer(mFactory.action(Factory.TYPE_PUT, key, value));
      return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Editor remove(final String key) {
      mActions.offer(mFactory.action(Factory.TYPE_REMOVE, key, null));
      return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Editor clear() {
      // optimization: clear actions - reduce number of actions in queue.
      mActions.clear();

      mActions.offer(mFactory.action(Factory.TYPE_CLEAR, null, null));
      return this;
    }

    /** {@inheritDoc} */
    @Override
    public boolean commit() {
      boolean changed = false;

      sStats.Commits.incrementAndGet();

      if (commitToMemory()) {
        changed = mParent.forceCommitToDisk();
      }

      return changed;
    }

    /** {@inheritDoc} */
    @Override
    public void apply() {
      sStats.Applies.incrementAndGet();

      if (commitToMemory()) {
        // do scheduling only if data changes happens
        mParent.scheduleCommitToDisk();
      }
    }

    /**
     * Apply changes on memory map.
     *
     * @return <code>true</code> - changes applied, otherwise <code>false</code>.
     */
    private boolean commitToMemory() {
      int applied = 0;

      sStats.MemoryCommits.incrementAndGet();

      // do cheap check first, than start sync if needed
      if (!mActions.isEmpty()) {
        final List<String> notifications = new ArrayList<String>(mActions.size());

        // notify listeners before we start a transaction
        mParent.notifyCommitListeners(this, mActions);

        // DO memory data updates
        synchronized (mStorage.ModifySync) {
          Action action;

          while (null != (action = mActions.poll())) {
            action.apply(this, mStorage.Objects);

            applied++;

            // if key value exists for action
            if (action instanceof SupportsKey) {
              notifications.add(((SupportsKey) action).getKey());
            }
          }

          // increase version of the data on each update
          mStorage.Version.addAndGet(applied);

          // notify that transaction is done.
          mParent.notifyCommitListeners(this);
        }

        // notify listeners about changed keys
        mParent.notifyChangeListeners(notifications);

        // register shutdown guard
        if (applied != 0 && null == mStorage.Shutdown) {
          synchronized (mStorage) {
            if (null == mStorage.Shutdown) {
              Runtime.getRuntime().addShutdownHook(mStorage.Shutdown = new Thread(mParent));
            }
          }
        }
      }

      return (applied != 0);
    }
  }

}
