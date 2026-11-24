package com.schoste.ddd.util.fs;

import java.io.Serializable;

import com.schoste.ddd.util.AbstractCachedHashMap;

/**
 * Implementation of the {@link com.schoste.ddd.util.CachedHashMap} interface that uses {@link com.schoste.ddd.util.fs.CachedObjectImpl}
 * to cache the values.
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
// TODO: abstract only until the Map methods are implemented (step "1, 2, 4" of the CachedHashMap plan)
abstract public class CachedHashMapImpl<K, V extends Serializable> extends AbstractCachedHashMap<K, V>
{
    /**
     * Creates a new, empty map.
     */
    public CachedHashMapImpl() { }
}
