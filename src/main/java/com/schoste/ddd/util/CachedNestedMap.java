package com.schoste.ddd.util;

import java.io.Closeable;
import java.io.Serializable;

/**
 * A {@link com.schoste.ddd.util.NestedMap} which caches its values in a storage, like a
 * {@link com.schoste.ddd.util.CachedHashMap} for each level: the keys are kept in memory; each value is kept in memory
 * only weakly and is loaded from the storage when it was garbage collected.
 * <p>
 * Rules for using the map, in addition to the ones of {@link com.schoste.ddd.util.NestedMap}:
 * </p>
 * <ul>
 * <li>{@code put} saves the value. To save changes to a value, call {@code put} again with the changed value,
 *     or call {@link #commit()} to save all values in memory. Changes which were not saved are lost once the value
 *     was garbage collected.</li>
 * <li>{@code get} is not guaranteed to return the instance which was put: once the value was garbage collected,
 *     it returns a copy which was loaded from the storage.</li>
 * <li>Removing a value, replacing it with {@code put}, {@link #clear()} and {@link #close()} free the storage of the
 *     affected values. Close the map when it isn't needed anymore.</li>
 * <li>{@link #getSubMap(Object...)} returns a {@link com.schoste.ddd.util.NestedMap} view without {@link #commit()}
 *     and {@link #close()}: these two always act on the whole map. {@code clear()} of a sub map frees the storage of
 *     its values.</li>
 * <li>{@link #clone()} works like {@link com.schoste.ddd.util.CachedHashMap#clone()}: the clone contains the current
 *     state of the values, including changes which were not committed yet, and has its own storage and instances.</li>
 * <li>Like {@link com.schoste.ddd.util.CachedHashMap}, the map serializes only references to the storage of its
 *     values, not the values themselves. Call {@link #commit()} before serializing; the deserialized map shares the
 *     storage with the original one, so closing or removing values in one of them frees the storage of the other
 *     one too.</li>
 * <li>Methods of {@link com.schoste.ddd.util.NestedMap} can't throw checked exceptions; if the storage fails,
 *     they throw an {@link IllegalStateException} with the original exception as cause.</li>
 * </ul>
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
public interface CachedNestedMap<K, V extends Serializable> extends NestedMap<K, V>, Closeable
{
    /**
     * Saves all values of the whole map which are in memory to the storage
     * (see {@link com.schoste.ddd.util.CachedHashMap#commit()}).
     * Values which were already garbage collected keep their last saved state.
     *
     * @throws Exception re-throws every exception
     */
    public void commit() throws Exception;
}
