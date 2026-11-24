package com.schoste.ddd.util.fs;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.schoste.ddd.util.CachedHashMap;
import com.schoste.ddd.util.CachedHashMapUT;

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

            Assert.assertFalse(original == copy);
            Assert.assertEquals(original.cacheFilePath, copy.cacheFilePath);
            Assert.assertEquals(testMap.storagePath, deserializedMap.storagePath);
        }
    }

    private static int countFiles(File folder)
    {
        return folder.list().length;
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
            for (File file : storage.listFiles()) file.delete();

            storage.delete();
        }
    }
}
