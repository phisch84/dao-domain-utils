package com.schoste.ddd.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract implementation of a hash map which's values can be saved to and retrieved from a storage so that it doesn't need to be kept in memory.
 * The values are cached by decorating them as {@link com.schoste.ddd.util.CachedObject}.
 * Whenever {@link java.util.Map#put(Object, Object)}, {@link java.util.Map#putAll(java.util.Map)} and {@link java.util.Map#putIfAbsent(Object, Object)} is called,
 * {@link com.schoste.ddd.util.CachedObject#commit()} is called to persist the values. Therefore an implementation must persist the values
 * whenever it is done with a strong reference to avoid data loss (after GC collection, the last saved object state will be loaded).
 * <p>
 * The map logic is inherited from {@link java.util.AbstractMap} and the wrapped values are kept in a {@link java.util.HashMap}.
 * Deriving classes only decide how a value is cached, see {@link #createCachedObject(Serializable)}.
 * {@link #entrySet()} is a view which loads a value only when {@link java.util.Map.Entry#getValue()} is called.
 * </p>
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
abstract public class AbstractCachedHashMap<K, V extends Serializable> extends AbstractMap<K, V> implements CachedHashMap<K, V>
{
    /**
     * The values of the map, each one wrapped in a {@link com.schoste.ddd.util.CachedObject}.
     */
    protected final HashMap<K, CachedObject<V>> cachedValues = new HashMap<>();

    /**
     * Wraps a value in a new {@link com.schoste.ddd.util.CachedObject}.
     * The caching logic must be implemented by the deriving class.
     *
     * @param value the value to cache, never null
     * @return a new instance which caches the value
     * @throws Exception re-throws every exception
     */
    abstract protected CachedObject<V> createCachedObject(V value) throws Exception;

    /**
     * Converts an exception of the storage to an unchecked exception, since the methods of {@link java.util.Map} can't throw checked exceptions.
     *
     * @param e the exception to convert
     * @return the exception itself if it is unchecked, otherwise an {@link IllegalStateException} with it as cause
     */
    protected static RuntimeException unchecked(Exception e)
    {
        return (e instanceof RuntimeException) ? (RuntimeException) e : new IllegalStateException(e);
    }

    /**
     * Gets the value of a {@link com.schoste.ddd.util.CachedObject}, loading it from the storage if necessary.
     *
     * @param cachedValue the cached value, may be null
     * @return the value or null if cachedValue is null
     * @throws IllegalStateException if the value could not be loaded
     */
    protected V getValue(CachedObject<V> cachedValue) throws IllegalStateException
    {
        if (cachedValue == null) return null;

        try
        {
            return cachedValue.get();
        }
        catch (Exception e)
        {
            throw unchecked(e);
        }
    }

    /**
     * Constructor for deriving classes.
     */
    protected AbstractCachedHashMap() { }

    /**
     * Wraps the value in a new {@link com.schoste.ddd.util.CachedObject}, saves it and adds it to the map.
     * Call it again to save changes to the value.
     *
     * @param key the key of the value
     * @param value the value to add, must not be null
     * @return the previous value of the key (possibly a copy loaded from the storage), or null if there was none
     * @throws NullPointerException if value is null
     * @throws IllegalStateException if the value could not be saved or the previous value could not be loaded
     */
    @Override
    public V put(K key, V value) throws NullPointerException, IllegalStateException
    {
        if (value == null) throw new NullPointerException("value");

        try
        {
            CachedObject<V> cachedValue = this.createCachedObject(value);

            cachedValue.commit();

            return this.getValue(this.cachedValues.put(key, cachedValue));
        }
        catch (Exception e)
        {
            throw unchecked(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return the value (possibly a copy loaded from the storage), or null if the key is not in the map
     * @throws IllegalStateException if the value could not be loaded
     */
    @Override
    public V get(Object key) throws IllegalStateException
    {
        return this.getValue(this.cachedValues.get(key));
    }

    /**
     * {@inheritDoc}
     *
     * @return the removed value (possibly a copy loaded from the storage), or null if the key was not in the map
     * @throws IllegalStateException if the value could not be loaded
     */
    @Override
    public V remove(Object key) throws IllegalStateException
    {
        return this.getValue(this.cachedValues.remove(key));
    }

    @Override
    public boolean containsKey(Object key)
    {
        return this.cachedValues.containsKey(key);
    }

    @Override
    public int size()
    {
        return this.cachedValues.size();
    }

    /**
     * Returns a view of the map's entries. A value is loaded only when {@link java.util.Map.Entry#getValue()} is called,
     * and the view does not keep a reference to it. {@link java.util.Map.Entry#setValue(Object)} saves the value like {@link #put(Object, Object)}.
     *
     * @return a view of the map's entries
     */
    @Override
    public Set<Map.Entry<K, V>> entrySet()
    {
        return new AbstractSet<Map.Entry<K, V>>()
        {
            @Override
            public Iterator<Map.Entry<K, V>> iterator()
            {
                Iterator<K> keys = AbstractCachedHashMap.this.cachedValues.keySet().iterator();

                return new Iterator<Map.Entry<K, V>>()
                {
                    @Override
                    public boolean hasNext()
                    {
                        return keys.hasNext();
                    }

                    @Override
                    public Map.Entry<K, V> next()
                    {
                        return new LazyEntry(keys.next());
                    }

                    @Override
                    public void remove()
                    {
                        keys.remove();
                    }
                };
            }

            @Override
            public int size()
            {
                return AbstractCachedHashMap.this.size();
            }
        };
    }

    /**
     * An entry of {@link AbstractCachedHashMap#entrySet()} which loads its value only when it is requested.
     */
    private class LazyEntry implements Map.Entry<K, V>
    {
        private final K key;

        private LazyEntry(K key)
        {
            this.key = key;
        }

        @Override
        public K getKey()
        {
            return this.key;
        }

        @Override
        public V getValue()
        {
            return AbstractCachedHashMap.this.get(this.key);
        }

        @Override
        public V setValue(V value)
        {
            return AbstractCachedHashMap.this.put(this.key, value);
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof Map.Entry)) return false;

            Map.Entry<?, ?> otherEntry = (Map.Entry<?, ?>) other;

            return Objects.equals(this.key, otherEntry.getKey()) && Objects.equals(this.getValue(), otherEntry.getValue());
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.key) ^ Objects.hashCode(this.getValue());
        }

        @Override
        public String toString()
        {
            return this.key + "=" + this.getValue();
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see com.schoste.ddd.util.CachedHashMap#commit()
     */
    @Override
    public void commit() throws Exception
    {
        for (CachedObject<V> cachedValue : this.cachedValues.values()) cachedValue.commit();
    }

    /**
     * Closes the map.
     *
     * @throws IOException re-throws every exception
     */
    // TODO: close the CachedObjects and delete their storage (step 5 of the CachedHashMap plan)
    @Override
    public void close() throws IOException { }
}
