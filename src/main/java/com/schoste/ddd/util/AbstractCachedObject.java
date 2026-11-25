package com.schoste.ddd.util;

import java.io.Serializable;
import java.lang.ref.WeakReference;

/**
 * Abstract implementation of an object which's data can be saved to and retrieved from a storage so that it doesn't need to be kept in memory.
 * The object to cache is kept in memory with a {@link WeakReference}. Therefore, when using this class in your implementation, make sure to
 * call {@link AbstractCachedObject#commit()} before the last hard reference is lost. Otherwise you may lose the object's data.
 * <p>
 * Deriving classes keep the thread safety rules of {@link CachedObject}: {@link #save(Serializable)} and {@link #load()}
 * are called while holding {@link #objRefLock}, and {@code close()} must set {@link #objRef} to null while holding it.
 * </p>
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
     * Once the instance is published to other threads, read and write it only while holding {@link #objRefLock}.
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

    /**
     * Returns the object if it is still in memory, otherwise loads it with {@link #load()} and keeps it weakly again.
     * Runs while holding {@link #objRefLock}, so concurrent calls get the same instance.
     *
     * @see com.schoste.ddd.util.CachedObject#get()
     * @return the cached object
     * @throws IllegalStateException if the instance was closed or not initialized
     * @throws Exception re-throws every exception of {@link #load()}
     */
    @Override
    public T get() throws IllegalStateException, Exception
    {
        synchronized (this.objRefLock)
        {
            // checked inside the lock, so a concurrent close() can't set objRef to null after the check
            if (this.objRef == null) throw new IllegalStateException();

            T obj = this.objRef.get();

            if (obj == null)
            {
                obj = this.load();

                this.objRef = new WeakReference<T>(obj);
            }

            return obj;
        }
    }

    /**
     * Saves the object with {@link #save(Serializable)} if it is still in memory; does nothing if it was
     * garbage collected (the storage keeps the last saved state). Runs while holding {@link #objRefLock}.
     *
     * @see com.schoste.ddd.util.CachedObject#commit()
     * @throws IllegalStateException if the instance was closed or not initialized
     * @throws Exception re-throws every exception of {@link #save(Serializable)}
     */
    @Override
    public void commit() throws IllegalStateException, Exception
    {
        synchronized (this.objRefLock)
        {
            // checked inside the lock, so a concurrent close() can't set objRef to null after the check
            if (this.objRef == null) throw new IllegalStateException();

            T obj = this.objRef.get();

            // if the object was garbage collected, there is nothing in memory to save
            // and the storage still holds the last committed state
            if (obj != null) this.save(obj);
        }
    }

    /**
     * Creates a clone with its own storage and its own copy of the current object, including changes which were not
     * saved yet (see {@link com.schoste.ddd.util.CachedObject#clone()}). Implemented by the deriving class, because
     * the storage is.
     *
     * @return a clone of this instance
     * @throws CloneNotSupportedException if cloning failed, with the original exception as cause
     */
    @Override
    abstract public Object clone() throws CloneNotSupportedException;

    /**
     * Returns the hash code which the object had when this instance was created (see
     * {@link com.schoste.ddd.util.CachedObject#hashCode()}). Never reads from the storage, so it also works after
     * {@code close()}.
     *
     * @return the hash code of the cached object, obtained when this instance was created
     * @throws IllegalStateException if the deriving class didn't set {@link #objHashCode}
     */
    @Override
    public int hashCode() throws IllegalStateException
    {
        if (this.objHashCode == null) throw new IllegalStateException();

        return this.objHashCode;
    }

    /**
     * Compares the cached objects (see {@link com.schoste.ddd.util.CachedObject#equals(Object)}). Implemented by the
     * deriving class; it must return false, without throwing or writing output, if an object can't be read.
     *
     * @param other the object to compare to
     * @return true if other is a cached object whose object is equal to the object of this instance
     */
    @Override
    abstract public boolean equals(Object other);
}
