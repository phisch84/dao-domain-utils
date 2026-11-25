package com.schoste.ddd.util;

import java.io.Closeable;
import java.io.Serializable;

/**
 * Interface to the {@link com.schoste.ddd.util.AbstractCachedObject} class and its concrete implementations.
 * E.g. {@link com.schoste.ddd.util.fs.CachedObjectImpl} - an implementation that serializes objects into a temporary file in the file system (fs)
 * <p>
 * <b>Thread safety</b>: one instance may be shared by several threads, with these limits:
 * </p>
 * <ul>
 * <li>{@link #get()}, {@link #commit()} and {@link #close()} synchronize on the instance.
 *     While the object is in memory, concurrent calls of {@link #get()} return the same instance;
 *     loading and saving of one instance never overlap. After {@link #close()}, also if another thread called it,
 *     {@link #get()} and {@link #commit()} throw {@link IllegalStateException}.</li>
 * <li>The cached object itself is not protected. Callers synchronize changes to it themselves, and must not change it
 *     while another thread calls {@link #commit()} or {@link #clone()}: serializing an object that is being changed
 *     may fail (e.g. with {@link java.util.ConcurrentModificationException}) or save an inconsistent state.</li>
 * <li>Instances that share a storage, e.g. an instance and its deserialized copy, don't synchronize with each other:
 *     {@link #get()} on one while the other saves may load an incomplete state.
 *     Share one instance between threads instead of copies.</li>
 * <li>Hand an instance to other threads safely (e.g. through a concurrent collection or a {@code volatile} field),
 *     because its fields are not {@code final}.</li>
 * </ul>
 * <p>
 * Collections built on cached objects, like {@link com.schoste.ddd.util.CachedHashMap}, are not thread-safe.
 * </p>
 *
 * @param <T> the serializable class of object instances to hold and cache
 */
public interface CachedObject<T> extends Cloneable, Closeable, Serializable
{
    /**
     * Gets the cached object. If the object doesn't exist in memory (yet or anymore), it is loaded from the storage into memory and then returned.
     * Therefore it is not guaranteed that subsequent calls to this method will return the same instance.
     *
     * @return an instance of the cached object
     * @throws IllegalStateException if the instance was closed or not initialized
     * @throws Exception re-throws every exception
     */
    public T get() throws Exception;

    /**
     * Write the data of the cached object to the underlying storage.
     * <p>
     * If the cached object is no longer in memory (it was garbage collected), there is nothing to write
     * and this method does nothing: the storage still holds the last committed state.
     * Therefore, changes to the cached object must be committed while a reference to it is still held,
     * otherwise they are lost.
     * </p>
     *
     * @throws IllegalStateException if the instance was closed or not initialized
     * @throws Exception re-throws every exception
     */
    public void commit() throws Exception;

    /**
     * Implements {@link java.lang.Object#clone()}.
     * The clone caches its own copy of the object in its own storage.
     *
     * @return a clone of this instance
     * @throws CloneNotSupportedException if the instance could not be cloned
     */
    public Object clone() throws CloneNotSupportedException;

    /**
     * Implements {@link java.lang.Object#hashCode()}.
     * The hash code is obtained once when the cached instance is created and is never recomputed,
     * so this method never reads from the storage and also works after {@link #close()}.
     * <p>
     * Therefore, changes to the cached object must not change its hash code
     * (the same rule as for keys of a {@link java.util.HashMap}).
     * Otherwise two instances may be {@link #equals(Object) equal} but have different hash codes,
     * which breaks the contract of {@link java.lang.Object#hashCode()}.
     * </p>
     *
     * @return the hash code of the cached object that was obtained when the cached instance was created.
     * @throws IllegalStateException if the implementing class failed to register a hash code in its constructor
     */
    public int hashCode() throws IllegalStateException;

    /**
     * Implements {@link java.lang.Object#equals(Object)}.
     * Two references of a {@link com.schoste.ddd.util.CachedObject} are considered equal if their cached objects are equal.
     *
     * @param other the other object to compare to
     * @return true if the cached object of other is equal to the cached object of this. False otherwise
     */
    public boolean equals(Object other);
}
