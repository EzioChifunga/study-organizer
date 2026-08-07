package com.study.organizer.service;

import com.study.organizer.model.SessionState;
import com.study.organizer.model.StudySession;
import com.study.organizer.util.DurationFormatter;

import javafx.animation.AnimationTimer;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.time.Instant;

/**
 * Runs the stopwatch and owns the whole session lifecycle.
 *
 * <p>This class contains <b>no UI code</b>. It exposes its state through JavaFX
 * properties that a view can bind to, which keeps the rules of the timer in one
 * testable place and lets the screen be rebuilt without touching them.
 *
 * <h2>Why elapsed time is not counted by ticks</h2>
 * A tempting implementation is to run a repeating timer and do
 * {@code elapsed++} on every tick. That is wrong: ticks are not guaranteed to
 * arrive on schedule, and if the machine is busy or goes to sleep they are
 * skipped entirely. The error accumulates, so a long session would drift
 * noticeably.
 *
 * <p>Instead the elapsed time is always <i>derived</i> from the clock:
 *
 * <pre>
 *   elapsed = accumulatedMillis + (now - lastResumedAt)
 * </pre>
 *
 * where {@code accumulatedMillis} is the time banked before the most recent
 * pause. The {@link AnimationTimer} exists only to <i>re-read</i> that value so
 * the display refreshes; a late or dropped frame makes the display update
 * slightly late but can never make the number wrong.
 *
 * <h2>Why an AnimationTimer rather than a one-second Timeline</h2>
 * The stopwatch face shows thousandths and has a hand that sweeps smoothly, so
 * refreshing once a second is not enough. {@link AnimationTimer} fires once per
 * screen refresh — around sixty times a second — which is exactly the rate the
 * display can actually show. It runs only while a session is running, so an idle
 * timer costs nothing.
 *
 * <h2>Only one session at a time</h2>
 * A single {@code TimerService} instance is shared by the whole application, and
 * {@link #start(String)} refuses to run unless the state is {@link SessionState#IDLE}.
 * Together those two facts guarantee the specification's rule that only one
 * session can be running at any moment.
 */
public class TimerService {

    /** The current lifecycle state. The UI binds button availability to this. */
    private final ReadOnlyObjectWrapper<SessionState> state =
            new ReadOnlyObjectWrapper<>(SessionState.IDLE);

    /**
     * Elapsed studied milliseconds, excluding time spent paused.
     *
     * <p>This is the value the stopwatch face is driven from: the dial listens
     * to it and repaints, and the readouts derive their text from it.
     */
    private final ReadOnlyLongWrapper elapsedMillis = new ReadOnlyLongWrapper(0);

    /** Elapsed studied seconds, excluding time spent paused. */
    private final ReadOnlyLongWrapper elapsedSeconds = new ReadOnlyLongWrapper(0);

    /** The elapsed time pre-formatted as {@code HH:mm:ss}, ready to display. */
    private final ReadOnlyStringWrapper elapsedDisplay = new ReadOnlyStringWrapper("00:00:00");

    /** The category of the session in progress, or {@code null} when idle. */
    private final ReadOnlyObjectWrapper<String> currentCategory = new ReadOnlyObjectWrapper<>(null);

    /** Drives the per-frame refresh of the display properties. */
    private final AnimationTimer ticker;

    /** When the session originally began. Kept for the saved record. */
    private Instant startedAt;

    /** When the clock was last started or resumed. {@code null} while paused. */
    private Instant lastResumedAt;

    /** Studied milliseconds banked before the most recent pause. */
    private long accumulatedMillis;

    /** Creates an idle timer. */
    public TimerService() {
        this.ticker = new AnimationTimer() {
            @Override
            public void handle(long now) {
                refresh();
            }
        };
    }

    /**
     * Starts a new session in the given category.
     *
     * @param category what is being studied; must not be blank
     * @throws IllegalStateException    if a session is already running or paused
     * @throws IllegalArgumentException if the category is null or blank
     */
    public void start(String category) {
        if (state.get() != SessionState.IDLE) {
            throw new IllegalStateException(
                    "Cannot start a new session because one is already " + state.get()
                            + ". Finish or cancel it first.");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Please choose or type a category before starting.");
        }

        this.startedAt = Instant.now();
        this.lastResumedAt = this.startedAt;
        this.accumulatedMillis = 0;

        currentCategory.set(category.trim());
        state.set(SessionState.RUNNING);

        refresh();
        ticker.start();
    }

    /**
     * Pauses the clock. Time spent paused is not counted towards the session.
     *
     * @throws IllegalStateException if no session is currently running
     */
    public void pause() {
        if (state.get() != SessionState.RUNNING) {
            throw new IllegalStateException("Only a running session can be paused.");
        }

        // Bank the time studied since the last resume, then stop measuring.
        accumulatedMillis = computeElapsedMillis();
        lastResumedAt = null;

        ticker.stop();
        state.set(SessionState.PAUSED);
        refresh();
    }

