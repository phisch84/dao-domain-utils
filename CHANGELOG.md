# Changelog

What changed between the versions of DDD Utils (`com.schoste.ddd:utils`), for users of the artifacts. Versions without a
release date aren't released yet. Release versions end with `.RELEASE`.

## 1.0.0 (2026-11-27)

The first release. All classes are in `com.schoste.ddd.util`; the file system implementations are in
`com.schoste.ddd.util.fs`.

**Added**

- `CachedObject<T>` / `fs.CachedObjectImpl`: holds one `Serializable` object, keeps it in memory only weakly and reloads
  it from its own cache file on demand. Cache files that weren't closed are deleted when the JVM exits.
- `CachedHashMap<K, V>` / `fs.CachedHashMapImpl`: a `java.util.Map` whose keys stay in memory and whose values are each
  kept in a `CachedObject`.
- `NestedMap<K, V>`: a map with key paths (`put(k1, k2, k3, v)`, `get(k1, k2, k3)`, `getSubMap(k1)`), in memory as
  `NestedHashMap`, cached as `CachedNestedMap` / `fs.CachedNestedHashMapImpl`.
- `CachedQueue<E>` / `fs.CachedQueueImpl`: a thread-safe FIFO `java.util.Queue` whose elements are each kept in a
  `CachedObject`.
- `RunningStatistics`: thread-safe online statistics (count, sum, min, max, mean, variance and standard deviation of the
  population and the sample), which can be combined.
- The contract tests ship as test-jar (`tests` classifier), so other implementations can reuse them.
- With Java modules (JPMS), the jar is the automatic module `com.schoste.ddd.util`.
- Documentation in the artifacts: the Javadoc jar starts with an overview (what to instantiate, the rules of all cached
  types, an example); the jar and the sources jar contain `META-INF/README.md`, `CHANGELOG.md` and `LICENSE`.

**Built with** JDK 21 and tested with JDK 21 and 25; compiled for Java 8.
