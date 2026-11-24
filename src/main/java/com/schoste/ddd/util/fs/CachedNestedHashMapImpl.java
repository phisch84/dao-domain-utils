package com.schoste.ddd.util.fs;

import java.io.Serializable;

import com.schoste.ddd.util.AbstractNestedMap;

/**
 * Implementation of {@link com.schoste.ddd.util.NestedMap} which caches the values in the file system,
 * using {@link com.schoste.ddd.util.fs.CachedHashMapImpl}.
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
public class CachedNestedHashMapImpl<K, V extends Serializable> extends AbstractNestedMap<K, V>
{
    /**
     * Creates a new, empty map.
     */
    public CachedNestedHashMapImpl() { }
}
