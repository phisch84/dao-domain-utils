package com.schoste.ddd.util.fs;

import java.io.Serializable;
import java.util.Map;

import com.schoste.ddd.util.AbstractNestedMap;

/**
 * Implementation of {@link com.schoste.ddd.util.NestedMap} which caches the values in the file system,
 * using {@link com.schoste.ddd.util.fs.CachedHashMapImpl}.
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
// TODO: storage path, commit(), close() and serialization are decided in the next step
public class CachedNestedHashMapImpl<K, V extends Serializable> extends AbstractNestedMap<K, V>
{
    /**
     * Creates a new, empty map.
     */
    public CachedNestedHashMapImpl() { }

    /**
     * Creates a {@link com.schoste.ddd.util.fs.CachedHashMapImpl} for the values of a level.
     *
     * @return a new, empty {@link com.schoste.ddd.util.fs.CachedHashMapImpl}
     */
    @Override
    protected Map<K, V> createValueMap()
    {
        return new CachedHashMapImpl<K, V>();
    }
}
