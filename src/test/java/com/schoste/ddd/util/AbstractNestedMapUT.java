package com.schoste.ddd.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests how {@link AbstractNestedMap} handles failures of its value maps: when putting a value, and while
 * {@link AbstractNestedMap#clone()} copies the value maps of the levels (see {@link TestNestedMap}). The behaviour without errors is tested by the contract test {@link NestedMapUT}.
 */
public class AbstractNestedMapUT
{
    /**
     * A {@link HashMap} whose {@link #get(Object)}, {@link #put(Object, Object)} and {@link #clear()} can be made to fail.
     */
    static class FailingHashMap extends HashMap<String, String>
    {
        private static final long serialVersionUID = 1L;

        /**
         * If not null, {@link #get(Object)} throws it.
         */
        RuntimeException failOnGet;

        /**
         * If not null, {@link #put(Object, Object)} throws it.
         */
        RuntimeException failOnPut;

        /**
         * If not null, {@link #clear()} throws it.
         */
        RuntimeException failOnClear;

        @Override
        public String get(Object key)
        {
            if (this.failOnGet != null) throw this.failOnGet;

            return super.get(key);
        }

        @Override
        public String put(String key, String value)
        {
            if (this.failOnPut != null) throw this.failOnPut;

            return super.put(key, value);
        }

        @Override
        public void clear()
        {
            if (this.failOnClear != null) throw this.failOnClear;

            super.clear();
        }
    }

    /**
     * A nested map whose value maps can be made to fail on put, and whose copying of value maps
     * ({@link #cloneValueMap(Map)}) can be made to fail at a given call.
     */
    static class TestNestedMap extends AbstractNestedMap<String, String>
    {
        private static final long serialVersionUID = 1L;

        /**
         * The number of the call of {@link #cloneValueMap(Map)} which fails (1 = the first one), 0 for none.
         */
        int failOnCopyNo;

        /**
         * Thrown by the failing call, if not null.
         */
        CloneNotSupportedException copyFailure;

        /**
         * Thrown by the failing call, if {@link #copyFailure} is null.
         */
        RuntimeException uncheckedCopyFailure;

        /**
         * Passed to {@link FailingHashMap#failOnClear} of every copy.
         */
        RuntimeException failOnClearOfCopies;

        /**
         * Passed to {@link FailingHashMap#failOnPut} of every value map created from now on.
         */
        RuntimeException failOnPutOfNewValueMaps;

        /**
         * The value maps created by {@link #createValueMap()} (also for copies), in order.
         */
        final List<FailingHashMap> created = new ArrayList<>();

        /**
         * The value maps copied so far, in order.
         */
        final List<FailingHashMap> copies = new ArrayList<>();

        @Override
        protected Map<String, String> createValueMap()
        {
            FailingHashMap valueMap = new FailingHashMap();

            valueMap.failOnPut = this.failOnPutOfNewValueMaps;
            this.created.add(valueMap);

            return valueMap;
        }

        @Override
        protected Map<String, String> cloneValueMap(Map<String, String> valueMap) throws CloneNotSupportedException
        {
            if (this.copies.size() + 1 == this.failOnCopyNo)
            {
                if (this.copyFailure != null) throw this.copyFailure;

                throw this.uncheckedCopyFailure;
            }

            FailingHashMap copy = (FailingHashMap) super.cloneValueMap(valueMap);

            copy.failOnClear = this.failOnClearOfCopies;
            this.copies.add(copy);

            return copy;
        }
    }

    /**
     * Creates a map with a value on each of three levels, which have one path: {@link AbstractNestedMap#clone()} copies
     * the first level, then the second one, then the third one. Failing on the third copy checks that the copy of the
     * second level was already added to the clone, so it is cleared as well.
     *
     * @return the map
     */
    private static TestNestedMap mapWithThreeLevels()
    {
        TestNestedMap testMap = new TestNestedMap();

        testMap.put("a", "1");
        testMap.put("b", "x", "2");
        testMap.put("b", "y", "z", "3");

        return testMap;
    }

    private static void assertUnchanged(TestNestedMap testMap)
    {
        Assert.assertEquals(3, testMap.size());
        Assert.assertEquals("1", testMap.get("a"));
        Assert.assertEquals("2", testMap.get("b", "x"));
        Assert.assertEquals("3", testMap.get("b", "y", "z"));
    }

    /**
     * Asserts that if copying a value map fails with {@link CloneNotSupportedException},
     * {@link AbstractNestedMap#clone()} throws that exception, clears the value maps it already copied
     * (which frees their storage) and leaves the original map unchanged.
     */
    @Test
    public void testCloneWhenCopyingFailsClearsCopiedValueMaps()
    {
        TestNestedMap testMap = mapWithThreeLevels();
        CloneNotSupportedException failure = new CloneNotSupportedException("injected");

        testMap.failOnCopyNo = 3;
        testMap.copyFailure = failure;

        Assert.assertSame(failure, Assert.assertThrows(CloneNotSupportedException.class, testMap::clone));
        Assert.assertEquals(2, testMap.copies.size());
        Assert.assertTrue("the copy of the first level was not cleared", testMap.copies.get(0).isEmpty());
        Assert.assertTrue("the copy of the second level was not cleared", testMap.copies.get(1).isEmpty());
        assertUnchanged(testMap);
    }

    /**
     * Asserts that if copying a value map fails with an unchecked exception, {@link AbstractNestedMap#clone()}
     * throws {@link CloneNotSupportedException} with it as cause and clears the value maps it already copied.
     */
    @Test
    public void testCloneWhenCopyingFailsUncheckedWrapsIt()
    {
        TestNestedMap testMap = mapWithThreeLevels();
        IllegalStateException failure = new IllegalStateException("injected");

        testMap.failOnCopyNo = 3;
        testMap.uncheckedCopyFailure = failure;

        CloneNotSupportedException thrown = Assert.assertThrows(CloneNotSupportedException.class, testMap::clone);

        Assert.assertSame(failure, thrown.getCause());
        for (FailingHashMap copy : testMap.copies) Assert.assertTrue("a copied value map was not cleared", copy.isEmpty());
        assertUnchanged(testMap);
    }

    /**
     * Asserts that if clearing the copied value maps fails too, {@link AbstractNestedMap#clone()} throws the
     * failure of copying, with the failure of clearing as suppressed exception.
     */
    @Test
    public void testCloneWhenClearingTheCopyFailsAddsSuppressed()
    {
        TestNestedMap testMap = mapWithThreeLevels();
        CloneNotSupportedException failure = new CloneNotSupportedException("injected");
        IllegalStateException clearFailure = new IllegalStateException("injected on clear");

        testMap.failOnCopyNo = 3;
        testMap.copyFailure = failure;
        testMap.failOnClearOfCopies = clearFailure;

        CloneNotSupportedException thrown = Assert.assertThrows(CloneNotSupportedException.class, testMap::clone);

        Assert.assertSame(failure, thrown);
        Assert.assertArrayEquals(new Throwable[] { clearFailure }, thrown.getSuppressed());
        assertUnchanged(testMap);
    }

    /**
     * Asserts that if storing a value at a new path fails, {@link AbstractNestedMap#put(java.util.List, Object)} throws
     * the failure and removes the levels it created for the path, so the map is unchanged
     * (no key in {@link AbstractNestedMap#keySet()} without a value below it).
     */
    @Test
    public void testPutWhenStoringFailsRemovesCreatedLevels()
    {
        TestNestedMap testMap = new TestNestedMap();
        IllegalStateException failure = new IllegalStateException("injected");

        testMap.put("a", "1");
        testMap.failOnPutOfNewValueMaps = failure;

        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, () -> testMap.put("b", "x", "y", "2")));
        Assert.assertEquals(TestHelpers.set("a"), testMap.keySet());
        Assert.assertEquals(1, testMap.size());
        Assert.assertTrue(testMap.getSubMap("b").isEmpty());

        TestNestedMap emptyMap = new TestNestedMap();

        emptyMap.failOnPutOfNewValueMaps = failure;

        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, () -> emptyMap.put("a", "1")));
        Assert.assertTrue(emptyMap.keySet().isEmpty());
        Assert.assertTrue(emptyMap.isEmpty());
    }

    /**
     * Asserts that if storing a value in a level which already has values fails, the level and its values stay.
     */
    @Test
    public void testPutWhenStoringInExistingLevelFailsKeepsLevel()
    {
        TestNestedMap testMap = new TestNestedMap();
        IllegalStateException failure = new IllegalStateException("injected");

        testMap.put("b", "x", "1");
        testMap.created.get(0).failOnPut = failure;

        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, () -> testMap.put("b", "y", "2")));
        Assert.assertEquals(TestHelpers.set("b"), testMap.keySet());
        Assert.assertEquals("1", testMap.get("b", "x"));
        Assert.assertEquals(1, testMap.size());
    }

    /**
     * Asserts that {@link AbstractNestedMap#equals(Object)} returns false, instead of throwing, if the other map
     * rejects a key path of this map with {@link ClassCastException} or {@link NullPointerException}
     * (like {@link java.util.AbstractMap#equals(Object)}).
     */
    @Test
    public void testEqualsIsFalseIfTheOtherMapRejectsAKey()
    {
        for (RuntimeException rejection : new RuntimeException[] { new ClassCastException("injected"), new NullPointerException("injected") })
        {
            TestNestedMap testMap = new TestNestedMap();
            TestNestedMap other = new TestNestedMap();

            testMap.put("a", "1");
            other.put("a", "1");
            other.created.get(0).failOnGet = rejection;

            Assert.assertNotEquals(testMap, other);
        }
    }
}
