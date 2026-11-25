package com.schoste.ddd.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import static com.schoste.ddd.util.TestHelpers.list;

/**
 * Test suite class for the {@link com.schoste.ddd.util.CachedNestedMap} interface and its implementations
 * like {@link com.schoste.ddd.util.fs.CachedNestedHashMapImpl}.
 * It runs the contract tests of {@link com.schoste.ddd.util.NestedMapUT} too,
 * and adds the ones which are specific to cached maps.
 */
abstract public class CachedNestedMapUT extends NestedMapUT
{
    /**
     * Creates a new, empty map of the implementation under test.
     *
     * @param <V> the class of the values
     * @return the new map
     * @throws Exception if the map can't be created
     */
    abstract protected <V extends Serializable> CachedNestedMap<String, V> createCachedNestedMap() throws Exception;

    @Override
    protected NestedMap<String, String> createNestedMap() throws Exception
    {
        return this.<String>createCachedNestedMap();
    }

    /**
     * Returns the {@link com.schoste.ddd.util.CachedHashMap}s which hold the values of all levels.
     * Only applies to implementations which derive from {@link com.schoste.ddd.util.AbstractNestedMap}
     * and use {@link com.schoste.ddd.util.CachedHashMap}s; for others, the calling test is skipped.
     *
     * @param map the map
     * @return the value maps of all levels
     */
    protected static List<CachedHashMap<?, ?>> valueMapsOf(CachedNestedMap<?, ?> map)
    {
        Assume.assumeTrue(map instanceof AbstractNestedMap);

        List<CachedHashMap<?, ?>> valueMaps = new ArrayList<>();

        for (Map<?, ?> valueMap : ((AbstractNestedMap<?, ?>) map).getValueMaps())
        {
            Assume.assumeTrue(valueMap instanceof CachedHashMap);

            valueMaps.add((CachedHashMap<?, ?>) valueMap);
        }
        return valueMaps;
    }

    /**
     * Simulates that the GC collected all values of the map which are not referenced elsewhere
     * (see {@link com.schoste.ddd.util.CachedHashMapUT#simulateGC(CachedHashMap)}).
     *
     * @param map the map whose values are "collected"
     */
    protected static void simulateGC(CachedNestedMap<?, ?> map)
    {
        for (CachedHashMap<?, ?> valueMap : valueMapsOf(map))
        {
            CachedHashMapUT.simulateGC(valueMap);
        }
    }

    /**
     * Returns the {@link com.schoste.ddd.util.CachedObject}s which wrap the values of all levels.
     *
     * @param map the map
     * @return the wrapped values
     */
    protected static List<CachedObject<?>> cachedValuesOf(CachedNestedMap<?, ?> map)
    {
        List<CachedObject<?>> cachedValues = new ArrayList<>();

        for (CachedHashMap<?, ?> valueMap : valueMapsOf(map))
        {
            Assume.assumeTrue(valueMap instanceof AbstractCachedHashMap);

            cachedValues.addAll(((AbstractCachedHashMap<?, ?>) valueMap).cachedValues.values());
        }
        return cachedValues;
    }

    /**
     * Asserts that if a value can't be saved (it isn't serializable), {@link NestedMap#put(java.util.List, Object)}
     * throws {@link IllegalStateException} and leaves the map unchanged: no key without a value below it.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutOfUnsavableValueLeavesMapUnchanged() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<Object>> map = this.createCachedNestedMap())
        {
            ArrayList<Object> saved = new ArrayList<>();
            ArrayList<Object> unsavable = new ArrayList<>();

            saved.add("1");
            unsavable.add(new Object());
            map.put("a", saved);

            Assert.assertThrows(IllegalStateException.class, () -> map.put("b", "x", unsavable));
            Assert.assertEquals(TestHelpers.set("a"), map.keySet());
            Assert.assertEquals(1, map.size());
            Assert.assertTrue(map.getSubMap("b").isEmpty());
        }
    }

    /**
     * Asserts that changes to a value which were not saved are lost once the value was garbage collected.
     * This shows that {@link #simulateGC(CachedNestedMap)} works for the other tests.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testUnsavedChangesAreLostAfterGC() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap())
        {
            map.put("a", "b", list("1"));
            map.get("a", "b").add("2");

            simulateGC(map);

            Assert.assertEquals(list("1"), map.get("a", "b"));
        }
    }

    /**
     * Asserts that calling put again saves a changed value.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutAgainSavesChangedValue() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap())
        {
            map.put("a", "b", list("1"));

            ArrayList<String> value = map.get("a", "b");

            value.add("2");
            map.put("a", "b", value);
            simulateGC(map);

            Assert.assertEquals(list("1", "2"), map.get("a", "b"));
        }
    }

    /**
     * Asserts that commit saves the values in memory on all levels.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCommitSavesValuesOnAllLevels() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap())
        {
            map.put("a", list("1"));
            map.put("a", "b", "c", list("3"));

            map.get("a").add("changed");
            map.get("a", "b", "c").add("changed");
            map.commit();
            simulateGC(map);

            Assert.assertEquals(list("1", "changed"), map.get("a"));
            Assert.assertEquals(list("3", "changed"), map.get("a", "b", "c"));
        }
    }

    /**
     * Asserts that close frees the storage of all values and empties the map.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCloseFreesStorageOfAllValues() throws Exception
    {
        CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap();

        map.put("a", list("1"));
        map.put("a", "b", list("2"));
        map.put("x", "y", "z", list("3"));

        List<CachedObject<?>> cachedValues = cachedValuesOf(map);

        Assert.assertEquals(3, cachedValues.size());

        map.close();

        for (CachedObject<?> cachedValue : cachedValues)
        {
            CachedHashMapUT.assertClosed(cachedValue);
        }
        Assert.assertTrue(map.isEmpty());
        Assert.assertTrue(map.keySet().isEmpty());
    }

    /**
     * Returns the {@link com.schoste.ddd.util.CachedObject} of the map which holds a value.
     *
     * @param map the map
     * @param value the value to look for
     * @return the wrapped value
     * @throws Exception re-throws every exception
     */
    protected static CachedObject<?> cachedValueWith(CachedNestedMap<?, ?> map, Object value) throws Exception
    {
        for (CachedObject<?> cachedValue : cachedValuesOf(map))
        {
            if (value.equals(cachedValue.get())) return cachedValue;
        }
        Assert.fail("no cached value " + value);

        return null;
    }

