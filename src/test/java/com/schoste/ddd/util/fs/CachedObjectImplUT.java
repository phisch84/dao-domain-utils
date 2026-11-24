package com.schoste.ddd.util.fs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.schoste.ddd.util.CachedObject;
import com.schoste.ddd.util.CachedObjectUT;

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
}
