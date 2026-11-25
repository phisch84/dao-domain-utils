package com.schoste.ddd.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Random;
import java.util.stream.DoubleStream;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests {@link RunningStatistics}: the statistics of known data, the values for no and one data point, numerical
 * accuracy (compared with an exact two-pass computation), NaN and infinite data points, and combining instances.
 */
public class RunningStatisticsUT
{
    /**
     * Creates an instance which accepted the values, in their order.
     */
    private static RunningStatistics of(double... values)
    {
        RunningStatistics stats = new RunningStatistics();

        for (double value : values) stats.accept(value);

        return stats;
    }

    /**
     * Asserts that two values are equal up to a relative error of 1e-9 (NaN equals NaN, infinities equal themselves).
     */
    private static void assertClose(String message, double expected, double actual)
    {
        Assert.assertEquals(message, expected, actual, 1e-9 * Math.max(1.0, Math.abs(expected)));
    }

    /**
     * Asserts that two instances have the same statistics, up to rounding.
     */
    private static void assertSameStatistics(RunningStatistics expected, RunningStatistics actual)
    {
        Assert.assertEquals("count", expected.getCount(), actual.getCount());
        assertClose("sum", expected.getSum(), actual.getSum());
        assertClose("min", expected.getMin(), actual.getMin());
        assertClose("max", expected.getMax(), actual.getMax());
        assertClose("mean", expected.getMean(), actual.getMean());
        assertClose("population variance", expected.getPopulationVariance(), actual.getPopulationVariance());
        assertClose("sample variance", expected.getSampleVariance(), actual.getSampleVariance());
    }

    /**
     * Asserts the values of an instance without data points: count and sum 0, minimum +infinity, maximum -infinity
     * (like {@link java.util.DoubleSummaryStatistics}), and NaN for the mean, the variances and the standard deviations.
     */
    @Test
    public void testNoDataPoints()
    {
        RunningStatistics stats = new RunningStatistics();

        Assert.assertEquals(0, stats.getCount());
        Assert.assertEquals(0.0, stats.getSum(), 0.0);
        Assert.assertEquals(Double.POSITIVE_INFINITY, stats.getMin(), 0.0);
        Assert.assertEquals(Double.NEGATIVE_INFINITY, stats.getMax(), 0.0);
        Assert.assertTrue(Double.isNaN(stats.getMean()));
        Assert.assertTrue(Double.isNaN(stats.getPopulationVariance()));
        Assert.assertTrue(Double.isNaN(stats.getSampleVariance()));
        Assert.assertTrue(Double.isNaN(stats.getPopulationStandardDeviation()));
        Assert.assertTrue(Double.isNaN(stats.getSampleStandardDeviation()));
    }

    /**
     * Asserts the values of an instance with one data point: the population variance is 0, the sample variance
     * (divided by count - 1) is NaN.
     */
    @Test
    public void testOneDataPoint()
    {
        RunningStatistics stats = of(7.5);

        Assert.assertEquals(1, stats.getCount());
        Assert.assertEquals(7.5, stats.getSum(), 0.0);
        Assert.assertEquals(7.5, stats.getMin(), 0.0);
        Assert.assertEquals(7.5, stats.getMax(), 0.0);
        Assert.assertEquals(7.5, stats.getMean(), 0.0);
        Assert.assertEquals(0.0, stats.getPopulationVariance(), 0.0);
        Assert.assertEquals(0.0, stats.getPopulationStandardDeviation(), 0.0);
        Assert.assertTrue(Double.isNaN(stats.getSampleVariance()));
        Assert.assertTrue(Double.isNaN(stats.getSampleStandardDeviation()));
    }

    /**
     * Asserts the values of two data points (1 and 3), the smallest number for which the sample variance is defined:
     * population variance 1, sample variance 2.
     */
    @Test
    public void testTwoDataPoints()
    {
        RunningStatistics stats = of(1, 3);

        assertClose("population variance", 1.0, stats.getPopulationVariance());
        assertClose("sample variance", 2.0, stats.getSampleVariance());
        assertClose("sample standard deviation", Math.sqrt(2.0), stats.getSampleStandardDeviation());
    }

