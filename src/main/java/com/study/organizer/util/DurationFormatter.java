package com.study.organizer.util;

/**
 * Turns a number of seconds into the human-readable clock strings the UI shows.
 *
 * <p>Like {@link com.study.organizer.service.PointsCalculator}, this is a pure
 * utility class with no state, so it is easy to test and safe to call from any
 * thread.
 */
public final class DurationFormatter {

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 3600;

    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    /** This class is a utility holder and must never be instantiated. */
    private DurationFormatter() {
        throw new AssertionError("DurationFormatter is a utility class and cannot be instantiated.");
    }

    /**
     * Formats a duration as a zero-padded stopwatch string, for example
     * {@code "02:34:18"}.
     *
     * <p>The hours field is <b>not</b> capped at 24 and is not padded beyond two
     * digits, so a very long total still reads correctly as {@code "123:04:05"}.
     * This matters because the same method formats both a single session and
     * lifetime totals.
     *
     * @param totalSeconds the duration in seconds; must not be negative
     * @return the duration as {@code HH:mm:ss}
     * @throws IllegalArgumentException if {@code totalSeconds} is negative
     */
    public static String format(long totalSeconds) {
        if (totalSeconds < 0) {
            throw new IllegalArgumentException(
                    "A duration cannot be negative, but was: " + totalSeconds + " seconds.");
        }

        long hours = totalSeconds / SECONDS_PER_HOUR;
        long minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long seconds = totalSeconds % SECONDS_PER_MINUTE;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Formats a duration in a compact, friendly way for dashboard tiles, for
     * example {@code "2h 34m"} or — when under an hour — {@code "34m"}.
     *
     * <p>Seconds are intentionally left out here. On a summary tile the extra
     * precision is noise, and it would make the number flicker every second.
     *
     * @param totalSeconds the duration in seconds; must not be negative
     * @return a short description of the duration
     * @throws IllegalArgumentException if {@code totalSeconds} is negative
     */
    public static String formatShort(long totalSeconds) {
        if (totalSeconds < 0) {
            throw new IllegalArgumentException(
                    "A duration cannot be negative, but was: " + totalSeconds + " seconds.");
        }

        long hours = totalSeconds / SECONDS_PER_HOUR;
        long minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;

        if (hours == 0) {
            return minutes + "m";
        }
        return hours + "h " + minutes + "m";
    }

    // ------------------------------------------------------------------------
    // The three methods below split a duration into the parts the stopwatch face
    // shows separately: hours and minutes on the left, seconds and thousandths
    // on the right. They are kept apart rather than returning one string because
    // each part is drawn at a different size and colour.
    // ------------------------------------------------------------------------

    /**
     * Formats the hours-and-minutes part of the stopwatch, for example
     * {@code "02:34"}.
     *
     * <p>Hours are not wrapped at 24, so a very long total still reads correctly.
     *
     * @param totalMillis the duration in milliseconds; must not be negative
     * @return the duration as {@code HH:mm}
     * @throws IllegalArgumentException if {@code totalMillis} is negative
     */
    public static String formatHoursMinutes(long totalMillis) {
        requireNotNegative(totalMillis);

        long hours = totalMillis / MILLIS_PER_HOUR;
        long minutes = (totalMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE;

        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Formats the whole-seconds part of the stopwatch, for example {@code "07"}.
     *
     * <p>This is the seconds <b>within the current minute</b>, so it counts 0 to
     * 59 and then rolls over — which is what makes it line up with the dial's
     * sweeping hand.
     *
     * @param totalMillis the duration in milliseconds; must not be negative
     * @return two digits, zero-padded
     * @throws IllegalArgumentException if {@code totalMillis} is negative
     */
    public static String formatSecondsPart(long totalMillis) {
        requireNotNegative(totalMillis);

        long seconds = (totalMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND;
        return String.format("%02d", seconds);
    }

    /**
     * Formats the thousandths part of the stopwatch, for example {@code "081"}.
     *
     * @param totalMillis the duration in milliseconds; must not be negative
     * @return three digits, zero-padded
     * @throws IllegalArgumentException if {@code totalMillis} is negative
     */
    public static String formatMillisPart(long totalMillis) {
        requireNotNegative(totalMillis);

        return String.format("%03d", totalMillis % MILLIS_PER_SECOND);
    }

    private static void requireNotNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "A duration cannot be negative, but was: " + value);
        }
    }
}
