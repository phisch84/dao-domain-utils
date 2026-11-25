package com.schoste.ddd.util.fs;

import java.io.Serializable;

import com.schoste.ddd.util.AbstractCachedHashMap;
import com.schoste.ddd.util.CachedObject;

/**
 * Implementation of the {@link com.schoste.ddd.util.CachedHashMap} interface that uses {@link com.schoste.ddd.util.fs.CachedObjectImpl}
 * to cache the values: each value is stored in its own cache file.
 * <p>
 * A serialized map contains the keys, the storage path and the paths of the values' cache files (see {@link CachedObjectImpl}),
 * but not the values. It can only be deserialized where the cache files exist, and while the original map didn't delete them.
 * </p>
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
public class CachedHashMapImpl<K, V extends Serializable> extends AbstractCachedHashMap<K, V>
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

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
     * Wraps the value in a {@link com.schoste.ddd.util.fs.CachedObjectImpl}, which stores it in its own cache file
     * in the storage path.
     *
     * @param value the value to cache, never null
     * @return a new instance which caches the value
     * @throws Exception re-throws every exception of creating the cache file (e.g. if the value isn't serializable)
     */
    @Override
    protected CachedObject<V> createCachedObject(V value) throws Exception
    {
        return new CachedObjectImpl<V>(value, this.storagePath);
    }
}
