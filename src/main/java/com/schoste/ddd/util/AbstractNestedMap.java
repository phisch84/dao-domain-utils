package com.schoste.ddd.util;

/**
 * Abstract implementation of {@link com.schoste.ddd.util.NestedMap} with all logic that doesn't depend on the map
 * which is used for each level. Deriving classes decide which map is used.
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
abstract public class AbstractNestedMap<K, V> implements NestedMap<K, V>
{
    /**
     * Constructor for deriving classes.
     */
    protected AbstractNestedMap() { }
}
