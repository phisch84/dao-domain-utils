/**
 * Contains helper classes similar to those of {@link java.util}: holders whose data is kept in a storage and only weakly in memory,
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
 * <p>
 * <b>What to instantiate:</b> declare variables with the interface and create
 * {@link com.schoste.ddd.util.fs.CachedObjectImpl}, {@link com.schoste.ddd.util.fs.CachedHashMapImpl},
 * {@link com.schoste.ddd.util.fs.CachedNestedHashMapImpl} or {@link com.schoste.ddd.util.fs.CachedQueueImpl}
 * (each with a folder for its cache files, or {@code java.io.tmpdir} without one), {@link com.schoste.ddd.util.NestedHashMap}
 * for an in-memory {@link com.schoste.ddd.util.NestedMap}, and {@link com.schoste.ddd.util.RunningStatistics} directly.
 * The {@code Abstract*} classes are only for implementing another storage.
 * </p>
 * <p>
 * <b>Rules of all cached types</b> (the interfaces have the details):
 * </p>
 * <ul>
 * <li>Save changes while you hold a reference ({@code commit()}, or {@code put} again); changes which weren't saved are lost
 *     once the value is garbage collected.</li>
 * <li>Returned values may be copies loaded from the storage, not the instances which were put.</li>
 * <li>Close the holder when done (try-with-resources): {@code close()} frees the storage. Removing or replacing a value
 *     frees its storage too.</li>
 * <li>Values must be {@link java.io.Serializable}; null values are rejected; a cached object's hash code must not change.</li>
 * <li>Serializing a cached type writes references to the storage only, not the values: commit before.</li>
 * <li>{@link com.schoste.ddd.util.CachedObject}, {@link com.schoste.ddd.util.CachedQueue} and
 *     {@link com.schoste.ddd.util.RunningStatistics} are thread-safe; the maps are not.</li>
 * </ul>
 * <p>
 * The guide with examples is {@code META-INF/README.md} in the jar, also at
 * <a href="https://github.com/phisch84/dao-domain-utils">github.com/phisch84/dao-domain-utils</a>.
 * </p>
 */
package com.schoste.ddd.util;
