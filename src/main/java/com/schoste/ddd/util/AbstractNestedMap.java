package com.schoste.ddd.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract implementation of {@link com.schoste.ddd.util.NestedMap} with all logic that doesn't depend on the map
 * which is used for each level. Deriving classes decide which map holds the values of a level
 * ({@link #createValueMap()}).
 *
 * <p>Structure: every level has a map of its values (created by {@link #createValueMap()} when the level gets its
 * first value) and a {@link java.util.HashMap} of its deeper levels. Both are keyed by the key of that level, so a
 * key can have a value and deeper keys at the same time. Levels without values and deeper keys are removed.</p>
 *
 * <p>A sub map ({@link #getSubMap(Object...)}) is an instance of this class which shares the levels of the map it
 * was created from and prefixes every key path with its own path.</p>
 *
 * <p>Serialization writes the levels with their value maps as they are, so what is written for the values is
 * decided by the value maps (e.g. {@link com.schoste.ddd.util.fs.CachedHashMapImpl} writes only references to its
 * cache files). {@link #clone()} copies every level and every value map with {@link #cloneValueMap(Map)}.</p>
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
abstract public class AbstractNestedMap<K, V> implements NestedMap<K, V>
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

    /**
     * One level of the map: the values of its keys and the deeper levels of its keys.
     *
     * @param <K> the class of the keys
     * @param <V> the class of the values
     */
    private static final class Level<K, V> implements Serializable
    {
        private static final long serialVersionUID = 42L;

        /** The values of this level; null while the level has no values. */
        private Map<K, V> values;

        /** The deeper levels of this level. */
        private final HashMap<K, Level<K, V>> children = new HashMap<K, Level<K, V>>();

        private boolean hasValues()
        {
            return this.values != null && !this.values.isEmpty();
        }

        private boolean isEmpty()
        {
            return !this.hasValues() && this.children.isEmpty();
        }
    }

    /**
     * A live view of the level below a key path of a map.
     *
     * @param <K> the class of the keys
     * @param <V> the class of the values
     */
    private static final class SubMap<K, V> extends AbstractNestedMap<K, V>
    {
        private static final long serialVersionUID = 42L;

        private SubMap(AbstractNestedMap<K, V> owner, List<K> path)
        {
            super(owner, path);
        }

        /**
         * Never called: the levels belong to the owner, and every value map is created by the owner
         * ({@code put} calls {@code owner.createValueMap()}, and a sub map can't be cloned).
         * Implemented only because the method is abstract; it delegates to the owner in case that changes.
         */
        @Override
        protected Map<K, V> createValueMap()
        {
            return ((AbstractNestedMap<K, V>) this).owner.createValueMap();
        }
    }

    /**
     * The map which holds the levels: this map itself, or for a sub map the map it was created from.
     * Not final only because {@link #clone()} replaces it.
     */
    private AbstractNestedMap<K, V> owner;

    /** The key path of this (sub) map; empty for the map itself. */
    private final List<K> path;

    /**
     * The first level; only used by the owner, null for a sub map.
     * Not final only because {@link #clone()} replaces it.
     */
    private Level<K, V> root;

    /**
     * Constructor for deriving classes.
     */
    protected AbstractNestedMap()
    {
        this.owner = this;
        this.path = Collections.emptyList();
        this.root = new Level<K, V>();
    }

    private AbstractNestedMap(AbstractNestedMap<K, V> owner, List<K> path)
    {
        this.owner = owner;
        this.path = path;
        this.root = null;
    }

    /**
     * Creates the map which holds the values of one level. It is called when a level gets its first value,
     * and dropped when the level has no values any more.
     *
     * @return a new, empty map
     */
    abstract protected Map<K, V> createValueMap();

    /**
     * Returns the maps which hold the values of all levels of the whole map (also for a sub map), for example
     * to save or close them. The list is a copy; the maps are the ones the levels use.
     *
     * @return the value maps of all levels which have values
     */
    protected List<Map<K, V>> getValueMaps()
    {
        List<Map<K, V>> valueMaps = new ArrayList<Map<K, V>>();

        addValueMaps(this.owner.root, valueMaps);

        return valueMaps;
    }

    private static <K, V> void addValueMaps(Level<K, V> level, List<Map<K, V>> valueMaps)
    {
        if (level.values != null)
        {
            valueMaps.add(level.values);
        }
        for (Level<K, V> child : level.children.values())
        {
            addValueMaps(child, valueMaps);
        }
    }

    @SafeVarargs
    private static <K> List<K> keys(K... keys)
    {
        return Arrays.asList(keys);
    }

    /**
     * Prefixes the keys with the path of this (sub) map and copies them.
     */
    private List<K> fullPath(List<K> keys)
    {
        List<K> fullPath = new ArrayList<K>(this.path.size() + keys.size());

        fullPath.addAll(this.path);
        fullPath.addAll(keys);

        return fullPath;
    }

    private static <K> void requireKeys(List<K> keys)
    {
        if (keys.isEmpty())
        {
            throw new IllegalArgumentException("A key path needs at least one key");
        }
    }

    /**
     * Returns the levels along a key path, starting with the first level, or null if a level doesn't exist.
     *
     * @param fullPath the key path
     * @param depth the number of keys of the path to follow
     */
    private List<Level<K, V>> levels(List<K> fullPath, int depth)
    {
        List<Level<K, V>> levels = new ArrayList<Level<K, V>>(depth + 1);
        Level<K, V> level = this.owner.root;

        levels.add(level);

        for (int i = 0; i < depth; i++)
        {
            level = level.children.get(fullPath.get(i));

            if (level == null)
            {
                return null;
            }
            levels.add(level);
        }
        return levels;
    }

    /**
     * Returns the level at the end of a key path (the first level for an empty path), or null if it doesn't exist.
     */
    private Level<K, V> level(List<K> fullPath, int depth)
    {
        List<Level<K, V>> levels = this.levels(fullPath, depth);

        return (levels == null) ? null : levels.get(levels.size() - 1);
    }

    /**
     * Removes the levels at the end of the path which have neither values nor deeper levels, from the deepest up to
     * the first one which still has one. The first level is never removed.
     */
    private static <K, V> void prune(List<Level<K, V>> levels, List<K> fullPath)
    {
        for (int i = levels.size() - 1; i >= 0; i--)
        {
            Level<K, V> level = levels.get(i);

            if (!level.hasValues())
            {
                level.values = null;
            }
            if (i == 0 || !level.isEmpty())
            {
                return;
            }
            levels.get(i - 1).children.remove(fullPath.get(i - 1));
        }
    }

    /**
     * Removes all values and deeper levels of a level. Clearing the value maps lets them free their storage.
     */
    private static <K, V> void clear(Level<K, V> level)
    {
        for (Level<K, V> child : level.children.values())
        {
            clear(child);
        }
        level.children.clear();

        if (level.values != null)
        {
            level.values.clear();
            level.values = null;
        }
    }

    private static <K, V> int size(Level<K, V> level)
    {
        int size = (level.values == null) ? 0 : level.values.size();

        for (Level<K, V> child : level.children.values())
        {
            size += size(child);
        }
        return size;
    }

    private static <K, V> boolean hasValuesAtAnyDepth(Level<K, V> level)
    {
        if (level.hasValues())
        {
            return true;
        }
        for (Level<K, V> child : level.children.values())
        {
            if (hasValuesAtAnyDepth(child))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public V put(K k1, V value)
    {
        return this.put(keys(k1), value);
    }

    @Override
    public V put(K k1, K k2, V value)
    {
        return this.put(keys(k1, k2), value);
    }

    @Override
    public V put(K k1, K k2, K k3, V value)
    {
        return this.put(keys(k1, k2, k3), value);
    }

    @Override
    public V put(K k1, K k2, K k3, K k4, V value)
    {
        return this.put(keys(k1, k2, k3, k4), value);
    }

    @Override
    public V put(K k1, K k2, K k3, K k4, K k5, V value)
    {
        return this.put(keys(k1, k2, k3, k4, k5), value);
    }

    @Override
    public V put(List<K> keys, V value)
    {
        if (keys == null)
        {
            throw new NullPointerException("The key path must not be null");
        }
        if (value == null)
        {
            throw new NullPointerException("A nested map can't hold null values");
        }
        requireKeys(keys);

        List<K> fullPath = this.fullPath(keys);
        int depth = fullPath.size() - 1;
        Level<K, V> level = this.owner.root;

        for (int i = 0; i < depth; i++)
        {
            Level<K, V> child = level.children.get(fullPath.get(i));

            if (child == null)
            {
                child = new Level<K, V>();
                level.children.put(fullPath.get(i), child);
            }
            level = child;
        }
        if (level.values == null)
        {
            level.values = this.owner.createValueMap();
        }
        return level.values.put(fullPath.get(depth), value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(K... keys)
    {
        List<K> keyList = keys(keys);

        requireKeys(keyList);

        List<K> fullPath = this.fullPath(keyList);
        int depth = fullPath.size() - 1;
        Level<K, V> level = this.level(fullPath, depth);

        return (level == null || level.values == null) ? null : level.values.get(fullPath.get(depth));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(K... keys)
    {
        List<K> keyList = keys(keys);

        requireKeys(keyList);

        List<K> fullPath = this.fullPath(keyList);
        int depth = fullPath.size() - 1;
        Level<K, V> level = this.level(fullPath, depth);

        return level != null && level.values != null && level.values.containsKey(fullPath.get(depth));
    }

    @Override
    @SuppressWarnings("unchecked")
    public NestedMap<K, V> getSubMap(K... keys)
    {
        if (keys.length == 0)
        {
            return this;
        }
        return new SubMap<K, V>(this.owner, Collections.unmodifiableList(this.fullPath(keys(keys))));
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(K... keys)
    {
        List<K> keyList = keys(keys);

        requireKeys(keyList);

        List<K> fullPath = this.fullPath(keyList);
        int depth = fullPath.size() - 1;
        List<Level<K, V>> levels = this.levels(fullPath, depth);

        if (levels == null)
        {
            return null;
        }

        Level<K, V> level = levels.get(depth);

        if (level.values == null)
        {
            return null;
        }

        V removed = level.values.remove(fullPath.get(depth));

        prune(levels, fullPath);

        return removed;
    }

    @Override
    public Set<K> keySet()
    {
        Set<K> keys = new HashSet<K>();
        Level<K, V> level = this.level(this.path, this.path.size());

        if (level != null)
        {
            if (level.values != null)
            {
                keys.addAll(level.values.keySet());
            }
            keys.addAll(level.children.keySet());
        }
        return keys;
    }

    @Override
    public int size()
    {
        Level<K, V> level = this.level(this.path, this.path.size());

        return (level == null) ? 0 : size(level);
    }

    @Override
    public boolean isEmpty()
    {
        Level<K, V> level = this.level(this.path, this.path.size());

        return level == null || !hasValuesAtAnyDepth(level);
    }

    @Override
    public void clear()
    {
        List<Level<K, V>> levels = this.levels(this.path, this.path.size());

        if (levels == null)
        {
            return;
        }
        clear(levels.get(levels.size() - 1));
        prune(levels, this.path);
    }

    /**
     * Copies the map which holds the values of one level, for {@link #clone()}. This implementation creates a map
     * with {@link #createValueMap()} and puts all keys and values into it, so the clone shares the value instances
     * (like {@link java.util.HashMap#clone()}). Deriving classes whose value maps have a storage override it.
     *
     * @param valueMap the map of the level to copy
     * @return the copy for the clone
     * @throws CloneNotSupportedException if the map could not be copied
     */
    protected Map<K, V> cloneValueMap(Map<K, V> valueMap) throws CloneNotSupportedException
    {
        Map<K, V> copy = this.createValueMap();

        copy.putAll(valueMap);

        return copy;
    }

    /**
     * Copies the values and deeper levels of a level into an empty level of the clone. A deeper level is added to the
     * clone before it is filled, so that the clone can free everything it copied if copying fails.
     */
    private void copyLevel(Level<K, V> from, Level<K, V> to) throws CloneNotSupportedException
    {
        if (from.values != null)
        {
            to.values = this.cloneValueMap(from.values);
        }
        for (Map.Entry<K, Level<K, V>> child : from.children.entrySet())
        {
            Level<K, V> childCopy = new Level<K, V>();

            to.children.put(child.getKey(), childCopy);
            this.copyLevel(child.getValue(), childCopy);
        }
    }

    /**
     * Clones the map: {@code super.clone()} copies all fields (also of deriving classes), then every level and every
     * value map is copied with {@link #cloneValueMap(Map)}. If copying fails, everything which was already copied is
     * cleared (which frees its storage).
     *
     * @return a clone of this map
     * @throws CloneNotSupportedException if this is a sub map, or if a value map could not be copied
     *         (with the original exception as cause, if it was another exception)
     */
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        if (this.owner != this)
        {
            throw new CloneNotSupportedException("A sub map can't be cloned; clone the map instead");
        }

        @SuppressWarnings("unchecked")
        AbstractNestedMap<K, V> clone = (AbstractNestedMap<K, V>) super.clone();

        clone.owner = clone;
        clone.root = new Level<K, V>();

        try
        {
            this.copyLevel(this.root, clone.root);
        }
        catch (CloneNotSupportedException | RuntimeException e)
        {
            try
            {
                clear(clone.root);
            }
            catch (RuntimeException suppressed)
            {
                e.addSuppressed(suppressed);
            }
            if (e instanceof CloneNotSupportedException) throw (CloneNotSupportedException) e;

            CloneNotSupportedException cnse = new CloneNotSupportedException(e.getMessage());

            cnse.initCause(e);
            throw cnse;
        }
        return clone;
    }
}
