package com.study.organizer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for {@link DurationFormatter}. */
class DurationFormatterTest {

    @DisplayName("formats durations as zero-padded HH:mm:ss")
    @ParameterizedTest(name = "{0} seconds -> {1}")
    @CsvSource({
            "0,      00:00:00",
            "1,      00:00:01",
            "59,     00:00:59",
            "60,     00:01:00",
            "3599,   00:59:59",
            "3600,   01:00:00",
            "9258,   02:34:18",   // the example given in the specification
            "86400,  24:00:00"    // hours are not wrapped at 24
    })
    void formatsClockString(long seconds, String expected) {
        assertEquals(expected, DurationFormatter.format(seconds));
    }

    @Test
    @DisplayName("lets the hours field grow past two digits for lifetime totals")
    void allowsLargeHourValues() {
        // 100 hours exactly.
        assertEquals("100:00:00", DurationFormatter.format(360_000));
    }

    @DisplayName("formats a compact summary without seconds")
    @ParameterizedTest(name = "{0} seconds -> {1}")
    @CsvSource({
            "0,     0m",
            "59,    0m",
            "60,    1m",
            "3599,  59m",
            "3600,  1h 0m",
            "9258,  2h 34m"
    })
    void formatsShortString(long seconds, String expected) {
        assertEquals(expected, DurationFormatter.formatShort(seconds));
    }

    @Test
    @DisplayName("rejects a negative duration")
    void rejectsNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> DurationFormatter.format(-1));
        assertThrows(IllegalArgumentException.class, () -> DurationFormatter.formatShort(-1));
        assertThrows(IllegalArgumentException.class, () -> DurationFormatter.formatHoursMinutes(-1));
        assertThrows(IllegalArgumentException.class, () -> DurationFormatter.formatSecondsPart(-1));
        assertThrows(IllegalArgumentException.class, () -> DurationFormatter.formatMillisPart(-1));
    }

    // ------------------------------------------------------------------------
    // The stopwatch face splits a duration into three parts. These tests pin
    // that split, including the rollover behaviour the dial depends on.
    // ------------------------------------------------------------------------

    @DisplayName("splits the hours and minutes for the stopwatch face")
    @ParameterizedTest(name = "{0} ms -> {1}")
    @CsvSource({
            "0,          00:00",
            "59999,      00:00",   // still under one minute
            "60000,      00:01",
            "3599999,    00:59",
            "3600000,    01:00",
            "9258081,    02:34",   // the 02:34:18.081 example
            "360000000,  100:00"   // hours are not wrapped at 24
    })
    void splitsHoursAndMinutes(long millis, String expected) {
        assertEquals(expected, DurationFormatter.formatHoursMinutes(millis));
    }

    @DisplayName("shows seconds within the current minute, rolling over at 60")
    @ParameterizedTest(name = "{0} ms -> {1}")
    @CsvSource({
            "0,       00",
            "999,     00",
            "1000,    01",
            "7081,    07",   // the "07.081" reading from the reference
            "59999,   59",
            "60000,   00",   // rolls over rather than continuing to 60
            "61000,   01",
            "9258081, 18"    // 02:34:18.081
    })
    void showsSecondsWithinTheMinute(long millis, String expected) {
        assertEquals(expected, DurationFormatter.formatSecondsPart(millis));
    }

    @DisplayName("shows thousandths, always three digits")
    @ParameterizedTest(name = "{0} ms -> {1}")
    @CsvSource({
            "0,       000",
            "7,       007",
            "81,      081",
            "7081,    081",
            "999,     999",
            "1000,    000",
            "9258081, 081"
    })
    void showsThousandths(long millis, String expected) {
        assertEquals(expected, DurationFormatter.formatMillisPart(millis));
    }

    @Test
    @DisplayName("the three parts reassemble into the original duration")
    void partsAgreeWithEachOther() {
        // 02:34:18.081 - the parts must describe the same instant the single
        // HH:mm:ss formatter reports, or the face would contradict itself.
        long millis = 9_258_081L;

        assertEquals("02:34", DurationFormatter.formatHoursMinutes(millis));
        assertEquals("18", DurationFormatter.formatSecondsPart(millis));
        assertEquals("081", DurationFormatter.formatMillisPart(millis));
        assertEquals("02:34:18", DurationFormatter.format(millis / 1000));
    }
}
