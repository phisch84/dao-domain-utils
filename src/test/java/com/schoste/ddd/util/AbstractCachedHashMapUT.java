package com.schoste.ddd.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests how {@link AbstractCachedHashMap} handles storage errors. The values are wrapped in
 * {@link FailingCachedObject}s (see {@link TestMap}), so each test can make a single storage operation fail.
 * The behaviour without errors is tested by the contract test {@link CachedHashMapUT}.
 * The test maps are not closed, because they keep their values in memory only.
 */
@SuppressWarnings("resource")
public class AbstractCachedHashMapUT
{
    /**
     * A map which wraps every value in a {@link FailingCachedObject} and remembers all of them.
     */
    static class TestMap extends AbstractCachedHashMap<String, String>
    {
        private static final long serialVersionUID = 1L;

        /**
         * If not null, {@link #createCachedObject(String)} throws it.
         */
        Exception failOnCreate;

        /**
         * Passed to {@link FailingCachedObject#failOnCommit} of every new cached object.
         */
        Exception failOnCommit;

        /**
         * Every cached object this map created, in order.
         */
        final List<FailingCachedObject<String>> created = new ArrayList<>();

        @Override
        protected CachedObject<String> createCachedObject(String value) throws Exception
        {
            if (this.failOnCreate != null) throw this.failOnCreate;

            FailingCachedObject<String> cachedValue = new FailingCachedObject<>(value);

            cachedValue.failOnCommit = this.failOnCommit;
            this.created.add(cachedValue);

            return cachedValue;
        }

