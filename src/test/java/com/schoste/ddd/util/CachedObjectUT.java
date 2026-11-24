package com.schoste.ddd.util;

import org.junit.Assert;
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
}
