package com.study.organizer.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a study duration into points.
 *
 * <p>The rule from the specification is simply <b>1 hour = 1 point</b>:
 *
 * <pre>
 *   30 min  -&gt; 0.5
 *   45 min  -&gt; 0.75
 *   90 min  -&gt; 1.5
 *   120 min -&gt; 2.0
 * </pre>
 *
 * <p>This is the <b>only</b> place in the application where points are computed.
 * The user is never asked to type a point value; it is always derived from the
 * measured duration. Centralising the rule here means that if the formula ever
 * changes, exactly one method has to change with it.
 *
 * <p>This class is deliberately a pure function holder: it has no state and no
 * dependencies, which is what makes it trivial to unit test.
 */
public final class PointsCalculator {

    /** Number of seconds in one hour — and therefore in one point. */
    private static final int SECONDS_PER_HOUR = 3600;

    /** How many decimal places a point value is rounded to. */
    private static final int DECIMAL_PLACES = 2;

    /** This class is a utility holder and must never be instantiated. */
    private PointsCalculator() {
        throw new AssertionError("PointsCalculator is a utility class and cannot be instantiated.");
    }

    /**
     * Converts a duration in seconds into points, rounded to two decimal places.
     *
     * <p>We round with {@link BigDecimal} rather than plain {@code double}
     * arithmetic because binary floating point cannot represent values such as
     * 0.75 exactly. For displayed and stored scores, exact decimal rounding
     * avoids surprises like {@code 0.7500000000000001}.
     *
     * @param seconds the studied duration in seconds; must not be negative
     * @return the points earned, rounded half-up to two decimals
     * @throws IllegalArgumentException if {@code seconds} is negative
     */
    public static double fromSeconds(long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException(
                    "A study duration cannot be negative, but was: " + seconds + " seconds.");
        }

        return BigDecimal.valueOf(seconds)
                .divide(BigDecimal.valueOf(SECONDS_PER_HOUR), DECIMAL_PLACES, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Converts a duration in seconds into whole hours as a decimal number.
     *
     * <p>Because one hour equals one point this returns the same number as
     * {@link #fromSeconds(long)}, but the two are kept separate so that the
     * calling code reads clearly — statistics talk about <i>hours</i>, the
     * dashboard talks about <i>points</i> — and so that the point formula can
     * change later without dragging the hour calculation along with it.
     *
     * @param seconds the studied duration in seconds; must not be negative
     * @return the duration expressed in hours, rounded to two decimals
     */
    public static double toHours(long seconds) {
        return fromSeconds(seconds);
    }

    /**
     * Formats a point value for display, dropping a pointless {@code .00}.
     *
     * <p>So 2 points reads as "2" rather than "2.00", while 1.5 still reads as
     * "1.50". Whole numbers are the common case on a dashboard tile and the
     * trailing zeros are just noise there.
     *
     * @param points the value to format
     * @return the value as text
     */
    public static String format(double points) {
        if (points == Math.floor(points) && !Double.isInfinite(points)) {
            return String.valueOf((long) points);
        }
        return String.format("%.2f", points);
    }
}
