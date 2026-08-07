package com.study.organizer.ui;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.PointsCalculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the ready-made orderings offered by the filter grid.
 *
 * <p>These are plain comparators with no UI involved, so they can be checked
 * directly and without a display. The filtering itself needs the controls, so it
 * is exercised from {@link UserInterfaceSmokeTest} instead.
 */
class SessionFilterPaneTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    private static StudySession session(int daysAgo, long seconds, String category) {
        Instant startedAt = LocalDate.of(2026, 8, 7).minusDays(daysAgo)
                .atTime(LocalTime.NOON).atZone(ZONE).toInstant();

        return new StudySession(
                category + "/" + daysAgo + ".md", category, startedAt,
                startedAt.plusSeconds(seconds), seconds,
                PointsCalculator.fromSeconds(seconds), "summary", "", List.of());
    }

    /** Three sessions that differ in every dimension the sort offers. */
    private static List<StudySession> sample() {
        return new ArrayList<>(List.of(
                session(0, 1800, "Math"),        // newest, shortest, fewest points
                session(5, 7200, "Algorithms"),  // oldest, longest, most points
                session(2, 3600, "Java")));
    }

    private static List<String> orderedBy(SessionFilterPane.SortOrder order) {
        List<StudySession> sessions = sample();
        sessions.sort(order.comparator());
        return sessions.stream().map(StudySession::getCategory).toList();
    }

    @Test
    @DisplayName("newest first is the default reading order")
    void sortsNewestFirst() {
        assertEquals(List.of("Math", "Java", "Algorithms"),
                orderedBy(SessionFilterPane.SortOrder.NEWEST));
    }

    @Test
    @DisplayName("oldest first reverses it")
    void sortsOldestFirst() {
        assertEquals(List.of("Algorithms", "Java", "Math"),
                orderedBy(SessionFilterPane.SortOrder.OLDEST));
    }

    @Test
    @DisplayName("longest sessions rank the real study blocks to the top")
    void sortsByLongest() {
        assertEquals(List.of("Algorithms", "Java", "Math"),
                orderedBy(SessionFilterPane.SortOrder.LONGEST));
    }

    @Test
    @DisplayName("shortest sessions surface the ones that were barely worth counting")
    void sortsByShortest() {
        assertEquals(List.of("Math", "Java", "Algorithms"),
                orderedBy(SessionFilterPane.SortOrder.SHORTEST));
    }

    @Test
    @DisplayName("most points ranks by score")
    void sortsByPoints() {
        assertEquals(List.of("Algorithms", "Java", "Math"),
                orderedBy(SessionFilterPane.SortOrder.MOST_POINTS));
    }

    @Test
    @DisplayName("by category groups alphabetically, newest first inside each group")
    void sortsByCategory() {
        List<StudySession> sessions = sample();
        // A second Java session, older than the first, to check the tie-break.
        sessions.add(session(9, 3600, "Java"));
        sessions.sort(SessionFilterPane.SortOrder.BY_CATEGORY.comparator());

        assertEquals(List.of("Algorithms", "Java", "Java", "Math"),
                sessions.stream().map(StudySession::getCategory).toList());

        // Within Java, the newer session comes first.
        assertEquals("Java/2.md", sessions.get(1).getId());
        assertEquals("Java/9.md", sessions.get(2).getId());
    }

    @Test
    @DisplayName("every sort order has a label for the drop-down")
    void everyOrderIsLabelled() {
        for (SessionFilterPane.SortOrder order : SessionFilterPane.SortOrder.values()) {
            String label = order.toString();
            assertEquals(label.strip(), label, order + " has a padded label.");
            org.junit.jupiter.api.Assertions.assertFalse(label.isBlank(),
                    order + " has no label to show in the drop-down.");
        }
    }
}
