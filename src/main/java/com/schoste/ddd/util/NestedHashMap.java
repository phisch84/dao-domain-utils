package com.schoste.ddd.util;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory implementation of {@link com.schoste.ddd.util.NestedMap} which uses a {@link java.util.HashMap} for each level.
 * Like {@link java.util.HashMap#clone()}, {@link #clone()} copies the levels but shares the value instances.
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
public class NestedHashMap<K, V> extends AbstractNestedMap<K, V>
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

    /**
     * Creates a new, empty map.
     */
    public NestedHashMap() { }

    /**
     * Creates a {@link java.util.HashMap} for the values of a level.
     *
     * @return a new, empty {@link java.util.HashMap}
     */
    @Override
    protected Map<K, V> createValueMap()
    {
        return new HashMap<K, V>();
    }
}
