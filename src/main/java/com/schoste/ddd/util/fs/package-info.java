/**
 * File system implementations of {@link com.schoste.ddd.util}: the data is serialized into cache files
 * in {@code java.io.tmpdir} or in a folder passed to the constructor.
 * <ul>
 * <li>{@link com.schoste.ddd.util.fs.CachedObjectImpl} stores one object in one cache file.</li>
 * <li>{@link com.schoste.ddd.util.fs.CachedHashMapImpl} stores each value of the map in its own cache file
 *     (using {@link com.schoste.ddd.util.fs.CachedObjectImpl}).</li>
 * <li>{@link com.schoste.ddd.util.fs.CachedNestedHashMapImpl} stores each value of the nested map in its own cache file
 *     (using a {@link com.schoste.ddd.util.fs.CachedHashMapImpl} per level).</li>
 * <li>{@link com.schoste.ddd.util.fs.CachedQueueImpl} stores each element of the queue in its own cache file
 *     (using {@link com.schoste.ddd.util.fs.CachedObjectImpl}).</li>
 * </ul>
 * Cache files are deleted when they are closed, or when the JVM exits.
 */
package com.schoste.ddd.util.fs;