    /**
     * Asserts that removing a value frees its storage.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemoveFreesStorage() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap())
        {
            map.put("a", "b", list("removed"));

            CachedObject<?> removed = cachedValueWith(map, list("removed"));

            map.remove("a", "b");

            CachedHashMapUT.assertClosed(removed);
        }
    }

    /**
     * Asserts that replacing a value with put frees the storage of the old value.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testReplaceFreesStorage() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap())
        {
            map.put("a", "b", list("replaced"));

            CachedObject<?> replaced = cachedValueWith(map, list("replaced"));

            map.put("a", "b", list("new"));

            CachedHashMapUT.assertClosed(replaced);
        }
    }

    /**
     * Asserts that clearing a sub map frees the storage of the values below its path,
     * but not of the value at its path.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testClearSubMapFreesStorage() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap())
        {
            map.put("x", list("kept"));
            map.put("x", "y", "z", list("cleared"));

            CachedObject<?> kept = cachedValueWith(map, list("kept"));
            CachedObject<?> cleared = cachedValueWith(map, list("cleared"));

            map.getSubMap("x").clear();

            CachedHashMapUT.assertClosed(cleared);
            Assert.assertEquals(list("kept"), kept.get());
            Assert.assertEquals(list("kept"), map.get("x"));
        }
    }

    /**
     * Asserts that a clone contains changes which were not committed yet, holds its own instances,
     * and keeps its state when the original values are garbage collected.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloneHasCurrentStateAndOwnInstances() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap())
        {
            map.put("a", list("1"));
            map.put("a", "b", "c", list("3"));

            ArrayList<String> value = map.get("a", "b", "c");

            value.add("uncommitted");

            try (CachedNestedMap<String, ArrayList<String>> clone = (CachedNestedMap<String, ArrayList<String>>) map.clone())
            {
                Assert.assertEquals(list("3", "uncommitted"), clone.get("a", "b", "c"));
                Assert.assertNotSame(value, clone.get("a", "b", "c"));

                simulateGC(map);
                simulateGC(clone);

                Assert.assertEquals(list("3"), map.get("a", "b", "c"));
                Assert.assertEquals(list("3", "uncommitted"), clone.get("a", "b", "c"));
                Assert.assertEquals(list("1"), clone.get("a"));
            }
        }
    }

    /**
     * Asserts that closing a clone frees only its own storage, not the one of the original map.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloseOfCloneKeepsOriginalStorage() throws Exception
    {
        try (CachedNestedMap<String, ArrayList<String>> map = this.createCachedNestedMap())
        {
            map.put("a", list("1"));
            map.put("a", "b", list("2"));

            CachedNestedMap<String, ArrayList<String>> clone = (CachedNestedMap<String, ArrayList<String>>) map.clone();
            List<CachedObject<?>> cloneValues = cachedValuesOf(clone);

            clone.close();

            for (CachedObject<?> cachedValue : cloneValues)
            {
                CachedHashMapUT.assertClosed(cachedValue);
            }
            simulateGC(map);

            Assert.assertEquals(list("1"), map.get("a"));
            Assert.assertEquals(list("2"), map.get("a", "b"));
        }
    }
}
