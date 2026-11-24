package com.schoste.ddd.util;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Map;

/**
 * Interface to the {@link com.schoste.ddd.util.AbstractCachedHashMap} class, an implementation of a {@link java.util.Map} that caches values by storing them as
 * {@link com.schoste.ddd.util.CachedObject}.
 *
 * @param <K> the class of the map's keys
 * @param <V> the class of the map's values inside of a {@link com.schoste.ddd.util.CachedObject}
 */
public interface CachedHashMap<K, V extends Serializable> extends Map<K, V>, Cloneable, Closeable
{

}
