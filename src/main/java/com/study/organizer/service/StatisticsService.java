package com.study.organizer.service;

import com.study.organizer.model.StudySession;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Computes every number shown on the dashboard, the charts and the statistics
 * screen from a list of finished sessions.
 *
 * <p>An instance is a <b>snapshot</b>: it is built from the sessions that were
 * loaded at that moment and never changes afterwards. When new data arrives the
 * application simply builds a new {@code StatisticsService}. That removes any
 * question of stale or half-updated totals, and it makes the class easy to test
 * — hand it a fixed list, check the numbers.
 *
 * <p>All calendar questions ("which day?", "which week?") are answered in an
 * explicit {@link ZoneId} rather than silently using the machine default, so the
 * tests can pin a zone and get repeatable answers.
 *
 * <h2>Definitions used here</h2>
 * <ul>
 *   <li><b>Week</b> runs Monday to Sunday.</li>
 *   <li><b>Month</b> is the calendar month.</li>
 *   <li><b>Streak</b> is the run of consecutive days with at least one session,
 *       counting backwards. It is measured from today if you have already
 *       studied today, otherwise from yesterday — so an untouched streak does
 *       not read as zero every morning before you start.</li>
 * </ul>
 */
public class StatisticsService {

    private final List<StudySession> sessions;
    private final ZoneId zone;
    private final LocalDate today;

    /**
     * Total studied seconds per calendar day.
     *
     * <p>Declared as a {@link TreeMap} rather than a plain {@link Map} because
     * the sorted-map operations are used directly: dates come out in order for
     * the charts, and {@code firstKey()} gives the very first study day.
     */
    private final TreeMap<LocalDate, Long> secondsByDate;

    /**
     * Builds a snapshot of the statistics.
     *
     * @param sessions all finished sessions; must not be {@code null}
     * @param zone     the time zone used to decide which day a session falls on
     * @param today    the date treated as "today"; injectable so tests are stable
     */
    public StatisticsService(List<StudySession> sessions, ZoneId zone, LocalDate today) {
        this.sessions = List.copyOf(sessions);
        this.zone = zone;
        this.today = today;
        this.secondsByDate = buildSecondsByDate();
    }

    /**
     * Builds a snapshot using the system time zone and the real current date.
     *
     * @param sessions all finished sessions
     */
    public StatisticsService(List<StudySession> sessions) {
        this(sessions, ZoneId.systemDefault(), LocalDate.now(ZoneId.systemDefault()));
    }

    // ---------------------------------------------------------------- dashboard

    /** @return seconds studied today */
    public long getTodaySeconds() {
        return secondsByDate.getOrDefault(today, 0L);
    }

    /** @return points earned today */
    public double getTodayPoints() {
        return PointsCalculator.fromSeconds(getTodaySeconds());
    }

    /** @return seconds studied since Monday of the current week */
    public long getWeekSeconds() {
        return sumBetween(startOfWeek(), today);
    }

    /** @return points earned since Monday of the current week */
    public double getWeekPoints() {
        return PointsCalculator.fromSeconds(getWeekSeconds());
    }

    /** @return seconds studied since the first day of the current month */
    public long getMonthSeconds() {
        return sumBetween(today.withDayOfMonth(1), today);
    }

    /** @return points earned since the first day of the current month */
    public double getMonthPoints() {
        return PointsCalculator.fromSeconds(getMonthSeconds());
    }

