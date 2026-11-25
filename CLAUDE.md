# DDD Utils (`com.schoste.ddd:utils`)

Helper library for the DDD libraries [dao](https://github.com/phisch84/dao) and
[domain-repository](https://github.com/phisch84/domain-repository): code that fits none of the
infrastructure, domain or application layers. It provides memory-saving holders whose data lives in a
storage and is kept in memory only weakly, a map with key paths, and a thread-safe queue:

- `CachedObject<T>`: one `Serializable` object, reloaded from storage on demand.
- `CachedHashMap<K, V>`: a `java.util.Map` whose keys stay in memory and whose values are each a `CachedObject`.
- `NestedMap<K, V>`: a map of maps (trie): `put(k1, k2, k3, v)`, `get(k1, k2, k3)`, `getSubMap(k1)`.
  `NestedHashMap` is in memory; `CachedNestedMap` / `fs.CachedNestedHashMapImpl` caches the values.
- `CachedQueue<E>`: a thread-safe FIFO `java.util.Queue` whose elements are each a `CachedObject`.

User-facing documentation is in `README.md`; the contract of each class is its Javadoc.

## Commands

| Purpose | Command |
|---|---|
| Definition of done (tests + strict Javadoc) | `mvn -B clean verify` |
| One test class | `mvn -B test -Dtest=CachedHashMapImplUT` |
| Some test methods | `mvn -B test -Dtest='CachedHashMapImplUT#testClone*'` |
| Mutation testing (PIT, ~1 min) | `mvn -B clean test-compile org.pitest:pitest-maven:mutationCoverage` |

- Use `verify`, not just `test`: only `verify` runs the Javadoc check, which fails the build on any finding.
- `verify` also writes the JaCoCo coverage report: `target/site/jacoco/index.html`, per line in `jacoco.xml`,
  per class in `jacoco.csv` (easiest to read in a terminal). It has no thresholds and never fails the build.
  Use it to find untested branches when adding tests; with `-Dtest=...` it only covers those tests.
- PIT (mutation testing) is a report only and not part of `verify`. It writes `target/pit-reports/index.html` and
  `mutations.csv` (one line per mutant: file, class, mutator, method, line, status, killing test). After adding tests,
  run it and look at the lines whose status isn't `KILLED`; a surviving mutant is a change no test notices.
  Instead of the hand-written `sed` mutations, use it to check that new tests can fail.
  PIT ends its test JVMs without shutdown hooks, so cache files the tests leave to the hook stay behind; PIT's
  `java.io.tmpdir` is therefore `target/` (removed by `clean`), and `.gitignore` ignores 13-digit file names in the
  repo root (a mutant can redirect a cache file there).
  Accepted survivors (equivalent or not testable; don't write tests for them), all others must be killed:
  - `CachedObjectImpl.initializeCacheFile`: `cacheFileName++` → `--` (counting down also finds a free name), and the
    suppressed delete failure (the file system can't be made to refuse a delete in a test).
  - `AbstractNestedMap.prune`: `i >= 0` → `i > 0` (an empty value map stays on the root; invisible through the API).
  - `AbstractNestedMap.SubMap.createValueMap`: never called (see its comment).
  - `AbstractCachedQueue.containsInstance`/`removeInstance`: "replaced boolean return with true" on a line which
    returns true (and the same for false) changes nothing.
  - `CachedObjectImpl.initializeCacheFile`: the `MAX_ACCESS_DENIED_RETRIES` limit has no coverage on Windows, because
    `testUnwritableFolderIsRejected` needs POSIX permissions; it runs on CI (Linux).
- Use `clean` when results look odd: the IDE (VS Code Java / Eclipse compiler) also compiles into `target/`,
  and Maven then runs stale classes ("Unresolved compilation problem").
- CI (`.github/workflows/build.yml`) runs `mvn -B verify` on JDK 21 and 25 for every push and pull request.
  Each successful run keeps the coverage report of JDK 21 as artifact `coverage-report` (30 days);
  failed runs keep the Surefire reports.

## Code map

| File | Role |
|---|---|
| `src/main/java/com/schoste/ddd/util/CachedObject.java` | Interface + usage rules of a cached object |
| `src/main/java/com/schoste/ddd/util/AbstractCachedObject.java` | Weak reference, locking, `get()`/`commit()`; subclasses implement `save()`/`load()` |
| `src/main/java/com/schoste/ddd/util/fs/CachedObjectImpl.java` | File system storage: one cache file per object |
| `src/main/java/com/schoste/ddd/util/CachedHashMap.java` | Interface + usage rules of the map, adds `commit()` and `clone()` |
| `src/main/java/com/schoste/ddd/util/AbstractCachedHashMap.java` | All map logic (extends `java.util.AbstractMap`); subclasses implement `createCachedObject(V)` |
| `src/main/java/com/schoste/ddd/util/fs/CachedHashMapImpl.java` | Creates a `CachedObjectImpl` per value in `storagePath` |
| `src/main/java/com/schoste/ddd/util/NestedMap.java` | Interface + usage rules of the nested map (not a `java.util.Map`) |
| `src/main/java/com/schoste/ddd/util/AbstractNestedMap.java` | All nested-map logic (levels, sub-map views, pruning, clone); subclasses implement `createValueMap()` |
| `src/main/java/com/schoste/ddd/util/NestedHashMap.java` | In-memory implementation: a `HashMap` per level |
| `src/main/java/com/schoste/ddd/util/CachedNestedMap.java` | Interface: `NestedMap` + `commit()` + `Closeable`, rules of a cached nested map |
| `src/main/java/com/schoste/ddd/util/fs/CachedNestedHashMapImpl.java` | A `CachedHashMapImpl` per level in `storagePath`; `commit`/`close`/`cloneValueMap` |
| `src/main/java/com/schoste/ddd/util/CachedQueue.java` | Interface + usage rules of the thread-safe queue, adds `commit()` and `clone()` |
| `src/main/java/com/schoste/ddd/util/AbstractCachedQueue.java` | All queue logic (extends `java.util.AbstractQueue`, lock, snapshot iterator); subclasses implement `createCachedObject(E)` |
| `src/main/java/com/schoste/ddd/util/fs/CachedQueueImpl.java` | Creates a `CachedObjectImpl` per element in `storagePath` |
| `src/test/java/com/schoste/ddd/util/CachedObjectUT.java` | Contract test for every `CachedObject` implementation |
| `src/test/java/com/schoste/ddd/util/CachedHashMapUT.java` | Contract test for every `CachedHashMap` implementation |
| `src/test/java/com/schoste/ddd/util/AbstractCachedHashMapUT.java` | Storage errors of `AbstractCachedHashMap` (with `FailingCachedObject`) |
| `src/test/java/com/schoste/ddd/util/TestHelpers.java` | Shared static test helpers (lists, sets, serialization, temp folders) |
| `src/test/java/com/schoste/ddd/util/FailingCachedObject.java` | Test double: in-memory `CachedObject` whose `get`/`commit`/`close`/`clone` can be made to fail |
| `src/test/java/com/schoste/ddd/util/NestedMapUT.java` | Contract test for every `NestedMap` implementation |
| `src/test/java/com/schoste/ddd/util/AbstractNestedMapUT.java` | Failures in `AbstractNestedMap#clone()` (test map whose n-th value-map copy fails) |
| `src/test/java/com/schoste/ddd/util/CachedNestedMapUT.java` | Contract test for every `CachedNestedMap` (extends `NestedMapUT`) |
| `src/test/java/com/schoste/ddd/util/NestedHashMapUT.java` | Factory for `NestedMapUT` + `NestedHashMap` specific tests |
| `src/test/java/com/schoste/ddd/util/CachedQueueUT.java` | Contract test for every `CachedQueue` (incl. a concurrency stress test) |
| `src/test/java/com/schoste/ddd/util/AbstractCachedQueueUT.java` | Storage errors and races of `AbstractCachedQueue` (with `FailingCachedObject`), lock release |
| `src/test/java/com/schoste/ddd/util/fs/*ImplUT.java` | Factories for the contract tests + file-system specific tests |

## Architecture pattern (follow it for every new utility)

1. **Interface** in `com.schoste.ddd.util`: the contract. Its Javadoc lists the usage rules.
2. **`Abstract*` class** in `com.schoste.ddd.util`: all logic that doesn't depend on the storage.
   The storage-specific part is one or two `abstract protected` methods.
3. **Implementation** per storage in a sub-package (`fs` = file system), named `*Impl`.
4. **Contract test** `*UT` (abstract) in `com.schoste.ddd.util` with `abstract protected` factory methods,
   and per implementation a subclass `<sub-package>/*ImplUT` that implements the factories and adds
   implementation-specific tests. Contract tests may use protected fields (same package) and skip with
   `Assume` for implementations that don't derive from the `Abstract*` class.

Checklist for a new utility or implementation:

- [ ] Write the tests first and run them red; if the behaviour already exists, break the code temporarily
      and check that the new tests fail, then restore it.
- [ ] Javadoc for everything public/protected, including the usage rules in the interface.
- [ ] `serialVersionUID` on serializable classes; decide what serialization writes.
- [ ] Every way to remove or replace something frees its storage (`close()`).
- [ ] Decide the thread safety and state it in the interface Javadoc and `README.md` (the maps are not thread-safe,
      `CachedQueue` is); a thread-safe type needs concurrency tests (see Tests).
- [ ] `mvn -B clean verify` passes; update `README.md` (users) and this file (decisions, code map).

## Design decisions (don't change without asking)

### CachedObject

- `hashCode()` is taken once when the holder is created and never recalculated. It is serialized,
  so a deserialized copy keeps it. Cached objects must not change their hash code.
- `commit()` does nothing if the object was already garbage collected: the storage keeps the last
  committed state. Callers must commit while they hold a reference.
- If `clone()` fails, it throws `CloneNotSupportedException` with the original exception as cause (like the maps),
  never just a stack trace on `System.err`.
- `clone()` copies the current state (via `get()`), including uncommitted changes, into its own
  cache file and holds its own instance.
- `close()` does nothing on an instance that is already closed or was never initialized (no-arg constructor);
  `hashCode()` still works after `close()`, `get()`/`commit()` throw `IllegalStateException`,
  `equals` is false (it can't read the object). The library never writes to `System.err`/`System.out`:
  failures are thrown (with cause) or, in `equals`, are the `false` result.
- `fs.CachedObjectImpl` deletes cache files on `close()`; files that were not closed are deleted by
  one shutdown hook (tracked in `cacheFilesToDeleteOnExit`). Don't use `File#deleteOnExit()`, it leaks.
- `objRef` is read and written only under `objRefLock` (also the closed/null check), except while an instance
  isn't published yet (constructor, `readObject`, `clone`). That's why it needs no `volatile`. A concurrent
  `close()` makes `get()`/`commit()` throw `IllegalStateException` (tested deterministically by
  `closeWhileBlockedOnLock` in `CachedObjectUT`).
- **Thread safety** (rules in the `CachedObject` Javadoc): the lock protects the reference, not the object's state.
  Instances that share a cache file (deserialized copies) have separate locks; that race is accepted and
  documented, not fixed (a lock per file, e.g. a static map, was rejected as too big a change).
- `fs.CachedObjectImpl` creates each cache file atomically (`Files.createFile`, next name on
  `FileAlreadyExistsException`), so instances created concurrently in one folder (also by other JVMs) never
  share a file. Never check-then-create (`exists()` + write) or use fixed helper file names in the folder
  (tested by `testConcurrentCreationInSameFolder`). If writing the new file fails (e.g. not serializable), the
  constructor deletes and unregisters it before rethrowing (`testConstructorDeletesCacheFileIfWritingFails`).
- On Windows, `Files.createFile` fails with `AccessDeniedException` (not `FileAlreadyExistsException`) while another
  thread deletes a file of the same name. The constructor then tries the next name too, but at most
  `MAX_ACCESS_DENIED_RETRIES` (100) times, because an unwritable folder denies every name
  (`testConcurrentCreationAndCloseInSameFolder`; the limit is tested by `testUnwritableFolderIsRejected`, which needs
  POSIX permissions and is skipped on Windows, so it runs only on CI).
- Serialization of `fs.CachedObjectImpl` writes only `cacheFilePath` and `objHashCode`, never the cached object.
  `readObject` loads the object once (into the weak reference).

### CachedHashMap

- Values live in `protected HashMap<K, CachedObject<V>> cachedValues`; `AbstractCachedHashMap` extends
  `java.util.AbstractMap` and reuses it wherever possible. `cachedValues` is not `final` only because
  `clone()` replaces it.
- `put(k, v)` wraps the value in a new `CachedObject` and commits it; if the commit fails, the new `CachedObject`
  is closed and the previous value stays. If the previous value can't be loaded to be returned, it is still
  replaced and closed, and `put` throws. Calling `put` again is how a single
  changed value is saved; `commit()` saves all values that are in memory.
- Null values are rejected with `NullPointerException` (a `CachedObject` can't hold null); null keys are allowed.
- `Map` methods can't throw checked exceptions: storage errors become `IllegalStateException` with the
  original exception as cause (`unchecked(...)`).
- Values returned by the map may be copies loaded from storage, not the instance that was put.
- **Lazy views**: `entrySet()` iterates over `cachedValues`; its entries (`LazyEntry`) load a value only in
  `getValue()` (via `map.get(key)`) and keep no reference. Every other view and bulk method of `AbstractMap`
  (`keySet`, `values`, `containsValue`, `equals`, `hashCode`, `toString`) goes through it. Never add caches,
  lists of values or eager loading; key-only methods must not load values (tested by `testKeysDontLoadValues`,
  `testValuesAreNotRetained`).
- **Every removal path closes the removed `CachedObject`** (deletes its storage): `remove`, replacing via
  `put`/`setValue`, `clear`, `close`, and the iterator's `remove()` (which covers `keySet()`/`values()`
  removal, `removeIf`, `retainAll`). A new removal path must do the same.
- `close()`/`clear()` try to close every value; the first `IOException` is thrown with the others suppressed.
  `commit()` likewise tries to save every value (the first exception thrown, the others suppressed); so does
  `CachedNestedHashMapImpl.commit()`/`close()` across the levels.
- **Serialization writes references only**: `cachedValues` is serialized as is, so each `CachedObjectImpl` writes
  its cache-file path. The deserialized map shares the cache files with the original (closing one frees
  the other's storage). Callers must `commit()` before serializing. `K` has no `Serializable` bound
  (like `java.util.HashMap`); non-serializable keys fail at runtime with `NotSerializableException`.
- `clone()`: `super.clone()` copies all fields (also of subclasses, e.g. `storagePath`), then every
  `CachedObject` is cloned, so the clone has its own storage and instances with the current state.
  If cloning fails, already cloned values are closed and `CloneNotSupportedException` (with cause) is thrown.

### NestedMap / CachedNestedMap

- `NestedMap` is its own interface, **not** a `java.util.Map` (a path has several keys). Reading/removing take
  varargs (`get(K...)`, `containsKey`, `remove`, `getSubMap`); `put` has overloads for 1 to 5 keys plus
  `put(List<K>, V)`, all delegating to `put(List, V)`. `put(null, v)` is ambiguous; callers cast the key.
- Classic trie: a path can have a value **and** deeper keys; paths of any depth can be mixed.
- `get` returns null for a path without value; null values are rejected (NPE), null keys allowed;
  an empty path throws `IllegalArgumentException` (except `getSubMap()`, which returns `this`).
- `size()`/`isEmpty()` count values at any depth; `keySet()` is a copy of the first-level keys.
- **Equality by content**, like `Map` with the key path (`List<K>`) as key, defined in the `NestedMap` interface so
  implementations agree: `equals` = other is a `NestedMap`, same `size()`, and `other.get(path)` equals each value;
  `hashCode` = sum of `path.hashCode() ^ value.hashCode()`; `toString` = `{[a]=1, [a, x]=2}` (paths as lists,
  because a path can have a value and deeper keys). `AbstractNestedMap` implements them with `visitValues`, which
  reads one value at a time. Sub maps compare with paths relative to their own path.
- **Structure** (`AbstractNestedMap`): private `Level` = `Map<K, V> values` (from `createValueMap()`, null while empty)
  + `HashMap<K, Level> children`. Only the value map is storage-specific; keys and levels are always in memory.
- **Sub maps are live views by path**: private `SubMap extends AbstractNestedMap` shares the owner's `root` and
  prefixes its `path`; every call resolves the path again, so a view survives pruning and re-creation. A missing
  path gives an empty view; `put` through it creates the path. `clear()` on a view keeps the value at its path.
- **Pruning**: `remove`/`clear` remove levels left without values and children (never the root); empty value maps are
  dropped (set to null). A `put` whose value map throws prunes the path too, so it leaves no empty levels
  (tested by `AbstractNestedMapUT` and `testPutOfUnsavableValueLeavesMapUnchanged`).
  `clear` empties every value map below, which frees cached storage.
- `CachedNestedMap`: `commit()`/`close()` act on the whole map (via `getValueMaps()`); `getSubMap` returns a plain
  `NestedMap`. `close()` closes every level's value map (first `IOException` thrown, others suppressed).
- `CachedNestedHashMapImpl` uses its value maps only through the `CachedHashMap` interface (never casts to
  `CachedHashMapImpl`), so a subclass may return any `CachedHashMap` from `createValueMap()`
  (tested by `testWorksWithOtherCachedHashMaps`).
- **Serialization**: levels and value maps are serialized as they are (cached: references only, shared cache files).
  A serialized sub map includes its owner. Keys/values have no `Serializable` bound in `NestedMap`.
- **`clone()`**: `super.clone()` + copy every level with `cloneValueMap(Map)` (default: new value map + `putAll`, i.e.
  shared value instances like `HashMap`; `CachedNestedHashMapImpl` clones each `CachedHashMapImpl`). On failure the
  copied part is cleared. `owner`/`root` are not `final` only because of `clone()`. Cloning a sub map throws
  `CloneNotSupportedException` without cause.
- `NestedHashMap` is in `com.schoste.ddd.util`, not in a sub-package, because it has no storage.
- `commit()`/`close()` live in `fs.CachedNestedHashMapImpl`; extract an `AbstractCachedNestedMap` when a second
  storage implementation appears.

### CachedQueue

- `CachedQueue<E extends Serializable>` extends `java.util.Queue` (FIFO, unbounded, not blocking like
  `ConcurrentLinkedQueue`; `BlockingQueue` can be added later, it extends `Queue`). One `CachedObject` per element.
  Use case: `domain-repository`'s `GenericEntityLoaderImpl` keeps entities in a `Queue` with `add`/`poll`.
- **Thread-safe** (unlike the maps): `protected ReentrantLock lock` guards only the order in
  `protected ArrayDeque<CachedObject<E>> cachedElements`. Loading and saving happen **outside** the lock, so a slow
  storage doesn't block other threads (needed for the planned background pre-loading); each `CachedObject` locks itself.
  Every access to `cachedElements` holds the lock; every method releases it (`testEveryMethodReleasesTheLock`).
- **Elements removed meanwhile**: between reading the order and loading, another thread may remove and close an
  element. `peek` then retries with the new head; the iterator, `commit` and `clone` skip it. A failure of an element
  which is still in the queue (`containsInstance`) is a real storage error and is thrown.
- **Identity, not `equals`**: `CachedObject.equals` compares content, so `ArrayDeque.remove(Object)`/`contains` could hit
  an equal element. `containsInstance`/`removeInstance` compare `CachedObject`s with `==`.
- **Iterator** (weakly consistent): snapshot of the `CachedObject` references; loads in `hasNext`, keeps no reference
  after `next`; `remove()` removes and closes exactly the element returned last (no-op if already removed).
- `offer` wraps and commits outside the lock, then appends; a failed commit closes the new element. `poll` takes the head
  under the lock, then loads and closes it (closed also if loading fails). `clear()`/`close()` close every element
  without loading it (overrides `AbstractQueue.clear`, which polls). `commit()`/`close()` try every element (first
  exception thrown, others suppressed). The queue can be used again after `close()`.
- `equals`/`hashCode` are identity (like `ArrayDeque`); bulk methods of `AbstractQueue` aren't atomic as a whole.
- **Serialization**: `cachedElements` as is (references only, shared cache files), written by `writeObject` while
  holding the lock; `lock` is `transient` and recreated in `readObject`.
- **`clone()`**: `super.clone()` + own lock + a clone of every `CachedObject` from a snapshot (skips elements removed
  meanwhile); on failure the clones are closed and `CloneNotSupportedException` (with cause) is thrown.

## Code and Javadoc rules

- The code must compile against **Java 8** (`maven.compiler.release=8`), even though newer JDKs build it.
  No `var`, records, `List.of`, text blocks, etc.
- Formatting follows `.editorconfig`: UTF-8, LF line endings (enforced by `.gitattributes`), 4 spaces,
  no tabs, no trailing whitespace, final newline. Braces on their own line (Allman style), `this.` for members.
- doclint runs with `failOnWarnings`: every public/protected class, constructor, field and method needs a
  complete comment (`@param`, `@return`, `@throws`). Write explicit constructors instead of relying on the default one.
- The private serialization members (`serialPersistentFields`, `writeObject`, `readObject`) need comments too;
  they appear on the "Serialized Form" page.
- `{@see ...}` is not an inline tag; use `{@link ...}`. Field references use `#`: `{@link System#err}`.
- In an interface, `{@link #toString()}` fails because the interface doesn't declare it;
  use `{@link java.lang.Object#toString() toString()}`.
- Test classes are checked as well, except for missing comments.

## Tests

- Test classes are named `*UT` (Surefire is configured to include `**/*UT.java`).
- The test classes ship as a test-jar so other implementations can reuse the contract tests.
- Don't rely on GC timing: simulate collection by clearing the weak reference (`objRef.clear()`), or wait on
  your own `WeakReference` with a bounded `System.gc()` loop and assert that it was cleared.
- `TestHelpers` (static imports, also from sub-packages): `list(...)`, `set(...)`, `serializeAndDeserialize(obj)`,
  `countFiles(folder)`, `deleteRecursively(folder)` (cleanup of temporary folders in `finally`). Add helpers that
  don't need a class's internals there instead of copying them into a test class.
- Assertions show both values on failure: `assertEquals`/`assertNotEquals`/`assertSame`/`assertNotSame`, never
  `assertTrue(a.equals(b))` or `assertTrue(a == b)`. To test `equals` symmetry, assert both directions.
- Helpers in `CachedHashMapUT`: `simulateGC(map)`, `cachedValueOf(map, key)`, `isLoaded(map, key)`,
  `assertClosed(cachedObject)`.
- Helpers in `CachedNestedMapUT`: `valueMapsOf(map)`, `simulateGC(map)`, `cachedValuesOf(map)`,
  `cachedValueWith(map, value)`. Its generic factory `createCachedNestedMap()` lets tests use mutable values
  (`ArrayList`), while `NestedMapUT` uses `String` values.
- Test error paths with `FailingCachedObject`: set `failOnGet`/`failOnCommit`/`failOnClose`/`failOnClone`;
  `closeCalled`, `closed` and `clones` show what the code under test did. `AbstractCachedHashMapUT.TestMap`
  wraps every value in one and lists them in `created`. For the nested maps, `AbstractNestedMapUT.TestNestedMap`
  makes the n-th `cloneValueMap` call fail, and `CachedNestedHashMapImplUT.TestCachedNestedMap` uses a
  `FailingValueMap` (a `CachedHashMapImpl` whose `close`/`clone` fail) per level, so tests can count cache files.
- `FailingCachedObject.beforeNextAccess` runs once at the start of the next `get`/`commit`/`clone`: it lets a
  single-threaded test act as another thread just then (e.g. `= queue::poll` takes the element while it is loaded).
  `AbstractCachedQueueUT.TestQueue.of(...)` wraps every element in one; `commitCalls`/`closeCalls` count calls.
- Helpers in `CachedQueueUT`: `cachedElementsOf(queue)` (copy under the lock), `simulateGC(queue)`, `isLoaded(cachedObject)`.
  Its generic factory `createCachedQueue()` lets tests use `String` or mutable `ArrayList` elements.
- Concurrency tests check results, never timing: start threads with a `CountDownLatch`, let loops stop when
  interrupted, get every `Future` with a timeout. A missing `unlock()` is found deterministically with
  `lock.isLocked()` after each call.
- Helper in `CachedObjectUT`: `closeWhileBlockedOnLock(operation)` closes an instance while the operation waits
  for `objRefLock` in another thread.
- Don't rely on thread timing either: make races deterministic by holding the lock until the other thread is
  `BLOCKED` (see `closeWhileBlockedOnLock`). Where that isn't possible (file creation), start many threads at once
  with a `CountDownLatch`, and check that the test fails on the old code every time.
  Wait for the threads (`awaitTermination`) before cleaning up in `finally`.
- `NestedMapUT` tests don't close cached maps; their few cache files are deleted by the shutdown hook.
- Tests that need files use their own temporary folder (`Files.createTempDirectory`) and delete it in `finally`.

## Working in this repository

- `AGENTS.md` points other AI agents to this file and repeats only its essentials. When those change here
  (definition of done, Java 8, working mode), update `AGENTS.md` too.
- Work in small, independently committable steps; the maintainer reviews and commits each one.
  Ask before changing a design decision above, and offer options with a recommendation.
- Environment: Windows with Git Bash and PowerShell. In Git Bash, `grep $'\r'` doesn't work (MSYS drops
  the argument); count CR bytes with `tr -cd '\r' < file | wc -c`. For `java -cp`, separate entries with `;`
  and convert paths with `cygpath -w`.
- Python and the GitHub CLI (`gh`) may not be installed; if CI fails, ask the maintainer for the log.

## Publishing

- Deploys to GitHub Packages: `https://maven.pkg.github.com/phisch84/dao-domain-utils`.
- `mvn deploy` needs a `<server>` with id `github` in `~/.m2/settings.xml` (token with `write:packages`).
