package com.schoste.ddd.util;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Queue;

/**
 * Interface of a thread-safe FIFO {@link java.util.Queue} that caches its elements by storing each one as a
 * {@link com.schoste.ddd.util.CachedObject}: an element is kept in memory only weakly and is loaded from the storage
 * when it was garbage collected. It is not blocking: {@link #poll()} returns null for an empty queue instead of waiting
 * (like {@link java.util.concurrent.ConcurrentLinkedQueue}).
 * <p>
 * Rules for using the queue, which follow from {@link com.schoste.ddd.util.CachedObject}:
 * </p>
 * <ul>
 * <li>{@link #offer(Object)} and {@link #add(Object)} append the element at the tail and save it;
 *     {@link #poll()} and {@link #remove()} take the element at the head. The queue has no capacity limit.</li>
 * <li>To save changes to an element which is in the queue, call {@link #commit()} while you still hold a reference
 *     to it. Changes which were not saved are lost once the element was garbage collected.</li>
 * <li>Methods which return elements (e.g. {@link #poll()}, {@link #peek()}) are not guaranteed to return the instance
 *     which was added: once it was garbage collected, they return a copy which was loaded from the storage.</li>
 * <li>Methods which need all elements ({@link #contains(Object)}, {@link #toArray()}, iterating, streaming,
 *     {@link java.lang.Object#toString() toString()}) load the elements one at a time and keep no references to them.
 *     Each call reads all elements from the storage, though, which is slow for large queues. {@link #size()} and
 *     {@link #isEmpty()} don't load any element.</li>
 * <li>Removing an element ({@link #poll()}, {@link #remove()}, {@link #remove(Object)}, {@link #removeIf(java.util.function.Predicate)},
 *     the iterator's {@code remove()}, ...), {@link #clear()} and {@link #close()} free the storage of the removed elements.
 *     Close the queue when it isn't needed anymore.</li>
 * <li>Null elements are not supported: {@link #offer(Object)} and {@link #add(Object)} throw a {@link NullPointerException}.</li>
 * <li>The queue is thread-safe: all methods may be called from several threads at the same time. Each method which
 *     adds, takes or removes one element is atomic, e.g. two threads never {@link #poll()} the same element. Bulk
 *     methods ({@link #addAll(java.util.Collection)}, {@link #removeAll(java.util.Collection)}, ...) are not atomic
 *     as a whole. Changes to the elements themselves are not synchronized by the queue.</li>
 * <li>Iterators are weakly consistent (like those of {@link java.util.concurrent.ConcurrentLinkedQueue}): an iterator
 *     returns the elements which were in the queue when it was created, in their order, and never throws a
 *     {@link java.util.ConcurrentModificationException}. Elements which another thread removed in the meantime are
 *     skipped; elements added in the meantime are not returned.</li>
 * <li>The queue is {@link java.io.Serializable}, but like {@link com.schoste.ddd.util.CachedObject} it serializes only
 *     references to the storage of its elements, not the elements themselves (see the implementations for details).
 *     Therefore call {@link #commit()} before serializing, and note that the deserialized queue shares the storage with
 *     the original one: removing elements from one of them frees the storage of the other one too.</li>
 * <li>{@link java.lang.Object#equals(Object) equals(Object)} and {@link java.lang.Object#hashCode() hashCode()} are
 *     those of {@link java.lang.Object} (identity), like for {@link java.util.ArrayDeque} and
 *     {@link java.util.concurrent.ConcurrentLinkedQueue}: a queue is not equal to another queue with the same elements.</li>
 * <li>Methods of {@link java.util.Queue} can't throw checked exceptions; if the storage fails,
 *     they throw an {@link IllegalStateException} with the original exception as cause.</li>
 * </ul>
 *
 * @param <E> the class of the queue's elements, each one inside of a {@link com.schoste.ddd.util.CachedObject}
 */
public interface CachedQueue<E extends Serializable> extends Queue<E>, Cloneable, Closeable, Serializable
{
    /**
     * Saves all elements which are in memory to the storage (see {@link com.schoste.ddd.util.CachedObject#commit()}).
     * Elements which were already garbage collected keep their last saved state.
     * If saving an element fails, the other elements are still saved; the first exception is thrown
     * with the others as suppressed exceptions.
     *
     * @throws Exception the first exception of saving an element
     */
    public void commit() throws Exception;

    /**
     * Implements {@link java.lang.Object#clone()}.
     * The clone contains the elements in their current state and order, including changes which were not committed yet.
     * It has its own storage and its own instances of the elements, so changes to one queue or its elements do not
     * affect the other. Like the methods which need all elements, it loads the elements one at a time.
     *
     * @return a clone of this instance
     * @throws CloneNotSupportedException if the instance could not be cloned, with the original exception as cause
     */
    public Object clone() throws CloneNotSupportedException;
}
