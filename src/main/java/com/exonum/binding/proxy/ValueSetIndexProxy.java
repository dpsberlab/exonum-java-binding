package com.exonum.binding.proxy;

import static com.exonum.binding.proxy.StoragePreconditions.checkCanModify;
import static com.exonum.binding.proxy.StoragePreconditions.checkIndexPrefix;
import static com.exonum.binding.proxy.StoragePreconditions.checkStorageKey;
import static com.exonum.binding.proxy.StoragePreconditions.checkStorageValue;
import static com.exonum.binding.proxy.StoragePreconditions.checkValid;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A value set is an index that contains no duplicate elements (values).
 * This implementation does not permit null elements.
 *
 * <p>The elements are stored in the underlying database as values,
 * whereas their cryptographic hashes are used as keys, making this set implementation
 * more suitable for storing large elements. If your application has <em>small</em> elements and
 * does not need to perform set operations by hashes of the elements,
 * consider using a {@link KeySetIndexProxy}.
 *
 * <p>The "destructive" methods of the set, i.e., the ones that change its contents,
 * are specified to throw {@link UnsupportedOperationException} if the set has been created with
 * a read-only database view.
 *
 * <p>As any native proxy, the set <em>must be closed</em> when no longer needed.
 * Subsequent use of the closed set is prohibited and will result in {@link IllegalStateException}.
 *
 * @see KeySetIndexProxy
 */
public class ValueSetIndexProxy extends AbstractIndexProxy {

  /**
   * Creates a new value set proxy.
   *
   * @param prefix a set prefix — a unique identifier of this set in the underlying storage
   * @param view a database view. Must be valid. If a view is read-only,
   *             "destructive" operations are not permitted.
   * @throws IllegalStateException if the view is not valid
   * @throws IllegalArgumentException if the prefix has zero size
   * @throws NullPointerException if any argument is null
   */
  ValueSetIndexProxy(byte[] prefix, View view) {
    super(nativeCreate(checkIndexPrefix(prefix), view.getNativeHandle()), view);
  }

  /**
   * Adds a new element to the set. The method has no effect if
   * the set already contains such element.
   *
   * @param e an element to add
   * @throws NullPointerException if the element is null
   * @throws IllegalStateException if this set is not valid
   * @throws UnsupportedOperationException if this set is read-only
   */
  public void add(byte[] e) {
    notifyModified();
    nativeAdd(getNativeHandle(), checkStorageKey(e));
  }

  /**
   * Removes all of the elements from this set.
   * The set will be empty after this method returns.
   *
   * @throws IllegalStateException if this set is not valid
   * @throws UnsupportedOperationException if this set is read-only
   */
  public void clear() {
    notifyModified();
    nativeClear(getNativeHandle());
  }

  /**
   * Returns true if this set contains the specified element.
   *
   * @throws NullPointerException if the element is null
   * @throws IllegalStateException if this set is not valid
   * @see #containsByHash(byte[])
   */
  public boolean contains(byte[] e) {
    return nativeContains(getNativeHandle(), checkStorageKey(e));
  }

  /**
   * Returns true if this set contains an element with the specified hash.
   *
   * @param elementHash a hash of an element
   * @throws NullPointerException if the hash is null
   * @throws IllegalStateException if this set is not valid
   */
  public boolean containsByHash(byte[] elementHash) {
    return nativeContainsByHash(getNativeHandle(), checkNotNull(elementHash));
  }

  /**
   * Creates an iterator over the hashes of the elements in this set.
   * The hashes are ordered lexicographically.
   *
   * <p>Any destructive operation on the same {@link Fork} this set uses
   * (but not necessarily on <em>this set</em>) will invalidate the iterator.
   *
   * @return an iterator over the hashes of the elements in this set
   * @throws IllegalStateException if this set is not valid
   */
  public RustIter<byte[]> hashes() {
    return new ConfigurableRustIter<>(
        nativeCreateHashIterator(getNativeHandle()),
        this::nativeHashIteratorNext,
        this::nativeHashIteratorFree,
        dbView,
        modCounter);
  }

  /**
   * Returns an iterator over the entries of this set. An entry is a hash-value pair.
   * The entries are ordered by keys lexicographically.
   *
   * <p>Any destructive operation on the same {@link Fork} this set uses
   * (but not necessarily on <em>this set</em>) will invalidate the iterator.
   *
   * @return an iterator over the entries of this set
   * @throws IllegalStateException if this set is not valid
   */
  public RustIter<Entry> iterator() {
    return new ConfigurableRustIter<>(
        nativeCreateIterator(getNativeHandle()),
        this::nativeIteratorNext,
        this::nativeIteratorFree,
        dbView,
        modCounter
    );
  }

  private native long nativeCreateIterator(long nativeHandle);

  private native Entry nativeIteratorNext(long iterNativeHandle);

  private native void nativeIteratorFree(long iterNativeHandle);

  /**
   * An entry of a value set index: a hash-value pair.
   *
   * <p>An entry contains <em>a copy</em> of the data in the value set index.
   * It does not reflect the changes made to the index since this entry had been created.
   */
  public static class Entry {
    private final byte[] hash;
    private final byte[] value;

    @SuppressWarnings("unused")  // native API
    private Entry(byte[] hash, byte[] value) {
      this.hash = checkNotNull(hash);
      this.value = checkStorageValue(value);
    }

    /**
     * Returns a hash of the element of the set.
     */
    public byte[] getHash() {
      return hash;
    }

    /**
     * Returns an element of the set.
     */
    public byte[] getValue() {
      return value;
    }
  }

  /**
   * Removes the element from this set. If it's not in the set, does nothing.
   *
   * @param e an element to remove.
   * @throws NullPointerException if the element is null
   * @throws IllegalStateException if this set is not valid
   * @throws UnsupportedOperationException if this set is read-only
   */
  public void remove(byte[] e) {
    notifyModified();
    nativeRemove(getNativeHandle(), checkStorageKey(e));
  }

  /**
   * Removes an element from this set by its hash. If there is no such element in the set,
   * does nothing.
   *
   * @param elementHash the hash of an element to remove.
   * @throws NullPointerException if the hash is null
   * @throws IllegalStateException if this set is not valid
   * @throws UnsupportedOperationException if this set is read-only
   */
  public void removeByHash(byte[] elementHash) {
    notifyModified();
    nativeRemoveByHash(getNativeHandle(), checkNotNull(elementHash));
  }

  @Override
  void disposeInternal() {
    checkValid(dbView);
    nativeFree(getNativeHandle());
  }

  private static native long nativeCreate(byte[] setPrefix, long viewNativeHandle);

  private native void nativeAdd(long nativeHandle, byte[] e);

  private native void nativeClear(long nativeHandle);

  private native boolean nativeContains(long nativeHandle, byte[] e);

  private native boolean nativeContainsByHash(long nativeHandle, byte[] elementHash);

  private native long nativeCreateHashIterator(long nativeHandle);

  private native byte[] nativeHashIteratorNext(long iterNativeHandle);

  private native void nativeHashIteratorFree(long iterNativeHandle);

  private native void nativeRemove(long nativeHandle, byte[] e);

  private native void nativeRemoveByHash(long nativeHandle, byte[] elementHash);

  private native void nativeFree(long nativeHandle);
}