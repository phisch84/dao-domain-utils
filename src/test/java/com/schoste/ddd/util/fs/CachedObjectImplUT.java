package com.schoste.ddd.util.fs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.schoste.ddd.util.CachedObject;
import com.schoste.ddd.util.CachedObjectUT;
import com.schoste.ddd.util.TestHelpers;

public class CachedObjectImplUT extends CachedObjectUT
{
    @Override
    protected CachedObject<Integer> createCachedObj(Integer objToCache) throws Exception
    {
        return new CachedObjectImpl<Integer>(objToCache);
    }

    @Override
    protected CachedObject<String> createCachedObj(String objToCache) throws Exception
    {
        return new CachedObjectImpl<String>(objToCache);
    }

    @Override
    protected CachedObject<ArrayList<String>> createCachedObj(ArrayList<String> objToCache) throws Exception
    {
        return new CachedObjectImpl<ArrayList<String>>(objToCache);
    }

    /**
     * Asserts that the cache file of a new instance (and of its clone) is registered for deletion on exit,
     * and that {@link CachedObjectImpl#close()} deletes the file and removes the registration again,
     * so the registrations don't accumulate in a long running JVM.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloseUnregistersCacheFile() throws Exception
    {
        CachedObjectImpl<String> testObj = new CachedObjectImpl<String>("testCloseUnregistersCacheFile");
        CachedObjectImpl<String> clonedObj = (CachedObjectImpl<String>) testObj.clone();

        for (CachedObjectImpl<String> obj : Arrays.asList(testObj, clonedObj))
        {
            Assert.assertTrue(CachedObjectImpl.cacheFilesToDeleteOnExit.contains(obj.cacheFilePath));

            obj.close();

            Assert.assertFalse(CachedObjectImpl.cacheFilesToDeleteOnExit.contains(obj.cacheFilePath));
            Assert.assertFalse(new File(obj.cacheFilePath).exists());
        }
    }

    /**
     * Asserts that the constructors reject a null object and a null storage path with {@link IllegalArgumentException}.
     */
    @Test
    public void testNullArgumentsAreRejected()
    {
        String tmpDir = System.getProperty("java.io.tmpdir");

        Assert.assertThrows(IllegalArgumentException.class, () -> new CachedObjectImpl<String>((String) null));
        Assert.assertThrows(IllegalArgumentException.class, () -> new CachedObjectImpl<String>(null, tmpDir));
        Assert.assertThrows(IllegalArgumentException.class, () -> new CachedObjectImpl<String>("object", null));
    }

    /**
     * Asserts that a storage path which is a file (not a folder) is rejected with {@link IllegalArgumentException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testStoragePathWhichIsAFileIsRejected() throws Exception
    {
        Path file = Files.createTempFile("testStoragePathWhichIsAFileIsRejected", null);

        try
        {
            Assert.assertThrows(IllegalArgumentException.class, () -> new CachedObjectImpl<String>("object", file.toString()));
        }
        finally
        {
            Files.delete(file);
        }
    }

    /**
     * Asserts that a storage path which doesn't exist is created, including missing parent folders,
     * and that the cache file is stored in it.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testMissingStoragePathIsCreated() throws Exception
    {
        Path tempDir = Files.createTempDirectory("testMissingStoragePathIsCreated");
        Path storagePath = tempDir.resolve("parent").resolve("storage");

        try
        {
            try (CachedObjectImpl<String> testObj = new CachedObjectImpl<String>("object", storagePath.toString()))
            {
                Assert.assertTrue(Files.isDirectory(storagePath));
                Assert.assertEquals(storagePath.toFile().getAbsoluteFile(), new File(testObj.cacheFilePath).getParentFile());
            }
        }
        finally
        {
            TestHelpers.deleteRecursively(tempDir.toFile());
        }
    }

    /**
     * Asserts that an instance created with the constructor for serialization, which was never initialized,
     * throws {@link IllegalStateException} for {@link CachedObjectImpl#get()}, {@link CachedObjectImpl#commit()}
     * and {@link CachedObjectImpl#hashCode()}, and that {@link CachedObjectImpl#close()} does nothing.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testUninitializedInstance() throws Exception
    {
        CachedObjectImpl<String> testObj = new CachedObjectImpl<String>();

        Assert.assertThrows(IllegalStateException.class, testObj::get);
        Assert.assertThrows(IllegalStateException.class, testObj::commit);
        Assert.assertThrows(IllegalStateException.class, testObj::hashCode);

        testObj.close();
    }

    /**
     * Asserts that an instance can't be deserialized after the original was closed,
     * because its cache file was deleted: deserializing throws {@link IllegalStateException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testDeserializeAfterCloseIsRejected() throws Exception
    {
        CachedObjectImpl<String> testObj = new CachedObjectImpl<String>("testDeserializeAfterCloseIsRejected");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream oos = new ObjectOutputStream(bytes))
        {
            oos.writeObject(testObj);
        }

        testObj.close();

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
        {
            Assert.assertThrows(IllegalStateException.class, ois::readObject);
        }
    }

    /**
     * Asserts that instances created concurrently in the same folder don't fail and don't share a cache file:
     * every instance has its own file and loads its own object.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testConcurrentCreationInSameFolder() throws Exception
    {
        final int threadCount = 16;
        final int objectsPerThread = 50;
        final Path storagePath = Files.createTempDirectory("testConcurrentCreationInSameFolder");
        final CountDownLatch start = new CountDownLatch(1);
        final List<CachedObjectImpl<String>> createdObjs = Collections.synchronizedList(new ArrayList<CachedObjectImpl<String>>());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try
        {
            List<Future<?>> results = new ArrayList<>();

            for (int t = 0; t < threadCount; t++)
            {
                final int threadNo = t;

                results.add(executor.submit(() ->
                {
                    start.await();

                    for (int o = 0; o < objectsPerThread; o++)
                    {
                        createdObjs.add(new CachedObjectImpl<String>(threadNo + "-" + o, storagePath.toString()));
                    }

                    return null;
                }));
            }

            start.countDown();

            for (Future<?> result : results) result.get();

            Set<String> cacheFilePaths = new HashSet<>();

            for (CachedObjectImpl<String> obj : createdObjs)
            {
                Assert.assertTrue("shared cache file " + obj.cacheFilePath, cacheFilePaths.add(obj.cacheFilePath));
            }

            Assert.assertEquals(threadCount * objectsPerThread, cacheFilePaths.size());

            for (CachedObjectImpl<String> obj : createdObjs)
            {
                Assert.assertEquals(obj.get(), obj.load());
            }
        }
        finally
        {
            executor.shutdown();

            Assert.assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));

            for (CachedObjectImpl<String> obj : createdObjs) obj.close();

            TestHelpers.deleteRecursively(storagePath.toFile());
        }
    }
}
