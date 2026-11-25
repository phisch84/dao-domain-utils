package com.schoste.ddd.util.fs;

import static com.schoste.ddd.util.TestHelpers.countFiles;
import static com.schoste.ddd.util.TestHelpers.serializeAndDeserialize;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import com.schoste.ddd.util.CachedQueue;
import com.schoste.ddd.util.CachedQueueUT;
import com.schoste.ddd.util.TestHelpers;

/**
 * Runs the contract tests of {@link com.schoste.ddd.util.CachedQueueUT} for {@link com.schoste.ddd.util.fs.CachedQueueImpl},
 * and adds the tests which are specific to the file system.
 */
public class CachedQueueImplUT extends CachedQueueUT
{
    @Override
    protected <E extends Serializable> CachedQueue<E> createCachedQueue() throws Exception
    {
        return new CachedQueueImpl<E>();
    }

    /**
     * Asserts that a new queue without a storage path uses {@code java.io.tmpdir}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testDefaultStoragePathIsTmpDir() throws Exception
    {
        try (CachedQueueImpl<String> queue = new CachedQueueImpl<>())
        {
            Assert.assertEquals(System.getProperty("java.io.tmpdir"), queue.storagePath);
        }
    }

    /**
     * Asserts that a null storage path is rejected with an {@link IllegalArgumentException}.
     */
    @Test
    public void testNullStoragePathIsRejected()
    {
        Assert.assertThrows(IllegalArgumentException.class, () -> new CachedQueueImpl<String>(null));
    }

    /**
     * Asserts that each element has its own cache file in the storage path,
     * and that poll, remove(Object) and clear delete the cache files of the removed elements.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCacheFilesAreInStoragePathAndDeleted() throws Exception
    {
        File storage = Files.createTempDirectory(CachedQueueImplUT.class.getSimpleName()).toFile();

        try (CachedQueueImpl<String> queue = new CachedQueueImpl<>(storage.getAbsolutePath()))
        {
            queue.offer("a");
            queue.offer("b");
            queue.offer("c");
            queue.offer("d");

            Assert.assertEquals(storage.getAbsolutePath(), queue.storagePath);
            Assert.assertEquals(4, countFiles(storage));

            queue.poll();

            Assert.assertEquals(3, countFiles(storage));

            queue.remove("c");

            Assert.assertEquals(2, countFiles(storage));

            queue.clear();

            Assert.assertEquals(0, countFiles(storage));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }

    /**
     * Asserts that a deserialized queue keeps the storage path and shares the cache files with the original one:
     * serializing creates no files, and closing the copy deletes the files of both.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testDeserializedQueueSharesCacheFiles() throws Exception
    {
        File storage = Files.createTempDirectory(CachedQueueImplUT.class.getSimpleName()).toFile();

        try (CachedQueueImpl<String> queue = new CachedQueueImpl<>(storage.getAbsolutePath()))
        {
            queue.offer("a");
            queue.offer("b");

            CachedQueueImpl<String> copy = serializeAndDeserialize(queue);

            Assert.assertEquals(queue.storagePath, copy.storagePath);
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
     * Asserts that a clone stores its elements in own cache files in the same storage path,
     * and that closing the clone deletes only its own cache files.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloneHasOwnCacheFiles() throws Exception
    {
        File storage = Files.createTempDirectory(CachedQueueImplUT.class.getSimpleName()).toFile();

        try (CachedQueueImpl<String> queue = new CachedQueueImpl<>(storage.getAbsolutePath()))
        {
            queue.offer("a");
            queue.offer("b");

            CachedQueueImpl<String> clone = (CachedQueueImpl<String>) queue.clone();

            Assert.assertEquals(queue.storagePath, clone.storagePath);
            Assert.assertEquals(4, countFiles(storage));

            clone.close();

            Assert.assertEquals(2, countFiles(storage));
        }
        finally
        {
            TestHelpers.deleteRecursively(storage);
        }
    }
}
