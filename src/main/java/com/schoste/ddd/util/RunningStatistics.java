package com.schoste.ddd.util;

import java.io.Serializable;
import java.util.function.DoubleConsumer;

/**
 * Computes statistics of an unknown number of data points online, i.e. one data point at a time, without keeping the
 * data points: count, sum, minimum, maximum, mean, and the variance and standard deviation of the population and of
 * the sample. The mean and the variances are computed with Welford's algorithm, which stays accurate also for many
 * data points and large values.
 *
 * <pre>
 * RunningStatistics stats = new RunningStatistics();
 *
 * stats.accept(2.0);
 * stats.accept(4.0);
 * stats.accept(9.0);
 *
 * stats.getMean();                        // 5.0
 * stats.getPopulationVariance();          // 8.666...: the sum of squared deviations / count
 * stats.getSampleVariance();              // 13.0: the sum of squared deviations / (count - 1)
 * stats.getSampleStandardDeviation();     // 3.605...
 *
 * // with streams, like java.util.DoubleSummaryStatistics:
 * RunningStatistics all = values.stream().collect(RunningStatistics::new, RunningStatistics::accept, RunningStatistics::combine);
 * </pre>
 *
 * <p>Rules:</p>
 * <ul>
 * <li>Values which are undefined for the current number of data points are {@link Double#NaN}: the mean and the
 *     population variance of no data points, and the sample variance of fewer than two data points (it divides by
 *     {@code count - 1}). Like {@link java.util.DoubleSummaryStatistics}, the sum of no data points is 0, the minimum
 *     is {@link Double#POSITIVE_INFINITY} and the maximum is {@link Double#NEGATIVE_INFINITY}.</li>
 * <li>Data points which are {@link Double#NaN} or infinite are accepted and make the affected statistics NaN or
 *     infinite, following the rules of floating-point arithmetic (as for {@link java.util.DoubleSummaryStatistics}).</li>
 * <li>{@link #combine(RunningStatistics)} adds the data points of another instance, so statistics can be computed in
 *     parts (e.g. in parallel) and merged. The result is the same as if all data points had been accepted by one
 *     instance, up to rounding.</li>
 * <li>The class is thread-safe: all methods may be called from several threads at the same time, and each method sees
 *     a consistent state (e.g. never the count of one data point with the mean of another). Several calls in a row
 *     (e.g. {@link #getMean()}, then {@link #getSampleVariance()}) may see data points which other threads accepted in
 *     between; use {@link #clone()} to get a consistent snapshot of all statistics.</li>
 * <li>The class is not a {@link java.util.DoubleSummaryStatistics}, because that class can't be combined with
 *     statistics which have a variance, and isn't {@link Serializable}. It offers the same methods
 *     ({@link #accept(double)}, {@link #getCount()}, {@link #getSum()}, {@link #getMin()}, {@link #getMax()}) and
 *     {@link #getMean()} instead of {@code getAverage()}.</li>
 * <li>{@link #equals(Object)} and {@link #hashCode()} are those of {@link Object} (identity), like for
 *     {@link java.util.DoubleSummaryStatistics}, because the statistics change with every data point.</li>
 * </ul>
 * <p>
 * Not implemented yet: the methods throw {@link UnsupportedOperationException}.
 * </p>
 */
public class RunningStatistics implements DoubleConsumer, Serializable, Cloneable
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

    /**
     * Creates an instance without data points.
     */
    public RunningStatistics() { }

    /**
     * Adds a data point to the statistics.
     *
     * @param value the data point
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    @Override
    public void accept(double value) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Adds the data points of another instance to this one. The other instance stays unchanged.
     * Combining an instance with itself counts its data points twice.
     *
     * @param other the statistics to add, not null
     * @throws NullPointerException if other is null
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public void combine(RunningStatistics other) throws NullPointerException, UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the number of data points.
     *
     * @return the number of data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public long getCount() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the sum of the data points.
     *
     * @return the sum, or 0 if there are no data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public double getSum() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the smallest data point.
     *
     * @return the minimum, or {@link Double#POSITIVE_INFINITY} if there are no data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public double getMin() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the largest data point.
     *
     * @return the maximum, or {@link Double#NEGATIVE_INFINITY} if there are no data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public double getMax() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the arithmetic mean of the data points.
     *
     * @return the mean, or {@link Double#NaN} if there are no data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public double getMean() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the variance of the data points as a population: the sum of the squared deviations from the mean,
     * divided by the number of data points.
     *
     * @return the population variance, or {@link Double#NaN} if there are no data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public double getPopulationVariance() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the variance of the data points as a sample of a larger population: the sum of the squared deviations
     * from the mean, divided by the number of data points minus one (Bessel's correction).
     *
     * @return the sample variance, or {@link Double#NaN} if there are fewer than two data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public double getSampleVariance() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the standard deviation of the data points as a population: the square root of
     * {@link #getPopulationVariance()}.
     *
     * @return the population standard deviation, or {@link Double#NaN} if there are no data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public double getPopulationStandardDeviation() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns the standard deviation of the data points as a sample: the square root of {@link #getSampleVariance()}.
     *
     * @return the sample standard deviation, or {@link Double#NaN} if there are fewer than two data points
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    public double getSampleStandardDeviation() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Returns a copy with the current statistics, taken consistently (no other thread can add data points while it is
     * being copied). Adding data points to one instance doesn't change the other.
     *
     * @return a copy of this instance
     * @throws UnsupportedOperationException always (not implemented yet)
     */
    @Override
    public RunningStatistics clone() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Lists the statistics, like {@link java.util.DoubleSummaryStatistics#toString()}, plus the variances.
     *
     * @return the class name and the statistics
     */
    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() + "{not implemented yet}";
    }
}
