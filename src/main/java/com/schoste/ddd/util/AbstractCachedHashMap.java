package com.schoste.ddd.util;

import java.io.Serializable;

/**
 * Abstract implementation of a hash map which's values can be saved to and retrieved from a storage so that it doesn't need to be kept in memory.
 * The values are cached by decorating them as {@link com.schoste.ddd.util.CachedObject}.
 * Whenever {@link java.util.Map#put(Object, Object)}, {@link java.util.Map#putAll(java.util.Map)} and {@link java.util.Map#putIfAbsent(Object, Object)} is called,
 * {@link com.schoste.ddd.util.CachedObject#commit()} is called to persist the values. Therefore an implementation must persist the values
 * whenever it is done with a strong reference to avoid data loss (after GC collection, the last saved object state will be loaded).
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
abstract public class AbstractCachedHashMap<K, V extends Serializable> implements CachedHashMap<K, V>
{
    /**
     * Constructor for deriving classes.
     */
    protected AbstractCachedHashMap() { }
}
