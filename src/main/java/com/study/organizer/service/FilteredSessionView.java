package com.study.organizer.service;

import com.study.organizer.model.StudySession;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.function.Predicate;

/**
 * The slice of the history the user is currently looking at.
 *
 * <p>One filter grid sits above the tabs, and the history table, the charts and
 * the statistics all read from this. That is the whole point of the class: with
 * a single source of "what is currently in view", the three screens cannot
 * disagree with each other, and narrowing to a category or a month re-answers
 * every question at once rather than only the one on screen.
 *
 * <p>Two things are published:
 *
 * <ul>
 *   <li>{@link #getSessions()} — the matching sessions, as a live list the table
 *       binds to directly.</li>
 *   <li>{@link #statisticsProperty()} — a {@link StatisticsService} computed over
 *       exactly those sessions, which the charts and statistics screens read.</li>
 * </ul>
 *
 * <p>The statistics snapshot is rebuilt from scratch whenever the filter or the
 * stored data changes. That is a full pass over the visible sessions on every
 * keystroke in the search box, which sounds wasteful until you notice a personal
 * study log is a few hundred small objects — the pass is far too fast to see,
 * and rebuilding removes any possibility of a half-updated total.
 */
public class FilteredSessionView {

    private final FilteredList<StudySession> sessions;

    private final ReadOnlyObjectWrapper<StatisticsService> statistics =
            new ReadOnlyObjectWrapper<>(new StatisticsService(java.util.List.of()));

    /**
     * Builds the view.
     *
     * @param source    every stored session
     * @param predicate which of them to show; the view follows it as it changes
     */
    public FilteredSessionView(ObservableList<StudySession> source,
                               ObservableValue<Predicate<StudySession>> predicate) {

        this.sessions = new FilteredList<>(source, predicate.getValue());
        this.sessions.predicateProperty().bind(predicate);

        // Recompute when the filter narrows the list, and when a session is
        // saved, edited or deleted underneath it. Listening to the filtered list
        // covers both, since either one changes its contents.
        this.sessions.addListener((ListChangeListener<StudySession>) change -> recompute());

        recompute();
    }

    private void recompute() {
        statistics.set(new StatisticsService(java.util.List.copyOf(sessions)));
    }

    /**
     * The sessions currently in view.
     *
     * @return a live list that updates as the filter changes
     */
    public FilteredList<StudySession> getSessions() {
        return sessions;
    }

    /**
     * The figures for the sessions currently in view.
     *
     * @return a snapshot replaced whenever the view changes
     */
    public ReadOnlyObjectProperty<StatisticsService> statisticsProperty() {
        return statistics.getReadOnlyProperty();
    }

    /** @return the current snapshot */
    public StatisticsService getStatistics() {
        return statistics.get();
    }
}
