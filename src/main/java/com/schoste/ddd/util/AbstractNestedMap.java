package com.schoste.ddd.util;

import java.util.List;
import java.util.Set;

/**
 * Abstract implementation of {@link com.schoste.ddd.util.NestedMap} with all logic that doesn't depend on the map
 * which is used for each level. Deriving classes decide which map is used.
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
// TODO: placeholders until the contract test NestedMapUT is written; then implemented test-first
abstract public class AbstractNestedMap<K, V> implements NestedMap<K, V>
{
    /**
     * Constructor for deriving classes.
     */
    protected AbstractNestedMap() { }

    @Override
    public V put(K k1, V value)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public V put(K k1, K k2, V value)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public V put(K k1, K k2, K k3, V value)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public V put(K k1, K k2, K k3, K k4, V value)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public V put(K k1, K k2, K k3, K k4, K k5, V value)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public V put(List<K> keys, V value)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(K... keys)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(K... keys)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    @SuppressWarnings("unchecked")
    public NestedMap<K, V> getSubMap(K... keys)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(K... keys)
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Set<K> keySet()
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public int size()
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public boolean isEmpty()
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
