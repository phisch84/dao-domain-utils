package com.schoste.ddd.util;

import java.io.Serializable;
import java.lang.ref.WeakReference;

/**
 * Abstract implementation of an object which's data can be saved to and retrieved from a storage so that it doesn't need to be kept in memory.
 * The object to cache is kept in memory with a {@link WeakReference}. Therefore, when using this class in your implementation, make sure to
 * call {@link AbstractCachedObject#commit()} before the last hard reference is lost. Otherwise you may lose the object's data.
 *
 * @param <T> the class of the object that is being cached.
 */
abstract public class AbstractCachedObject<T extends Serializable> implements CachedObject<T>
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

    /**
     * Weak reference to the cached object, so that the garbage collector may reclaim it.
     * Is null if the instance was not initialized or was closed.
     */
    protected transient WeakReference<T> objRef;

    /**
     * Lock to synchronize access to {@link #objRef}.
     */
    protected transient Object objRefLock = new Object();

    /**
     * Hash code of the cached object, obtained when the cached instance was created.
     * It is never recomputed, see {@link CachedObject#hashCode()}.
     */
    protected transient Integer objHashCode = null;

    /**
     * Constructor for deriving classes, which must initialize {@link #objRef} and {@link #objHashCode}.
     */
    protected AbstractCachedObject() { }

    /**
     * The method to actually write the object data to the storage (the cache).
     * The persistence logic must be implemented by the deriving class.
     *
     * @param obj the object to cache
     * @throws Exception re-throws every exception
     */
    abstract protected void save(T obj) throws Exception;

    /**
     * The method to actually read the object data from the storage (the cache).
     * The persistence logic must be implemented by the deriving class.
     *
     * @return the cached object's data
     * @throws Exception re-throws every exception
     */
    abstract protected T load() throws Exception;

    /**    (non-Javadoc)
     *
     * @see com.schoste.ddd.util.CachedObject#get()
     */
    public T get() throws IllegalStateException, Exception
    {
        if (this.objRef == null) throw new IllegalStateException();

        synchronized (this.objRefLock)
        {
            T obj = this.objRef.get();

            if (obj == null)
            {
                obj = this.load();

                this.objRef = new WeakReference<T>(obj);
            }

            return obj;
        }
    }

    /**    (non-Javadoc)
     *
     * @see com.schoste.ddd.util.CachedObject#commit()
     */
    public void commit() throws IllegalStateException, Exception
    {
        if (this.objRef == null) throw new IllegalStateException();

        synchronized (this.objRefLock)
        {
            T obj = this.objRef.get();

            // if the object was garbage collected, there is nothing in memory to save
            // and the storage still holds the last committed state
            if (obj != null) this.save(obj);
        }
    }

    /**    (non-Javadoc)
     *
     * @see java.lang.Object#clone()
     */
    @Override
    abstract public Object clone() throws CloneNotSupportedException;

    /**    (non-Javadoc)
     *
     * @see com.schoste.ddd.util.CachedObject#hashCode()
     */
    @Override
    public int hashCode() throws IllegalStateException
    {
        if (this.objHashCode == null) throw new IllegalStateException();

        return this.objHashCode;
    }

    /**    (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    abstract public boolean equals(Object other);
}
