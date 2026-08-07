package com.study.organizer.ui;

import com.study.organizer.service.FilteredSessionView;
import com.study.organizer.service.PointsCalculator;
import com.study.organizer.service.StatisticsService;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

/**
 * The charts screen: study hours by weekday, across the month, and by category,
 * plus the frequency heat map.
 *
 * <p>All three chart types come from JavaFX itself, so no charting library is
 * needed. Each chart is rebuilt from scratch whenever the data changes — the
 * JavaFX chart classes are awkward to update in place, and with a handful of
 * data points rebuilding is instant.
 *
 * <p>Everything is plotted in <b>hours</b> rather than seconds, since a y-axis
 * reading "5400" is far less useful than one reading "1.5".
 */
public class ChartsPane extends ScrollPane {

    /** How many weeks of history the heat map shows. */
    private static final int HEATMAP_WEEKS = 12;

    private final VBox content = new VBox(24);

    private final ObjectProperty<Theme> theme;

    /**
     * Builds the charts screen.
     *
     * @param view  the sessions currently in view, after the shared filters
     * @param theme the current theme, needed by the heat map
     */
    public ChartsPane(FilteredSessionView view, ObjectProperty<Theme> theme) {
        this.theme = theme;

        content.setPadding(new Insets(20));

        setContent(content);
        setFitToWidth(true);

        // Redrawn whenever the filter changes, so the charts always describe the
        // same slice the history table is showing.
        view.statisticsProperty().addListener((observable, old, current) -> rebuild(current));

        // The three JavaFX charts restyle themselves from the stylesheet, but the
        // heat map paints its squares in code, so it has to be rebuilt by hand
        // when the theme changes.
        theme.addListener((observable, old, current) -> rebuild(view.getStatistics()));

        rebuild(view.getStatistics());
    }

    /**
     * Throws away the old charts and draws new ones.
     *
     * @param stats the current snapshot
     */
    private void rebuild(StatisticsService stats) {
        content.getChildren().setAll(
                buildWeeklyBarChart(stats),
                buildMonthlyLineChart(stats),
                buildCategoryPieChart(stats),
                new HeatMapPane(stats.getHeatMapData(HEATMAP_WEEKS), theme.get()));
    }

    /** Hours studied on each day of the current week. */
    private BarChart<String, Number> buildWeeklyBarChart(StatisticsService stats) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Day");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Hours");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("This week");
        chart.setLegendVisible(false);
        chart.setPrefHeight(300);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<DayOfWeek, Long> entry : stats.getCurrentWeekByDay().entrySet()) {
            String label = entry.getKey().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            series.getData().add(
                    new XYChart.Data<>(label, PointsCalculator.toHours(entry.getValue())));
        }
        chart.getData().add(series);

        return chart;
    }

    /** Hours studied on each day of the current month so far. */
    private LineChart<String, Number> buildMonthlyLineChart(StatisticsService stats) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Day of month");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Hours");

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("This month");
        chart.setLegendVisible(false);
        chart.setPrefHeight(300);
        chart.setCreateSymbols(true);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<LocalDate, Long> entry : stats.getCurrentMonthByDay().entrySet()) {
            String label = String.valueOf(entry.getKey().getDayOfMonth());
            series.getData().add(
                    new XYChart.Data<>(label, PointsCalculator.toHours(entry.getValue())));
        }
        chart.getData().add(series);

        return chart;
    }

    /** Share of total study time per category. */
    private javafx.scene.Node buildCategoryPieChart(StatisticsService stats) {
        Map<String, Long> byCategory = stats.getSecondsByCategory();

        if (byCategory.isEmpty()) {
            Label empty = new Label("No sessions recorded yet - finish a session to see this chart.");
            empty.getStyleClass().add("empty-state");
            return empty;
        }

        PieChart chart = new PieChart();
        chart.setTitle("Time by category");
        chart.setPrefHeight(340);

        byCategory.forEach((category, seconds) ->
                chart.getData().add(new PieChart.Data(
                        category + " (" + PointsCalculator.toHours(seconds) + "h)",
                        PointsCalculator.toHours(seconds))));

        return chart;
    }
}
