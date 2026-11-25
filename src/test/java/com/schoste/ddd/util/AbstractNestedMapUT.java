package com.schoste.ddd.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests how {@link AbstractNestedMap#clone()} handles failures while copying the value maps of the levels
 * (see {@link TestNestedMap}). The behaviour without errors is tested by the contract test {@link NestedMapUT}.
 */
public class AbstractNestedMapUT
{
    /**
     * A {@link HashMap} whose {@link #clear()} can be made to fail.
     */
    static class ClearFailingMap extends HashMap<String, String>
    {
        private static final long serialVersionUID = 1L;

        /**
         * If not null, {@link #clear()} throws it.
         */
        RuntimeException failOnClear;

        @Override
        public void clear()
        {
            if (this.failOnClear != null) throw this.failOnClear;

            super.clear();
        }
    }

    /**
     * A nested map whose copying of value maps ({@link #cloneValueMap(Map)}) can be made to fail at a given call.
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
         * Passed to {@link ClearFailingMap#failOnClear} of every copy.
         */
        RuntimeException failOnClearOfCopies;

        /**
         * The value maps copied so far, in order.
         */
        final List<ClearFailingMap> copies = new ArrayList<>();

        @Override
        protected Map<String, String> createValueMap()
        {
            return new ClearFailingMap();
        }

        @Override
        protected Map<String, String> cloneValueMap(Map<String, String> valueMap) throws CloneNotSupportedException
        {
            if (this.copies.size() + 1 == this.failOnCopyNo)
            {
                if (this.copyFailure != null) throw this.copyFailure;

                throw this.uncheckedCopyFailure;
            }

            ClearFailingMap copy = (ClearFailingMap) super.cloneValueMap(valueMap);

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
        for (ClearFailingMap copy : testMap.copies) Assert.assertTrue("a copied value map was not cleared", copy.isEmpty());
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
}
