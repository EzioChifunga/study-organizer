package com.study.organizer.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * One completed study session — an uninterrupted period of work, minus any time
 * the user spent paused.
 *
 * <p>Instances are <b>immutable</b>: every field is {@code final} and there are
 * no setters. Once a session is finished its facts cannot change, and immutable
 * objects are safe to hand to background threads (notes are written off the
 * UI thread) without any locking.
 *
 * <p>Times are stored as {@link Instant}, which is an exact point on the global
 * timeline with no time zone attached. The time zone is applied only when we
 * need to ask a calendar question, such as "which day did this belong to?" —
 * see {@link #getStudyDate(ZoneId)}.
 *
 * <h2>How this maps onto the vault</h2>
 * Every session is one Markdown note inside its category's folder. The
 * {@link #getId() id} is that note's path relative to the vault root, which is
 * why it doubles as the record's identity — there is no separate database
 * assigning numbers.
 */
public final class StudySession {

    private final String id;
    private final String category;
    private final Instant startedAt;
    private final Instant endedAt;
    private final long durationSeconds;
    private final double points;
    private final String summary;
    private final String observations;
    private final List<String> references;

    /**
     * Creates a study session with no references to other notes.
     *
     * @param id              the note path relative to the vault root, or {@code null}
     *                        for a session that has not been saved yet
     * @param category        what was studied, e.g. "Java"; must not be blank
     * @param startedAt       when the session began
     * @param endedAt         when the session ended
     * @param durationSeconds the studied time in seconds, <b>excluding</b> paused time
     * @param points          the score, always produced by
     *                        {@link com.study.organizer.service.PointsCalculator}
     * @param summary         the user's written study summary; must not be blank
     * @param observations    optional free-text notes; may be {@code null}
     */
    public StudySession(String id,
                        String category,
                        Instant startedAt,
                        Instant endedAt,
                        long durationSeconds,
                        double points,
                        String summary,
                        String observations) {
        this(id, category, startedAt, endedAt, durationSeconds, points,
                summary, observations, List.of());
    }

    /**
     * Creates a study session.
     *
     * @param id              the note path relative to the vault root, or {@code null}
     *                        for a session that has not been saved yet
     * @param category        what was studied, e.g. "Java"; must not be blank
     * @param startedAt       when the session began
     * @param endedAt         when the session ended
     * @param durationSeconds the studied time in seconds, <b>excluding</b> paused time
     * @param points          the score, always produced by
     *                        {@link com.study.organizer.service.PointsCalculator}
     * @param summary         the user's written study summary; must not be blank
     * @param observations    optional free-text notes; may be {@code null}
     * @param references      titles of other notes in the <b>same</b> category folder
     *                        that this session links to; may be {@code null}
     */
    public StudySession(String id,
                        String category,
                        Instant startedAt,
                        Instant endedAt,
                        long durationSeconds,
                        double points,
                        String summary,
                        String observations,
                        List<String> references) {

        this.category = requireNotBlank(category, "category");
        this.summary = requireNotBlank(summary, "summary");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.endedAt = Objects.requireNonNull(endedAt, "endedAt must not be null");

        if (durationSeconds < 0) {
            throw new IllegalArgumentException(
                    "durationSeconds cannot be negative, but was: " + durationSeconds);
        }

        this.id = id;
        this.durationSeconds = durationSeconds;
        this.points = points;
        // Observations are optional. Normalising null to an empty string here means
        // no other class ever has to null-check this field.
        this.observations = observations == null ? "" : observations;

        // References are copied into an immutable list, so a caller cannot alter
        // this session's links after handing them over.
        this.references = references == null ? List.of() : List.copyOf(references);
    }

    /**
     * Returns the calendar day this session counts towards, in the given zone.
     *
     * <p>The <b>start</b> time decides the day. A session that begins at 23:30
     * and ends at 00:15 belongs entirely to the day it started on, which is how
     * a person naturally thinks about it and keeps daily totals and streaks
     * consistent with each other.
     *
     * @param zone the time zone to interpret the timestamp in
     * @return the local date this session is counted on
     */
    public LocalDate getStudyDate(ZoneId zone) {
        return startedAt.atZone(zone).toLocalDate();
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public double getPoints() {
        return points;
    }

    public String getSummary() {
        return summary;
    }

    public String getObservations() {
        return observations;
    }

    /**
     * The titles of other notes this session links to.
     *
     * <p>All of them live in the same category folder — linking across folders
     * is not allowed, which is what keeps each category self-contained.
     *
     * @return the referenced note titles; never {@code null}, possibly empty
     */
    public List<String> getReferences() {
        return references;
    }

    /**
     * Returns a copy of this session carrying the given id.
     *
     * <p>Needed because the id — the note's path in the vault — only exists once
     * the file has been written, and the object itself is immutable.
     *
     * @param newId the note path relative to the vault root
     * @return a new session identical to this one but with the id set
     */
    public StudySession withId(String newId) {
        return new StudySession(newId, category, startedAt, endedAt,
                durationSeconds, points, summary, observations, references);
    }

    @Override
    public String toString() {
        return "StudySession{category='" + category + "'"
                + ", startedAt=" + startedAt
                + ", durationSeconds=" + durationSeconds
                + ", points=" + points + "}";
    }
}
