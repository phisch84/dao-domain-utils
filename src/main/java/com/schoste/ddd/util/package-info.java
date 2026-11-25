/**
 * Contains helper classes analog to {@link java.util}: holders whose data is kept in a storage and only weakly in memory,
 * so the garbage collector may reclaim it and it is loaded again on demand, a map with key paths, a thread-safe queue,
 * and online statistics.
 * <ul>
 * <li>{@link com.schoste.ddd.util.CachedObject} holds one {@link java.io.Serializable} object.</li>
 * <li>{@link com.schoste.ddd.util.CachedHashMap} is a {@link java.util.Map} whose values are each held by a {@link com.schoste.ddd.util.CachedObject}.</li>
 * <li>{@link com.schoste.ddd.util.NestedMap} is a map of maps (a trie): a value is stored under a path of keys.
 *     {@link com.schoste.ddd.util.NestedHashMap} is its in-memory implementation.</li>
 * <li>{@link com.schoste.ddd.util.CachedNestedMap} is a {@link com.schoste.ddd.util.NestedMap} whose values are cached
 *     like in a {@link com.schoste.ddd.util.CachedHashMap}.</li>
 * <li>{@link com.schoste.ddd.util.CachedQueue} is a thread-safe FIFO {@link java.util.Queue} whose elements are each held by a
 *     {@link com.schoste.ddd.util.CachedObject}.</li>
 * <li>{@link com.schoste.ddd.util.RunningStatistics} computes count, sum, minimum, maximum, mean, variance and standard
 *     deviation of data points one at a time, thread-safe.</li>
 * </ul>
 * <p>
 * Each type with a storage consists of an interface (the contract and its usage rules) and an abstract class with all
 * logic that doesn't depend on the storage ({@link com.schoste.ddd.util.AbstractCachedObject},
 * {@link com.schoste.ddd.util.AbstractCachedHashMap}, {@link com.schoste.ddd.util.AbstractNestedMap},
 * {@link com.schoste.ddd.util.AbstractCachedQueue}). Implementations for a specific storage are in sub-packages, e.g.
 * {@link com.schoste.ddd.util.fs} for the file system. Types without storage, like
 * {@link com.schoste.ddd.util.RunningStatistics}, are single classes.
 * </p>
 */
package com.schoste.ddd.util;
