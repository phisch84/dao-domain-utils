# DDD Utils (`com.schoste.ddd:utils`)

Helper library for the DDD libraries [dao](https://github.com/phisch84/dao) and
[domain-repository](https://github.com/phisch84/domain-repository): code that fits none of the
infrastructure, domain or application layers. Currently it provides `CachedObject<T>`, a holder
that keeps a `Serializable` object only weakly in memory and reloads it from storage on demand.

## Build and verify

- Always check changes with `mvn -B verify`, not just `mvn test`: only `verify` runs the Javadoc
  check, which fails the build on any finding.
- The code must compile against **Java 8** (`maven.compiler.release=8`), even though newer JDKs build it.
  No `var`, records, `List.of`, text blocks, etc.
- Formatting follows `.editorconfig`: UTF-8, LF line endings (enforced by `.gitattributes`), 4 spaces,
  no tabs, no trailing whitespace, final newline.

## Javadoc rules (doclint, `failOnWarnings`)

- Every public/protected class, constructor, field and method needs a complete comment
  (`@param`, `@return`, `@throws`). Write explicit constructors instead of relying on the default one.
- The private serialization members (`serialPersistentFields`, `writeObject`, `readObject`) need comments too;
  they appear on the "Serialized Form" page.
- `{@see ...}` is not an inline tag; use `{@link ...}`. Field references use `#`: `{@link System#err}`.
- Test classes are checked as well, except for missing comments.

## Tests

- Test classes are named `*UT` (Surefire is configured to include `**/*UT.java`).
- `CachedObjectUT` is the abstract **contract test** every `CachedObject` implementation must pass.
  Add shared behaviour there; each implementation has a small subclass (e.g. `fs/CachedObjectImplUT`)
  that implements the `createCachedObj(...)` factories and holds implementation-specific tests.
- The test classes ship as a test-jar so other implementations can reuse the contract test.
- Don't rely on GC timing: either simulate collection (`objRef.clear()`, accessible from package
  `com.schoste.ddd.util`) or wait on your own `WeakReference` and assert that it was cleared.

## Design decisions (don't change without asking)

- `hashCode()` is taken once when the holder is created and never recalculated. It is serialized,
  so a deserialized copy keeps it. Cached objects must not change their hash code.
- `commit()` does nothing if the object was already garbage collected: the storage keeps the last
  committed state. Callers must commit while they hold a reference.
- `clone()` copies the current state (via `get()`), including uncommitted changes, into its own
  cache file and holds its own instance.
- `fs.CachedObjectImpl` deletes cache files on `close()`; files that were not closed are deleted by
  one shutdown hook (tracked in `cacheFilesToDeleteOnExit`). Don't use `File#deleteOnExit()`, it leaks.
- Serialization of `fs.CachedObjectImpl` writes only `cacheFilePath` and `objHashCode`, never the cached object.

## Publishing

- Deploys to GitHub Packages: `https://maven.pkg.github.com/phisch84/dao-domain-utils`.
- `mvn deploy` needs a `<server>` with id `github` in `~/.m2/settings.xml` (token with `write:packages`).
