package com.study.organizer.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Map;

import com.study.organizer.util.DurationFormatter;

/**
 * A calendar heat map showing how much was studied on each day.
 *
 * <p>JavaFX has no heat map control, so this is built by hand out of a
 * {@link GridPane} of small coloured squares — one column per week, one row per
 * weekday, in the style of a contribution graph. It turns out to be about sixty
 * lines, which is less than it would take to configure a charting library to do
 * the same thing.
 *
 * <p>Colour intensity is scaled against the <b>busiest day in the window</b>
 * rather than a fixed number of hours. That way the map stays readable whether
 * you study twenty minutes a day or six hours.
 *
 * <h2>Why the colours are passed in</h2>
 * The squares are filled with {@link javafx.scene.layout.Background} objects
 * built in code, which CSS cannot restyle. So the two ends of the scale come
 * from the current {@link Theme} — a green-on-white scale that reads well in
 * daylight turns muddy on a dark background, and the reverse. Each theme
 * therefore carries its own pair.
 */
public class HeatMapPane extends VBox {

    /** Size of one day square, in pixels. */
    private static final int CELL_SIZE = 16;

    private static final int CELL_GAP = 3;

    /** The palette the squares are filled with. */
    private final Theme theme;

    /**
     * Builds the heat map.
     *
     * @param dailySeconds seconds studied per day, in date order and starting on
     *                     a Monday; days with no study must be present with zero
     * @param theme        the current theme, for the heat scale
     */
    public HeatMapPane(Map<LocalDate, Long> dailySeconds, Theme theme) {
        this.theme = theme;

        setSpacing(8);
        setPadding(new Insets(10));

        Label title = new Label(I18n.t("heatmap.title"));
        title.getStyleClass().add("chart-title");

        getChildren().addAll(title, buildGrid(dailySeconds), buildLegend());
    }

    private GridPane buildGrid(Map<LocalDate, Long> dailySeconds) {
        GridPane grid = new GridPane();
        grid.setHgap(CELL_GAP);
        grid.setVgap(CELL_GAP);

        long busiestDay = dailySeconds.values().stream().mapToLong(Long::longValue).max().orElse(0);

        // Row labels down the left edge: Mon, Tue, ...
        for (DayOfWeek day : DayOfWeek.values()) {
            Label label = new Label(day.getDisplayName(TextStyle.SHORT, I18n.getLanguage().locale()));
            label.getStyleClass().add("heatmap-label");
            label.setMinWidth(34);
            grid.add(label, 0, day.getValue() - 1);
        }

        int column = 1;
        for (Map.Entry<LocalDate, Long> entry : dailySeconds.entrySet()) {
            LocalDate date = entry.getKey();
            int row = date.getDayOfWeek().getValue() - 1;   // Monday is 1, so row 0

            grid.add(buildCell(date, entry.getValue(), busiestDay), column, row);

            // Move to the next column after each Sunday, so one column is one week.
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                column++;
            }
        }

        return grid;
    }

    /** Builds one day square, coloured by how much was studied that day. */
    private Region buildCell(LocalDate date, long seconds, long busiestDay) {
        Region cell = new Region();
        cell.setMinSize(CELL_SIZE, CELL_SIZE);
        cell.setPrefSize(CELL_SIZE, CELL_SIZE);
        cell.setBackground(new Background(
                new BackgroundFill(colorFor(seconds, busiestDay), new CornerRadii(3), Insets.EMPTY)));

        Tooltip.install(cell, new Tooltip(
                date + System.lineSeparator()
                        + (seconds == 0
                                ? I18n.t("heatmap.noStudy") : DurationFormatter.formatShort(seconds))));

        return cell;
    }

    /**
     * Picks the colour for a day.
     *
     * @param seconds     that day's study time
     * @param busiestDay  the largest daily total in the window, used as the scale
     * @return a colour between the empty shade and the full shade
     */
    private Color colorFor(long seconds, long busiestDay) {
        if (seconds == 0 || busiestDay == 0) {
            return theme.heatEmpty();
        }

        double intensity = (double) seconds / busiestDay;

        // Keep even the lightest studied day clearly separated from an empty one,
        // otherwise a short session is indistinguishable from no session at all.
        double floor = 0.25;
        double scaled = floor + intensity * (1 - floor);

        return theme.heatEmpty().interpolate(theme.heatFull(), scaled);
    }

    /** A "less ... more" strip explaining the shading. */
    private VBox buildLegend() {
        GridPane swatches = new GridPane();
        swatches.setHgap(CELL_GAP);

        Label less = new Label(I18n.t("heatmap.less"));
        less.getStyleClass().add("heatmap-label");
        swatches.add(less, 0, 0);

        for (int step = 0; step <= 4; step++) {
            Region swatch = new Region();
            swatch.setMinSize(CELL_SIZE, CELL_SIZE);
            swatch.setPrefSize(CELL_SIZE, CELL_SIZE);
            swatch.setBackground(new Background(new BackgroundFill(
                    colorFor(step, 4), new CornerRadii(3), Insets.EMPTY)));
            swatches.add(swatch, step + 1, 0);
        }

        Label more = new Label(I18n.t("heatmap.more"));
        more.getStyleClass().add("heatmap-label");
        swatches.add(more, 6, 0);

        swatches.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(swatches);
        box.setPadding(new Insets(6, 0, 0, 34));
        return box;
    }
}
