package com.study.organizer.service;

import com.study.organizer.model.StudySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link StatisticsService}.
 *
 * <p>Both the time zone and "today" are fixed here, so the results do not depend
 * on when or where the test happens to run.
 */
class StatisticsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    /** A Wednesday, chosen so week boundaries are easy to reason about. */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);

    /**
     * Builds a session on the given date.
     *
     * @param date    the day the session started
     * @param minutes how long it lasted
     * @param category the study category
     */
    private static StudySession session(LocalDate date, int minutes, String category) {
        Instant start = date.atTime(LocalTime.NOON).atZone(ZONE).toInstant();
        long seconds = minutes * 60L;
        return new StudySession(
                "id-" + date + "-" + minutes,
                category,
                start,
                start.plusSeconds(seconds),
                seconds,
                PointsCalculator.fromSeconds(seconds),
                "summary",
                "");
    }

    private static StatisticsService statsFor(List<StudySession> sessions) {
        return new StatisticsService(sessions, ZONE, TODAY);
    }

    @Test
    @DisplayName("an empty history produces zeros rather than errors")
    void handlesEmptyHistory() {
        StatisticsService stats = statsFor(List.of());

        assertEquals(0, stats.getTotalSessions());
        assertEquals(0, stats.getTotalSeconds());
        assertEquals(0, stats.getTodaySeconds());
        assertEquals(0, stats.getCurrentStreak());
        assertEquals(0, stats.getAverageSessionSeconds());
        assertEquals(0, stats.getAverageDailySeconds());
        assertTrue(stats.getLastSession().isEmpty());
        assertTrue(stats.getMostStudiedCategory().isEmpty());
        assertTrue(stats.getMostProductiveWeekday().isEmpty());
    }

    @Test
    @DisplayName("adds up multiple sessions on the same day")
    void sumsTodaysSessions() {
        StatisticsService stats = statsFor(List.of(
                session(TODAY, 30, "Java"),
                session(TODAY, 60, "Java")));

        assertEquals(90 * 60, stats.getTodaySeconds());
        assertEquals(1.5, stats.getTodayPoints(), 0.0001);
    }

    @Test
    @DisplayName("the week runs Monday to Sunday")
    void weekStartsOnMonday() {
        LocalDate monday = TODAY.with(DayOfWeek.MONDAY);          // 2026-08-03
        LocalDate previousSunday = monday.minusDays(1);           // 2026-08-02

        StatisticsService stats = statsFor(List.of(
                session(monday, 60, "Java"),
                session(TODAY, 60, "Java"),
                session(previousSunday, 60, "Java")));   // last week, must be excluded

        assertEquals(120 * 60, stats.getWeekSeconds());
    }

    @Test
    @DisplayName("the month total ignores sessions from the previous month")
    void monthExcludesPreviousMonth() {
        StatisticsService stats = statsFor(List.of(
                session(LocalDate.of(2026, 8, 1), 60, "Java"),
                session(LocalDate.of(2026, 7, 31), 60, "Java")));

        assertEquals(60 * 60, stats.getMonthSeconds());
    }

    @Test
    @DisplayName("counts consecutive days, ending today")
    void countsStreakEndingToday() {
        StatisticsService stats = statsFor(List.of(
                session(TODAY, 30, "Java"),
                session(TODAY.minusDays(1), 30, "Java"),
                session(TODAY.minusDays(2), 30, "Java")));

        assertEquals(3, stats.getCurrentStreak());
    }

    @Test
    @DisplayName("keeps the streak alive before you have studied today")
    void streakSurvivesUntouchedToday() {
        // Nothing studied today yet, but yesterday and the day before were.
        StatisticsService stats = statsFor(List.of(
                session(TODAY.minusDays(1), 30, "Java"),
                session(TODAY.minusDays(2), 30, "Java")));

        assertEquals(2, stats.getCurrentStreak());
    }

    @Test
    @DisplayName("a missed day breaks the streak")
    void gapBreaksStreak() {
        StatisticsService stats = statsFor(List.of(
                session(TODAY, 30, "Java"),
                // no session yesterday
                session(TODAY.minusDays(2), 30, "Java"),
                session(TODAY.minusDays(3), 30, "Java")));

        assertEquals(1, stats.getCurrentStreak());
    }

    @Test
    @DisplayName("the streak is zero once two days have been missed")
    void staleStreakIsZero() {
        StatisticsService stats = statsFor(List.of(
                session(TODAY.minusDays(2), 30, "Java")));

        assertEquals(0, stats.getCurrentStreak());
    }

    @Test
    @DisplayName("ranks categories by total time, largest first")
    void ranksCategories() {
        StatisticsService stats = statsFor(List.of(
                session(TODAY, 30, "Math"),
                session(TODAY, 120, "Java"),
                session(TODAY, 60, "English")));

        assertEquals("Java", stats.getMostStudiedCategory().orElseThrow());
        assertEquals(List.of("Java", "English", "Math"),
                new ArrayList<>(stats.getSecondsByCategory().keySet()));
    }

    @Test
    @DisplayName("the weekly chart always has seven days, zeros included")
    void weeklyChartCoversWholeWeek() {
        Map<DayOfWeek, Long> week = statsFor(List.of(session(TODAY, 60, "Java")))
                .getCurrentWeekByDay();

        assertEquals(7, week.size());
        assertEquals(60 * 60, week.get(DayOfWeek.WEDNESDAY));
        assertEquals(0, week.get(DayOfWeek.MONDAY));
        assertEquals(0, week.get(DayOfWeek.SUNDAY));
    }

    @Test
    @DisplayName("averages daily time over every day since the first session")
    void averagesOverCalendarDaysNotActiveDays() {
        // Two hours total, spread over a four-day span (today and three days back).
        StatisticsService stats = statsFor(List.of(
                session(TODAY, 60, "Java"),
                session(TODAY.minusDays(3), 60, "Java")));

        // 7200 seconds / 4 days = 1800 seconds per day.
        assertEquals(1800, stats.getAverageDailySeconds());
    }

    @Test
    @DisplayName("reports the longest and shortest sessions")
    void findsLongestAndShortest() {
        StatisticsService stats = statsFor(List.of(
                session(TODAY, 30, "Java"),
                session(TODAY, 120, "Java"),
                session(TODAY, 45, "Java")));

        assertEquals(120 * 60, stats.getLongestSession().orElseThrow().getDurationSeconds());
        assertEquals(30 * 60, stats.getShortestSession().orElseThrow().getDurationSeconds());
        assertEquals(65 * 60, stats.getAverageSessionSeconds());
    }

    @Test
    @DisplayName("reports the most and least productive weekdays, including never-studied days")
    void findsProductiveWeekdays() {
        StatisticsService stats = statsFor(List.of(
                session(TODAY, 120, "Java"),                 // Wednesday
                session(TODAY.minusDays(2), 30, "Java")));   // Monday

        assertEquals(DayOfWeek.WEDNESDAY, stats.getMostProductiveWeekday().orElseThrow());
        // Several weekdays are tied at zero; the least productive must be one of them.
        DayOfWeek least = stats.getLeastProductiveWeekday().orElseThrow();
        assertEquals(0L, stats.getSecondsByWeekday().get(least));
    }
}
