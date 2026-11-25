package com.schoste.ddd.util.fs;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import static com.schoste.ddd.util.TestHelpers.countFiles;
import static com.schoste.ddd.util.TestHelpers.serializeAndDeserialize;

import com.schoste.ddd.util.CachedHashMap;
import com.schoste.ddd.util.CachedHashMapUT;
import com.schoste.ddd.util.TestHelpers;

public class CachedHashMapImplUT extends CachedHashMapUT
{
    @Override
    protected CachedHashMap<String, ArrayList<String>> createCachedMap() throws Exception
    {
        return new CachedHashMapImpl<String, ArrayList<String>>();
    }

    /**
     * Asserts that a deserialized map refers to the same cache files as the original one
     * (only references to the cache files are serialized, not the values) and keeps the storage path.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testDeserializedMapSharesCacheFiles() throws Exception
    {
        try (CachedHashMapImpl<String, ArrayList<String>> testMap = new CachedHashMapImpl<>())
        {
            testMap.put("one", new ArrayList<>(Arrays.asList("1")));

            CachedHashMapImpl<String, ArrayList<String>> deserializedMap = serializeAndDeserialize(testMap);

            CachedObjectImpl<?> original = (CachedObjectImpl<?>) cachedValueOf(testMap, "one");
            CachedObjectImpl<?> copy = (CachedObjectImpl<?>) cachedValueOf(deserializedMap, "one");

            Assert.assertNotSame(original, copy);
            Assert.assertEquals(original.cacheFilePath, copy.cacheFilePath);
            Assert.assertEquals(testMap.storagePath, deserializedMap.storagePath);
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
        File storage = Files.createTempDirectory(CachedHashMapImplUT.class.getSimpleName()).toFile();

        try (CachedHashMapImpl<String, ArrayList<String>> testMap = new CachedHashMapImpl<>(storage.getAbsolutePath()))
        {
            testMap.put("one", new ArrayList<>(Arrays.asList("1")));
            testMap.put("two", new ArrayList<>(Arrays.asList("2")));

            CachedHashMapImpl<String, ArrayList<String>> clonedMap = (CachedHashMapImpl<String, ArrayList<String>>) testMap.clone();

            Assert.assertEquals(testMap.storagePath, clonedMap.storagePath);
            Assert.assertEquals(4, countFiles(storage));

            clonedMap.close();

            Assert.assertEquals(2, countFiles(storage));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }

    /**
     * Asserts that each value has its own cache file in the storage path,
     * and that replacing, removing and closing delete the cache files of the affected values.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCacheFilesAreDeleted() throws Exception
    {
        File storage = Files.createTempDirectory(CachedHashMapImplUT.class.getSimpleName()).toFile();

        try (CachedHashMap<String, ArrayList<String>> testMap = new CachedHashMapImpl<>(storage.getAbsolutePath()))
        {
            testMap.put("one", new ArrayList<>(Arrays.asList("1")));
            testMap.put("two", new ArrayList<>(Arrays.asList("2")));

            Assert.assertEquals(2, countFiles(storage));

            testMap.put("one", new ArrayList<>(Arrays.asList("1 replaced")));

            Assert.assertEquals(2, countFiles(storage));

            testMap.remove("two");

            Assert.assertEquals(1, countFiles(storage));

            testMap.close();

            Assert.assertEquals(0, countFiles(storage));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }
}
