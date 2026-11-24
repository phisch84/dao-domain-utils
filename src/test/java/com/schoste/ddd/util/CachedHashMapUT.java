package com.schoste.ddd.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Test suite class for the abstract {@link com.schoste.ddd.util.AbstractCachedHashMap} class and its implementations
 * like {@link com.schoste.ddd.util.fs.CachedHashMapImpl}.
 * Since all implementations are supposed to behave the same way, their common tests are collected in this class
 */
abstract public class CachedHashMapUT
{
    abstract protected CachedHashMap<String, ArrayList<String>> createCachedMap() throws Exception;

    /**
     * Simulates that the GC collected all values of the map which are not referenced elsewhere,
     * by clearing the weak references of their {@link com.schoste.ddd.util.AbstractCachedObject}s.
     * Only applies to implementations which derive from {@link com.schoste.ddd.util.AbstractCachedHashMap}
     * and use {@link com.schoste.ddd.util.AbstractCachedObject}s; for others, the calling test is skipped.
     *
     * @param map the map whose values are "collected"
     */
    protected static void simulateGC(CachedHashMap<?, ?> map)
    {
        Assume.assumeTrue(map instanceof AbstractCachedHashMap);

        for (CachedObject<?> cachedValue : ((AbstractCachedHashMap<?, ?>) map).cachedValues.values())
        {
            Assume.assumeTrue(cachedValue instanceof AbstractCachedObject);

            ((AbstractCachedObject<?>) cachedValue).objRef.clear();
        }
    }

    private static ArrayList<String> list(String... values)
    {
        return new ArrayList<>(Arrays.asList(values));
    }

    /**
     * Gets the {@link com.schoste.ddd.util.CachedObject} which wraps the value of a key.
     * Only applies to implementations which derive from {@link com.schoste.ddd.util.AbstractCachedHashMap};
     * for others, the calling test is skipped.
     *
     * @param map the map
     * @param key the key of the value
     * @return the wrapped value
     */
    protected static CachedObject<?> cachedValueOf(CachedHashMap<?, ?> map, Object key)
    {
        Assume.assumeTrue(map instanceof AbstractCachedHashMap);

        CachedObject<?> cachedValue = ((AbstractCachedHashMap<?, ?>) map).cachedValues.get(key);

        Assert.assertNotNull(cachedValue);

        return cachedValue;
    }

    /**
     * Asserts that a {@link com.schoste.ddd.util.CachedObject} was closed: {@link com.schoste.ddd.util.CachedObject#get()}
     * throws an {@link IllegalStateException}.
     *
     * @param cachedValue the wrapped value
     * @throws Exception re-throws every other exception
     */
    protected static void assertClosed(CachedObject<?> cachedValue) throws Exception
    {
        try
        {
            cachedValue.get();

            Assert.fail("the cached value was not closed");
        }
        catch (IllegalStateException expected)
        {
            // closed
        }
    }

