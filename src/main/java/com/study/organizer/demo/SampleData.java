package com.study.organizer.demo;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.PointsCalculator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Invents a believable study history for the demo application.
 *
 * <p>The point is to make every screen show something interesting: a streak the
 * dashboard can report, several categories so the pie chart has slices, gaps in
 * the calendar so the heat map is not a solid block, and enough range in session
 * length that "longest" and "shortest" differ.
 *
 * <h2>Why the randomness is seeded</h2>
 * {@link Random} is created with a fixed seed, so the generated history is the
 * <b>same every run</b>. When you are adjusting spacing or colours, data that
 * changed on every launch would make it impossible to tell whether a difference
 * came from your edit or from new numbers.
 */
public final class SampleData {

    /** Fixed so the demo looks identical on every launch. */
    private static final long RANDOM_SEED = 20260807L;

    /** How far back the invented history reaches. */
    private static final int DAYS_OF_HISTORY = 75;

    /**
     * The categories used, and how likely each is to be picked.
     *
     * <p>Deliberately uneven: a perfectly even split would make the pie chart
     * a set of identical wedges and "most studied category" meaningless.
     */
    private static final String[] CATEGORIES = {
            "Java", "Java", "Java", "Java",      // the main subject
            "Algorithms", "Algorithms",
            "Databases",
            "English"
    };

    private SampleData() {
        throw new AssertionError("SampleData is a utility class and cannot be instantiated.");
    }

    /**
     * Builds the invented history.
     *
     * @param zone the time zone the sessions are placed in
     * @return sessions spread over the last few months
     */
    public static List<StudySession> generate(ZoneId zone) {
        Random random = new Random(RANDOM_SEED);
        List<StudySession> sessions = new ArrayList<>();

        LocalDate today = LocalDate.now(zone);

        for (int daysAgo = DAYS_OF_HISTORY; daysAgo >= 0; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);

            for (int i = 0; i < sessionsOnDay(day, daysAgo, random); i++) {
                sessions.add(buildSession(day, zone, random));
            }
        }

        return sessions;
    }

    /**
     * Decides how many sessions happened on a given day.
     *
     * <p>Weekdays are busier than weekends, and the most recent week is always
     * given at least one session per day so the dashboard shows a live streak.
     *
     * @param day     the calendar day
     * @param daysAgo how far back that day is
     * @param random  the seeded source of randomness
     * @return how many sessions to invent for that day
     */
    private static int sessionsOnDay(LocalDate day, int daysAgo, Random random) {
        // Guarantee an unbroken run over the last week, so the streak tile and
        // the weekly bar chart always have something to show.
        if (daysAgo <= 6) {
            return 1 + random.nextInt(2);
        }

        boolean weekend = switch (day.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> false;
        };

        // Roughly: a rest day now and then, more of them at weekends.
        int roll = random.nextInt(10);
        if (weekend) {
            return roll < 6 ? 0 : 1;
        }
        return roll < 2 ? 0 : 1 + random.nextInt(2);
    }

    /** Builds one session on the given day, at a plausible hour and length. */
    private static StudySession buildSession(LocalDate day, ZoneId zone, Random random) {
        // Somewhere between 08:00 and 21:00.
        LocalTime startTime = LocalTime.of(8 + random.nextInt(13), random.nextInt(60));

        // Between 20 minutes and 2h20, in five-minute steps, so the statistics
        // screen has a real spread between its longest and shortest session.
        long durationSeconds = (20 + random.nextInt(21) * 5) * 60L;

        var startedAt = day.atTime(startTime).atZone(zone).toInstant();
        String category = CATEGORIES[random.nextInt(CATEGORIES.length)];

        return new StudySession(
                null,
                category,
                startedAt,
                startedAt.plusSeconds(durationSeconds),
                durationSeconds,
                PointsCalculator.fromSeconds(durationSeconds),
                summaryFor(category),
                random.nextBoolean() ? "Went well. Keep going tomorrow." : "");
    }

    /** A plausible-looking summary line, so the data does not read as filler. */
    private static String summaryFor(String category) {
        return switch (category) {
            case "Java" -> "Worked through interfaces, generics and collections.";
            case "Algorithms" -> "Practised sorting and complexity analysis.";
            case "Databases" -> "Studied normalisation and wrote some queries.";
            default -> "Reading practice and new vocabulary.";
        };
    }
}
