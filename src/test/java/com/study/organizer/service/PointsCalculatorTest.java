package com.study.organizer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link PointsCalculator}.
 *
 * <p>The first test locks in the exact worked examples from the project
 * specification, so that if the formula is ever changed by accident the build
 * fails immediately.
 */
class PointsCalculatorTest {

    /**
     * The delta allowed when comparing doubles. Two values are considered equal
     * if they differ by less than this, which sidesteps binary floating-point
     * representation noise.
     */
    private static final double TOLERANCE = 0.0001;

    @DisplayName("matches the worked examples in the specification")
    @ParameterizedTest(name = "{0} seconds -> {1} points")
    @CsvSource({
            "1800,  0.5",    // 30 minutes
            "2700,  0.75",   // 45 minutes
            "5400,  1.5",    // 90 minutes
            "7200,  2.0",    // 120 minutes
            "3600,  1.0",    // exactly one hour
            "0,     0.0"     // a zero-length session
    })
    void convertsSecondsToPoints(long seconds, double expectedPoints) {
        assertEquals(expectedPoints, PointsCalculator.fromSeconds(seconds), TOLERANCE);
    }

    @Test
    @DisplayName("rounds to two decimal places")
    void roundsToTwoDecimals() {
        // 100 seconds is 0.02777... hours, which must round to 0.03.
        assertEquals(0.03, PointsCalculator.fromSeconds(100), TOLERANCE);

        // 90 seconds is 0.025 hours; HALF_UP rounding takes this to 0.03.
        assertEquals(0.03, PointsCalculator.fromSeconds(90), TOLERANCE);
    }

    @Test
    @DisplayName("handles very long sessions without losing precision")
    void handlesLongSessions() {
        // 100 hours.
        assertEquals(100.0, PointsCalculator.fromSeconds(360_000), TOLERANCE);
    }

    @Test
    @DisplayName("rejects a negative duration")
    void rejectsNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> PointsCalculator.fromSeconds(-1));
    }

    @Test
    @DisplayName("toHours agrees with fromSeconds, since 1 hour is 1 point")
    void toHoursMatchesPoints() {
        assertEquals(PointsCalculator.fromSeconds(5400), PointsCalculator.toHours(5400), TOLERANCE);
    }
}