    /**
     * Asserts that the sum is compensated (Kahan summation, like {@link java.util.DoubleSummaryStatistics}), also when
     * combining. The step between doubles is 2.2e-16 above 1.0 and 1.1e-16 below it; the values are compared exactly.
     * <ul>
     * <li>1.0 + 1e-16 rounds to 1.0; applying the compensation (-1e-16) with the wrong sign gives the double below 1.0.</li>
     * <li>1e-16 combined with (1.0 + 1e-16) is exactly 1 + 2e-16, which is closest to the double above 1.0. Adding the
     *     values without compensation, or combining without the other's compensation, gives 1.0.</li>
     * </ul>
     */
    @Test
    public void testSumIsCompensated()
    {
        Assert.assertEquals(1.0, of(1.0, 1e-16).getSum(), 0.0);

        Assert.assertEquals("the test data needs compensation", 1.0, (1e-16 + 1.0) + 1e-16, 0.0);

        RunningStatistics combined = of(1e-16);

        combined.combine(of(1.0, 1e-16));

        Assert.assertEquals(Math.nextUp(1.0), combined.getSum(), 0.0);
    }

    /**
     * Asserts that combining with an infinite sum gives an infinite sum (the compensation of an infinite sum is NaN, so
     * the uncompensated sum must be combined too).
     */
    @Test
    public void testCombineWithInfiniteSum()
    {
        RunningStatistics stats = of(1);

        stats.combine(of(Double.POSITIVE_INFINITY));

        Assert.assertEquals(Double.POSITIVE_INFINITY, stats.getSum(), 0.0);
        Assert.assertEquals(Double.POSITIVE_INFINITY, stats.getMax(), 0.0);
    }

    /**
     * Asserts the statistics of the example in the class Javadoc (2, 4, 9) and of a textbook data set
     * (2, 4, 4, 4, 5, 5, 7, 9: mean 5, population variance 4, population standard deviation 2).
     */
    @Test
    public void testKnownDataSets()
    {
        RunningStatistics example = of(2, 4, 9);

        Assert.assertEquals(3, example.getCount());
        assertClose("sum", 15.0, example.getSum());
        assertClose("min", 2.0, example.getMin());
        assertClose("max", 9.0, example.getMax());
        assertClose("mean", 5.0, example.getMean());
        assertClose("population variance", 26.0 / 3, example.getPopulationVariance());
        assertClose("sample variance", 13.0, example.getSampleVariance());
        assertClose("population standard deviation", Math.sqrt(26.0 / 3), example.getPopulationStandardDeviation());
        assertClose("sample standard deviation", Math.sqrt(13.0), example.getSampleStandardDeviation());

        RunningStatistics textbook = of(2, 4, 4, 4, 5, 5, 7, 9);

        assertClose("mean", 5.0, textbook.getMean());
        assertClose("population variance", 4.0, textbook.getPopulationVariance());
        assertClose("population standard deviation", 2.0, textbook.getPopulationStandardDeviation());
        assertClose("sample variance", 32.0 / 7, textbook.getSampleVariance());
    }

    /**
     * Asserts that the statistics of 10,000 random data points match an exact two-pass computation with
     * {@link BigDecimal} (mean first, then the squared deviations), up to rounding.
     */
    @Test
    public void testMatchesExactTwoPassComputation()
    {
        Random random = new Random(42);
        double[] values = new double[10_000];

        for (int i = 0; i < values.length; i++) values[i] = random.nextGaussian() * 1000 + 50;

        MathContext context = MathContext.DECIMAL128;
        BigDecimal count = BigDecimal.valueOf(values.length);
        BigDecimal sum = BigDecimal.ZERO;

        for (double value : values) sum = sum.add(new BigDecimal(value));

        BigDecimal mean = sum.divide(count, context);
        BigDecimal squaredDeviations = BigDecimal.ZERO;

        for (double value : values)
        {
            BigDecimal deviation = new BigDecimal(value).subtract(mean);

            squaredDeviations = squaredDeviations.add(deviation.multiply(deviation));
        }

        RunningStatistics stats = of(values);

        assertClose("sum", sum.doubleValue(), stats.getSum());
        assertClose("mean", mean.doubleValue(), stats.getMean());
        assertClose("population variance", squaredDeviations.divide(count, context).doubleValue(), stats.getPopulationVariance());
        assertClose("sample variance", squaredDeviations.divide(count.subtract(BigDecimal.ONE), context).doubleValue(), stats.getSampleVariance());
        assertClose("min", DoubleStream.of(values).min().getAsDouble(), stats.getMin());
        assertClose("max", DoubleStream.of(values).max().getAsDouble(), stats.getMax());
    }

