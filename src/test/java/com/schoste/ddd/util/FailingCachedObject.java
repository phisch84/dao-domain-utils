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
     * How often {@link #commit()} was called, also if it failed.
     */
    public int commitCalls;

    /**
     * True once {@link #close()} was called, also if it failed.
     */
    public boolean closeCalled;

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

    @Override
    public V get() throws Exception
    {
        if (this.closed) throw new IllegalStateException("closed");
        if (this.failOnGet != null) throw this.failOnGet;

        return this.value;
    }

    @Override
    public void commit() throws Exception
    {
        this.commitCalls++;

        if (this.closed) throw new IllegalStateException("closed");
        if (this.failOnCommit != null) throw this.failOnCommit;
    }

    @Override
    public void close() throws IOException
    {
        this.closeCalled = true;

        if (this.failOnClose != null) throw this.failOnClose;

        this.closed = true;
    }

    @Override
    public Object clone() throws CloneNotSupportedException
    {
        if (this.failOnClone != null) throw this.failOnClone;

        FailingCachedObject<V> clone = new FailingCachedObject<>(this.value);

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
