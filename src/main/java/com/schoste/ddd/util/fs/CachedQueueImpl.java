package com.schoste.ddd.util.fs;

import java.io.Serializable;

import com.schoste.ddd.util.AbstractCachedQueue;
import com.schoste.ddd.util.CachedObject;

/**
 * Implementation of {@link com.schoste.ddd.util.CachedQueue} which caches the elements in the file system:
 * each element is stored in its own cache file (see {@link com.schoste.ddd.util.fs.CachedObjectImpl}) in the storage path.
 *
 * @param <E> the class of the queue's elements
 */
public class CachedQueueImpl<E extends Serializable> extends AbstractCachedQueue<E>
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

    /**
     * The folder of the elements' cache files.
     */
    protected final String storagePath;

    /**
     * Creates a new, empty queue which stores the elements' cache files in {@code java.io.tmpdir}.
     */
    public CachedQueueImpl()
    {
        this(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Creates a new, empty queue and allows to specify the folder where to store the elements' cache files.
     *
     * @param storagePath the folder of the cache files
     * @throws IllegalArgumentException if storagePath is null
     */
    public CachedQueueImpl(String storagePath) throws IllegalArgumentException
    {
        if (storagePath == null) throw new IllegalArgumentException("storagePath");

        this.storagePath = storagePath;
    }

    /**
     * Wraps the element in a {@link com.schoste.ddd.util.fs.CachedObjectImpl}, which stores it in its own cache file
     * in the storage path.
     *
     * @param element the element to cache, never null
     * @return a new instance which caches the element
     * @throws Exception re-throws every exception of creating the cache file (e.g. if the element isn't serializable)
     */
    @Override
    protected CachedObject<E> createCachedObject(E element) throws Exception
    {
        return new CachedObjectImpl<E>(element, this.storagePath);
    }
}
