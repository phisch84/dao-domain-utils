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
 * {@link #entrySet()} is a view which loads a value only when {@link java.util.Map.Entry#getValue()} is called and keeps no reference to it.
 * All other views and bulk methods of {@link java.util.AbstractMap} ({@link #keySet()}, {@link #values()}, {@link #containsValue(Object)},
 * {@link #equals(Object)}, {@link #hashCode()}, {@link #toString()}) iterate over it, so they load the values one at a time
 * and the GC can collect each one again; methods which only need the keys don't load any value.
 * </p>
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
abstract public class AbstractCachedHashMap<K, V extends Serializable> extends AbstractMap<K, V> implements CachedHashMap<K, V>
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

    /**
     * The values of the map, each one wrapped in a {@link com.schoste.ddd.util.CachedObject}.
     * Serialized as it is, so a serialized map contains the keys and the serialized {@link com.schoste.ddd.util.CachedObject}s,
     * which refer to the storage of the values.
     * Not final, because {@link #clone()} replaces it in the clone.
     */
    protected HashMap<K, CachedObject<V>> cachedValues = new HashMap<>();

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
     * Closes a {@link com.schoste.ddd.util.CachedObject}, which frees its storage.
     *
     * @param cachedValue the cached value, may be null
     * @throws IllegalStateException if closing failed
     */
    protected static void close(CachedObject<?> cachedValue) throws IllegalStateException
    {
        if (cachedValue == null) return;

        try
        {
            cachedValue.close();
        }
        catch (IOException e)
        {
            throw unchecked(e);
        }
    }

    /**
     * Gets the value of a {@link com.schoste.ddd.util.CachedObject} which was removed from the map, and closes it.
     *
     * @param cachedValue the removed cached value, may be null
     * @return the value or null if cachedValue is null
     * @throws IllegalStateException if the value could not be loaded or closing failed
     */
    protected V getValueAndClose(CachedObject<V> cachedValue) throws IllegalStateException
    {
        try
        {
            return this.getValue(cachedValue);
        }
        finally
        {
            close(cachedValue);
        }
    }

    /**
     * Closes all values and removes them from the map.
     * Tries to close every value; if closing fails, the first exception is thrown with the others as suppressed exceptions.
     *
     * @throws IOException if closing a value failed
     */
    protected void closeAll() throws IOException
    {
        IOException failure = null;

        for (CachedObject<V> cachedValue : this.cachedValues.values())
        {
            try
            {
                cachedValue.close();
            }
            catch (IOException e)
            {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }

        this.cachedValues.clear();

        if (failure != null) throw failure;
    }

    /**
     * Constructor for deriving classes.
     */
    protected AbstractCachedHashMap() { }

    /**
     * Wraps the value in a new {@link com.schoste.ddd.util.CachedObject}, saves it and adds it to the map.
     * Call it again to save changes to the value. The previous value of the key is closed, which frees its storage.
     * If saving fails, the new {@link com.schoste.ddd.util.CachedObject} is closed and the previous value stays in the map.
     *
     * @param key the key of the value
     * @param value the value to add, must not be null
     * @return the previous value of the key (possibly a copy loaded from the storage), or null if there was none
     * @throws NullPointerException if value is null
     * @throws IllegalStateException if the value could not be saved, or the previous value could not be loaded or closed
     */
    @Override
    public V put(K key, V value) throws NullPointerException, IllegalStateException
    {
        if (value == null) throw new NullPointerException("value");

        try
        {
            CachedObject<V> cachedValue = this.createCachedObject(value);

            try
            {
                cachedValue.commit();
            }
            catch (Exception e)
            {
                // the new value never gets into the map, so nothing else would free its storage
                try
                {
                    cachedValue.close();
                }
                catch (IOException closeFailure)
                {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }

            return this.getValueAndClose(this.cachedValues.put(key, cachedValue));
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
     * The removed value is closed, which frees its storage.
     *
     * @return the removed value (possibly a copy loaded from the storage), or null if the key was not in the map
     * @throws IllegalStateException if the value could not be loaded or closed
     */
    @Override
    public V remove(Object key) throws IllegalStateException
    {
        return this.getValueAndClose(this.cachedValues.remove(key));
    }

    /**
     * {@inheritDoc}
     * All values are closed, which frees their storage.
     *
     * @throws IllegalStateException if closing a value failed
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
     * Removing entries through the view (or through {@link #keySet()} and {@link #values()}, which use it) closes the removed values.
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
                Iterator<Map.Entry<K, CachedObject<V>>> entries = AbstractCachedHashMap.this.cachedValues.entrySet().iterator();

                return new Iterator<Map.Entry<K, V>>()
                {
                    // the entry of cachedValues returned last; it reflects a value replaced by setValue()
                    private Map.Entry<K, CachedObject<V>> current;

                    @Override
                    public boolean hasNext()
                    {
                        return entries.hasNext();
                    }

                    @Override
                    public Map.Entry<K, V> next()
                    {
                        this.current = entries.next();

                        return new LazyEntry(this.current.getKey());
                    }

                    @Override
                    public void remove()
                    {
                        entries.remove();
                        close(this.current.getValue());
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
     * Saves every value which is in memory. Tries to save every value; if saving fails,
     * the first exception is thrown with the others as suppressed exceptions (like {@link #close()}).
     *
     * @see com.schoste.ddd.util.CachedHashMap#commit()
     * @throws Exception the first exception of saving a value
     */
    @Override
    public void commit() throws Exception
    {
        Exception failure = null;

        for (CachedObject<V> cachedValue : this.cachedValues.values())
        {
            try
            {
                cachedValue.commit();
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
     * Creates the clone with {@link java.util.AbstractMap#clone()}, which copies all fields (also those of deriving classes),
     * and gives it its own values by cloning every {@link com.schoste.ddd.util.CachedObject} (see {@link com.schoste.ddd.util.CachedObject#clone()}).
     * If cloning a value fails, the values which were already cloned are closed again.
     *
     * @see com.schoste.ddd.util.CachedHashMap#clone()
     * @return a clone of this instance
     * @throws CloneNotSupportedException if the instance could not be cloned, with the original exception as cause
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object clone() throws CloneNotSupportedException
    {
        AbstractCachedHashMap<K, V> clone = (AbstractCachedHashMap<K, V>) super.clone();

        clone.cachedValues = new HashMap<>();

        try
        {
            for (Map.Entry<K, CachedObject<V>> entry : this.cachedValues.entrySet())
            {
                clone.cachedValues.put(entry.getKey(), (CachedObject<V>) entry.getValue().clone());
            }

            return clone;
        }
        catch (Exception e)
        {
            try
            {
                clone.closeAll();
            }
            catch (IOException closeFailure)
            {
                e.addSuppressed(closeFailure);
            }

            CloneNotSupportedException failure = new CloneNotSupportedException(e.getMessage());

            failure.initCause(e);

            throw failure;
        }
    }

    /**
     * Closes all values, which frees their storage, and removes them from the map.
     * Tries to close every value; if closing fails, the first exception is thrown with the others as suppressed exceptions.
     *
     * @throws IOException if closing a value failed
     */
    @Override
    public void close() throws IOException
    {
        this.closeAll();
    }
}
