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
 * <li>Removing a value, replacing it with {@link #put(Object, Object)}, {@link #clear()} and {@link #close()} free the storage of the affected values.
 *     Close the map when it isn't needed anymore.</li>
 * <li>Null values are not supported, {@link #put(Object, Object)} throws a {@link NullPointerException} for them.</li>
 * <li>Methods of {@link java.util.Map} can't throw checked exceptions; if the storage fails,
 *     they throw an {@link IllegalStateException} with the original exception as cause.</li>
 * </ul>
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
public interface CachedHashMap<K, V extends Serializable> extends Map<K, V>, Cloneable, Closeable
{
    /**
     * Saves all values which are in memory to the storage (see {@link com.schoste.ddd.util.CachedObject#commit()}).
     * Values which were already garbage collected keep their last saved state.
     *
     * @throws Exception re-throws every exception
     */
    public void commit() throws Exception;
}
