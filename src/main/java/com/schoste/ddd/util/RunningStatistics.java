package com.schoste.ddd.util;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;
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
 * <li>Data points are {@code double}s, like for {@link java.util.DoubleSummaryStatistics}: {@code int}, {@code long} and
 *     {@code float} values widen to {@code double} automatically, other {@link Number}s via {@link Number#doubleValue()}.
 *     A {@code double} holds integers exactly only up to 2<sup>53</sup>; for larger {@code long} values (e.g. timestamps
 *     in nanoseconds), add the differences to a base value instead: the variances stay the same, and the mean is the
 *     base plus the mean of the differences.</li>
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
 * Implementation: {@link #accept(double)} updates the mean and the sum of the squared deviations from the mean with
 * Welford's algorithm; {@link #combine(RunningStatistics)} merges them with the formula of Chan et al. The sum uses
 * compensated (Kahan) summation, like {@link java.util.DoubleSummaryStatistics}. Every method is {@code synchronized} on
 * the instance; {@link #combine(RunningStatistics)} first copies the other instance (holding only its lock), then
 * merges the copy (holding only this one's), so it never holds two locks and can't deadlock.
 * </p>
 */
public class RunningStatistics implements DoubleConsumer, Serializable, Cloneable
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 42L;

    /**
     * The number of data points.
     */
    private long count;

    /**
     * The mean of the data points; 0 while there are none.
     */
    private double mean;

    /**
     * The sum of the squared deviations of the data points from their mean (Welford's M2).
     */
    private double squaredDeviations;

    /**
     * The sum of the data points, without the rounding error in {@link #sumCompensation}.
     */
    private double sum;

    /**
     * The rounding error of {@link #sum} (Kahan summation): the exact sum is about {@code sum - sumCompensation}.
     */
    private double sumCompensation;

    /**
     * The sum of the data points without compensation; returned by {@link #getSum()} if the compensated sum is NaN
     * because of infinite data points.
     */
    private double simpleSum;

    /**
     * The smallest data point; {@link Double#POSITIVE_INFINITY} while there are none.
     */
    private double min = Double.POSITIVE_INFINITY;

    /**
     * The largest data point; {@link Double#NEGATIVE_INFINITY} while there are none.
     */
    private double max = Double.NEGATIVE_INFINITY;

    /**
     * Creates an instance without data points.
     */
    public RunningStatistics() { }

    /**
     * Adds a value to {@link #sum} with Kahan summation (like {@link java.util.DoubleSummaryStatistics}).
     */
    private void addToSum(double value)
    {
        double corrected = value - this.sumCompensation;
        double newSum = this.sum + corrected;

        this.sumCompensation = (newSum - this.sum) - corrected;
        this.sum = newSum;
    }

    /**
     * Adds a data point to the statistics.
     *
     * @param value the data point
     */
    @Override
    public synchronized void accept(double value)
    {
        this.count++;

        double delta = value - this.mean;

        this.mean += delta / this.count;
        this.squaredDeviations += delta * (value - this.mean);

        this.simpleSum += value;
        this.addToSum(value);
        this.min = Math.min(this.min, value);
        this.max = Math.max(this.max, value);
    }

    /**
     * Adds the data points of another instance to this one. The other instance stays unchanged.
     * Combining an instance with itself counts its data points twice.
     *
     * @param other the statistics to add, not null
     * @throws NullPointerException if other is null
     */
    public void combine(RunningStatistics other) throws NullPointerException
    {
        if (other == null) throw new NullPointerException("other");

        // copied while holding only the other's lock; merged while holding only this one's
        this.merge(other.clone());
    }

    /**
     * Merges a copy of other statistics into this one (Chan et al.), while holding this instance's lock.
     */
    private synchronized void merge(RunningStatistics other)
    {
        if (other.count == 0) return;

        if (this.count == 0)
        {
            this.mean = other.mean;
            this.squaredDeviations = other.squaredDeviations;
        }
        else
        {
            double total = (double) this.count + other.count;
            double delta = other.mean - this.mean;

            this.mean += delta * (other.count / total);
            this.squaredDeviations += other.squaredDeviations + delta * delta * (this.count * (other.count / total));
        }

        this.count += other.count;
        this.simpleSum += other.simpleSum;
        this.addToSum(other.sum);
        this.addToSum(-other.sumCompensation);
        this.min = Math.min(this.min, other.min);
        this.max = Math.max(this.max, other.max);
    }

    /**
     * Returns the number of data points.
     *
     * @return the number of data points
     */
    public synchronized long getCount()
    {
        return this.count;
    }

    /**
     * Returns the sum of the data points.
     *
     * @return the sum, or 0 if there are no data points
     */
    public synchronized double getSum()
    {
        double compensated = this.sum - this.sumCompensation;

        // the compensation of an infinite sum is NaN; the simple sum is right then (like DoubleSummaryStatistics)
        return (Double.isNaN(compensated) && Double.isInfinite(this.simpleSum)) ? this.simpleSum : compensated;
    }

    /**
     * Returns the smallest data point.
     *
     * @return the minimum, or {@link Double#POSITIVE_INFINITY} if there are no data points
     */
    public synchronized double getMin()
    {
        return this.min;
    }

    /**
     * Returns the largest data point.
     *
     * @return the maximum, or {@link Double#NEGATIVE_INFINITY} if there are no data points
     */
    public synchronized double getMax()
    {
        return this.max;
    }

    /**
     * Returns the arithmetic mean of the data points.
     *
     * @return the mean, or {@link Double#NaN} if there are no data points
     */
    public synchronized double getMean()
    {
        return (this.count == 0) ? Double.NaN : this.mean;
    }

    /**
     * Returns the variance of the data points as a population: the sum of the squared deviations from the mean,
     * divided by the number of data points.
     *
     * @return the population variance, or {@link Double#NaN} if there are no data points
     */
    public synchronized double getPopulationVariance()
    {
        return (this.count == 0) ? Double.NaN : this.squaredDeviations / this.count;
    }

    /**
     * Returns the variance of the data points as a sample of a larger population: the sum of the squared deviations
     * from the mean, divided by the number of data points minus one (Bessel's correction).
     *
     * @return the sample variance, or {@link Double#NaN} if there are fewer than two data points
     */
    public synchronized double getSampleVariance()
    {
        return (this.count < 2) ? Double.NaN : this.squaredDeviations / (this.count - 1);
    }

    /**
     * Returns the standard deviation of the data points as a population: the square root of
     * {@link #getPopulationVariance()}.
     *
     * @return the population standard deviation, or {@link Double#NaN} if there are no data points
     */
    public double getPopulationStandardDeviation()
    {
        return Math.sqrt(this.getPopulationVariance());
    }

    /**
     * Returns the standard deviation of the data points as a sample: the square root of {@link #getSampleVariance()}.
     *
     * @return the sample standard deviation, or {@link Double#NaN} if there are fewer than two data points
     */
    public double getSampleStandardDeviation()
    {
        return Math.sqrt(this.getSampleVariance());
    }

    /**
     * Returns a copy with the current statistics, taken consistently (no other thread can add data points while it is
     * being copied). Adding data points to one instance doesn't change the other.
     *
     * @return a copy of this instance
     */
    @Override
    public synchronized RunningStatistics clone()
    {
        try
        {
            return (RunningStatistics) super.clone();
        }
        catch (CloneNotSupportedException e)
        {
            throw new AssertionError("RunningStatistics is Cloneable", e);
        }
    }

    /**
     * Lists the statistics like {@link java.util.DoubleSummaryStatistics#toString()}, plus the variances, taken from a
     * consistent snapshot. The numbers always use a decimal point, independent of the default locale.
     *
     * @return the class name and the statistics
     */
    @Override
    public String toString()
    {
        RunningStatistics snapshot = this.clone();

        return String.format(Locale.ROOT, "%s{count=%d, sum=%f, min=%f, mean=%f, max=%f, populationVariance=%f, sampleVariance=%f}",
                             this.getClass().getSimpleName(), snapshot.getCount(), snapshot.getSum(), snapshot.getMin(),
                             snapshot.getMean(), snapshot.getMax(), snapshot.getPopulationVariance(), snapshot.getSampleVariance());
    }

    /**
     * Writes the statistics while holding the lock, so another thread can't add data points while the instance is
     * being serialized.
     *
     * @serialData the default serializable fields (count, mean, squared deviations, sums, minimum and maximum)
     * @param out the stream to write to
     * @throws IOException if writing to the stream failed
     */
    private synchronized void writeObject(ObjectOutputStream out) throws IOException
    {
        out.defaultWriteObject();
    }
}
