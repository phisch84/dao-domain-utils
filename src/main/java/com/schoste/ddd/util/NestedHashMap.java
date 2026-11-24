package com.schoste.ddd.util;

/**
 * In-memory implementation of {@link com.schoste.ddd.util.NestedMap} which uses a {@link java.util.HashMap} for each level.
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
public class NestedHashMap<K, V> extends AbstractNestedMap<K, V>
{
    /**
     * Creates a new, empty map.
     */
    public NestedHashMap() { }
}
