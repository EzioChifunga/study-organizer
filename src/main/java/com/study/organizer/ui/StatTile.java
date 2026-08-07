package com.study.organizer.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A single dashboard tile: a small caption above a large value.
 *
 * <p>Written once and reused for every figure on the dashboard, so the tiles are
 * guaranteed to line up and look alike, and so adding a new statistic is one
 * line rather than a block of layout code.
 */
public class StatTile extends VBox {

    private final Label valueLabel = new Label();

    /**
     * Creates a tile.
     *
     * @param caption the description shown above the number, e.g. "Today"
     * @param value   the initial value to display
     */
    public StatTile(String caption, String value) {
        setSpacing(4);
        setPadding(new Insets(14, 18, 14, 18));
        setMinWidth(160);
        getStyleClass().add("stat-tile");

        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("stat-caption");

        valueLabel.setText(value);
        valueLabel.getStyleClass().add("stat-value");

        getChildren().addAll(captionLabel, valueLabel);
    }

    /**
     * Replaces the displayed value.
     *
     * @param value the new value
     */
    public void setValue(String value) {
        valueLabel.setText(value);
    }
}
