/**
 * Contains helper classes analog to {@link java.util}: holders whose data is kept in a storage and only weakly in memory,
 * so the garbage collector may reclaim it and it is loaded again on demand.
 * <ul>
 * <li>{@link com.schoste.ddd.util.CachedObject} holds one {@link java.io.Serializable} object.</li>
 * <li>{@link com.schoste.ddd.util.CachedHashMap} is a {@link java.util.Map} whose values are each held by a {@link com.schoste.ddd.util.CachedObject}.</li>
 * </ul>
 * <p>
 * Each type consists of an interface (the contract and its usage rules) and an abstract class with all logic that doesn't
 * depend on the storage ({@link com.schoste.ddd.util.AbstractCachedObject}, {@link com.schoste.ddd.util.AbstractCachedHashMap}).
 * Implementations for a specific storage are in sub-packages, e.g. {@link com.schoste.ddd.util.fs} for the file system.
 * </p>
 */
package com.schoste.ddd.util;
