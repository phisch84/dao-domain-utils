package com.schoste.ddd.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double of a {@link CachedObject} which holds its value in memory (no storage) and can be made to fail,
 * so tests can check how the classes built on cached objects handle storage errors.
 * <p>
 * Set one of the {@code failOn*} fields to make the next call of that method throw the given exception.
 * After {@link #close()} succeeded, {@link #get()} and {@link #commit()} throw {@link IllegalStateException},
 * like a real implementation.
 * </p>
 *
 * @param <V> the class of the cached value
 */
public class FailingCachedObject<V extends Serializable> implements CachedObject<V>
{
    private static final long serialVersionUID = 1L;

    private final V value;

    /**
     * If not null, {@link #get()} throws it.
     */
    public Exception failOnGet;

    /**
     * If not null, {@link #commit()} throws it.
     */
    public Exception failOnCommit;

    /**
     * If not null, {@link #close()} throws it and the instance stays open.
     */
    public IOException failOnClose;

    /**
     * If not null, {@link #clone()} throws it.
     */
    public CloneNotSupportedException failOnClone;

    /**
     * Passed to {@link #failOnClose} of every clone created by {@link #clone()}.
     */
    public IOException failOnCloseOfClones;

    /**
     * How often {@link #commit()} was called, also if it failed.
     */
    public int commitCalls;

    /**
     * True once {@link #close()} was called, also if it failed.
     */
    public boolean closeCalled;

    /**
     * How often {@link #close()} was called, also if it failed.
     */
    public int closeCalls;

    /**
     * If not null, runs once at the start of the next {@link #get()}, {@link #commit()} or {@link #clone()}
     * (and is then reset to null).
     * Lets a single-threaded test simulate another thread which acts just while this object is being read or saved,
     * e.g. removes it from its queue.
     */
    public Runnable beforeNextAccess;

    /**
     * True once {@link #close()} succeeded.
     */
    public boolean closed;

    /**
     * The clones created by {@link #clone()}.
     */
    public final List<FailingCachedObject<V>> clones = new ArrayList<>();

    /**
     * Creates an instance which holds the value.
     *
     * @param value the value
     */
    public FailingCachedObject(V value)
    {
        this.value = value;
    }

    /**
     * Runs {@link #beforeNextAccess} once, if it is set.
     */
    private void runBeforeNextAccess()
    {
        Runnable hook = this.beforeNextAccess;

        this.beforeNextAccess = null;

        if (hook != null) hook.run();
    }

    @Override
    public V get() throws Exception
    {
        this.runBeforeNextAccess();

        if (this.closed) throw new IllegalStateException("closed");
        if (this.failOnGet != null) throw this.failOnGet;

        return this.value;
    }

    @Override
    public void commit() throws Exception
    {
        this.commitCalls++;
        this.runBeforeNextAccess();

        if (this.closed) throw new IllegalStateException("closed");
        if (this.failOnCommit != null) throw this.failOnCommit;
    }

    @Override
    public void close() throws IOException
    {
        this.closeCalled = true;
        this.closeCalls++;

        if (this.failOnClose != null) throw this.failOnClose;

        this.closed = true;
    }

    @Override
    public Object clone() throws CloneNotSupportedException
    {
        this.runBeforeNextAccess();

        if (this.closed)
        {
            // like a real implementation, which can't read the value of a closed instance
            CloneNotSupportedException closedFailure = new CloneNotSupportedException("closed");

            closedFailure.initCause(new IllegalStateException("closed"));
            throw closedFailure;
        }
        if (this.failOnClone != null) throw this.failOnClone;

        FailingCachedObject<V> clone = new FailingCachedObject<>(this.value);

        clone.failOnClose = this.failOnCloseOfClones;
        this.clones.add(clone);

        return clone;
    }

    @Override
    public int hashCode()
    {
        return this.value.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        return (other instanceof FailingCachedObject) && this.value.equals(((FailingCachedObject<?>) other).value);
    }
}