    /**
     * Asserts that a new map is empty
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testConstructor() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            Assert.assertNotNull(testMap);
            Assert.assertTrue(testMap.isEmpty());
            Assert.assertEquals(0, testMap.size());
        }
    }

    /**
     * Asserts that {@link java.util.Map#put(Object, Object)} adds a value which {@link java.util.Map#get(Object)} returns,
     * and that {@link java.util.Map#get(Object)} returns null for unknown keys.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutAndGet() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            Assert.assertNull(testMap.put("key", list("value")));

            Assert.assertEquals(1, testMap.size());
            Assert.assertTrue(testMap.containsKey("key"));
            Assert.assertEquals(list("value"), testMap.get("key"));
            Assert.assertFalse(testMap.containsKey("unknown"));
            Assert.assertNull(testMap.get("unknown"));
        }
    }

    /**
     * Asserts that {@link java.util.Map#put(Object, Object)} for an existing key replaces the value
     * and returns the previous one.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutReplacesValue() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("key", list("first"));

            Assert.assertEquals(list("first"), testMap.put("key", list("second")));
            Assert.assertEquals(1, testMap.size());
            Assert.assertEquals(list("second"), testMap.get("key"));
        }
    }

    /**
     * Asserts that null values are rejected with a {@link NullPointerException}, since a
     * {@link com.schoste.ddd.util.CachedObject} can't cache null.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = NullPointerException.class)
    public void testPutNullValue() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("key", null);
        }
    }

    /**
     * Asserts that {@link java.util.Map#get(Object)} returns an equal copy of the value, not the instance that was put,
     * once the value was garbage collected.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testGetReturnsCopyAfterGC() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            ArrayList<String> value = list("value");

            testMap.put("key", value);
            simulateGC(testMap);

            ArrayList<String> actual = testMap.get("key");

            Assert.assertEquals(value, actual);
            Assert.assertFalse(value == actual);
        }
    }

    /**
     * Asserts that changes to a value which were not saved are lost after the GC collected it,
     * and that calling {@link java.util.Map#put(Object, Object)} again saves the changes.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutAgainSavesChanges() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            ArrayList<String> value = list("saved");

            testMap.put("key", value);
            value.add("not saved");
            simulateGC(testMap);

            Assert.assertEquals(list("saved"), testMap.get("key"));

            value = testMap.get("key");
            value.add("saved again");
            testMap.put("key", value);
            simulateGC(testMap);

            Assert.assertEquals(list("saved", "saved again"), testMap.get("key"));
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.CachedHashMap#commit()} saves the changes of all values in memory.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCommitSavesAllValues() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));

            ArrayList<String> one = testMap.get("one");
            ArrayList<String> two = testMap.get("two");

            one.add("1 changed");
            two.add("2 changed");
            testMap.commit();
            simulateGC(testMap);

            Assert.assertEquals(list("1", "1 changed"), testMap.get("one"));
            Assert.assertEquals(list("2", "2 changed"), testMap.get("two"));
        }
    }

    /**
     * Asserts that {@link java.util.Map#remove(Object)} removes the value and returns it,
     * and returns null for unknown keys.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemove() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("key", list("value"));

            Assert.assertEquals(list("value"), testMap.remove("key"));
            Assert.assertFalse(testMap.containsKey("key"));
            Assert.assertTrue(testMap.isEmpty());
            Assert.assertNull(testMap.remove("key"));
        }
    }

    /**
     * Asserts that {@link java.util.Map#remove(Object)} closes the removed value.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemoveClosesValue() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("key", list("value"));

            CachedObject<?> cachedValue = cachedValueOf(testMap, "key");

            testMap.remove("key");

            assertClosed(cachedValue);
        }
    }

    /**
     * Asserts that {@link java.util.Map#put(Object, Object)} for an existing key closes the previous value.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutClosesPreviousValue() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("key", list("first"));

            CachedObject<?> previousValue = cachedValueOf(testMap, "key");

            testMap.put("key", list("second"));

            assertClosed(previousValue);
            Assert.assertEquals(list("second"), testMap.get("key"));
        }
    }

    /**
     * Asserts that removing entries through the views of the map (here {@link java.util.Collection#removeIf(java.util.function.Predicate)}
     * of {@link java.util.Map#keySet()}, which uses the iterator) closes the removed values.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemoveThroughViewClosesValue() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));

            CachedObject<?> removedValue = cachedValueOf(testMap, "one");

            Assert.assertTrue(testMap.keySet().removeIf("one"::equals));

            assertClosed(removedValue);
            Assert.assertFalse(testMap.containsKey("one"));
            Assert.assertEquals(list("2"), testMap.get("two"));
        }
    }

    /**
     * Asserts that {@link java.util.Map#clear()} closes all values and empties the map.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testClearClosesAllValues() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));

            CachedObject<?> one = cachedValueOf(testMap, "one");
            CachedObject<?> two = cachedValueOf(testMap, "two");

            testMap.clear();

            assertClosed(one);
            assertClosed(two);
            Assert.assertTrue(testMap.isEmpty());
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.CachedHashMap#close()} closes all values and empties the map.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCloseClosesAllValues() throws Exception
    {
        CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap();

        testMap.put("one", list("1"));
        testMap.put("two", list("2"));

        CachedObject<?> one = cachedValueOf(testMap, "one");
        CachedObject<?> two = cachedValueOf(testMap, "two");

        testMap.close();

        assertClosed(one);
        assertClosed(two);
        Assert.assertTrue(testMap.isEmpty());
    }

    /**
     * Asserts that the map contains the same entries as a {@link java.util.HashMap} with the same content
     * (which iterates over {@link java.util.Map#entrySet()}).
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEntrySet() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            Map<String, ArrayList<String>> expected = new HashMap<>();

            expected.put("one", list("1"));
            expected.put("two", list("2"));
            testMap.putAll(expected);
            simulateGC(testMap);

            Assert.assertEquals(expected, new HashMap<>(testMap));
            Assert.assertEquals(expected.keySet(), testMap.keySet());
        }
    }
}