    /**
     * Resumes a paused session from exactly where it stopped.
     *
     * @throws IllegalStateException if the session is not paused
     */
    public void resume() {
        if (state.get() != SessionState.PAUSED) {
            throw new IllegalStateException("Only a paused session can be resumed.");
        }

        lastResumedAt = Instant.now();
        state.set(SessionState.RUNNING);

        refresh();
        ticker.start();
    }

    /**
     * Stops the clock and builds the finished session record.
     *
     * <p>This does <b>not</b> save anything and does not reset the timer. The
     * caller is expected to collect the summary from the user and persist the
     * result, then call {@link #reset()}. Keeping those steps separate is what
     * lets the application honour the rule that a session only becomes complete
     * once its summary has been saved — if the user closes the summary dialog,
     * nothing has been lost and the timer is still holding the session.
     *
     * @param summary      the user's study summary; must not be blank
     * @param observations optional notes; may be {@code null}
     * @return the completed session, with duration and points already calculated
     * @throws IllegalStateException    if there is no session to finish
     * @throws IllegalArgumentException if the summary is blank
     */
    public StudySession finish(String summary, String observations) {
        if (state.get() != SessionState.RUNNING && state.get() != SessionState.PAUSED) {
            throw new IllegalStateException("There is no active session to finish.");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("A study summary is required to finish a session.");
        }

        long durationMillis = computeElapsedMillis();

        // Freeze the clock so the display stops moving while the summary is saved.
        ticker.stop();
        accumulatedMillis = durationMillis;
        lastResumedAt = null;
        refresh();

        // The stored record is in whole seconds - millisecond precision matters
        // for a smooth display, but not for a study log.
        long durationSeconds = durationMillis / 1000;

        // Points are always derived, never typed by the user.
        double points = PointsCalculator.fromSeconds(durationSeconds);

        return new StudySession(
                null,                   // the id is the note path, assigned on save
                currentCategory.get(),
                startedAt,
                Instant.now(),
                durationSeconds,
                points,
                summary,
                observations);
    }

    /**
     * Discards the session in progress entirely. Nothing is saved.
     *
     * @throws IllegalStateException if there is no session to cancel
     */
    public void cancel() {
        if (state.get() == SessionState.IDLE) {
            throw new IllegalStateException("There is no active session to cancel.");
        }
        reset();
    }

    /**
     * Returns the timer to its idle state, ready for the next session.
     *
     * <p>Call this after a finished session has been saved successfully.
     */
    public void reset() {
        ticker.stop();

        startedAt = null;
        lastResumedAt = null;
        accumulatedMillis = 0;

        currentCategory.set(null);
        state.set(SessionState.IDLE);
        refresh();
    }

    /**
     * Computes the true elapsed studied milliseconds from the system clock.
     *
     * @return banked milliseconds, plus the time since the last resume if running
     */
    private long computeElapsedMillis() {
        long elapsed = accumulatedMillis;
        if (lastResumedAt != null) {
            elapsed += java.time.Duration.between(lastResumedAt, Instant.now()).toMillis();
        }
        return elapsed;
    }

    /**
     * Re-reads the elapsed time and pushes it into the observable properties.
     *
     * <p>Called once per screen refresh, so the two derived values are only
     * written when they actually change. Setting a property to a value it
     * already holds would notify every listener sixty times a second for
     * nothing — and the {@code HH:mm:ss} string only changes once a second.
     */
    private void refresh() {
        long millis = computeElapsedMillis();
        elapsedMillis.set(millis);

        long seconds = millis / 1000;
        if (elapsedSeconds.get() != seconds) {
            elapsedSeconds.set(seconds);
            elapsedDisplay.set(DurationFormatter.format(seconds));
        }
    }

    /** @return whether a session is currently running or paused */
    public boolean isSessionActive() {
        return state.get() == SessionState.RUNNING || state.get() == SessionState.PAUSED;
    }

    public ReadOnlyObjectProperty<SessionState> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public SessionState getState() {
        return state.get();
    }

    /**
     * The elapsed time in milliseconds, updated once per screen refresh.
     *
     * <p>This is what the stopwatch face listens to.
     *
     * @return the observable elapsed milliseconds
     */
    public ReadOnlyLongProperty elapsedMillisProperty() {
        return elapsedMillis.getReadOnlyProperty();
    }

    public long getElapsedMillis() {
        return elapsedMillis.get();
    }

    public ReadOnlyLongProperty elapsedSecondsProperty() {
        return elapsedSeconds.getReadOnlyProperty();
    }

    public long getElapsedSeconds() {
        return elapsedSeconds.get();
    }

    public ReadOnlyStringProperty elapsedDisplayProperty() {
        return elapsedDisplay.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<String> currentCategoryProperty() {
        return currentCategory.getReadOnlyProperty();
    }

    public String getCurrentCategory() {
        return currentCategory.get();
    }
}
