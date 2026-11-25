package com.schoste.ddd.util.fs;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import com.schoste.ddd.util.AbstractNestedMap;
import com.schoste.ddd.util.CachedHashMap;
import com.schoste.ddd.util.CachedNestedMap;

/**
 * Implementation of {@link com.schoste.ddd.util.CachedNestedMap} which caches the values in the file system:
 * each level which has values uses a {@link com.schoste.ddd.util.fs.CachedHashMapImpl}, so each value is stored in
 * its own cache file in the storage path.
 * <p>
 * A serialized map contains the keys, the storage path and the paths of the values' cache files (see
 * {@link com.schoste.ddd.util.fs.CachedHashMapImpl}), but not the values. It can only be deserialized where the cache
 * files exist, and while the original map didn't delete them.
 * </p>
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
public class CachedNestedHashMapImpl<K, V extends Serializable> extends AbstractNestedMap<K, V> implements CachedNestedMap<K, V>
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
    public CachedNestedHashMapImpl()
    {
        this(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Creates a new, empty map and allows to specify the folder where to store the values' cache files.
     *
     * @param storagePath the folder of the cache files
     * @throws IllegalArgumentException if storagePath is null
     */
    public CachedNestedHashMapImpl(String storagePath) throws IllegalArgumentException
    {
        if (storagePath == null) throw new IllegalArgumentException("storagePath");

        this.storagePath = storagePath;
    }

    /**
     * Creates a {@link com.schoste.ddd.util.fs.CachedHashMapImpl} in the storage path for the values of a level.
     * Deriving classes may override it, but must return a {@link com.schoste.ddd.util.CachedHashMap}, because
     * {@link #commit()}, {@link #close()} and {@link #cloneValueMap(Map)} use its methods.
     *
     * @return a new, empty {@link com.schoste.ddd.util.fs.CachedHashMapImpl}
     */
    @Override
    protected Map<K, V> createValueMap()
    {
        return new CachedHashMapImpl<K, V>(this.storagePath);
    }

    /**
     * Clones the {@link com.schoste.ddd.util.CachedHashMap} of a level (see
     * {@link com.schoste.ddd.util.CachedHashMap#clone()}), so the clone has its own cache files and instances
     * with the current state of the values.
     *
     * @param valueMap the map of the level to copy
     * @return the clone of the map
     * @throws CloneNotSupportedException if the map could not be cloned
     */
    @SuppressWarnings("unchecked")
    @Override
    protected Map<K, V> cloneValueMap(Map<K, V> valueMap) throws CloneNotSupportedException
    {
        return (Map<K, V>) ((CachedHashMap<K, V>) valueMap).clone();
    }

    /**
     * Saves the values in memory of every level (see {@link com.schoste.ddd.util.CachedHashMap#commit()}).
     *
     * @throws Exception re-throws every exception
     */
    @Override
    public void commit() throws Exception
    {
        for (Map<K, V> valueMap : this.getValueMaps())
        {
            ((CachedHashMap<K, V>) valueMap).commit();
        }
    }

    /**
     * Closes the {@link com.schoste.ddd.util.CachedHashMap} of every level, which deletes the cache files
     * of all values, and empties the map. It tries to close every level; the first {@link IOException} is thrown
     * with the others added as suppressed exceptions.
     *
     * @throws IOException if a cache file could not be deleted
     */
    @Override
    public void close() throws IOException
    {
        IOException firstException = null;

        for (Map<K, V> valueMap : this.getValueMaps())
        {
            try
            {
                ((CachedHashMap<K, V>) valueMap).close();
            }
            catch (IOException e)
            {
                if (firstException == null) firstException = e;
                else firstException.addSuppressed(e);
            }
        }
        this.clear();

        if (firstException != null) throw firstException;
    }
}
