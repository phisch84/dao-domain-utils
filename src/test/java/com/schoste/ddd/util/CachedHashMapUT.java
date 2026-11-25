package com.schoste.ddd.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
     * Checks whether the value of a key is currently in memory (loaded) or only in the storage.
     * Only applies to implementations which use {@link com.schoste.ddd.util.AbstractCachedObject}s;
     * for others, the calling test is skipped.
     *
     * @param map the map
     * @param key the key of the value
     * @return true if the value is in memory
     */
    protected static boolean isLoaded(CachedHashMap<?, ?> map, Object key)
    {
        CachedObject<?> cachedValue = cachedValueOf(map, key);

        Assume.assumeTrue(cachedValue instanceof AbstractCachedObject);

        return ((AbstractCachedObject<?>) cachedValue).objRef.get() != null;
    }

    /**
     * Serializes an object into a byte array and deserializes it again.
     *
     * @param <T> the class of the object
     * @param obj the object to serialize
     * @return the deserialized copy
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    protected static <T> T serializeAndDeserialize(T obj) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream oos = new ObjectOutputStream(bytes))
        {
            oos.writeObject(obj);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
        {
            return (T) ois.readObject();
        }
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
     * Asserts that a map which was serialized and deserialized
     * <ul>
     * <li>is another instance,</li>
     * <li>contains the same keys and equal values,</li>
     * <li>including changes which were committed before serializing.</li>
     * </ul>
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSerializeAndDeserialize() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));

            ArrayList<String> two = testMap.get("two");

            two.add("2 committed");
            testMap.commit();

            CachedHashMap<String, ArrayList<String>> deserializedMap = serializeAndDeserialize(testMap);

            Assert.assertNotNull(deserializedMap);
            Assert.assertFalse(testMap == deserializedMap);
            Assert.assertEquals(testMap.keySet(), deserializedMap.keySet());
            Assert.assertEquals(list("1"), deserializedMap.get("one"));
            Assert.assertEquals(list("2", "2 committed"), deserializedMap.get("two"));
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.CachedHashMap#clone()} returns another instance
     * <ul>
     * <li>which is equal to the original one,</li>
     * <li>with its own {@link com.schoste.ddd.util.CachedObject} for every value,</li>
     * <li>whose values are equal to the original ones, but other instances.</li>
     * </ul>
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testClone() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));

            try (CachedHashMap<String, ArrayList<String>> clonedMap = (CachedHashMap<String, ArrayList<String>>) testMap.clone())
            {
                Assert.assertNotNull(clonedMap);
                Assert.assertFalse(testMap == clonedMap);
                Assert.assertTrue(testMap.equals(clonedMap));
                Assert.assertTrue(clonedMap.equals(testMap));

                for (String key : testMap.keySet())
                {
                    Assert.assertFalse(cachedValueOf(testMap, key) == cachedValueOf(clonedMap, key));
                    Assert.assertEquals(testMap.get(key), clonedMap.get(key));
                    Assert.assertFalse(testMap.get(key) == clonedMap.get(key));
                }
            }
        }
    }

    /**
     * Asserts that {@link com.schoste.ddd.util.CachedHashMap#clone()} clones the current state of the values,
     * including changes which were not committed yet, and that changes to the values of the clone don't affect the original.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloneIncludesUncommittedChanges() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("key", list("committed"));

            ArrayList<String> value = testMap.get("key");

            value.add("not committed");

            try (CachedHashMap<String, ArrayList<String>> clonedMap = (CachedHashMap<String, ArrayList<String>>) testMap.clone())
            {
                ArrayList<String> clonedValue = clonedMap.get("key");

                Assert.assertEquals(list("committed", "not committed"), clonedValue);

                clonedValue.add("changed in clone");
                clonedMap.commit();

                Assert.assertEquals(list("committed", "not committed"), testMap.get("key"));
            }
        }
    }

    /**
     * Asserts that a clone is independent of the original map: adding, removing and closing in the clone
     * doesn't affect the original.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloneIsIndependent() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));

            CachedHashMap<String, ArrayList<String>> clonedMap = (CachedHashMap<String, ArrayList<String>>) testMap.clone();

            clonedMap.put("three", list("3"));
            clonedMap.remove("one");
            clonedMap.close();
            simulateGC(testMap);

            Assert.assertEquals(2, testMap.size());
            Assert.assertFalse(testMap.containsKey("three"));
            Assert.assertEquals(list("1"), testMap.get("one"));
            Assert.assertEquals(list("2"), testMap.get("two"));
        }
    }

    /**
     * Asserts that methods which only need the keys don't load any value from the storage:
     * {@link java.util.Map#size()}, {@link java.util.Map#containsKey(Object)}, iterating {@link java.util.Map#keySet()}
     * and {@link java.util.Map.Entry#getKey()} of {@link java.util.Map#entrySet()}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testKeysDontLoadValues() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));
            simulateGC(testMap);

            Assert.assertEquals(2, testMap.size());
            Assert.assertTrue(testMap.containsKey("one"));

            int keys = 0;

            for (String key : testMap.keySet()) if (key != null) keys++;
            for (Map.Entry<String, ArrayList<String>> entry : testMap.entrySet()) if (entry.getKey() != null) keys++;

            Assert.assertEquals(4, keys);
            Assert.assertFalse(isLoaded(testMap, "one"));
            Assert.assertFalse(isLoaded(testMap, "two"));
        }
    }

    /**
     * Asserts that an entry of {@link java.util.Map#entrySet()} loads its value only when
     * {@link java.util.Map.Entry#getValue()} is called.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEntryLoadsValueOnGetValue() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));
            simulateGC(testMap);

            ArrayList<String> one = null;

            for (Map.Entry<String, ArrayList<String>> entry : testMap.entrySet())
            {
                if ("one".equals(entry.getKey())) one = entry.getValue();
            }

            Assert.assertEquals(list("1"), one);
            Assert.assertTrue(isLoaded(testMap, "one"));
            Assert.assertFalse(isLoaded(testMap, "two"));
        }
    }

    /**
     * Asserts that iterating and streaming the values doesn't keep references to them:
     * once the caller dropped the values, the GC can collect every one of them, so only the values the caller holds use memory.
     * The test keeps a {@link java.lang.ref.WeakReference} to each returned value and runs the GC until all of them are cleared.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testValuesAreNotRetained() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            for (int i = 0; i < 10; i++) testMap.put("key" + i, list("value" + i));

            simulateGC(testMap);

            List<WeakReference<ArrayList<String>>> returnedValues = new ArrayList<>();

            for (ArrayList<String> value : testMap.values()) returnedValues.add(new WeakReference<>(value));

            testMap.entrySet().stream().map(Map.Entry::getValue).forEach(value -> returnedValues.add(new WeakReference<>(value)));

            Assert.assertEquals(20, returnedValues.size());

            for (int i = 0; (i < 100) && returnedValues.stream().anyMatch(ref -> ref.get() != null); i++) System.gc();

            Assert.assertTrue("the map kept references to its values", returnedValues.stream().allMatch(ref -> ref.get() == null));
        }
    }

    /**
     * Asserts that the methods which need all values (loaded one at a time) give the same results as for a
     * {@link java.util.HashMap} with the same content: {@link java.util.Map#values()}, {@link java.util.Map#containsValue(Object)},
     * {@link java.util.Map#equals(Object)}, {@link java.util.Map#hashCode()} and streams of the views.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testBulkMethods() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            Map<String, ArrayList<String>> expected = new HashMap<>();

            expected.put("one", list("1"));
            expected.put("two", list("2"));
            testMap.putAll(expected);
            simulateGC(testMap);

            Assert.assertTrue(testMap.containsValue(list("2")));
            Assert.assertFalse(testMap.containsValue(list("3")));
            Assert.assertTrue(testMap.equals(expected));
            Assert.assertTrue(expected.equals(testMap));
            Assert.assertEquals(expected.hashCode(), testMap.hashCode());
            Assert.assertEquals(2, testMap.values().size());
            Assert.assertTrue(testMap.values().containsAll(expected.values()));
            Assert.assertEquals(1, testMap.values().stream().filter(list("1")::equals).count());
        }
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

    /**
     * Asserts that {@link java.util.Map.Entry#setValue(Object)} of an entry of {@link java.util.Map#entrySet()}
     * <ul>
     * <li>returns the previous value and saves the new one like {@link java.util.Map#put(Object, Object)},</li>
     * <li>closes the previous value,</li>
     * <li>and doesn't break the iteration (replacing a value is no structural change).</li>
     * </ul>
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEntrySetValueSavesValueAndClosesPreviousValue() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            testMap.put("two", list("2"));

            CachedObject<?> previousValue = cachedValueOf(testMap, "one");
            int iterated = 0;

            for (Map.Entry<String, ArrayList<String>> entry : testMap.entrySet())
            {
                iterated++;

                if (entry.getKey().equals("one")) Assert.assertEquals(list("1"), entry.setValue(list("changed")));
            }

            Assert.assertEquals(2, iterated);
            assertClosed(previousValue);

            simulateGC(testMap);

            Assert.assertEquals(list("changed"), testMap.get("one"));
            Assert.assertEquals(list("2"), testMap.get("two"));
        }
    }

    /**
     * Asserts that {@link java.util.Iterator#remove()} after {@link java.util.Map.Entry#setValue(Object)}
     * closes the new value, i.e. the one which was in the map when it was removed.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEntryIteratorRemoveAfterSetValueClosesNewValue() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));

            Iterator<Map.Entry<String, ArrayList<String>>> entries = testMap.entrySet().iterator();

            entries.next().setValue(list("changed"));

            CachedObject<?> newValue = cachedValueOf(testMap, "one");

            entries.remove();

            assertClosed(newValue);
            Assert.assertTrue(testMap.isEmpty());
        }
    }

    /**
     * Asserts that an entry of {@link java.util.Map#entrySet()} follows the contract of {@link java.util.Map.Entry}:
     * it is equal to (and has the same hash code and string as) any entry with an equal key and value,
     * and is not equal to entries with another value, nor to objects which are no entry.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testEntryEqualsHashCodeAndToString() throws Exception
    {
        try (CachedHashMap<String, ArrayList<String>> testMap = this.createCachedMap())
        {
            testMap.put("one", list("1"));
            simulateGC(testMap);

            Map.Entry<String, ArrayList<String>> entry = testMap.entrySet().iterator().next();
            Map.Entry<String, ArrayList<String>> expected = new AbstractMap.SimpleEntry<>("one", list("1"));

            Assert.assertTrue(entry.equals(expected));
            Assert.assertTrue(expected.equals(entry));
            Assert.assertEquals(expected.hashCode(), entry.hashCode());
            Assert.assertEquals(expected.toString(), entry.toString());
            Assert.assertFalse(entry.equals(new AbstractMap.SimpleEntry<>("one", list("other"))));
            Assert.assertFalse(entry.equals(new AbstractMap.SimpleEntry<>("other", list("1"))));
            Assert.assertFalse(entry.equals("one=[1]"));
        }
    }
}
