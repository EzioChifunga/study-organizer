package com.study.organizer.ui;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.FilteredSessionView;
import com.study.organizer.service.PointsCalculator;
import com.study.organizer.service.StatisticsService;
import com.study.organizer.util.DurationFormatter;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * The statistics screen: lifetime totals, session extremes, and averages.
 *
 * <p>Like the dashboard, the tiles are built once and their text is replaced on
 * each refresh.
 */
public class StatisticsPane extends ScrollPane {

    private final StatTile totalTime = new StatTile("Total study time", "0m");
    private final StatTile totalPoints = new StatTile("Total points", "0");
    private final StatTile totalSessions = new StatTile("Total sessions", "0");
    private final StatTile averageSession = new StatTile("Average session", "0m");
    private final StatTile longestSession = new StatTile("Longest session", "-");
    private final StatTile shortestSession = new StatTile("Shortest session", "-");
    private final StatTile averageDaily = new StatTile("Average per day", "0m");
    private final StatTile averageWeekly = new StatTile("Average per week", "0m");
    private final StatTile averageMonthly = new StatTile("Average per month", "0m");
    private final StatTile bestWeekday = new StatTile("Most productive day", "-");
    private final StatTile worstWeekday = new StatTile("Least productive day", "-");

    /**
     * Builds the statistics screen.
     *
     * @param view the sessions currently in view, after the shared filters
     */
    public StatisticsPane(FilteredSessionView view) {
        Label heading = new Label("Statistics");
        heading.getStyleClass().add("chart-title");

        // These figures follow the filter bar, so the heading has to say so —
        // otherwise a total of twelve hours would look like a bug to someone who
        // has studied for eighty.
        Label subheading = new Label(
                "For the sessions currently in view. Clear the filters above to see all time.");
        subheading.getStyleClass().add("stat-caption");

        FlowPane tiles = new FlowPane(14, 14,
                totalTime, totalPoints, totalSessions,
                averageSession, longestSession, shortestSession,
                averageDaily, averageWeekly, averageMonthly,
                bestWeekday, worstWeekday);

        VBox content = new VBox(4, heading, subheading, new VBox(16, tiles));
        content.setPadding(new Insets(20));

        setContent(content);
        setFitToWidth(true);

        view.statisticsProperty().addListener((observable, old, current) -> update(current));
        update(view.getStatistics());
    }

    /**
     * Refreshes every tile from a statistics snapshot.
     *
     * @param stats the current snapshot
     */
    private void update(StatisticsService stats) {
        totalTime.setValue(DurationFormatter.formatShort(stats.getTotalSeconds()));
        totalPoints.setValue(PointsCalculator.format(stats.getTotalPoints()));
        totalSessions.setValue(String.valueOf(stats.getTotalSessions()));

        averageSession.setValue(DurationFormatter.formatShort(stats.getAverageSessionSeconds()));
        longestSession.setValue(describeDuration(stats.getLongestSession()));
        shortestSession.setValue(describeDuration(stats.getShortestSession()));

        averageDaily.setValue(DurationFormatter.formatShort(stats.getAverageDailySeconds()));
        averageWeekly.setValue(DurationFormatter.formatShort(stats.getAverageWeeklySeconds()));
        averageMonthly.setValue(DurationFormatter.formatShort(stats.getAverageMonthlySeconds()));

        bestWeekday.setValue(describeWeekday(stats.getMostProductiveWeekday()));
        worstWeekday.setValue(describeWeekday(stats.getLeastProductiveWeekday()));
    }

    private static String describeDuration(Optional<StudySession> session) {
        return session
                .map(value -> DurationFormatter.formatShort(value.getDurationSeconds()))
                .orElse("-");
    }

    private static String describeWeekday(Optional<DayOfWeek> day) {
        return day
                .map(value -> value.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .orElse("-");
    }
}
