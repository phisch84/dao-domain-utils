package com.schoste.ddd.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract implementation of a thread-safe FIFO queue whose elements are saved to and loaded from a storage, so that
 * they don't need to be kept in memory (see {@link com.schoste.ddd.util.CachedQueue} for the rules).
 * Each element is wrapped in a {@link com.schoste.ddd.util.CachedObject}; deriving classes only decide how
 * (see {@link #createCachedObject(Serializable)}).
 * <p>
 * Not implemented yet: the methods of the queue throw {@link UnsupportedOperationException}.
 * </p>
 *
 * @param <E> the class of the queue's elements, each one inside of a {@link com.schoste.ddd.util.CachedObject}
 */
abstract public class AbstractCachedQueue<E extends Serializable> extends AbstractQueue<E> implements CachedQueue<E>
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

    /**
     * The elements of the queue in their order (head first), each one wrapped in a {@link com.schoste.ddd.util.CachedObject}.
     * Read and change it only while holding {@link #lock}.
     * Not final, because {@link #clone()} replaces it in the clone.
     */
    protected ArrayDeque<CachedObject<E>> cachedElements = new ArrayDeque<>();

    /**
     * Guards the order of the elements ({@link #cachedElements}). Loading and saving elements happen without holding it.
     * Not final, because {@link #clone()} gives the clone its own lock.
     */
    protected ReentrantLock lock = new ReentrantLock();

    /**
     * Constructor for deriving classes.
     */
    protected AbstractCachedQueue() { }

    /**
     * Wraps an element in a new {@link com.schoste.ddd.util.CachedObject}.
     * The caching logic must be implemented by the deriving class.
     *
     * @param element the element to cache, never null
     * @return a new instance which caches the element
     * @throws Exception re-throws every exception
     */
    abstract protected CachedObject<E> createCachedObject(E element) throws Exception;

    /**
     * Not implemented yet.
     *
     * @param element the element to append
     * @return never
     * @throws UnsupportedOperationException always
     */
    @Override
    public boolean offer(E element) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Not implemented yet.
     *
     * @return never
     * @throws UnsupportedOperationException always
     */
    @Override
    public E poll() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Not implemented yet.
     *
     * @return never
     * @throws UnsupportedOperationException always
     */
    @Override
    public E peek() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Not implemented yet.
     *
     * @return never
     * @throws UnsupportedOperationException always
     */
    @Override
    public int size() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Not implemented yet.
     *
     * @return never
     * @throws UnsupportedOperationException always
     */
    @Override
    public Iterator<E> iterator() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Not implemented yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void commit() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Not implemented yet.
     *
     * @throws IOException never
     * @throws UnsupportedOperationException always
     */
    @Override
    public void close() throws IOException, UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Not implemented yet.
     *
     * @return never
     * @throws CloneNotSupportedException always
     */
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        throw new CloneNotSupportedException("not implemented yet");
    }
}
