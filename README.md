# DDD Utils

Helper classes for the DDD libraries [dao](https://github.com/phisch84/dao) and
[domain-repository](https://github.com/phisch84/domain-repository), and for projects that use them:
code that belongs to none of the infrastructure, domain or application layers.

Requires Java 8 or newer. No runtime dependencies.

| Class | What it does |
|---|---|
| [`CachedObject<T>`](#cachedobject) | Holds one `Serializable` object, keeps it in memory only weakly and reloads it from a storage on demand |
| [`CachedHashMap<K, V>`](#cachedhashmap) | A `java.util.Map` whose values are each kept in a `CachedObject`, so large maps don't fill the heap |
| [`NestedMap<K, V>`](#nestedmap) | A map of maps (a trie): a value is stored under a path of keys, e.g. `put("a", "b", "c", value)` |
| [`CachedNestedMap<K, V>`](#cachednestedmap) | A `NestedMap` whose values are cached like in a `CachedHashMap` |
| [`CachedQueue<E>`](#cachedqueue) | A thread-safe FIFO `java.util.Queue` whose elements are each kept in a `CachedObject` |

The cached types come with a file system implementation in the package `com.schoste.ddd.util.fs`;
`NestedHashMap` is the in-memory `NestedMap`.

## CachedObject

`CachedObject<T>` holds a `Serializable` object without keeping it in memory all the time.
The object is referenced weakly, so the garbage collector may reclaim it when memory gets tight;
its data is kept in a storage and loaded again on the next `get()`.

`fs.CachedObjectImpl` is the file system implementation: it serializes the object into a cache file
in `java.io.tmpdir` or in a folder you choose.

```java
import com.schoste.ddd.util.CachedObject;
import com.schoste.ddd.util.fs.CachedObjectImpl;

try (CachedObject<ArrayList<String>> cached = new CachedObjectImpl<>(new ArrayList<>(), "/var/cache/my-app"))
{
    ArrayList<String> list = cached.get();

    list.add("entry");
    cached.commit();      // save the change while you still hold a reference to the object

    list = null;          // from now on, the GC may reclaim the list

    list = cached.get();  // in memory: same instance; collected: loaded from the cache file
}                         // close() deletes the cache file
```

### Rules

- **Commit while you hold a reference.** Changes that were not committed are lost once the object
  is garbage collected. If it was already collected, `commit()` does nothing.
- **Don't rely on getting the same instance.** `get()` may return a newly loaded copy.
- **Hash codes must not change.** `hashCode()` is taken once when the `CachedObject` is created,
  while `equals()` compares the current objects. Changes to the object must not change its hash code
  (the same rule as for keys of a `HashMap`).
- **`clone()` copies the current state**, including uncommitted changes, into its own cache file.
- **Close it.** `close()` deletes the cache file. Files that were not closed are deleted when the JVM exits.
- **Serializing a `CachedObjectImpl` writes only the path of the cache file**, not the object itself.
  It can only be deserialized where that file exists and while it was not closed.
- **Threads**: one `CachedObject` can be shared by several threads. `get()`, `commit()` and `close()` are
  synchronized; after `close()` (also from another thread) `get()` and `commit()` throw `IllegalStateException`.
  Several threads or JVMs may create `CachedObjectImpl`s in the same folder at the same time. But:
  - the object inside is not protected: synchronize your changes, and don't change it while another thread
    calls `commit()` or `clone()`;
  - copies that share a cache file (e.g. a deserialized copy) don't synchronize with each other: `get()` on one
    while the other one saves may read an incomplete file. Share one instance instead of copies.
  - `CachedHashMap`, `NestedMap` and `CachedNestedMap` are not thread-safe (like `java.util.HashMap`);
    [`CachedQueue`](#cachedqueue) is.

## CachedHashMap

`CachedHashMap<K, V>` is a `java.util.Map` for many or large values. The keys stay in memory;
each value is stored in its own `CachedObject`, so the garbage collector may reclaim values that aren't in use,
and the map loads them again when they are needed.

`fs.CachedHashMapImpl` stores each value in its own cache file in `java.io.tmpdir` or in a folder you choose.

```java
import com.schoste.ddd.util.CachedHashMap;
import com.schoste.ddd.util.fs.CachedHashMapImpl;

try (CachedHashMap<String, ArrayList<String>> map = new CachedHashMapImpl<>("/var/cache/my-app"))
{
    map.put("a", new ArrayList<>(Arrays.asList("1")));  // put() saves the value

    ArrayList<String> a = map.get("a");

    a.add("2");
    map.put("a", a);      // save a changed value by putting it again ...
    map.commit();         // ... or save all values which are in memory

    for (ArrayList<String> value : map.values())
    {
        // values are loaded one at a time; the GC can reclaim each one after use
    }
}                         // close() deletes all cache files
```

### Rules

- **Save changes**: `put()` saves a value. Save a changed value with `put()` again, or all values in memory
  with `commit()`, while you still hold a reference to them. Changes that weren't saved are lost
  once the value is garbage collected. If saving one value fails, `commit()` still saves the others and then
  throws the first failure (the others are attached as suppressed exceptions).
- **Values may be copies**: `get()`, `values()` and the entries may return a copy loaded from the storage,
  not the instance you put.
- **No `null` values**: `put(key, null)` throws a `NullPointerException`. `null` keys are allowed.
- **Methods that need all values are slow, not memory hungry**: `values()`, `containsValue()`, `equals()`,
  `hashCode()`, `toString()` and iterating or streaming `values()`/`entrySet()` load the values one at a time
  and keep no references. Each call reads all values from the storage, though.
  `size()`, `containsKey()` and `keySet()` don't load any value.
- **Removing frees storage**: `remove()`, replacing a value with `put()`, `clear()` and `close()` delete the
  cache files of the affected values. Close the map when you don't need it anymore.
- **Storage errors** are thrown as `IllegalStateException` with the original exception as cause,
  because the methods of `Map` can't throw checked exceptions.
- **`clone()` copies the current state**, including changes that weren't saved, into its own cache files.
  Changes to the clone don't affect the original, and the other way round.
- **Serializing writes only references**: the keys, the storage path and the paths of the cache files, not the values.
  Call `commit()` before serializing. The deserialized map shares the cache files with the original,
  so closing or removing values in one of them affects the other one too. The keys must be `Serializable`.
- **Not thread-safe**, like `java.util.HashMap`: synchronize access from several threads.

## NestedMap

`NestedMap<K, V>` stores values under a path of keys, like a map of maps but without creating the inner maps
yourself. A shorter path (a prefix) leads to the nested map of that level. `NestedHashMap` keeps everything in
memory and uses a `HashMap` for each level.

```java
import com.schoste.ddd.util.NestedHashMap;
import com.schoste.ddd.util.NestedMap;

NestedMap<String, Integer> map = new NestedHashMap<>();

map.put("europe", "austria", "vienna", 1_982_000);
map.put("europe", "austria", 9_160_000);         // a path can have a value and deeper keys
map.put(Arrays.asList("a", "b", "c", "d", "e", "f"), 6);  // more than 5 keys: pass a list

map.get("europe", "austria", "vienna");           // 1982000
map.get("europe", "austria");                     // 9160000
map.get("europe");                                // null: no value, only deeper keys

NestedMap<String, Integer> austria = map.getSubMap("europe", "austria");

austria.get("vienna");                            // 1982000
austria.put("graz", 303_000);                     // the sub map is a live view: also map.get("europe", "austria", "graz")

map.keySet();                                     // [europe, a]: the keys of the first level
map.size();                                       // 4: all values at any depth
```

### Rules

- **Paths**: `put` takes 1 to 5 keys and the value; longer paths use `put(List<K> keys, V value)`.
  `get`, `containsKey`, `remove` and `getSubMap` take any number of keys. A path for a value needs at least one key
  (`IllegalArgumentException` otherwise).
- **A path can have a value and deeper keys.** `get` returns `null` for a path without a value, also if it has deeper keys.
  Check for deeper keys with `!map.getSubMap(keys).isEmpty()`.
- **Sub maps are live views** of their path: changes are visible in both directions. The sub map of a path that
  doesn't exist is empty, and `put` through it creates the path. `getSubMap()` without keys returns the map itself.
- **`remove` removes only the value**; deeper keys stay. Levels without values and deeper keys are removed.
  To remove a path with everything below it: `map.getSubMap(keys).clear(); map.remove(keys);`
- **`clear()` of a sub map** removes everything below its path, but not the value at the path itself.
- **`size()` counts all values** at any depth; `keySet()` returns a copy of the keys of the first level.
- **No `null` values** (`NullPointerException`). `null` keys are allowed; cast a single `null` key to the key class,
  `put((String) null, value)`, because `put(null, value)` also matches `put(List, V)`.
- **`clone()`** copies all levels; `NestedHashMap` shares the value instances with the clone (like `HashMap`).
  A sub map can't be cloned (`CloneNotSupportedException`).
- **Serializable** if the keys and values are. A serialized sub map includes the whole map it is a view of.
- **Not thread-safe**, like `java.util.HashMap`: synchronize access from several threads, also through sub maps.
- `NestedMap` is not a `java.util.Map`, because a path has several keys.
- **Compared by content**, like a `Map` with the key paths as keys: two nested maps are `equals` if they have the same
  paths with equal values, also across implementations (a `NestedHashMap` can equal a `CachedNestedHashMapImpl`).
  `toString()` lists the values as `{[a]=1, [a, x]=2}`. For cached maps, `equals`/`hashCode`/`toString` load every value.

## CachedNestedMap

`CachedNestedMap<K, V>` is a `NestedMap` whose values are cached like in a `CachedHashMap`: the keys stay in memory,
each value is kept in memory only weakly and loaded from the storage when needed. `fs.CachedNestedHashMapImpl`
uses an `fs.CachedHashMapImpl` for the values of each level, so each value has its own cache file in
`java.io.tmpdir` or in a folder you choose.

```java
import com.schoste.ddd.util.CachedNestedMap;
import com.schoste.ddd.util.fs.CachedNestedHashMapImpl;

try (CachedNestedMap<String, ArrayList<String>> map = new CachedNestedHashMapImpl<>("/var/cache/my-app"))
{
    map.put("a", "b", new ArrayList<>(Arrays.asList("1")));  // put() saves the value

    ArrayList<String> ab = map.get("a", "b");

    ab.add("2");
    map.put("a", "b", ab);  // save a changed value by putting it again ...
    map.commit();           // ... or save all values in memory, on all levels
}                           // close() deletes all cache files
```

### Rules

All rules of [`NestedMap`](#nestedmap) apply, and the ones of [`CachedHashMap`](#cachedhashmap) for the values:

- **Save changes** with `put` again or `commit()` while you hold a reference; unsaved changes are lost once the
  value is garbage collected. **Values may be copies** loaded from the storage.
- **Removing frees storage**: `remove`, replacing with `put`, `clear()` (also of a sub map) and `close()` delete
  the cache files of the affected values. Close the map when you don't need it anymore.
- **`commit()` and `close()` act on the whole map.** `getSubMap` returns a plain `NestedMap` view without them.
- **`clone()` copies the current state**, including changes that weren't saved, into its own cache files.
- **Serializing writes only references** (keys, storage path, paths of the cache files). Call `commit()` before
  serializing; the deserialized map shares the cache files with the original.
- **Storage errors** are thrown as `IllegalStateException` with the original exception as cause.

## CachedQueue

`CachedQueue<E>` is a thread-safe FIFO `java.util.Queue` for many or large elements. Each element is stored in its own
`CachedObject`, so the garbage collector may reclaim the elements which aren't in use, and the queue loads them again
when they are needed. It doesn't block: `poll()` returns `null` for an empty queue instead of waiting, like
`java.util.concurrent.ConcurrentLinkedQueue`.

`fs.CachedQueueImpl` stores each element in its own cache file in `java.io.tmpdir` or in a folder you choose.

```java
import com.schoste.ddd.util.CachedQueue;
import com.schoste.ddd.util.fs.CachedQueueImpl;

try (CachedQueue<ArrayList<String>> queue = new CachedQueueImpl<>("/var/cache/my-app"))
{
    queue.offer(new ArrayList<>(Arrays.asList("1")));  // offer() saves the element
    queue.offer(new ArrayList<>(Arrays.asList("2")));

    ArrayList<String> head = queue.peek();  // the head stays in the queue ...

    head.add("changed");
    queue.commit();     // ... so save the change with commit() while you hold the reference

    head = queue.poll();  // [1, changed]; poll() deletes its cache file
}                         // close() deletes the remaining cache files
```

### Rules

- **Thread-safe**: all methods may be called from several threads at the same time. Adding, taking or removing one
  element is atomic, e.g. two threads never `poll()` the same element. Bulk methods (`addAll`, `removeAll`, ...) are not
  atomic as a whole, and changes to the elements themselves are not synchronized by the queue.
- **Save changes** to an element which is in the queue with `commit()`, while you still hold a reference to it.
  Changes that weren't saved are lost once the element is garbage collected.
- **Elements may be copies**: `poll()`, `peek()` and iterating may return a copy loaded from the storage,
  not the instance you added.
- **Iterators are weakly consistent**, like those of `ConcurrentLinkedQueue`: they return the elements which were in the
  queue when they were created, in order, skip elements removed in the meantime, and never throw a
  `ConcurrentModificationException`. They load the elements one at a time; `size()` and `isEmpty()` don't load any.
- **Removing frees storage**: `poll()`, `remove()`, `remove(Object)`, `removeIf`, the iterator's `remove()`, `clear()` and
  `close()` delete the cache files of the removed elements. Close the queue when you don't need it anymore.
- **No `null` elements**: `offer(null)` and `add(null)` throw a `NullPointerException`. There is no capacity limit.
- **`equals()` and `hashCode()` are identity**, like for `ArrayDeque` and `ConcurrentLinkedQueue`.
- **`clone()` copies the current state**, including changes that weren't saved, into its own cache files.
- **Serializing writes only references** (the paths of the cache files). Call `commit()` before serializing;
  the deserialized queue shares the cache files with the original.
- **Storage errors** are thrown as `IllegalStateException` with the original exception as cause.

## Installation

The library is published to GitHub Packages. Add the repository and the dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github-dao-domain-utils</id>
        <url>https://maven.pkg.github.com/phisch84/dao-domain-utils</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.schoste.ddd</groupId>
        <artifactId>utils</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

GitHub Packages requires authentication even for public packages. Add a server with the same id to
your `~/.m2/settings.xml`, using a [personal access token](https://github.com/settings/tokens)
with the `read:packages` scope:

```xml
<servers>
    <server>
        <id>github-dao-domain-utils</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_TOKEN</password>
    </server>
</servers>
```

Sources and Javadoc are published as well (`-sources.jar`, `-javadoc.jar`), so your IDE can show them;
the usage rules of each type are in the Javadoc of its interface.

## Implementing another storage

To store data somewhere else than in files (e.g. a database), implement the abstract classes:

| Type | Derive from | Implement |
|---|---|---|
| `CachedObject` | `AbstractCachedObject` | `save()`, `load()`, `clone()`, `equals()`, `close()` (sets `objRef` to `null` while holding `objRefLock`) |
| `CachedHashMap` | `AbstractCachedHashMap` | `createCachedObject(V)`, which wraps a value in your `CachedObject` |
| `NestedMap` | `AbstractNestedMap` | `createValueMap()`, the map for the values of one level; `cloneValueMap(Map)` if the values should not be shared by clones |
| `CachedNestedMap` | `AbstractNestedMap` | as for `NestedMap`, returning your `CachedHashMap`; plus `commit()`, `close()` and `cloneValueMap(Map)` (see `fs.CachedNestedHashMapImpl`) |
| `CachedQueue` | `AbstractCachedQueue` | `createCachedObject(E)`, which wraps an element in your `CachedObject` |

The test-jar (`<classifier>tests</classifier>`) contains the abstract contract tests `CachedObjectUT`,
`CachedHashMapUT`, `NestedMapUT`, `CachedNestedMapUT` (which includes `NestedMapUT`) and `CachedQueueUT`:
derive your test classes from them and implement their factory methods to check that your implementation behaves like the others.

## Building

```sh
mvn clean verify
```

This runs the tests and the strict Javadoc check, which fails the build on any finding.
It also writes a test coverage report to `target/site/jacoco/index.html` (JaCoCo; report only, no thresholds).

Mutation testing (PIT) checks whether the tests notice small changes to the code. It takes about a minute
and is therefore not part of `verify`:

```sh
mvn clean test-compile org.pitest:pitest-maven:mutationCoverage
```

The report is written to `target/pit-reports/index.html`.
Contributor and AI-assistant notes (architecture, design decisions, conventions) are in [CLAUDE.md](CLAUDE.md);
[AGENTS.md](AGENTS.md) points other AI agents there.

## License

[MIT](LICENSE)
