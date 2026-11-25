package com.schoste.ddd.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import static com.schoste.ddd.util.TestHelpers.serializeAndDeserialize;
import static com.schoste.ddd.util.TestHelpers.set;

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
     * Asserts that every put overload (1 to 5 keys) returns the previous value of its path.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPutReturnsPreviousValueForEveryOverload() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("1", "old1");
        map.put("1", "2", "old2");
        map.put("1", "2", "3", "old3");
        map.put("1", "2", "3", "4", "old4");
        map.put("1", "2", "3", "4", "5", "old5");

        Assert.assertEquals("old1", map.put("1", "new1"));
        Assert.assertEquals("old2", map.put("1", "2", "new2"));
        Assert.assertEquals("old3", map.put("1", "2", "3", "new3"));
        Assert.assertEquals("old4", map.put("1", "2", "3", "4", "new4"));
        Assert.assertEquals("old5", map.put("1", "2", "3", "4", "5", "new5"));
    }

    /**
     * Asserts that a map whose values are all on deeper levels (none on the first level) is not empty,
     * and neither is a sub map with values only below it.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testMapWithValuesOnlyOnDeeperLevelsIsNotEmpty() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "c", "v");

        Assert.assertFalse(map.isEmpty());
        Assert.assertFalse(map.getSubMap("a").isEmpty());
        Assert.assertTrue(map.getSubMap("a", "b", "c").isEmpty());
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
     * Asserts that reading, removing and clearing through the sub map of a path which doesn't exist
     * (here: the first key exists, the second doesn't) work like on an empty map, and don't create the path.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSubMapOfMissingPathDoesntCreatePath() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "v");

        NestedMap<String, String> sub = map.getSubMap("a", "b");

        Assert.assertEquals(0, sub.size());
        Assert.assertTrue(sub.isEmpty());
        Assert.assertFalse(sub.containsKey("c"));
        Assert.assertNull(sub.get("c"));
        Assert.assertNull(sub.remove("c"));
        Assert.assertTrue(sub.getSubMap("c").isEmpty());

        sub.clear();

        Assert.assertEquals(1, map.size());
        Assert.assertEquals("v", map.get("a"));
        Assert.assertEquals(set("a"), map.keySet());
        Assert.assertTrue("the path was created", map.getSubMap("a").keySet().isEmpty());
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

    /**
     * Asserts that a deserialized map has all key paths and values, and can be changed.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSerializeAndDeserialize() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "v1");
        map.put("a", "b", "c", "v2");
        map.put("x", "y", "v3");

        NestedMap<String, String> copy = serializeAndDeserialize(map);

        Assert.assertNotSame(map, copy);
        Assert.assertEquals("v1", copy.get("a"));
        Assert.assertEquals("v2", copy.get("a", "b", "c"));
        Assert.assertEquals("v3", copy.get("x", "y"));
        Assert.assertEquals(set("a", "x"), copy.keySet());
        Assert.assertEquals(3, copy.size());

        copy.put("n", "e", "w", "v4");
        copy.getSubMap("a").put("d", "v5");

        Assert.assertEquals("v4", copy.get("n", "e", "w"));
        Assert.assertEquals("v5", copy.get("a", "d"));
        Assert.assertFalse(map.containsKey("n", "e", "w"));
    }

    /**
     * Asserts that a deserialized sub map is still a view of its path.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSerializeSubMap() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "v1");

        NestedMap<String, String> sub = serializeAndDeserialize(map.getSubMap("a"));

        Assert.assertEquals("v1", sub.get("b"));
        Assert.assertEquals(set("b"), sub.keySet());

        sub.put("c", "v2");

        Assert.assertEquals("v2", sub.get("c"));
        Assert.assertNull(map.get("a", "c"));
    }

    /**
     * Asserts that a clone has all key paths and values, and that adding, replacing and removing in one map
     * doesn't affect the other.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testClone() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "v1");
        map.put("a", "b", "c", "v2");
        map.put("x", "y", "v3");

        NestedMap<String, String> clone = (NestedMap<String, String>) map.clone();

        Assert.assertNotSame(map, clone);
        Assert.assertSame(map.getClass(), clone.getClass());
        Assert.assertEquals("v1", clone.get("a"));
        Assert.assertEquals("v2", clone.get("a", "b", "c"));
        Assert.assertEquals("v3", clone.get("x", "y"));
        Assert.assertEquals(3, clone.size());

        clone.put("a", "b", "c", "replaced");
        clone.remove("x", "y");
        clone.put("n", "v4");
        map.put("a", "b", "d", "v5");

        Assert.assertEquals("v2", map.get("a", "b", "c"));
        Assert.assertEquals("v3", map.get("x", "y"));
        Assert.assertFalse(map.containsKey("n"));
        Assert.assertEquals(set("a", "x"), map.keySet());
        Assert.assertEquals("replaced", clone.get("a", "b", "c"));
        Assert.assertFalse(clone.containsKey("a", "b", "d"));
        Assert.assertEquals(set("a", "n"), clone.keySet());
    }

    /**
     * Asserts that the sub maps of a clone are views of the clone, not of the original map.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSubMapOfCloneIsViewOfClone() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "v1");

        NestedMap<String, String> clone = (NestedMap<String, String>) map.clone();

        clone.getSubMap("a").put("c", "v2");

        Assert.assertEquals("v2", clone.get("a", "c"));
        Assert.assertNull(map.get("a", "c"));
    }

    /**
     * Asserts that cloning a sub map is rejected with a {@link CloneNotSupportedException} without a cause,
     * i.e. deliberately and not because cloning failed.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCloneOfSubMapIsRejected() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        map.put("a", "b", "v");

        try
        {
            map.getSubMap("a").clone();
            Assert.fail("CloneNotSupportedException expected");
        }
        catch (CloneNotSupportedException e)
        {
            Assert.assertNull(e.getCause());
        }
    }

    /**
     * Creates a map of the implementation under test with the values 1 at the path a, 2 at a, x and 3 at b, y, z,
     * put in the given order of the paths.
     *
     * @param reversed true to put the paths in reverse order
     * @return the map
     * @throws Exception re-throws every exception
     */
    private NestedMap<String, String> mapWithThreePaths(boolean reversed) throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        if (reversed)
        {
            map.put("b", "y", "z", "3");
            map.put("a", "x", "2");
            map.put("a", "1");
        }
        else
        {
            map.put("a", "1");
            map.put("a", "x", "2");
            map.put("b", "y", "z", "3");
        }
        return map;
    }

    /**
     * Asserts that two maps are equal (both ways) if they have the same key paths with equal values,
     * independent of the order in which the values were put, and that equal maps have the same hash code.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEqualsComparesPathsAndValues() throws Exception
    {
        NestedMap<String, String> map = this.mapWithThreePaths(false);
        NestedMap<String, String> other = this.mapWithThreePaths(true);

        Assert.assertEquals(map, map);
        Assert.assertEquals(map, other);
        Assert.assertEquals(other, map);
        Assert.assertEquals(map.hashCode(), other.hashCode());
        Assert.assertEquals(this.createNestedMap(), this.createNestedMap());
    }

    /**
     * Asserts that maps are not equal if a value differs, if one has an additional path, or if the same value is
     * at another path; and that a map is not equal to null or to an object which is no {@link NestedMap}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testNotEqualsForOtherContent() throws Exception
    {
        NestedMap<String, String> map = this.mapWithThreePaths(false);

        NestedMap<String, String> otherValue = this.mapWithThreePaths(false);
        otherValue.put("a", "x", "other");

        NestedMap<String, String> additionalPath = this.mapWithThreePaths(false);
        additionalPath.put("c", "4");

        NestedMap<String, String> otherPath = this.createNestedMap();
        otherPath.put("a", "1");
        otherPath.put("a", "x", "2");
        otherPath.put("b", "y", "3");

        for (NestedMap<String, String> other : Arrays.asList(otherValue, additionalPath, otherPath))
        {
            Assert.assertNotEquals(map, other);
            Assert.assertNotEquals(other, map);
        }

        Assert.assertNotEquals(map, null);
        Assert.assertNotEquals(map, Collections.singletonMap("a", "1"));
    }

    /**
     * Asserts that equality depends only on the content: a map of the implementation under test is equal to a
     * {@link NestedHashMap} with the same paths and values (both ways), and has the same hash code.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEqualsAcrossImplementations() throws Exception
    {
        NestedMap<String, String> map = this.mapWithThreePaths(false);
        NestedHashMap<String, String> other = new NestedHashMap<>();

        other.put("b", "y", "z", "3");
        other.put("a", "1");
        other.put("a", "x", "2");

        Assert.assertEquals(map, other);
        Assert.assertEquals(other, map);
        Assert.assertEquals(map.hashCode(), other.hashCode());
    }

    /**
     * Asserts that a sub map is compared by its content, with paths relative to its own path:
     * the sub map of the path a equals a map with 2 at the path x.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSubMapEqualsByContent() throws Exception
    {
        NestedMap<String, String> map = this.mapWithThreePaths(false);
        NestedHashMap<String, String> expected = new NestedHashMap<>();

        expected.put("x", "2");

        Assert.assertEquals(expected, map.getSubMap("a"));
        Assert.assertEquals(map.getSubMap("a"), expected);
        Assert.assertEquals(expected.hashCode(), map.getSubMap("a").hashCode());
    }

    /**
     * Asserts the hash code defined by {@link NestedMap#hashCode()}: the sum over all values of
     * (the key path as {@link List}).hashCode() ^ value.hashCode(), like a {@link java.util.Map} with paths as keys.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testHashCodeFormula() throws Exception
    {
        NestedMap<String, String> map = this.mapWithThreePaths(false);
        int expected = (Arrays.asList("a").hashCode() ^ "1".hashCode())
                     + (Arrays.asList("a", "x").hashCode() ^ "2".hashCode())
                     + (Arrays.asList("b", "y", "z").hashCode() ^ "3".hashCode());

        Assert.assertEquals(expected, map.hashCode());
        Assert.assertEquals(0, this.createNestedMap().hashCode());
    }

    /**
     * Asserts the format of {@link NestedMap#toString()}: every value as "[key path]=value", separated by ", "
     * in braces, like {@link java.util.AbstractMap#toString()} with the paths as keys.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testToString() throws Exception
    {
        NestedMap<String, String> map = this.createNestedMap();

        Assert.assertEquals("{}", map.toString());

        map.put("a", "b", "v1");

        Assert.assertEquals("{[a, b]=v1}", map.toString());

        map.put("a", "v2");

        String twoValues = map.toString();

        Assert.assertTrue(twoValues, twoValues.equals("{[a]=v2, [a, b]=v1}") || twoValues.equals("{[a, b]=v1, [a]=v2}"));
        Assert.assertEquals("{[b]=v1}", map.getSubMap("a").toString());
    }
}
