package com.study.organizer.ui;

import javafx.scene.paint.Color;

/**
 * The two colour schemes the application can be shown in.
 *
 * <h2>Why the colours are listed here and also in the stylesheet</h2>
 * Most of the window is styled by {@code styles.css}, which switches
 * automatically when the {@link #styleClass()} is applied to the scene root.
 * But the stopwatch face is drawn on a {@link javafx.scene.canvas.Canvas}, and a
 * canvas paints pixels directly — CSS cannot reach inside it. So the dial needs
 * its colours as real {@link Color} objects, and those live here.
 *
 * <p>Keeping both sets in one enum means the two halves cannot drift apart
 * unnoticed: change a theme and every colour it owns is in front of you.
 */
public enum Theme {

    /** A light scheme, for daytime use. */
    LIGHT(
            "theme-light",
            "Light",
            Color.web("#e8eaed"),   // dial face
            Color.web("#c4c9d0"),   // minor ticks
            Color.web("#8a9199"),   // major ticks
            Color.web("#5c646d"),   // numbers around the rim
            Color.web("#ff5722"),   // second hand and accents
            Color.web("#3c4552"),   // minute hand
            Color.web("#f4f5f7"),   // centre disc
            Color.web("#d7dbe0"),   // centre disc edge
            Color.web("#e6e8eb"),   // heat map: a day with no study
            Color.web("#d84315")),  // heat map: the busiest day

    /** A dark scheme, closer to the reference stopwatch. */
    DARK(
            "theme-dark",
            "Dark",
            Color.web("#2b2724"),   // dial face
            Color.web("#4a443f"),   // minor ticks
            Color.web("#7d746c"),   // major ticks
            Color.web("#b5aca4"),   // numbers around the rim
            Color.web("#ff5722"),   // second hand and accents
            Color.web("#f2efec"),   // minute hand
            Color.web("#37322e"),   // centre disc
            Color.web("#241f1c"),   // centre disc edge
            Color.web("#332f2b"),   // heat map: a day with no study
            Color.web("#ff7043"));  // heat map: the busiest day

    private final String styleClass;
    private final String displayName;
    private final Color dialFace;
    private final Color minorTick;
    private final Color majorTick;
    private final Color rimNumber;
    private final Color secondHand;
    private final Color minuteHand;
    private final Color centreDisc;
    private final Color centreDiscEdge;
    private final Color heatEmpty;
    private final Color heatFull;

    Theme(String styleClass,
          String displayName,
          Color dialFace,
          Color minorTick,
          Color majorTick,
          Color rimNumber,
          Color secondHand,
          Color minuteHand,
          Color centreDisc,
          Color centreDiscEdge,
          Color heatEmpty,
          Color heatFull) {
        this.styleClass = styleClass;
        this.displayName = displayName;
        this.dialFace = dialFace;
        this.minorTick = minorTick;
        this.majorTick = majorTick;
        this.rimNumber = rimNumber;
        this.secondHand = secondHand;
        this.minuteHand = minuteHand;
        this.centreDisc = centreDisc;
        this.centreDiscEdge = centreDiscEdge;
        this.heatEmpty = heatEmpty;
        this.heatFull = heatFull;
    }

    /** @return the other theme, for the toggle button */
    public Theme opposite() {
        return this == LIGHT ? DARK : LIGHT;
    }

    /** @return the CSS class applied to the scene root to select this theme */
    public String styleClass() {
        return styleClass;
    }

    /** @return the name shown to the user */
    public String displayName() {
        return displayName;
    }

    public Color dialFace() {
        return dialFace;
    }

    public Color minorTick() {
        return minorTick;
    }

    public Color majorTick() {
        return majorTick;
    }

    public Color rimNumber() {
        return rimNumber;
    }

    public Color secondHand() {
        return secondHand;
    }

    public Color minuteHand() {
        return minuteHand;
    }

    public Color centreDisc() {
        return centreDisc;
    }

    public Color centreDiscEdge() {
        return centreDiscEdge;
    }

    /**
     * The colour of a heat-map day with no study.
     *
     * <p>Deliberately close to the surface behind it, so an untouched day reads
     * as an absence rather than as a value.
     *
     * @return the empty-day colour
     */
    public Color heatEmpty() {
        return heatEmpty;
    }

    /**
     * The colour of the busiest heat-map day; lighter days are blends towards
     * {@link #heatEmpty()}.
     *
     * <p>Darker in the light theme and lighter in the dark one, because the
     * contrast has to work against opposite backgrounds.
     *
     * @return the busiest-day colour
     */
    public Color heatFull() {
        return heatFull;
    }
}