    /**
     * Asserts that the variance stays accurate for large values with a small spread, where the naive formula
     * (mean of the squares minus the square of the mean) loses all digits: 1e9 + 4, 7, 13, 16 have the sample
     * variance 30.
     */
    @Test
    public void testLargeValuesWithSmallSpread()
    {
        RunningStatistics stats = of(1e9 + 4, 1e9 + 7, 1e9 + 13, 1e9 + 16);

        assertClose("mean", 1e9 + 10, stats.getMean());
        Assert.assertEquals("sample variance", 30.0, stats.getSampleVariance(), 1e-6);
        Assert.assertEquals("population variance", 22.5, stats.getPopulationVariance(), 1e-6);
    }

    /**
     * Asserts that a NaN data point makes count-independent statistics NaN, and that an infinite data point makes the
     * sum, the maximum and the mean infinite and the variances NaN, following floating-point arithmetic.
     */
    @Test
    public void testNaNAndInfiniteDataPoints()
    {
        RunningStatistics withNaN = of(1, Double.NaN, 3);

        Assert.assertEquals(3, withNaN.getCount());
        Assert.assertTrue(Double.isNaN(withNaN.getSum()));
        Assert.assertTrue(Double.isNaN(withNaN.getMean()));
        Assert.assertTrue(Double.isNaN(withNaN.getSampleVariance()));

        RunningStatistics withInfinity = of(1, Double.POSITIVE_INFINITY);

        Assert.assertEquals(2, withInfinity.getCount());
        Assert.assertEquals(Double.POSITIVE_INFINITY, withInfinity.getSum(), 0.0);
        Assert.assertEquals(Double.POSITIVE_INFINITY, withInfinity.getMax(), 0.0);
        Assert.assertEquals(1.0, withInfinity.getMin(), 0.0);
        Assert.assertEquals(Double.POSITIVE_INFINITY, withInfinity.getMean(), 0.0);
        Assert.assertTrue(Double.isNaN(withInfinity.getSampleVariance()));
    }

    /**
     * Asserts that combining the statistics of two parts gives the statistics of all data points, also when one
     * part is empty, and that the other instance stays unchanged.
     */
    @Test
    public void testCombineGivesStatisticsOfAllDataPoints()
    {
        double[] first = { 1, 2, 3, 100 };
        double[] second = { -5, 7.5, 42 };
        RunningStatistics all = of(1, 2, 3, 100, -5, 7.5, 42);
        RunningStatistics combined = of(first);
        RunningStatistics other = of(second);

        combined.combine(other);

        assertSameStatistics(all, combined);
        assertSameStatistics(of(second), other);

        RunningStatistics empty = new RunningStatistics();

        empty.combine(of(first));
        assertSameStatistics(of(first), empty);

        RunningStatistics withEmpty = of(first);

        withEmpty.combine(new RunningStatistics());
        assertSameStatistics(of(first), withEmpty);
    }

    /**
     * Asserts that combining an instance with itself counts its data points twice: same mean and population variance,
     * double count and sum.
     */
    @Test
    public void testCombineWithItself()
    {
        RunningStatistics stats = of(2, 4, 9);

        stats.combine(stats);

        assertSameStatistics(of(2, 4, 9, 2, 4, 9), stats);
    }

    /**
     * Asserts that combine rejects null with {@link NullPointerException}.
     */
    @Test
    public void testCombineRejectsNull()
    {
        Assert.assertThrows(NullPointerException.class, () -> new RunningStatistics().combine(null));
    }

    /**
     * Asserts that collecting a parallel stream (which accepts parts in several instances and combines them) gives the
     * same statistics as accepting the data points one by one.
     */
    @Test
    public void testCollectParallelStream()
    {
        double[] values = new Random(7).doubles(100_000, -1000, 1000).toArray();

        RunningStatistics collected = DoubleStream.of(values).parallel()
                                                  .collect(RunningStatistics::new, RunningStatistics::accept, RunningStatistics::combine);

        assertSameStatistics(of(values), collected);
    }

    /**
     * Asserts the format of toString: like {@link java.util.DoubleSummaryStatistics#toString()} (always with a decimal
     * point, independent of the default locale), plus the variances.
     */
    @Test
    public void testToString()
    {
        Assert.assertEquals("RunningStatistics{count=3, sum=15.000000, min=2.000000, mean=5.000000, max=9.000000, "
                          + "populationVariance=8.666667, sampleVariance=13.000000}", of(2, 4, 9).toString());
    }

    /**
     * Asserts that equals and hashCode are identity, like for {@link java.util.DoubleSummaryStatistics}.
     */
    @Test
    public void testEqualsIsIdentity()
    {
        RunningStatistics stats = of(1, 2);

        Assert.assertEquals(stats, stats);
        Assert.assertNotEquals(stats, of(1, 2));
        Assert.assertEquals(System.identityHashCode(stats), stats.hashCode());
    }
}
