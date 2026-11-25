package com.schoste.ddd.util.fs;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import static com.schoste.ddd.util.TestHelpers.countFiles;
import static com.schoste.ddd.util.TestHelpers.serializeAndDeserialize;

import com.schoste.ddd.util.CachedNestedMap;
import com.schoste.ddd.util.CachedNestedMapUT;
import com.schoste.ddd.util.TestHelpers;

/**
 * Runs the contract tests of {@link com.schoste.ddd.util.CachedNestedMapUT} and {@link com.schoste.ddd.util.NestedMapUT}
 * for {@link com.schoste.ddd.util.fs.CachedNestedHashMapImpl}, and adds the tests which are specific to the file system.
 */
public class CachedNestedHashMapImplUT extends CachedNestedMapUT
{
    @Override
    protected <V extends Serializable> CachedNestedMap<String, V> createCachedNestedMap() throws Exception
    {
        return new CachedNestedHashMapImpl<String, V>();
    }

    /**
     * Asserts that a new map without a storage path uses {@code java.io.tmpdir}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testDefaultStoragePathIsTmpDir() throws Exception
    {
        try (CachedNestedHashMapImpl<String, String> map = new CachedNestedHashMapImpl<>())
        {
            Assert.assertEquals(System.getProperty("java.io.tmpdir"), map.storagePath);
        }
    }

    /**
     * Asserts that a null storage path is rejected with an {@link IllegalArgumentException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNullStoragePathIsRejected() throws Exception
    {
        new CachedNestedHashMapImpl<String, String>(null);
    }

    /**
     * Asserts that each value has its own cache file in the storage path on every level,
     * and that removing, clearing a sub map and closing delete the cache files of the affected values.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCacheFilesAreInStoragePathAndDeleted() throws Exception
    {
        File storage = Files.createTempDirectory(CachedNestedHashMapImplUT.class.getSimpleName()).toFile();

        try
        {
            CachedNestedHashMapImpl<String, String> map = new CachedNestedHashMapImpl<>(storage.getAbsolutePath());

            map.put("a", "v1");
            map.put("a", "b", "v2");
            map.put("a", "b", "c", "v3");
            map.put("x", "y", "v4");

            Assert.assertEquals(storage.getAbsolutePath(), map.storagePath);
            Assert.assertEquals(4, countFiles(storage));

            map.remove("x", "y");

            Assert.assertEquals(3, countFiles(storage));

            map.getSubMap("a").clear();

            Assert.assertEquals(1, countFiles(storage));

            map.close();

            Assert.assertEquals(0, countFiles(storage));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }

    /**
     * Asserts that a deserialized map keeps the storage path and shares the cache files with the original one:
     * serializing creates no files, and closing the copy deletes the files of both.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testDeserializedMapSharesCacheFiles() throws Exception
    {
        File storage = Files.createTempDirectory(CachedNestedHashMapImplUT.class.getSimpleName()).toFile();

        try (CachedNestedHashMapImpl<String, String> map = new CachedNestedHashMapImpl<>(storage.getAbsolutePath()))
        {
            map.put("a", "v1");
            map.put("a", "b", "v2");

            CachedNestedHashMapImpl<String, String> copy = serializeAndDeserialize(map);

            Assert.assertEquals(map.storagePath, copy.storagePath);
            Assert.assertEquals(2, countFiles(storage));

            copy.close();

            Assert.assertEquals(0, countFiles(storage));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }

    /**
     * Asserts that a clone stores its values in own cache files in the same storage path,
     * and that closing the clone deletes only its own cache files.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloneHasOwnCacheFiles() throws Exception
    {
        File storage = Files.createTempDirectory(CachedNestedHashMapImplUT.class.getSimpleName()).toFile();

        try (CachedNestedHashMapImpl<String, String> map = new CachedNestedHashMapImpl<>(storage.getAbsolutePath()))
        {
            map.put("a", "v1");
            map.put("a", "b", "v2");
            map.put("x", "y", "z", "v3");

            CachedNestedHashMapImpl<String, String> clone = (CachedNestedHashMapImpl<String, String>) map.clone();

            Assert.assertEquals(map.storagePath, clone.storagePath);
            Assert.assertEquals(6, countFiles(storage));

            clone.close();

            Assert.assertEquals(3, countFiles(storage));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }

    /**
     * A value map whose {@link #close()} and {@link #clone()} can be made to fail.
     */
    static class FailingValueMap extends CachedHashMapImpl<String, String>
    {
        private static final long serialVersionUID = 1L;

