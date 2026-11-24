package com.schoste.ddd.util;

import java.io.Closeable;
import java.io.Serializable;

/**
 * Interface to the {@link com.schoste.ddd.util.AbstractCachedObject} class and its concrete implementations.
 * E.g. {@link com.schoste.ddd.util.fs.CachedObjectImpl} - an implementation that serializes objects into a temporary file in the file system (fs)
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
     * @throws Exception re-throws every exception
     */
    public T get() throws Exception;

    /**
     * Write the data of the cached object to the underlying storage.
     * 
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
