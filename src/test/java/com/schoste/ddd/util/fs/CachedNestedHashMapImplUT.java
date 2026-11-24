package com.schoste.ddd.util.fs;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import com.schoste.ddd.util.CachedNestedMap;
import com.schoste.ddd.util.CachedNestedMapUT;

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

    private static int countFiles(File folder)
    {
        return folder.list().length;
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
            for (File file : storage.listFiles()) file.delete();

            storage.delete();
        }
    }
}
