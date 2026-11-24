package com.schoste.ddd.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test suite class for the {@link com.schoste.ddd.util.NestedMap} interface and its implementations
 * like {@link com.schoste.ddd.util.NestedHashMap} and {@link com.schoste.ddd.util.fs.CachedNestedHashMapImpl}.
 * Since all implementations are supposed to behave the same way, their common tests are collected in this class
 */
abstract public class NestedMapUT
{
    /**
     * Creates a new, empty map of the implementation under test.
     *
     * @return the new map
     * @throws Exception if the map can't be created
     */
    abstract protected NestedMap<String, String> createNestedMap() throws Exception;

    private static Set<String> set(String... keys)
    {
        return new HashSet<>(Arrays.asList(keys));
    }

    /**
     * Asserts that every put overload stores the value at its path and that get finds it.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutAndGetForEveryDepth() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("1", "v1");
        map.put("1", "2", "v2");
        map.put("1", "2", "3", "v3");
        map.put("1", "2", "3", "4", "v4");
        map.put("1", "2", "3", "4", "5", "v5");

        Assert.assertEquals("v1", map.get("1"));
        Assert.assertEquals("v2", map.get("1", "2"));
        Assert.assertEquals("v3", map.get("1", "2", "3"));
        Assert.assertEquals("v4", map.get("1", "2", "3", "4"));
        Assert.assertEquals("v5", map.get("1", "2", "3", "4", "5"));
        Assert.assertEquals(5, map.size());
    }

    /**
     * Asserts that put with a list stores a value at a path of any length,
     * and that the list isn't kept by the map.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutListForAnyDepth() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();
        List<String> keys = new ArrayList<>(Arrays.asList("1", "2", "3", "4", "5", "6", "7"));

        map.put(keys, "v7");
        keys.set(0, "changed");

        Assert.assertEquals("v7", map.get("1", "2", "3", "4", "5", "6", "7"));
        Assert.assertNull(map.get("changed", "2", "3", "4", "5", "6", "7"));
        Assert.assertEquals(set("1"), map.keySet());

        map.put(Arrays.asList("x"), "vx");

        Assert.assertEquals("vx", map.get("x"));
    }

    /**
     * Asserts that put returns the previous value of the path and replaces it.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutReturnsPreviousValue() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        Assert.assertNull(map.put("a", "b", "old"));
        Assert.assertEquals("old", map.put("a", "b", "new"));
        Assert.assertEquals("new", map.put(Arrays.asList("a", "b"), "newer"));
        Assert.assertEquals("newer", map.get("a", "b"));
        Assert.assertEquals(1, map.size());
    }

    /**
     * Asserts that a path can have a value and deeper keys at the same time,
     * and that get returns null for a path which has only deeper keys.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPathWithValueAndDeeperKeys() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "c", "v1");
        map.put("a", "b", "v2");

        Assert.assertEquals("v1", map.get("a", "b", "c"));
        Assert.assertEquals("v2", map.get("a", "b"));
        Assert.assertNull(map.get("a"));
        Assert.assertEquals(2, map.size());
    }

    /**
     * Asserts that get returns null for paths which don't exist.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testGetMissingPathReturnsNull() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        Assert.assertNull(map.get("a"));

        map.put("a", "b", "v");

        Assert.assertNull(map.get("x"));
        Assert.assertNull(map.get("a", "x"));
        Assert.assertNull(map.get("a", "b", "c"));
    }

    /**
     * Asserts that containsKey is true only for paths which have a value.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testContainsKey() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "c", "v");

        Assert.assertTrue(map.containsKey("a", "b", "c"));
        Assert.assertFalse(map.containsKey("a", "b"));
        Assert.assertFalse(map.containsKey("a"));
        Assert.assertFalse(map.containsKey("x"));
        Assert.assertFalse(map.containsKey("a", "b", "c", "d"));
    }

    /**
     * Asserts that null keys are allowed on every level.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testNullKeysAreAllowed() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put((String) null, "v1");
        map.put("a", null, "v2");
        map.put(Arrays.asList((String) null, null), "v3");

        Assert.assertEquals("v1", map.get((String) null));
        Assert.assertEquals("v2", map.get("a", null));
        Assert.assertEquals("v3", map.get(null, null));
        Assert.assertTrue(map.containsKey("a", null));
        Assert.assertEquals("v2", map.remove("a", null));
        Assert.assertEquals(2, map.size());
    }

    /**
     * Asserts that put rejects null values with a {@link NullPointerException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = NullPointerException.class)
    public void testPutNullValueIsRejected() throws Exception
    {
        this.createNestedMap().put("a", "b", null);
    }

    /**
     * Asserts that put with a list rejects null values with a {@link NullPointerException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = NullPointerException.class)
    public void testPutListNullValueIsRejected() throws Exception
    {
        this.createNestedMap().put(Arrays.asList("a"), null);
    }

    /**
     * Asserts that a rejected null value doesn't leave an empty path behind.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRejectedNullValueDoesntCreatePath() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        try
        {
            map.put("a", "b", "c", null);
            Assert.fail("NullPointerException expected");
        }
        catch (NullPointerException e)
        {
            Assert.assertTrue(map.isEmpty());
            Assert.assertTrue(map.keySet().isEmpty());
        }
    }

    /**
     * Asserts that put with a null list is rejected with a {@link NullPointerException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = NullPointerException.class)
    public void testPutNullListIsRejected() throws Exception
    {
        this.createNestedMap().put((List<String>) null, "v");
    }

    /**
     * Asserts that put with an empty list is rejected with an {@link IllegalArgumentException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testPutEmptyListIsRejected() throws Exception
    {
        this.createNestedMap().put(Collections.<String>emptyList(), "v");
    }

    /**
     * Asserts that get without keys is rejected with an {@link IllegalArgumentException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetWithoutKeysIsRejected() throws Exception
    {
        this.createNestedMap().get();
    }

    /**
     * Asserts that containsKey without keys is rejected with an {@link IllegalArgumentException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testContainsKeyWithoutKeysIsRejected() throws Exception
    {
        this.createNestedMap().containsKey();
    }

    /**
     * Asserts that remove without keys is rejected with an {@link IllegalArgumentException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRemoveWithoutKeysIsRejected() throws Exception
    {
        this.createNestedMap().remove();
    }

    /**
     * Asserts that size counts the values at any depth and isEmpty follows it.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSizeCountsValuesAtAnyDepth() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        Assert.assertEquals(0, map.size());
        Assert.assertTrue(map.isEmpty());

        map.put("a", "b", "c", "v1");

        Assert.assertEquals(1, map.size());
        Assert.assertFalse(map.isEmpty());

        map.put("a", "v2");
        map.put("x", "y", "v3");
        map.put("a", "b", "c", "replaced");

        Assert.assertEquals(3, map.size());
    }

    /**
     * Asserts that remove removes only the value of the path; deeper keys stay.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemoveKeepsDeeperKeys() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "c", "v1");
        map.put("a", "b", "v2");

        Assert.assertEquals("v2", map.remove("a", "b"));
        Assert.assertNull(map.get("a", "b"));
        Assert.assertFalse(map.containsKey("a", "b"));
        Assert.assertEquals("v1", map.get("a", "b", "c"));
        Assert.assertEquals(1, map.size());
    }

    /**
     * Asserts that remove returns null for paths without a value and changes nothing.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemoveMissingPathReturnsNull() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        Assert.assertNull(map.remove("a"));

        map.put("a", "b", "v");

        Assert.assertNull(map.remove("a"));
        Assert.assertNull(map.remove("x", "y"));
        Assert.assertNull(map.remove("a", "b", "c"));
        Assert.assertEquals("v", map.get("a", "b"));
        Assert.assertEquals(set("a"), map.keySet());
    }

    /**
     * Asserts that remove prunes the levels which have neither values nor deeper keys afterwards,
     * up to the first level which still has one.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemovePrunesEmptyLevels() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "c", "d", "v1");
        map.put("a", "x", "v2");

        map.remove("a", "b", "c", "d");

        Assert.assertEquals(set("x"), map.getSubMap("a").keySet());
        Assert.assertTrue(map.getSubMap("a", "b").keySet().isEmpty());

        map.remove("a", "x");

        Assert.assertTrue(map.keySet().isEmpty());
        Assert.assertTrue(map.isEmpty());
    }

    /**
     * Asserts that keySet returns all keys of the first level (with a value or deeper keys) as a copy.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testKeySetIsCopyOfFirstLevel() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "v1");
        map.put("b", "c", "v2");

        Set<String> keys = map.keySet();

        Assert.assertEquals(set("a", "b"), keys);

        keys.remove("a");
        map.put("d", "v3");

        Assert.assertEquals(set("b"), keys);
        Assert.assertEquals(set("a", "b", "d"), map.keySet());
        Assert.assertEquals("v1", map.get("a"));
    }

    /**
     * Asserts that a sub map reads the level below its path, and that its size counts only that level and deeper.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testGetSubMapReadsLevelBelowPath() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "v0");
        map.put("a", "b", "c", "v1");
        map.put("a", "d", "v2");
        map.put("x", "v3");

        NestedMap<String, String> sub = map.getSubMap("a");

        Assert.assertEquals("v1", sub.get("b", "c"));
        Assert.assertEquals("v2", sub.get("d"));
        Assert.assertTrue(sub.containsKey("d"));
        Assert.assertNull(sub.get("x"));
        Assert.assertEquals(set("b", "d"), sub.keySet());
        Assert.assertEquals(2, sub.size());
        Assert.assertEquals("v1", map.getSubMap("a", "b").get("c"));
        Assert.assertEquals("v1", sub.getSubMap("b").get("c"));
    }

    /**
     * Asserts that getSubMap without keys returns the map itself.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testGetSubMapWithoutKeysReturnsThis() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        Assert.assertSame(map, map.getSubMap());
    }

    /**
     * Asserts that a sub map is a live view: changes of the map are visible in the view and vice versa.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testGetSubMapIsLiveView() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "v1");

        NestedMap<String, String> sub = map.getSubMap("a");

        map.put("a", "c", "v2");

        Assert.assertEquals("v2", sub.get("c"));
        Assert.assertEquals(2, sub.size());

        sub.put("d", "e", "v3");

        Assert.assertEquals("v3", map.get("a", "d", "e"));
        Assert.assertEquals(3, map.size());

        Assert.assertEquals("v1", sub.remove("b"));
        Assert.assertNull(map.get("a", "b"));
        Assert.assertEquals(set("c", "d"), map.getSubMap("a").keySet());
    }

    /**
     * Asserts that the sub map of a path which doesn't exist is empty, and that put through it creates the path.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSubMapOfMissingPathIsEmptyAndPutCreatesPath() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        NestedMap<String, String> sub = map.getSubMap("a", "b");

        Assert.assertNotNull(sub);
        Assert.assertTrue(sub.isEmpty());
        Assert.assertTrue(sub.keySet().isEmpty());
        Assert.assertNull(sub.get("c"));
        Assert.assertTrue(map.isEmpty());

        sub.put("c", "v");

        Assert.assertEquals("v", map.get("a", "b", "c"));
        Assert.assertEquals(set("a"), map.keySet());
    }

    /**
     * Asserts that a sub map stays usable when its level is pruned and created again.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSubMapSurvivesRemovalAndRecreation() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "v1");

        NestedMap<String, String> sub = map.getSubMap("a");

        map.remove("a", "b");

        Assert.assertTrue(sub.isEmpty());
        Assert.assertTrue(map.keySet().isEmpty());

        map.put("a", "c", "v2");

        Assert.assertEquals("v2", sub.get("c"));

        map.remove("a", "c");
        sub.put("d", "v3");

        Assert.assertEquals("v3", map.get("a", "d"));
    }

    /**
     * Asserts that clear removes all values and levels.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testClear() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "v1");
        map.put("a", "b", "c", "v2");
        map.put("x", "v3");

        map.clear();

        Assert.assertTrue(map.isEmpty());
        Assert.assertTrue(map.keySet().isEmpty());
        Assert.assertNull(map.get("a"));
        Assert.assertNull(map.get("a", "b", "c"));
    }

    /**
     * Asserts that clear on a sub map removes only the levels below its path; the value at the path stays.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testClearOnSubMapKeepsValueAtPath() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "v0");
        map.put("a", "b", "v1");
        map.put("a", "c", "d", "v2");
        map.put("x", "v3");

        map.getSubMap("a").clear();

        Assert.assertEquals("v0", map.get("a"));
        Assert.assertNull(map.get("a", "b"));
        Assert.assertNull(map.get("a", "c", "d"));
        Assert.assertEquals("v3", map.get("x"));
        Assert.assertEquals(2, map.size());
        Assert.assertTrue(map.getSubMap("a").isEmpty());
    }

    /**
     * Asserts the documented way to remove a path with all deeper keys:
     * clear its sub map, then remove its value; the emptied level is pruned.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemoveBranch() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "v0");
        map.put("a", "b", "c", "v1");
        map.put("x", "v2");

        map.getSubMap("a").clear();
        map.remove("a");

        Assert.assertEquals(set("x"), map.keySet());
        Assert.assertEquals(1, map.size());
    }
}