    /**
     * Counts the current run of consecutive study days.
     *
     * <p>The walk starts at today if today has a session, and at yesterday
     * otherwise. Without that allowance the streak would drop to zero every
     * midnight and only come back once you had studied, which is not what a
     * streak is meant to communicate.
     *
     * @return the number of consecutive days studied, or 0 if the streak is broken
     */
    public int getCurrentStreak() {
        LocalDate cursor = secondsByDate.containsKey(today) ? today : today.minusDays(1);

        int streak = 0;
        while (secondsByDate.containsKey(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** @return the most recent session, if there is one */
    public Optional<StudySession> getLastSession() {
        return sessions.stream().max(Comparator.comparing(StudySession::getStartedAt));
    }

    /** @return the category with the most total time, if any sessions exist */
    public Optional<String> getMostStudiedCategory() {
        return getSecondsByCategory().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    // ------------------------------------------------------------------- charts

    /**
     * Total seconds per category across all history, largest first.
     *
     * @return an ordered map of category name to seconds studied
     */
    public Map<String, Long> getSecondsByCategory() {
        Map<String, Long> totals = new HashMap<>();
        for (StudySession session : sessions) {
            totals.merge(session.getCategory(), session.getDurationSeconds(), Long::sum);
        }

        // Re-insert in descending order so the pie chart and the legend agree.
        Map<String, Long> ordered = new LinkedHashMap<>();
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    /**
     * Seconds studied on each day of the current week, Monday through Sunday.
     *
     * <p>Days with no study are present with a value of zero, so the bar chart
     * always shows seven bars and the week is easy to read at a glance.
     *
     * @return an ordered map from weekday to seconds
     */
    public Map<DayOfWeek, Long> getCurrentWeekByDay() {
        Map<DayOfWeek, Long> week = new EnumMap<>(DayOfWeek.class);
        LocalDate monday = startOfWeek();

        for (int offset = 0; offset < 7; offset++) {
            LocalDate day = monday.plusDays(offset);
            week.put(day.getDayOfWeek(), secondsByDate.getOrDefault(day, 0L));
        }
        return week;
    }

    /**
     * Seconds studied on each day of the current month so far.
     *
     * @return an ordered map from date to seconds, including zero-study days
     */
    public Map<LocalDate, Long> getCurrentMonthByDay() {
        Map<LocalDate, Long> month = new LinkedHashMap<>();
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        for (LocalDate day = firstOfMonth; !day.isAfter(today); day = day.plusDays(1)) {
            month.put(day, secondsByDate.getOrDefault(day, 0L));
        }
        return month;
    }

    /**
     * Daily totals over a recent window, used by the heat map.
     *
     * @param weeks how many weeks back to include
     * @return an ordered map from date to seconds, covering whole Monday-to-Sunday weeks
     */
    public Map<LocalDate, Long> getHeatMapData(int weeks) {
        Map<LocalDate, Long> data = new LinkedHashMap<>();

        // Start on the Monday of the earliest week so the grid columns line up.
        LocalDate start = startOfWeek().minusWeeks(weeks - 1L);
        LocalDate end = startOfWeek().plusDays(6);

        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            data.put(day, secondsByDate.getOrDefault(day, 0L));
        }
        return data;
    }

    // --------------------------------------------------------------- statistics

    /** @return total seconds studied across all sessions */
    public long getTotalSeconds() {
        return sessions.stream().mapToLong(StudySession::getDurationSeconds).sum();
    }

    /** @return total points earned across all sessions */
    public double getTotalPoints() {
        return PointsCalculator.fromSeconds(getTotalSeconds());
    }

    /** @return how many sessions have been recorded */
    public int getTotalSessions() {
        return sessions.size();
    }

    /** @return the mean session length in seconds, or 0 when there are no sessions */
    public long getAverageSessionSeconds() {
        if (sessions.isEmpty()) {
            return 0;
        }
        return getTotalSeconds() / sessions.size();
    }

    /** @return the longest session, if any */
    public Optional<StudySession> getLongestSession() {
        return sessions.stream().max(Comparator.comparingLong(StudySession::getDurationSeconds));
    }

    /** @return the shortest session, if any */
    public Optional<StudySession> getShortestSession() {
        return sessions.stream().min(Comparator.comparingLong(StudySession::getDurationSeconds));
    }

    /**
     * Average study time per day.
     *
     * <p>The divisor is the number of calendar days from the first ever session
     * up to today — <b>including days with no study</b>. Averaging only over
     * active days would flatter the number and would not answer the question
     * people actually mean by "how much do I study per day".
     *
     * @return mean seconds per day since studying began, or 0 with no sessions
     */
    public long getAverageDailySeconds() {
        return getTotalSeconds() / Math.max(1, countDaysSinceFirstSession());
    }

    /** @return mean seconds per week since studying began */
    public long getAverageWeeklySeconds() {
        long days = Math.max(1, countDaysSinceFirstSession());
        // Multiply before dividing to avoid losing precision to integer division.
        return getTotalSeconds() * 7 / days;
    }

    /** @return mean seconds per month (30-day month) since studying began */
    public long getAverageMonthlySeconds() {
        long days = Math.max(1, countDaysSinceFirstSession());
        return getTotalSeconds() * 30 / days;
    }

    /**
     * Total seconds studied on each weekday across the whole history.
     *
     * <p>All seven weekdays are always present, so a day never studied shows as
     * zero rather than being silently missing.
     *
     * @return an ordered map from weekday to total seconds
     */
    public Map<DayOfWeek, Long> getSecondsByWeekday() {
        Map<DayOfWeek, Long> totals = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            totals.put(day, 0L);
        }
        for (StudySession session : sessions) {
            DayOfWeek day = session.getStudyDate(zone).getDayOfWeek();
            totals.merge(day, session.getDurationSeconds(), Long::sum);
        }
        return totals;
    }

    /** @return the weekday with the most total study time, if any sessions exist */
    public Optional<DayOfWeek> getMostProductiveWeekday() {
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        return getSecondsByWeekday().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    /** @return the weekday with the least total study time, if any sessions exist */
    public Optional<DayOfWeek> getLeastProductiveWeekday() {
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        return getSecondsByWeekday().entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    // ------------------------------------------------------------------ helpers

    /** Groups every session's duration by the calendar day it started on. */
    private TreeMap<LocalDate, Long> buildSecondsByDate() {
        TreeMap<LocalDate, Long> totals = new TreeMap<>();
        for (StudySession session : sessions) {
            totals.merge(session.getStudyDate(zone), session.getDurationSeconds(), Long::sum);
        }
        return totals;
    }

    /** @return the Monday of the week containing {@code today} */
    private LocalDate startOfWeek() {
        return today.with(DayOfWeek.MONDAY);
    }

    /** Sums the daily totals between two dates, both inclusive. */
    private long sumBetween(LocalDate from, LocalDate to) {
        long total = 0;
        for (Map.Entry<LocalDate, Long> entry : secondsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            if (!date.isBefore(from) && !date.isAfter(to)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    /** @return days elapsed from the first ever session up to today, inclusive */
    private long countDaysSinceFirstSession() {
        if (secondsByDate.isEmpty()) {
            return 0;
        }
        return ChronoUnit.DAYS.between(secondsByDate.firstKey(), today) + 1;
    }
}
