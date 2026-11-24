package com.schoste.ddd.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Test suite class for the abstract {@link com.schoste.ddd.util.AbstractCachedObject} class and its implementations
 * like {@link com.schoste.ddd.util.fs.CachedObjectImpl}.
 * Since all implementations are supposed to behave the same way, their common tests are collected in this class
 */
abstract public class CachedObjectUT 
{
    abstract protected CachedObject<Integer> createCachedObj(Integer objToCache) throws Exception;
    abstract protected CachedObject<String> createCachedObj(String objToCache) throws Exception;
    abstract protected CachedObject<ArrayList<String>> createCachedObj(ArrayList<String> objToCache) throws Exception;

    /**
     * Asserts that the constructor works without exceptions
     * 
     * @throws Exception re-throws every exception
     */
    @Test
    public void testConstructor() throws Exception
    {
        String objToCache = "testConstructor";

        try(CachedObject<String> testObj = this.createCachedObj(objToCache))
        {
            Assert.assertNotNull(testObj);
            Assert.assertTrue(true);
        }
    }

    /**
     * Asserts that the {@link com.schoste.ddd.util.AbstractCachedObject#get()} method returns the instance
     * for which the cached object was created, even before it was committed or garbage collected
     * 
     * @throws Exception re-throws every exception
     */
    @Test 
    public void testGetBeforeCommitAndBeforeGC() throws Exception
    {
        Integer expectedObj = Integer.valueOf(1024);

        try(CachedObject<Integer> testObj = this.createCachedObj(expectedObj))
        {
            Integer actualObj = testObj.get();

            Assert.assertNotNull(actualObj);
            Assert.assertTrue(expectedObj.equals(actualObj));
            Assert.assertTrue(actualObj.equals(expectedObj));
            Assert.assertTrue(actualObj == expectedObj);
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.AbstractCachedObject#get()} gets the same object also
     * after {@link com.schoste.ddd.util.AbstractCachedObject#commit()} if the GC didn't run yet.
     * 
     * @throws Exception re-throws every exception
     */
    @Test 
    public void testGetAfterCommitAndBeforeGC() throws Exception
    {
        Integer expectedObj = Integer.valueOf(1024);

        try(CachedObject<Integer> testObj = this.createCachedObj(expectedObj))
        {
            testObj.commit();

            Integer actualObj = testObj.get();

            Assert.assertNotNull(actualObj);
            Assert.assertTrue(expectedObj.equals(actualObj));
            Assert.assertTrue(actualObj.equals(expectedObj));
            Assert.assertTrue(actualObj == expectedObj);            
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.AbstractCachedObject#get()} gets an equal object
     * after {@link com.schoste.ddd.util.AbstractCachedObject#commit()} and after the GC ran.
     * 
     * @throws Exception re-throws every exception
     */
    @Test 
    public void testGetAfterCommitAndAfterGC() throws Exception
    {
        Integer expectedObj = Integer.valueOf(1024);

        try(CachedObject<Integer> testObj = this.createCachedObj(expectedObj))
        {
            testObj.commit();

            expectedObj = null;

            for (int i=0; i<100; i++)
            {
                int[] buffer = new int[1024];

                buffer[0] = 0;
                buffer = null;

                System.gc();
            }

            Integer actualObj = testObj.get();

            Assert.assertNotNull(actualObj);
            Assert.assertTrue(Integer.valueOf(1024).equals(actualObj));
            Assert.assertTrue(actualObj.equals(Integer.valueOf(1024)));
            Assert.assertFalse(actualObj == Integer.valueOf(1024));
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.AbstractCachedObject#commit()} does nothing (and doesn't throw)
     * if the cached object was already garbage collected, and that the storage keeps the last committed state.
     * Garbage collection is simulated by clearing the weak reference, so the test does not depend on when the GC runs.
     * Only applies to implementations which derive from {@link com.schoste.ddd.util.AbstractCachedObject}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCommitAfterGC() throws Exception
    {
        ArrayList<String> oneInstance = new ArrayList<>(Arrays.asList("committed"));

        try(CachedObject<ArrayList<String>> testObj = this.createCachedObj(oneInstance))
        {
            Assume.assumeTrue(testObj instanceof AbstractCachedObject);

            oneInstance.add("not committed");

            ((AbstractCachedObject<?>) testObj).objRef.clear();

            testObj.commit();

            Assert.assertEquals(Arrays.asList("committed"), testObj.get());
        }
    }

    /**
     * Asserts that a {@link com.schoste.ddd.util.CachedObject} which was serialized and deserialized
     * <ul>
     * <li>has the same {@link com.schoste.ddd.util.CachedObject#hashCode()} as the original instance,</li>
     * <li>is equal to the original instance (both ways),</li>
     * <li>and {@link com.schoste.ddd.util.CachedObject#get()} returns an object equal to the cached one.</li>
     * </ul>
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSerializeAndDeserialize() throws Exception
    {
        String objToCache = "testSerializeAndDeserialize";

        try(CachedObject<String> testObj = this.createCachedObj(objToCache))
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (ObjectOutputStream oos = new ObjectOutputStream(bytes))
            {
                oos.writeObject(testObj);
            }

            CachedObject<String> deserializedObj;

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
            {
                deserializedObj = (CachedObject<String>) ois.readObject();
            }

            Assert.assertNotNull(deserializedObj);
            Assert.assertFalse(testObj == deserializedObj);
            Assert.assertEquals(testObj.hashCode(), deserializedObj.hashCode());
            Assert.assertTrue(testObj.equals(deserializedObj));
            Assert.assertTrue(deserializedObj.equals(testObj));
            Assert.assertEquals(objToCache, deserializedObj.get());
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.AbstractCachedObject#clone()} returns an instance
     * of {@link com.schoste.ddd.util.AbstractCachedObject}
     * <ul>
     * <li>for which {@link com.schoste.ddd.util.AbstractCachedObject#equals(Object)} is true,</li>
     * <li>but one instance != cloned instance</li>
     * <li>and {@link com.schoste.ddd.util.AbstractCachedObject#get()} of one instance is equal to {@link com.schoste.ddd.util.AbstractCachedObject#get()} of the cloned instance,</li>
     * <li>but {@link com.schoste.ddd.util.AbstractCachedObject#get()} of one instance != the instance of {@link com.schoste.ddd.util.AbstractCachedObject#get()} of the cloned instance,</li>
     * </ul>
     * 
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testClone() throws Exception
    {
        Integer oneInstance = Integer.valueOf(1024);

        try(CachedObject<Integer> oneCachedObj = this.createCachedObj(oneInstance);
            CachedObject<Integer> clonedCachedObj = (CachedObject<Integer>) oneCachedObj.clone())
        {
            Assert.assertNotNull(clonedCachedObj);
            Assert.assertTrue(oneCachedObj.equals(clonedCachedObj));
            Assert.assertTrue(clonedCachedObj.equals(oneCachedObj));
            Assert.assertFalse(oneCachedObj == clonedCachedObj);

            Integer clonedInstance = clonedCachedObj.get();

            Assert.assertNotNull(clonedInstance);
            Assert.assertTrue(oneInstance.equals(clonedInstance));
            Assert.assertTrue(clonedInstance.equals(oneInstance));
            Assert.assertFalse(oneInstance == clonedInstance);
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.AbstractCachedObject#clone()} clones the current state of the cached object
     * <ul>
     * <li>including changes which were not committed yet,</li>
     * <li>and that the clone holds its own copy, so changes to one instance do not affect the other.</li>
     * </ul>
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloneIncludesUncommittedChanges() throws Exception
    {
        ArrayList<String> oneInstance = new ArrayList<>(Arrays.asList("committed"));

        try(CachedObject<ArrayList<String>> oneCachedObj = this.createCachedObj(oneInstance))
        {
            oneInstance.add("uncommitted");

            try(CachedObject<ArrayList<String>> clonedCachedObj = (CachedObject<ArrayList<String>>) oneCachedObj.clone())
            {
                ArrayList<String> clonedInstance = clonedCachedObj.get();

                Assert.assertEquals(Arrays.asList("committed", "uncommitted"), clonedInstance);
                Assert.assertFalse(oneInstance == clonedInstance);

                clonedInstance.add("changed in clone");

                Assert.assertEquals(Arrays.asList("committed", "uncommitted"), oneCachedObj.get());
            }
        }
    }
}