        /**
         * If not null, {@link #close()} throws it without closing the values.
         */
        IOException failOnClose;

        /**
         * If not null, {@link #clone()} throws it.
         */
        CloneNotSupportedException failOnClone;

        /**
         * True once {@link #close()} was called, also if it failed.
         */
        boolean closeCalled;

        FailingValueMap(String storagePath)
        {
            super(storagePath);
        }

        @Override
        public void close() throws IOException
        {
            this.closeCalled = true;

            if (this.failOnClose != null) throw this.failOnClose;

            super.close();
        }

        @Override
        public Object clone() throws CloneNotSupportedException
        {
            if (this.failOnClone != null) throw this.failOnClone;

            return super.clone();
        }
    }

    /**
     * A map which uses a {@link FailingValueMap} for every level and remembers them in the order they were created.
     */
    static class TestCachedNestedMap extends CachedNestedHashMapImpl<String, String>
    {
        private static final long serialVersionUID = 1L;

        final List<FailingValueMap> created = new ArrayList<>();

        TestCachedNestedMap(String storagePath)
        {
            super(storagePath);
        }

        @Override
        protected Map<String, String> createValueMap()
        {
            FailingValueMap valueMap = new FailingValueMap(this.storagePath);

            this.created.add(valueMap);

            return valueMap;
        }
    }

    /**
     * Asserts that {@link CachedNestedHashMapImpl#close()} tries to close the value maps of all levels although
     * closing some fails, throws the first failure with the other ones as suppressed exceptions,
     * and still empties the map and deletes all cache files.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCloseTriesAllLevelsAndThrowsFirstFailure() throws Exception
    {
        File storage = Files.createTempDirectory(CachedNestedHashMapImplUT.class.getSimpleName()).toFile();

        try
        {
            TestCachedNestedMap map = new TestCachedNestedMap(storage.getAbsolutePath());
            IOException failureA = new IOException("injected a");
            IOException failureB = new IOException("injected b");

            map.put("a", "1");
            map.put("b", "x", "2");
            map.put("c", "y", "3");
            map.created.get(0).failOnClose = failureA;
            map.created.get(1).failOnClose = failureB;

            IOException thrown = Assert.assertThrows(IOException.class, map::close);
            List<Throwable> all = new ArrayList<>(Arrays.asList(thrown.getSuppressed()));

            all.add(thrown);

            Assert.assertEquals(2, all.size());
            Assert.assertTrue(all.containsAll(Arrays.asList(failureA, failureB)));

            for (FailingValueMap valueMap : map.created) Assert.assertTrue(valueMap.closeCalled);

            Assert.assertTrue(map.isEmpty());
            Assert.assertEquals(0, countFiles(storage));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }

    /**
     * Asserts that if copying the value map of a level fails, {@link CachedNestedHashMapImpl#clone()} deletes the
     * cache files of the levels it already copied, and leaves the original map and its cache files unchanged.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCloneWhenCopyingALevelFailsDeletesCopiedCacheFiles() throws Exception
    {
        File storage = Files.createTempDirectory(CachedNestedHashMapImplUT.class.getSimpleName()).toFile();

        try (TestCachedNestedMap map = new TestCachedNestedMap(storage.getAbsolutePath()))
        {
            CloneNotSupportedException failure = new CloneNotSupportedException("injected");

            // the first level is copied before the second one, which fails
            map.put("a", "1");
            map.put("b", "x", "2");
            map.created.get(1).failOnClone = failure;

            Assert.assertSame(failure, Assert.assertThrows(CloneNotSupportedException.class, map::clone));
            Assert.assertEquals(2, countFiles(storage));
            Assert.assertEquals("1", map.get("a"));
            Assert.assertEquals("2", map.get("b", "x"));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }
}
