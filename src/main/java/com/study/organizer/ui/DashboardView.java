package com.study.organizer.ui;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.PointsCalculator;
import com.study.organizer.service.StatisticsService;
import com.study.organizer.service.StudyDataService;
import com.study.organizer.service.TimerService;
import com.study.organizer.util.DurationFormatter;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * The home screen: the live timer on top, and the day's figures below it.
 *
 * <p>The tiles are created once and then updated in place whenever the data
 * changes. Rebuilding them from scratch on every refresh would also work, but
 * updating text avoids the layout visibly jumping.
 */
public class DashboardView extends VBox {

    /** How the "last session" timestamp is written out. */
    private static final DateTimeFormatter LAST_SESSION_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm");

    /**
     * The weekly target used by the progress bar, in hours.
     *
     * <p>The specification asks for "weekly progress" without saying progress
     * towards what, so a fixed, easy-to-change goal is used.
     */
    private static final double WEEKLY_GOAL_HOURS = 10.0;

    private final StatTile todayTime = new StatTile(I18n.t("dashboard.tile.today"), "0m");
    private final StatTile todayPoints = new StatTile(I18n.t("dashboard.tile.todayPoints"), "0");
    private final StatTile weekPoints = new StatTile(I18n.t("dashboard.tile.week"), "0");
    private final StatTile monthPoints = new StatTile(I18n.t("dashboard.tile.month"), "0");
    private final StatTile streak =
            new StatTile(I18n.t("dashboard.tile.streak"), I18n.t("dashboard.streak.days", 0));
    private final StatTile topCategory = new StatTile(I18n.t("dashboard.tile.topCategory"), "-");
    private final StatTile lastSession =
            new StatTile(I18n.t("dashboard.tile.lastSession"), I18n.t("dashboard.value.none"));

    private final ProgressBar weeklyProgress = new ProgressBar(0);
    private final Label weeklyProgressLabel = new Label();

    /**
     * Builds the dashboard.
     *
     * @param timer    the shared timer
     * @param data     the shared data service; the view listens to it for updates
     * @param theme    the current theme, passed through to the stopwatch dial
     * @param onFinish run when the user presses Finish on the timer
     */
    public DashboardView(TimerService timer,
                         StudyDataService data,
                         ObjectProperty<Theme> theme,
                         Runnable onFinish) {
        setSpacing(20);
        setPadding(new Insets(20));

        TimerPane timerPane = new TimerPane(timer, data.getCategories(), theme, onFinish);

        FlowPane tiles = new FlowPane(14, 14,
                todayTime, todayPoints, weekPoints, monthPoints, streak, topCategory, lastSession);
        tiles.setAlignment(Pos.CENTER);

        getChildren().addAll(timerPane, tiles, buildWeeklyProgress());

        // Redraw whenever a new snapshot arrives, and once now for the initial state.
        data.statisticsProperty().addListener((observable, old, current) -> update(current));
        update(data.getStatistics());
    }

    private VBox buildWeeklyProgress() {
        weeklyProgress.setPrefWidth(Double.MAX_VALUE);
        weeklyProgress.getStyleClass().add("weekly-progress");
        weeklyProgressLabel.getStyleClass().add("stat-caption");

        VBox box = new VBox(6, weeklyProgressLabel, weeklyProgress);
        box.setPadding(new Insets(0, 10, 0, 10));
        return box;
    }

    /**
     * Refreshes every tile from a statistics snapshot.
     *
     * @param stats the current snapshot
     */
    private void update(StatisticsService stats) {
        todayTime.setValue(DurationFormatter.formatShort(stats.getTodaySeconds()));
        todayPoints.setValue(PointsCalculator.format(stats.getTodayPoints()));
        weekPoints.setValue(PointsCalculator.format(stats.getWeekPoints()));
        monthPoints.setValue(PointsCalculator.format(stats.getMonthPoints()));

        int days = stats.getCurrentStreak();
        streak.setValue(days == 1
                ? I18n.t("dashboard.streak.day", days)
                : I18n.t("dashboard.streak.days", days));

        topCategory.setValue(stats.getMostStudiedCategory().orElse("-"));
        lastSession.setValue(describeLastSession(stats.getLastSession()));

        updateWeeklyProgress(stats.getWeekPoints());
    }

    private void updateWeeklyProgress(double hoursThisWeek) {
        // Clamp to 1.0 so the bar does not misdraw once the goal is beaten.
        weeklyProgress.setProgress(Math.min(1.0, hoursThisWeek / WEEKLY_GOAL_HOURS));
        weeklyProgressLabel.setText(I18n.t("dashboard.weeklyProgress",
                String.format("%.2f", hoursThisWeek), String.format("%.0f", WEEKLY_GOAL_HOURS)));
    }

    private static String describeLastSession(Optional<StudySession> session) {
        return session
                .map(value -> value.getStartedAt()
                        .atZone(ZoneId.systemDefault())
                        .format(LAST_SESSION_FORMAT))
                .orElse(I18n.t("dashboard.value.none"));
    }

}
