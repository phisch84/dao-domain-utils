package com.schoste.ddd.util;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Map;

/**
 * Interface to the {@link com.schoste.ddd.util.AbstractCachedHashMap} class, an implementation of a {@link java.util.Map} that caches values by storing them as
 * {@link com.schoste.ddd.util.CachedObject}.
 * The keys are kept in memory; each value is kept in memory only weakly and is loaded from the storage when it was garbage collected.
 * <p>
 * Rules for using the map, which follow from {@link com.schoste.ddd.util.CachedObject}:
 * </p>
 * <ul>
 * <li>{@link #put(Object, Object)} saves the value. To save changes to a value, call {@link #put(Object, Object)} again with the changed value,
 *     or call {@link #commit()} to save all values in memory. Changes which were not saved are lost once the value was garbage collected.</li>
 * <li>Methods which return values (e.g. {@link #get(Object)}) are not guaranteed to return the instance which was put:
 *     once the value was garbage collected, they return a copy which was loaded from the storage.</li>
 * <li>Methods which need all values ({@link #values()}, {@link #containsValue(Object)}, {@link #equals(Object)}, {@link #hashCode()},
 *     {@link java.lang.Object#toString() toString()}, and iterating or streaming {@link #values()} and {@link #entrySet()}) load the values one at a time
 *     and keep no references to them, so only the values the caller holds stay in memory. Each call reads all values from the storage, though,
 *     which is slow for large maps. Methods which only need the keys ({@link #size()}, {@link #containsKey(Object)}, {@link #keySet()},
 *     {@link java.util.Map.Entry#getKey()}) don't load any value.</li>
 * <li>Removing a value, replacing it with {@link #put(Object, Object)}, {@link #clear()} and {@link #close()} free the storage of the affected values.
 *     Close the map when it isn't needed anymore.</li>
 * <li>Null values are not supported, {@link #put(Object, Object)} throws a {@link NullPointerException} for them.</li>
 * <li>The map is {@link java.io.Serializable}, but like {@link com.schoste.ddd.util.CachedObject} it serializes only references to the
 *     storage of its values, not the values themselves (see the implementations for details). Therefore
 *     <ul>
 *     <li>call {@link #commit()} before serializing, because changes which were not saved are not included,</li>
 *     <li>the deserialized map shares the storage with the original one: closing or removing values in one of them
 *         frees the storage of the other one too,</li>
 *     <li>the keys must be {@link java.io.Serializable}, otherwise serializing fails with a {@link java.io.NotSerializableException}.</li>
 *     </ul>
 * </li>
 * <li>Methods of {@link java.util.Map} can't throw checked exceptions; if the storage fails,
 *     they throw an {@link IllegalStateException} with the original exception as cause.</li>
 * <li>The map is not thread-safe (like {@link java.util.HashMap}); synchronize access from several threads.</li>
 * </ul>
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
public interface CachedHashMap<K, V extends Serializable> extends Map<K, V>, Cloneable, Closeable, Serializable
{
    /**
     * Saves all values which are in memory to the storage (see {@link com.schoste.ddd.util.CachedObject#commit()}).
     * Values which were already garbage collected keep their last saved state.
     * If saving a value fails, the other values are still saved; the first exception is thrown
     * with the others as suppressed exceptions.
     *
     * @throws Exception the first exception of saving a value
     */
    public void commit() throws Exception;

    /**
     * Implements {@link java.lang.Object#clone()}.
     * The clone contains the current state of the values (see {@link #get(Object)}), including changes which were not committed yet.
     * It has its own storage and its own instances of the values, so changes to one map or its values do not affect the other.
     * Like the methods which need all values, it loads the values one at a time.
     *
     * @return a clone of this instance
     * @throws CloneNotSupportedException if the instance could not be cloned, with the original exception as cause
     */
    public Object clone() throws CloneNotSupportedException;
}