        /**
         * Gets the cached object of a key.
         *
         * @param key the key
         * @return the cached object of the key
         */
        FailingCachedObject<String> cachedValueOf(String key)
        {
            FailingCachedObject<String> cachedValue = (FailingCachedObject<String>) this.cachedValues.get(key);

            Assert.assertNotNull(cachedValue);

            return cachedValue;
        }
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#put(Object, Object)} throws {@link IllegalStateException}
     * with the original exception as cause, and leaves the map unchanged, if the value can't be wrapped.
     */
    @Test
    public void testPutWhenCreatingFails()
    {
        TestMap testMap = new TestMap();
        IOException failure = new IOException("injected");

        testMap.failOnCreate = failure;

        IllegalStateException thrown = Assert.assertThrows(IllegalStateException.class, () -> testMap.put("key", "value"));

        Assert.assertSame(failure, thrown.getCause());
        Assert.assertTrue(testMap.isEmpty());
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#put(Object, Object)} closes the new cached object (frees its storage)
     * if saving it fails, throws {@link IllegalStateException} with the original exception as cause,
     * and keeps the previous value of the key.
     */
    @Test
    public void testPutWhenCommitFailsClosesNewValueAndKeepsPreviousValue()
    {
        TestMap testMap = new TestMap();
        IOException failure = new IOException("injected");

        testMap.put("key", "previous");
        testMap.failOnCommit = failure;

        IllegalStateException thrown = Assert.assertThrows(IllegalStateException.class, () -> testMap.put("key", "new"));

        Assert.assertSame(failure, thrown.getCause());
        Assert.assertTrue("the new value was not closed", testMap.created.get(1).closed);
        Assert.assertFalse(testMap.created.get(0).closed);
        Assert.assertEquals("previous", testMap.get("key"));
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#put(Object, Object)} stores the new value and closes the previous one
     * also if the previous value can't be loaded to be returned; the failure is thrown as {@link IllegalStateException}.
     */
    @Test
    public void testPutWhenPreviousValueCantBeLoaded()
    {
        TestMap testMap = new TestMap();
        IOException failure = new IOException("injected");

        testMap.put("key", "previous");
        testMap.cachedValueOf("key").failOnGet = failure;

        IllegalStateException thrown = Assert.assertThrows(IllegalStateException.class, () -> testMap.put("key", "new"));

        Assert.assertSame(failure, thrown.getCause());
        Assert.assertTrue(testMap.created.get(0).closed);
        Assert.assertEquals("new", testMap.get("key"));
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#get(Object)} throws a checked exception of the storage as
     * {@link IllegalStateException} with it as cause, and an unchecked exception as it is.
     */
    @Test
    public void testGetWhenLoadingFails()
    {
        TestMap testMap = new TestMap();
        IOException checkedFailure = new IOException("injected");
        IllegalArgumentException uncheckedFailure = new IllegalArgumentException("injected");

        testMap.put("key", "value");
        testMap.cachedValueOf("key").failOnGet = checkedFailure;

        Assert.assertSame(checkedFailure, Assert.assertThrows(IllegalStateException.class, () -> testMap.get("key")).getCause());

        testMap.cachedValueOf("key").failOnGet = uncheckedFailure;

        Assert.assertSame(uncheckedFailure, Assert.assertThrows(IllegalArgumentException.class, () -> testMap.get("key")));
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#remove(Object)} removes and closes the value
     * also if it can't be loaded to be returned; the failure is thrown as {@link IllegalStateException}.
     */
    @Test
    public void testRemoveWhenLoadingFailsStillRemovesAndClosesValue()
    {
        TestMap testMap = new TestMap();
        IOException failure = new IOException("injected");

        testMap.put("key", "value");
        testMap.cachedValueOf("key").failOnGet = failure;

        IllegalStateException thrown = Assert.assertThrows(IllegalStateException.class, () -> testMap.remove("key"));

        Assert.assertSame(failure, thrown.getCause());
        Assert.assertTrue(testMap.created.get(0).closed);
        Assert.assertFalse(testMap.containsKey("key"));
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#close()} tries to close every value although closing some fails,
     * empties the map, and throws the first failure with the other ones as suppressed exceptions.
     */
    @Test
    public void testCloseTriesAllValuesAndThrowsFirstFailure()
    {
        TestMap testMap = new TestMap();
        IOException failureA = new IOException("injected a");
        IOException failureB = new IOException("injected b");

        testMap.put("a", "1");
        testMap.put("b", "2");
        testMap.put("c", "3");
        testMap.cachedValueOf("a").failOnClose = failureA;
        testMap.cachedValueOf("b").failOnClose = failureB;

        IOException thrown = Assert.assertThrows(IOException.class, testMap::close);
        List<Throwable> all = new ArrayList<>(Arrays.asList(thrown.getSuppressed()));

        all.add(thrown);

        Assert.assertEquals(2, all.size());
        Assert.assertTrue(all.containsAll(Arrays.asList(failureA, failureB)));
        Assert.assertTrue(testMap.created.get(2).closed);

        for (FailingCachedObject<String> cachedValue : testMap.created) Assert.assertTrue(cachedValue.closeCalled);

        Assert.assertTrue(testMap.isEmpty());
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#commit()} tries to save every value although saving some fails,
     * and throws the first failure with the other ones as suppressed exceptions (like {@link AbstractCachedHashMap#close()}).
     */
    @Test
    public void testCommitTriesAllValuesAndThrowsFirstFailure()
    {
        TestMap testMap = new TestMap();
        IOException failureA = new IOException("injected a");
        IOException failureB = new IOException("injected b");

        testMap.put("a", "1");
        testMap.put("b", "2");
        testMap.put("c", "3");
        testMap.cachedValueOf("a").failOnCommit = failureA;
        testMap.cachedValueOf("b").failOnCommit = failureB;

        IOException thrown = Assert.assertThrows(IOException.class, testMap::commit);
        List<Throwable> all = new ArrayList<>(Arrays.asList(thrown.getSuppressed()));

        all.add(thrown);

        Assert.assertEquals(2, all.size());
        Assert.assertTrue(all.containsAll(Arrays.asList(failureA, failureB)));

        // one commit by put, one by commit()
        for (FailingCachedObject<String> cachedValue : testMap.created) Assert.assertEquals(2, cachedValue.commitCalls);
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#clear()} empties the map although closing a value fails,
     * and throws the failure as {@link IllegalStateException} with it as cause.
     */
    @Test
    public void testClearWhenClosingFails()
    {
        TestMap testMap = new TestMap();
        IOException failure = new IOException("injected");

        testMap.put("key", "value");
        testMap.cachedValueOf("key").failOnClose = failure;

        IllegalStateException thrown = Assert.assertThrows(IllegalStateException.class, testMap::clear);

        Assert.assertSame(failure, thrown.getCause());
        Assert.assertTrue(testMap.isEmpty());
    }

    /**
     * Asserts that {@link AbstractCachedHashMap#clone()} closes the values it already cloned if cloning a value fails,
     * throws {@link CloneNotSupportedException} with the original exception as cause,
     * and leaves the original map unchanged.
     */
    @Test
    public void testCloneWhenCloningAValueFailsClosesClonedValues()
    {
        TestMap testMap = new TestMap();
        CloneNotSupportedException failure = new CloneNotSupportedException("injected");

        testMap.put("a", "1");
        testMap.put("b", "2");

        // a HashMap iterates in hash order: "a" is cloned before "b", which fails
        testMap.cachedValueOf("b").failOnClone = failure;

        CloneNotSupportedException thrown = Assert.assertThrows(CloneNotSupportedException.class, testMap::clone);

        Assert.assertSame(failure, thrown.getCause());
        Assert.assertEquals(1, testMap.cachedValueOf("a").clones.size());
        Assert.assertTrue("the cloned value was not closed", testMap.cachedValueOf("a").clones.get(0).closed);
        Assert.assertFalse(testMap.cachedValueOf("a").closed);
        Assert.assertFalse(testMap.cachedValueOf("b").closed);
        Assert.assertEquals("1", testMap.get("a"));
        Assert.assertEquals("2", testMap.get("b"));
    }
}
