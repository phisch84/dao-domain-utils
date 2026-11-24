package com.schoste.ddd.util;

import java.util.List;
import java.util.Set;

/**
 * A nested map (a trie of maps): a sequence of keys is a path to a value, and a shorter sequence of keys (a prefix)
 * leads to the nested map of that level.
 *
 * <pre>
 * map.put("a", "b", "c", v1);     // v1 at the path a, b, c
 * map.put("a", "b", v2);          // v2 at the path a, b: a path can have a value and deeper keys
 * map.get("a", "b", "c");         // v1
 * map.get("a", "b");              // v2
 * map.get("a");                   // null: no value at the path a, only deeper keys
 * map.getSubMap("a", "b");        // the level below a, b: get("c") returns v1
 * map.size();                     // 2: all values at any depth
 * </pre>
 *
 * <p>Rules:</p>
 * <ul>
 * <li>Key paths of any depth can be mixed. A path can have a value and deeper keys at the same time.</li>
 * <li>{@code put} exists for paths of 1 to 5 keys; longer paths use {@link #put(List, Object)}.
 *     Methods that read or remove take the path as varargs.</li>
 * <li>{@link #getSubMap(Object...)} returns a live view that remembers its key path: changes through the view are
 *     visible in this map and vice versa. The view of a path that doesn't exist is empty, and {@code put} through
 *     it creates the path. The view stays usable when its level is removed and created again.</li>
 * <li>{@link #remove(Object...)} removes only the value at the path; deeper keys stay. Levels that have neither
 *     values nor deeper keys afterwards are removed (pruned).</li>
 * <li>{@link #size()} counts all values at any depth of this (sub) map, not the keys of the first level.</li>
 * <li>Null values are rejected with {@link NullPointerException}, so {@code get} returning null means
 *     "no value at this path". Null keys are allowed.</li>
 * <li>A key path for a value has at least one key; an empty path throws {@link IllegalArgumentException}.</li>
 * <li>NestedMap is not a {@link java.util.Map}, because a path has several keys.</li>
 * </ul>
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
public interface NestedMap<K, V>
{
    /**
     * Stores a value at a key path of one key.
     *
     * @param k1 the key
     * @param value the value to store, not null
     * @return the previous value at the path, or null if there was none
     * @throws NullPointerException if the value is null
     */
    V put(K k1, V value);

    /**
     * Stores a value at a key path of two keys.
     *
     * @param k1 the key of the first level
     * @param k2 the key of the second level
     * @param value the value to store, not null
     * @return the previous value at the path, or null if there was none
     * @throws NullPointerException if the value is null
     */
    V put(K k1, K k2, V value);

    /**
     * Stores a value at a key path of three keys.
     *
     * @param k1 the key of the first level
     * @param k2 the key of the second level
     * @param k3 the key of the third level
     * @param value the value to store, not null
     * @return the previous value at the path, or null if there was none
     * @throws NullPointerException if the value is null
     */
    V put(K k1, K k2, K k3, V value);

    /**
     * Stores a value at a key path of four keys.
     *
     * @param k1 the key of the first level
     * @param k2 the key of the second level
     * @param k3 the key of the third level
     * @param k4 the key of the fourth level
     * @param value the value to store, not null
     * @return the previous value at the path, or null if there was none
     * @throws NullPointerException if the value is null
     */
    V put(K k1, K k2, K k3, K k4, V value);

    /**
     * Stores a value at a key path of five keys.
     *
     * @param k1 the key of the first level
     * @param k2 the key of the second level
     * @param k3 the key of the third level
     * @param k4 the key of the fourth level
     * @param k5 the key of the fifth level
     * @param value the value to store, not null
     * @return the previous value at the path, or null if there was none
     * @throws NullPointerException if the value is null
     */
    V put(K k1, K k2, K k3, K k4, K k5, V value);

    /**
     * Stores a value at a key path of any length. The list is not kept; later changes to it don't affect this map.
     *
     * @param keys the key path, starting with the key of the first level; at least one key
     * @param value the value to store, not null
     * @return the previous value at the path, or null if there was none
     * @throws NullPointerException if the list or the value is null
     * @throws IllegalArgumentException if the list is empty
     */
    V put(List<K> keys, V value);

    /**
     * Returns the value at a key path.
     *
     * @param keys the key path, starting with the key of the first level; at least one key
     * @return the value at the path, or null if the path has no value (also if it has only deeper keys)
     * @throws IllegalArgumentException if no key is given
     */
    @SuppressWarnings("unchecked")
    V get(K... keys);

    /**
     * Checks if a value is stored at a key path. To check if a path has deeper keys,
     * use {@code !getSubMap(keys).isEmpty()}.
     *
     * @param keys the key path, starting with the key of the first level; at least one key
     * @return true if a value is stored at the path
     * @throws IllegalArgumentException if no key is given
     */
    @SuppressWarnings("unchecked")
    boolean containsKey(K... keys);

    /**
     * Returns a live view of the level below a key path. The view remembers the path and resolves it on every call,
     * so it reflects all changes, also if the level is removed and created again. The view of a path that doesn't
     * exist is empty, and {@code put} through the view creates the path. Without keys, this map itself is returned.
     *
     * @param keys the key path of the level, starting with the key of the first level
     * @return the view of the level below the path, never null
     */
    @SuppressWarnings("unchecked")
    NestedMap<K, V> getSubMap(K... keys);

    /**
     * Removes the value at a key path. Deeper keys of the path stay. Levels that have neither values nor deeper keys
     * afterwards are removed. To remove a path with all deeper keys, use {@code getSubMap(keys).clear()}
     * and {@code remove(keys)}.
     *
     * @param keys the key path, starting with the key of the first level; at least one key
     * @return the removed value, or null if the path had no value
     * @throws IllegalArgumentException if no key is given
     */
    @SuppressWarnings("unchecked")
    V remove(K... keys);

    /**
     * Returns the keys of the first level of this map: every key that has a value or deeper keys.
     * The set is a copy; changes to it don't affect this map.
     *
     * @return the keys of the first level
     */
    Set<K> keySet();

    /**
     * Returns the number of values at any depth of this map.
     *
     * @return the number of values
     */
    int size();

    /**
     * Checks if this map has no values at any depth.
     *
     * @return true if {@link #size()} is 0
     */
    boolean isEmpty();

    /**
     * Removes all values and levels of this map. For a view returned by {@link #getSubMap(Object...)},
     * only the levels below its path are removed; the value at the path itself stays.
     */
    void clear();
}
