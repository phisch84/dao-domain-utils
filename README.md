# DDD Utils

Helper classes for the DDD libraries [dao](https://github.com/phisch84/dao) and
[domain-repository](https://github.com/phisch84/domain-repository), and for projects that use them:
code that belongs to none of the infrastructure, domain or application layers.

Requires Java 8 or newer. No runtime dependencies.

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

## Implementing another CachedObject

Derive from `AbstractCachedObject` and implement `save()`, `load()`, `clone()`, `equals()` and `close()`.
The test-jar (`<classifier>tests</classifier>`) contains the abstract contract test `CachedObjectUT`:
derive your test class from it to check that your implementation behaves like the others.

## Building

```sh
mvn verify
```

This runs the tests and the strict Javadoc check, which fails the build on any finding.

## License

[MIT](LICENSE)
