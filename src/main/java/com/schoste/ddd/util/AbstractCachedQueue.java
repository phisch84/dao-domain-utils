package com.schoste.ddd.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract implementation of a thread-safe FIFO queue whose elements are saved to and loaded from a storage, so that
 * they don't need to be kept in memory (see {@link com.schoste.ddd.util.CachedQueue} for the rules).
 * Each element is wrapped in a {@link com.schoste.ddd.util.CachedObject}; deriving classes only decide how
 * (see {@link #createCachedObject(Serializable)}).
 * <p>
 * The order of the elements ({@link #cachedElements}) is guarded by {@link #lock}. Loading and saving elements happen
 * without holding it, so a slow storage doesn't block other threads; each {@link com.schoste.ddd.util.CachedObject}
 * synchronizes itself. Because of that, an element can be removed and closed by another thread between reading the
 * order and loading the element: {@link #peek()} then retries, and iterators skip the element.
 * The elements are always compared by identity of their {@link com.schoste.ddd.util.CachedObject}, never with
 * {@link com.schoste.ddd.util.CachedObject#equals(Object)}, which compares the elements' content.
 * </p>
 * <p>
 * {@link java.util.AbstractQueue} provides {@code add}, {@code remove()}, {@code element} and {@code addAll} on top of
 * {@link #offer(Serializable)}, {@link #poll()} and {@link #peek()}; {@code contains}, {@code remove(Object)},
 * {@code removeIf} and {@code toArray} of {@link java.util.AbstractCollection} use the {@link #iterator()}.
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
     * Converts an exception of the storage to an unchecked exception, since the methods of {@link java.util.Queue}
     * can't throw checked exceptions.
     */
    private static RuntimeException unchecked(Exception e)
    {
        return (e instanceof RuntimeException) ? (RuntimeException) e : new IllegalStateException(e);
    }

    /**
     * Loads the element of a cached object.
     *
     * @throws IllegalStateException if it can't be loaded, also because it was closed
     */
    private static <E extends Serializable> E load(CachedObject<E> cachedElement) throws IllegalStateException
    {
        try
        {
            return cachedElement.get();
        }
        catch (Exception e)
        {
            throw unchecked(e);
        }
    }

    /**
     * Closes a cached object, which frees its storage.
     *
     * @throws IllegalStateException if closing failed
     */
    private static void close(CachedObject<?> cachedElement) throws IllegalStateException
    {
        try
        {
            cachedElement.close();
        }
        catch (IOException e)
        {
            throw unchecked(e);
        }
    }

    /**
     * Checks, while holding the lock, whether this exact cached object is still in the queue.
     */
    private boolean containsInstance(CachedObject<E> cachedElement)
    {
        this.lock.lock();
        try
        {
            for (CachedObject<E> candidate : this.cachedElements)
            {
                if (candidate == cachedElement) return true;
            }
            return false;
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Removes this exact cached object from the queue, while holding the lock.
     *
     * @return true if it was in the queue
     */
    private boolean removeInstance(CachedObject<E> cachedElement)
    {
        this.lock.lock();
        try
        {
            for (Iterator<CachedObject<E>> candidates = this.cachedElements.iterator(); candidates.hasNext(); )
            {
                if (candidates.next() == cachedElement)
                {
                    candidates.remove();
                    return true;
                }
            }
            return false;
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Removes all elements from the queue, while holding the lock, and returns them in their order.
     */
    private List<CachedObject<E>> drain()
    {
        this.lock.lock();
        try
        {
            List<CachedObject<E>> drained = new ArrayList<>(this.cachedElements);

            this.cachedElements.clear();

            return drained;
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Returns the cached objects of all elements in their order, while holding the lock.
     */
    private List<CachedObject<E>> snapshot()
    {
        this.lock.lock();
        try
        {
            return new ArrayList<>(this.cachedElements);
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Removes all elements and closes them, which frees their storage. Tries to close every element;
     * the first exception is thrown with the others as suppressed exceptions.
     *
     * @throws IOException if closing an element failed
     */
    private void closeAll() throws IOException
    {
        IOException failure = null;

        for (CachedObject<E> cachedElement : this.drain())
        {
            try
            {
                cachedElement.close();
            }
            catch (IOException e)
            {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }

        if (failure != null) throw failure;
    }

    /**
     * Wraps the element in a new {@link com.schoste.ddd.util.CachedObject}, saves it and appends it at the tail.
     * If saving fails, the new {@link com.schoste.ddd.util.CachedObject} is closed and the queue is unchanged.
     *
     * @param element the element to append, not null
     * @return true (the queue has no capacity limit)
     * @throws NullPointerException if element is null
     * @throws IllegalStateException if the element could not be saved
     */
    @Override
    public boolean offer(E element) throws NullPointerException, IllegalStateException
    {
        if (element == null) throw new NullPointerException("element");

        CachedObject<E> cachedElement;

        try
        {
            cachedElement = this.createCachedObject(element);
        }
        catch (Exception e)
        {
            throw unchecked(e);
        }
        try
        {
            cachedElement.commit();
        }
        catch (Exception e)
        {
            // the element never gets into the queue, so nothing else would free its storage
            try
            {
                cachedElement.close();
            }
            catch (IOException closeFailure)
            {
                e.addSuppressed(closeFailure);
            }
            throw unchecked(e);
        }

        this.lock.lock();
        try
        {
            this.cachedElements.addLast(cachedElement);
        }
        finally
        {
            this.lock.unlock();
        }
        return true;
    }

    /**
     * Takes the element at the head, loads it and closes its {@link com.schoste.ddd.util.CachedObject}, which frees
     * its storage. The element is removed and closed also if it can't be loaded.
     *
     * @return the head (possibly a copy loaded from the storage), or null if the queue is empty
     * @throws IllegalStateException if the element could not be loaded or closed
     */
    @Override
    public E poll() throws IllegalStateException
    {
        CachedObject<E> head;

        this.lock.lock();
        try
        {
            head = this.cachedElements.pollFirst();
        }
        finally
        {
            this.lock.unlock();
        }
        if (head == null) return null;

        try
        {
            return load(head);
        }
        finally
        {
            close(head);
        }
    }

    /**
     * Returns the element at the head without removing it. If another thread takes the head while it is being loaded,
     * the new head is returned.
     *
     * @return the head (possibly a copy loaded from the storage), or null if the queue is empty
     * @throws IllegalStateException if the element could not be loaded
     */
    @Override
    public E peek() throws IllegalStateException
    {
        while (true)
        {
            CachedObject<E> head;

            this.lock.lock();
            try
            {
                head = this.cachedElements.peekFirst();
            }
            finally
            {
                this.lock.unlock();
            }
            if (head == null) return null;

            try
            {
                return load(head);
            }
            catch (IllegalStateException e)
            {
                // closed because another thread removed it: try the new head; otherwise it's a storage error
                if (this.containsInstance(head)) throw e;
            }
        }
    }

    /**
     * {@inheritDoc}
     * Doesn't load any element.
     */
    @Override
    public int size()
    {
        this.lock.lock();
        try
        {
            return this.cachedElements.size();
        }
        finally
        {
            this.lock.unlock();
        }
    }

    /**
     * Returns a weakly consistent iterator: it iterates over the elements which were in the queue when it was created,
     * loads them one at a time and keeps no reference to them after returning them. Elements which were removed (and
     * closed) before the iterator reaches them are skipped. {@link Iterator#remove()} removes the element returned last
     * and closes it, which frees its storage.
     *
     * @return an iterator over the elements, head first
     */
    @Override
    public Iterator<E> iterator()
    {
        return new SnapshotIterator(this.snapshot());
    }

    /**
     * The weakly consistent iterator of {@link AbstractCachedQueue#iterator()}.
     */
    private final class SnapshotIterator implements Iterator<E>
    {
        // the cached objects which were in the queue when the iterator was created; released while iterating
        private final List<CachedObject<E>> snapshot;

        private int index;

        // the next element, loaded by hasNext(), and its cached object
        private E nextElement;
        private CachedObject<E> nextCachedElement;

        // the cached object of the element returned last, for remove()
        private CachedObject<E> lastReturned;

        private SnapshotIterator(List<CachedObject<E>> snapshot)
        {
            this.snapshot = snapshot;
        }

        @Override
        public boolean hasNext()
        {
            while (this.nextCachedElement == null && this.index < this.snapshot.size())
            {
                CachedObject<E> candidate = this.snapshot.set(this.index++, null);

                try
                {
                    this.nextElement = load(candidate);
                    this.nextCachedElement = candidate;
                }
                catch (IllegalStateException e)
                {
                    // closed because another thread removed it: skip it; otherwise it's a storage error
                    if (AbstractCachedQueue.this.containsInstance(candidate)) throw e;
                }
            }
            return this.nextCachedElement != null;
        }

        @Override
        public E next()
        {
            if (!this.hasNext()) throw new NoSuchElementException();

            E element = this.nextElement;

            this.lastReturned = this.nextCachedElement;
            this.nextElement = null;
            this.nextCachedElement = null;

            return element;
        }

        @Override
        public void remove()
        {
            if (this.lastReturned == null) throw new IllegalStateException("next() was not called, or remove() was called already");

            CachedObject<E> removed = this.lastReturned;

            this.lastReturned = null;

            if (AbstractCachedQueue.this.removeInstance(removed)) close(removed);
        }
    }

    /**
     * Removes all elements and closes them, which frees their storage. Unlike {@link java.util.AbstractQueue#clear()},
     * it doesn't load the elements.
     *
     * @throws IllegalStateException if closing an element failed, with the first exception as cause
     */
    @Override
    public void clear() throws IllegalStateException
    {
        try
        {
            this.closeAll();
        }
        catch (IOException e)
        {
            throw unchecked(e);
        }
    }

    /**
     * Saves every element which is in memory. Tries to save every element; if saving fails,
     * the first exception is thrown with the others as suppressed exceptions.
     *
     * @see com.schoste.ddd.util.CachedQueue#commit()
     * @throws Exception the first exception of saving an element
     */
    @Override
    public void commit() throws Exception
    {
        Exception failure = null;

        for (CachedObject<E> cachedElement : this.snapshot())
        {
            try
            {
                cachedElement.commit();
            }
            catch (IllegalStateException e)
            {
                // closed because another thread removed it in the meantime: nothing to save
                if (this.containsInstance(cachedElement))
                {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            catch (Exception e)
            {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }

        if (failure != null) throw failure;
    }

    /**
     * Removes all elements and closes them, which frees their storage. The queue can be used again afterwards.
     * Tries to close every element; the first exception is thrown with the others as suppressed exceptions.
     *
     * @throws IOException if closing an element failed
     */
    @Override
    public void close() throws IOException
    {
        this.closeAll();
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
