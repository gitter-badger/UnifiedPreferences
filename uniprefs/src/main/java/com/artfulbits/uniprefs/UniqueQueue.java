package com.artfulbits.uniprefs;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Allows to PUT only unique items into Queue. */
/* package */ final class UniqueQueue implements BlockingQueue<Runnable> {
  /** Internal storage. */
  private final LinkedBlockingQueue<Runnable> mQueue = new LinkedBlockingQueue<>();
  /**  */
  private final HashMap<Storage, PreferencesUnified> mLookup = new HashMap<>();

  @NonNull
  @Override
  public Runnable remove() {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Runnable poll() {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Runnable element() {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Runnable peek() {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(Collection<? extends Runnable> collection) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(@NonNull Collection<?> collection) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    return mQueue.isEmpty();
  }

  @NonNull
  @Override
  public Iterator<Runnable> iterator() {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(@NonNull Collection<?> collection) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(@NonNull Collection<?> collection) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return mQueue.size();
  }

  @NonNull
  @Override
  public Object[] toArray() {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public <T> T[] toArray(@NonNull T[] array) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean add(Runnable e) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offer(final Runnable e) {
    final PreferencesUnified pu = (PreferencesUnified) e;

    // TODO: offer only if we have unique save request
    synchronized (mLookup) {
      if (!mLookup.containsKey(pu.mStorage)) {
        PreferencesUnified.sStats.SetSaves.incrementAndGet();

        mLookup.put(pu.mStorage, pu);
        return mQueue.offer(e);
      }

      PreferencesUnified.sStats.SkippedSaves.incrementAndGet();
    }

    return true;
  }

  @Override
  public void put(Runnable e) throws InterruptedException {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offer(Runnable e, long timeout, @NonNull TimeUnit unit) throws InterruptedException {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Runnable take() throws InterruptedException {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public Runnable poll(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
    final Runnable run = mQueue.poll(timeout, unit);
    final PreferencesUnified pu = (PreferencesUnified) run;

    // NOTE: big question why is that really possible??? we never clean that variable. GC what are you doing?
    if (null != mLookup && null != pu && null != pu.mStorage) {
      // remove from lookup map save request
      synchronized (mLookup) {
        mLookup.remove(pu.mStorage);
      }
    }

    return run;
  }

  @Override
  public int remainingCapacity() {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object o) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean contains(Object o) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public int drainTo(Collection<? super Runnable> c) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }

  @Override
  public int drainTo(Collection<? super Runnable> c, int maxElements) {
    Log.e(PreferencesUnified.LOG_TAG, "unexpected call.");
    throw new UnsupportedOperationException();
  }
}
