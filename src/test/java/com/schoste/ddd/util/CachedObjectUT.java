package com.schoste.ddd.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

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
     * <p>
     * The test keeps a {@link WeakReference} to the cached object and runs the GC until it is cleared,
     * which proves that the object was collected and that {@link com.schoste.ddd.util.CachedObject#get()}
     * must load it from the storage. Since a change was committed before, the loaded object must contain it.
     * </p>
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testGetAfterCommitAndAfterGC() throws Exception
    {
        ArrayList<String> expectedObj = new ArrayList<>(Arrays.asList("created"));

        try(CachedObject<ArrayList<String>> testObj = this.createCachedObj(expectedObj))
        {
            expectedObj.add("committed");
            testObj.commit();

            WeakReference<ArrayList<String>> collected = new WeakReference<>(expectedObj);

            expectedObj = null;

            for (int i=0; (i<100) && (collected.get() != null); i++) System.gc();

            Assert.assertNull("the GC did not collect the cached object", collected.get());

            ArrayList<String> actualObj = testObj.get();

            Assert.assertEquals(Arrays.asList("created", "committed"), actualObj);
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

    /**
     * Asserts that {@link CachedObject#get()} and {@link CachedObject#commit()} throw {@link IllegalStateException}
     * after {@link CachedObject#close()}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testGetAndCommitAfterCloseAreRejected() throws Exception
    {
        CachedObject<String> testObj = this.createCachedObj("testGetAndCommitAfterCloseAreRejected");

        testObj.close();

        Assert.assertThrows(IllegalStateException.class, testObj::get);
        Assert.assertThrows(IllegalStateException.class, testObj::commit);
    }

    /**
     * Asserts that calling {@link CachedObject#close()} a second time does nothing (and doesn't throw).
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCloseTwiceDoesNothing() throws Exception
    {
        CachedObject<String> testObj = this.createCachedObj("testCloseTwiceDoesNothing");

        testObj.close();
        testObj.close();

        Assert.assertThrows(IllegalStateException.class, testObj::get);
    }

    /**
     * Asserts that {@link CachedObject#hashCode()} still returns the hash code of the cached object
     * after {@link CachedObject#close()}, because it never reads from the storage.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testHashCodeAfterClose() throws Exception
    {
        String objToCache = "testHashCodeAfterClose";
        CachedObject<String> testObj = this.createCachedObj(objToCache);

        testObj.close();

        Assert.assertEquals(objToCache.hashCode(), testObj.hashCode());
    }

    /**
     * Asserts that {@link CachedObject#equals(Object)} is false for null, for an object which is no
     * {@link CachedObject} (also not the cached object itself) and for a cached object with an unequal object.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEqualsWithNullOtherTypeAndOtherObjectIsFalse() throws Exception
    {
        String objToCache = "testEqualsWithNullOtherTypeAndOtherObjectIsFalse";

        try (CachedObject<String> testObj = this.createCachedObj(objToCache);
             CachedObject<String> otherObj = this.createCachedObj("other"))
        {
            Assert.assertFalse(testObj.equals(null));
            Assert.assertFalse(testObj.equals(objToCache));
            Assert.assertFalse(testObj.equals(otherObj));
            Assert.assertFalse(otherObj.equals(testObj));
        }
    }

    /**
     * Asserts that {@link CachedObject#equals(Object)} is false (and doesn't throw)
     * if one of the two instances was closed, because its object can't be read anymore.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEqualsWithClosedInstanceIsFalse() throws Exception
    {
        String objToCache = "testEqualsWithClosedInstanceIsFalse";

        try (CachedObject<String> testObj = this.createCachedObj(objToCache))
        {
            CachedObject<String> closedObj = this.createCachedObj(objToCache);

            closedObj.close();

            Assert.assertFalse(testObj.equals(closedObj));
            Assert.assertFalse(closedObj.equals(testObj));
        }
    }

    /**
     * An operation on a cached object, which may throw any exception.
     */
    @FunctionalInterface
    protected interface CachedObjectOperation
    {
        /**
         * Runs the operation.
         *
         * @param cachedObj the cached object to operate on
         * @throws Exception re-throws every exception
         */
        void run(CachedObject<String> cachedObj) throws Exception;
    }

    /**
     * Runs the operation in another thread, which is blocked on the lock of the cached object,
     * closes the cached object while that thread waits and returns what the operation threw (or null).
     * The other thread has passed every check before the lock, so this simulates
     * {@link CachedObject#close()} running concurrently between the check and the lock.
     *
     * @param operation the operation to run concurrently to closing
     * @return the exception thrown by the operation, or null
     * @throws Exception re-throws every exception
     */
    protected Throwable closeWhileBlockedOnLock(CachedObjectOperation operation) throws Exception
    {
        final CachedObject<String> testObj = this.createCachedObj("closeWhileBlockedOnLock");

        Assume.assumeTrue(testObj instanceof AbstractCachedObject);

        final AbstractCachedObject<String> abstractObj = (AbstractCachedObject<String>) testObj;
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread operationThread = new Thread(() ->
        {
            try
            {
                operation.run(testObj);
            }
            catch (Throwable t)
            {
                thrown.set(t);
            }
        });

        synchronized (abstractObj.objRefLock)
        {
            operationThread.start();

            long deadline = System.currentTimeMillis() + 10_000;

            while (operationThread.getState() != Thread.State.BLOCKED)
            {
                Assert.assertTrue("operation thread did not block on the lock", System.currentTimeMillis() < deadline);
                Assert.assertTrue("operation thread ended without blocking on the lock", operationThread.isAlive());

                Thread.yield();
            }

            testObj.close();
        }

        operationThread.join(10_000);

        Assert.assertFalse(operationThread.isAlive());

        return thrown.get();
    }

    /**
     * Asserts that {@link CachedObject#get()} throws {@link IllegalStateException}
     * (and no {@link NullPointerException}) if the instance is closed by another thread while get waits for the lock.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testGetConcurrentToClose() throws Exception
    {
        Throwable thrown = this.closeWhileBlockedOnLock(CachedObject::get);

        Assert.assertTrue(String.valueOf(thrown), thrown instanceof IllegalStateException);
    }

    /**
     * Asserts that {@link CachedObject#commit()} throws {@link IllegalStateException}
     * (and no {@link NullPointerException}) if the instance is closed by another thread while commit waits for the lock.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCommitConcurrentToClose() throws Exception
    {
        Throwable thrown = this.closeWhileBlockedOnLock(CachedObject::commit);

        Assert.assertTrue(String.valueOf(thrown), thrown instanceof IllegalStateException);
    }
}
