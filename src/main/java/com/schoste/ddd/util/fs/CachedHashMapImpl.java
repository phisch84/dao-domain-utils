package com.schoste.ddd.util.fs;

import java.io.Serializable;

import com.schoste.ddd.util.AbstractCachedHashMap;
import com.schoste.ddd.util.CachedObject;

/**
 * Implementation of the {@link com.schoste.ddd.util.CachedHashMap} interface that uses {@link com.schoste.ddd.util.fs.CachedObjectImpl}
 * to cache the values: each value is stored in its own cache file.
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
public class CachedHashMapImpl<K, V extends Serializable> extends AbstractCachedHashMap<K, V>
{
    /**
     * The folder of the values' cache files.
     */
    protected final String storagePath;

    /**
     * Creates a new, empty map which stores the values' cache files in {@code java.io.tmpdir}.
     */
    public CachedHashMapImpl()
    {
        this(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Creates a new, empty map and allows to specify the folder where to store the values' cache files.
     *
     * @param storagePath the folder of the cache files
     * @throws IllegalArgumentException if storagePath is null
     */
    public CachedHashMapImpl(String storagePath) throws IllegalArgumentException
    {
        if (storagePath == null) throw new IllegalArgumentException("storagePath");

        this.storagePath = storagePath;
    }

    /**
     * (non-Javadoc)
     *
     * @see com.schoste.ddd.util.AbstractCachedHashMap#createCachedObject(java.io.Serializable)
     */
    @Override
    protected CachedObject<V> createCachedObject(V value) throws Exception
    {
        return new CachedObjectImpl<V>(value, this.storagePath);
    }
}
