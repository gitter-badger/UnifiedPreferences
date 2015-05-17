package com.artfulbits.uniprefs;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.artfulbits.uniprefs.toolbox.RescheduleException;
import com.artfulbits.uniprefs.toolbox.Types;
import com.artfulbits.uniprefs.toolbox.counters.DbStatistics;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Store preferences in Database storage.<br/> <br/> Main features:<br/> <ol> <li>Storage for data is sqlite
 * database.</li> <li>Incremental load of memory storage updates based on timestamp.</li> <li>All features of the {@link
 * PreferencesUnified} class.</li> </ol> Known problems:<br/> <ul> <li>Timestamp do not allow to recover any info about
 * deleted items. I propose to use {@link Types#DROPPED} flag for those purposes. Instead of dropping the item we will
 * simply update the TYPE column by new value. Feature is not implemented yet.</li> <li>Multiple processes does not have
 * any chance to recognize that storage changed. Maybe should be used cursor listener for that.</li> <li>Apply calls
 * does not guaranty that all transactions will be saved to disk. OS can kill the application before the final
 * transaction is committed.</li> </ul>
 *
 * @author Oleksandr Kucherenko
 * @version 1.0 beta
 */
public final class PreferencesToDb
    implements PreferencesUnified.Serialization, PreferencesUnified.CommitListener {
  /* [ CONSTANTS ] ================================================================================================= */

  /** Statistics calculations. */
  private final static DbStatistics sStats = new DbStatistics();
  /** Scheduled commits counter. */
  private final static AtomicInteger sCommits = new AtomicInteger();
  /**
   * Current version of the disk storage. When all things are saved to disk this value should be equal to the {@link
   * PreferencesToDb#sCommits}. This rule is used for rescheduling serialization operations.
   */
  private final static AtomicInteger sVersion = new AtomicInteger();
  /** Global storage of the commit queues. Used for temporary storing transactions for serialize operation. */
  private final static Map<String, Queue<DbCommit>> sGlobalDiskQueue = new HashMap<>();
  /** Global database access synchronization objects. 'Database Name' - to - 'Synchronization Object Instance'. */
  private final static HashMap<String, Object> sLocks = new HashMap<>();

  /** Declared Table. */
  private interface Tables {
    /** The constant NAME. */
    String NAME = "preferences";
  }

  /** Declared columns. */
  private interface Columns {
    /** The constant ID. */
    String ID = "_id";
    /** The constant KEY. */
    String KEY = "key";
    /** The constant TYPE. */
    String TYPE = "type";
    /** The constant VALUE. */
    String VALUE = "value";
    /** The constant TIMESTAMP. */
    String TIMESTAMP = "time";
  }

	/* [ MEMBERS ] =================================================================================================== */

  /** Instance of the database creation helper. */
  private final DbHelper mDatabase;
  /** Timestamp of last loaded data from DB. */
  private long mTimestamp;

	/* [ CONSTRUCTORS ] ============================================================================================== */

  /**
   * Hidden constructor.
   *
   * @param context application context.
   * @param dbName proposed database name.
   */
  private PreferencesToDb(@NonNull final Context context, @NonNull final String dbName) {
    // do name cleanup
    final String name = new File(dbName).getName();
    final String finalName = name + ".s3db";

    mDatabase = new DbHelper(context, finalName);
  }

	/* [ STATIC METHODS ] ============================================================================================ */

  /**
   * Create a new instance of the shared preferences with DB storage.
   *
   * @param context application context.
   * @param dbName database proposed name.
   * @return instance of the SharedPreferences.
   */
  public static SharedPreferences newInstance(@NonNull final Context context, @NonNull final String dbName) {
    final PreferencesToDb storageInject = new PreferencesToDb(context, dbName);

    final PreferencesUnified shared = new PreferencesUnified(context, dbName, storageInject);
    shared.registerOnCommitListener(storageInject);

    return shared;
  }

	/* [ Interface CommitListener ] ================================================================================== */

  /** {@inheritDoc} */
  @Override
  public void onCommitStart(final Editor editor, final Queue<PreferencesUnified.Action> actions) {
    final DbCommit commit = new DbCommit();

    final Iterator<PreferencesUnified.Action> iterator = actions.iterator();
    while (iterator.hasNext()) {
      final PreferencesUnified.Action action = iterator.next();

      switch (action.getType()) {
        case PreferencesUnified.Factory.TYPE_PUT:
          commit.Actions.add(new DbPutValue(((PreferencesUnified.SupportsKey) action).getKey(),
              ((PreferencesUnified.SupportsValue) action).getValue()));
          break;

        case PreferencesUnified.Factory.TYPE_CLEAR:
          commit.Actions.clear();
          commit.Actions.add(new DbClearValue());
          break;

        case PreferencesUnified.Factory.TYPE_REMOVE:
          commit.Actions.add(new DbRemoveValue(((PreferencesUnified.SupportsKey) action).getKey()));
          break;
      }
    }

    sStats.CommitsCreated.incrementAndGet();

    String key = mDatabase.DatabaseName;
    Queue<DbCommit> queue;

    if (null == (queue = sGlobalDiskQueue.get(key))) {
      synchronized (sGlobalDiskQueue) {
        if (null == (queue = sGlobalDiskQueue.get(key))) {
          sGlobalDiskQueue.put(key, queue = new ConcurrentLinkedQueue<DbCommit>());
        }
      }
    }

    queue.offer(commit);
  }

  /** {@inheritDoc} */
  @Override
  public void onCommitEnd(final Editor editor) {
    // update statistics
    sCommits.incrementAndGet();
  }

	/* [ Interface Serialization ] =================================================================================== */

  /** {@inheritDoc} */
  @Override
  public byte[] serialize(final Map<String, ?> data) {
    try {
      synchronized (mDatabase.ReadWriteLock) {
        saveToDb();
      }
    } catch (final Throwable ignored) {
      // something wrong with database ignore this, something wrong on device side
      if (!(ignored instanceof RescheduleException)) {
        Log.e(PreferencesUnified.LOG_TAG, Log.getStackTraceString(ignored));
      }
    }

    // return NULL, we do not need a file from SharedPreference store algorithm
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, ?> deserialize(final byte[] data) {
    try {
      synchronized (mDatabase.ReadWriteLock) {
        return readFromDb();
      }
    } catch (final Throwable ignored) {
      // something wrong with database ignore this, something wrong on device side
      Log.e(PreferencesUnified.LOG_TAG, Log.getStackTraceString(ignored));
    }

    return null;
  }

	/* [ IMPLEMENTATION & HELPERS ] ================================================================================== */

  /** Dump diagnostic information to the logcat. */
  public void dump() {
    Log.d(PreferencesUnified.LOG_TAG, "-------------------- PreferencesToDb ----------------------------");
    Log.d(PreferencesUnified.LOG_TAG, "Commits done: " + sVersion.get());
    Log.d(PreferencesUnified.LOG_TAG, "Commits created: " + sStats.CommitsCreated.get());
    Log.d(PreferencesUnified.LOG_TAG, "Commits confirmed: " + sCommits.get());
    Log.d(PreferencesUnified.LOG_TAG, "DB updates calls: " + sStats.DbUpdates.get());
    Log.d(PreferencesUnified.LOG_TAG, "Serialize calls: " + sStats.Serialize.get());
    Log.d(PreferencesUnified.LOG_TAG, "commits queue: " + sGlobalDiskQueue.get(mDatabase.DatabaseName).size());
  }

  /**
   * Load data from database. Execution of this method should be guarded by {@link
   * PreferencesToDb.DbHelper#ReadWriteLock}.
   *
   * @return extracted data.
   */
  private Map<String, ?> readFromDb() {
    final Map<String, Object> results = new HashMap<String, Object>();

    long synchTime = mTimestamp;
    final SQLiteDatabase db = mDatabase.getReadableDatabase();
    final String where = "[" + Columns.TIMESTAMP + "] > ?";
    final String whereArgs = "" + synchTime;
    final Cursor cursor = db.query(Tables.NAME, null, where, new String[]{whereArgs}, null, null, null);

    if (cursor.moveToFirst()) {
      final int indexKey = cursor.getColumnIndex(Columns.KEY);
      final int indexType = cursor.getColumnIndex(Columns.TYPE);
      final int indexValue = cursor.getColumnIndex(Columns.VALUE);
      final int indexTime = cursor.getColumnIndex(Columns.TIMESTAMP);

      do {
        final String key = cursor.getString(indexKey);
        final int type = cursor.getInt(indexType);
        final byte[] value = cursor.getBlob(indexValue);
        final long time = cursor.getLong(indexTime);

        // find last update time
        synchTime = Math.max(synchTime, time);

        results.put(key, Types.convertTo(type, value));
      } while (cursor.moveToNext());

      // TODO: use synchTime value for accurate updates
    }

    db.close();

    // store time of last sync
    mTimestamp = System.nanoTime();

    return results;
  }

  /**
   * Save to the database. Execution of this method should be guarded by {@link PreferencesToDb.DbHelper#ReadWriteLock}.
   */
  private void saveToDb() {
    DbCommit commit;

    sStats.Serialize.incrementAndGet();

    final SQLiteDatabase db = mDatabase.getWritableDatabase();
    final String key = mDatabase.DatabaseName;
    final Queue<DbCommit> queue = sGlobalDiskQueue.get(key);

    // try to push as much as possible into one transaction. Otherwise will be a great performance loss.
    db.beginTransaction();

    // process all commit actions from queue on one run
    while (null != (commit = queue.poll())) {
      DbAction action = null;

      while (null != (action = commit.Actions.poll())) {
        action.apply(db, commit.SyncTime);

        sStats.DbUpdates.incrementAndGet();
      }

      sVersion.incrementAndGet();
    }

    db.setTransactionSuccessful();
    db.endTransaction();
    db.close();

    if (sCommits.get() != sVersion.get()) {
      throw new RescheduleException("Reschedule of synchronization job is required." +
          " Expected version does not match current.");
    }
  }

	/* [ NESTED DECLARATIONS ] ======================================================================================= */

  /** Actions that apply changes on DB. */
  private interface DbAction {
    /**
     * Apply action on the DB instance.
     *
     * @param db database instance.
     * @param timestamp timestamp which should be used for storing.
     */
    void apply(final SQLiteDatabase db, final long timestamp);
  }

  /** Number of actions that should be stored into DB with one timestamp. */
  private static final class DbCommit {
    /** Synchronization time which should be used for data updates. */
    public final long SyncTime = System.nanoTime();
    /** List of actions to apply on DB. */
    public final Queue<DbAction> Actions = new ArrayDeque<>();
  }

  /** Insert or Update value in DB. */
  private static final class DbPutValue implements DbAction, PreferencesUnified.SupportsKey, PreferencesUnified.SupportsValue {
    /** The Key. */
    public final String Key;
    /** The Value. */
    public final Object Value;

    /**
     * Instantiates a new Db put value.
     *
     * @param key the key
     * @param value the value
     */
    public DbPutValue(final String key, final Object value) {
      Key = key;
      Value = value;
    }

    /** {@inheritDoc} */
    @Override
    public String getKey() {
      return Key;
    }

    /** {@inheritDoc} */
    @Override
    public final Object getValue() {
      return Value;
    }

    /** {@inheritDoc} */
    @Override
    public void apply(final SQLiteDatabase db, final long timestamp) {
      final int type = Types.getDataType(Value);

      final ContentValues cv = new ContentValues();
      cv.put(Columns.KEY, Key);
      cv.put(Columns.TIMESTAMP, timestamp);
      cv.put(Columns.TYPE, type);
      cv.put(Columns.VALUE, Types.convertTo(type, Value));

      //	alternative: db.insertWithOnConflict(Tables.NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);

      final int affected = db.update(Tables.NAME, cv, "[" + Columns.KEY + "] = ?", new String[]{Key});

      if (0 == affected) {
        db.insert(Tables.NAME, null, cv);
      }
    }
  }

  /** Clear all values in DB. */
  private static final class DbClearValue implements DbAction {
    /** {@inheritDoc} */
    @Override
    public void apply(final SQLiteDatabase db, final long timestamp) {
      db.delete(Tables.NAME, null, null);
    }
  }

  /** Remove value from DB. */
  private static final class DbRemoveValue implements DbAction, PreferencesUnified.SupportsKey {
    /** The Key. */
    public final String Key;

    /**
     * Instantiates a new Db remove value.
     *
     * @param key the key
     */
    public DbRemoveValue(final String key) {
      Key = key;
    }

    /** {@inheritDoc} */
    @Override
    public String getKey() {
      return Key;
    }

    /** {@inheritDoc} */
    @Override
    public void apply(final SQLiteDatabase db, final long timestamp) {
      db.delete(Tables.NAME, "[" + Columns.KEY + "] = ?", new String[]{Key});
    }
  }

  /** Database helper. Controls creation, update/migration operations for storage data structure. */
  private static final class DbHelper extends SQLiteOpenHelper {
    /** User defined database name. */
    public final String DatabaseName;
    /** Synchronization object that guard DB read/write operations. */
    public final Object ReadWriteLock;
    /** Expected database data structure version code. */
    private static final int DB_VERSION = 1;
    /**
     * In API starting from #11 changed logic of the database creation. New additional stage in initialization added.
     */
    private final static boolean IsNewApi = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB);

    /** SQL. Create database table. */
    private static final String sqlTable = String.format(Locale.US,
        "CREATE TABLE [%1$s] ( " +
            "[%2$s] INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
            "[%3$s] NVARCHAR(200) UNIQUE NOT NULL, " +
            "[%4$s] TEXT, " +
            "[%5$s] INTEGER NOT NULL," +
            "[%6$s] INTEGER NOT NULL )",
        Tables.NAME, Columns.ID, Columns.KEY, Columns.VALUE, Columns.TYPE, Columns.TIMESTAMP);
    /** SQL. Create key search index. */
    private static final String sqlIndex1 = String.format(Locale.US,
        "CREATE UNIQUE INDEX [IDX_%1$s_%2$s] ON [%1$s] ( [%2$s] )",
        Tables.NAME, Columns.KEY);
    /** SQL. Create timestamp search index. */
    private static final String sqlIndex2 = String.format(Locale.US,
        "CREATE INDEX [IDX_%1$s_%2$s] ON [%1$s] ( [%2$s] )",
        Tables.NAME, Columns.TIMESTAMP);
    /** SQL. Drop data table. */
    private static final String dropTable = String.format(Locale.US,
        "DROP TABLE [%1$s]",
        Tables.NAME);
    /** SQL. Drop timestamp index. */
    private static final String dropIndex1 = String.format(Locale.US,
        "DROP INDEX [IDX_%1$s_%2$s]",
        Tables.NAME, Columns.TIMESTAMP);
    /** SQL. Drop key search index. */
    private static final String dropIndex2 = String.format(Locale.US,
        "DROP INDEX [IDX_%1$s_%2$s]",
        Tables.NAME, Columns.KEY);
    /** Application context used for initialization. */
    private final Context mContext;

    /**
     * Instantiates a new Db helper.
     *
     * @param context the application context
     * @param name the database file name
     */
    public DbHelper(@NonNull final Context context, @NonNull final String name) {
      super(context, name, null, DB_VERSION);

      DatabaseName = name;
      mContext = context;

      // construct database's read/write synchronization object
      if (!sLocks.containsKey(DatabaseName)) {
        synchronized (sLocks) {
          sLocks.put(DatabaseName, new Object());
        }
      }

      ReadWriteLock = sLocks.get(DatabaseName);
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {
      // http://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html

      db.execSQL(sqlTable);
      db.execSQL(sqlIndex1);
      db.execSQL(sqlIndex2);
    }

    /** {@inheritDoc} */
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db, final int oldVersion, final int newVersion) {
      // just drop them all, dummies action from perspective of correct data migration
      dropAll(db);

      onCreate(db);
    }

    /**
     * Drop all. Drop database scheme.
     *
     * @param db instance of the database.
     */
    private void dropAll(@NonNull final SQLiteDatabase db) {
      db.execSQL(dropIndex1);
      db.execSQL(dropIndex2);

      db.execSQL(dropTable);
    }

    /** {@inheritDoc} */
    @Override
    public SQLiteDatabase getReadableDatabase() {
      try {
        return super.getReadableDatabase();
      } catch (final Throwable ignored) {
        Log.e(PreferencesUnified.LOG_TAG, Log.getStackTraceString(ignored));

        // http://stackoverflow.com/questions/12601874/create-table-android-metadata-failed-in-android-2-3-3
        return getReadableDatabaseNoCollators();
      }
    }

    /**
     * Gets readable database with {@link SQLiteDatabase#NO_LOCALIZED_COLLATORS} flag set.
     *
     * @return the database instance.
     */
    @SuppressLint("NewApi")
    private SQLiteDatabase getReadableDatabaseNoCollators() {
      // SQLiteDatabase.OPEN_READONLY is not used in production according to Google in code comments
      final int flags = SQLiteDatabase.CREATE_IF_NECESSARY |
          SQLiteDatabase.NO_LOCALIZED_COLLATORS |
          SQLiteDatabase.OPEN_READWRITE;

      synchronized (this) {
        final File file = mContext.getDatabasePath(DatabaseName);
        final String path = file.getPath();

        SQLiteDatabase db = SQLiteDatabase.openDatabase(path, null, flags);

        if (IsNewApi) {
          onConfigure(db);
        }

        onVersionCheck(db);
        onOpen(db);

        // NOTE: if we are not able to update mDatabase value of our parent,
        // than in several cases upper code will force recreation of the database and leak of the object
        return tryToTrickParentCache(db);
      }
    }

    /**
     * Try to trick parent cache with use of reflection. try to update private member of the parent class.
     *
     * @param db database instance.
     * @return the same database instance.
     */
    @Nullable
    protected SQLiteDatabase tryToTrickParentCache(@Nullable final SQLiteDatabase db) {
      try {
        final Field field = this.getClass().getField("mDatabase");

        if (null != field) {
          field.setAccessible(true);
          field.set(this, db);
        }
      } catch (final Throwable ignored) {
        // do nothing
        Log.e(PreferencesUnified.LOG_TAG, Log.getStackTraceString(ignored));
      }

      return db;
    }

    /**
     * Check the database version and if its does not match than call upgrade or downgrade for that.
     *
     * @param db database instance.
     */
    @SuppressLint("NewApi")
    protected void onVersionCheck(@NonNull final SQLiteDatabase db) {
      final int version = db.getVersion();
      if (version != DB_VERSION) {
        if (db.isReadOnly()) {
          throw new SQLiteException("Can't upgrade read-only database from version " +
              db.getVersion() + " to " + DB_VERSION + ": " + DatabaseName);
        }

        db.beginTransaction();
        try {
          if (version == 0) {
            onCreate(db);
          } else {
            if (IsNewApi && version > DB_VERSION) {
              onDowngrade(db, version, DB_VERSION);
            } else if (version < DB_VERSION) {
              onUpgrade(db, version, DB_VERSION);
            }
          }

          db.setVersion(DB_VERSION);
          db.setTransactionSuccessful();
        } finally {
          db.endTransaction();
        }
      }
    }
  }
}
