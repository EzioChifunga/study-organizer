package com.study.organizer.ui;

import com.study.organizer.model.SessionState;
import com.study.organizer.service.TimerService;

import javafx.beans.property.ObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The round stopwatch face: a numbered rim, tick marks, a sweeping second hand,
 * a slower minute hand, and a raised disc in the middle.
 *
 * <h2>Why this is drawn on a Canvas</h2>
 * The face has sixty tick marks and two hands that move continuously. Building
 * that from individual JavaFX shape nodes would mean the scene graph carrying
 * seventy-odd objects that are re-laid-out on every frame. A
 * {@link Canvas} is a single node that we paint ourselves, which is both faster
 * and — for something as geometric as a clock face — considerably easier to
 * read as code.
 *
 * <h2>How the geometry works</h2>
 * Everything is measured in fractions of the radius rather than in fixed pixels,
 * so the same drawing code produces a correct face at any size.
 *
 * <p>Angles are handled by rotating the whole canvas rather than by computing
 * the endpoint of each line with sine and cosine. Drawing one straight vertical
 * line at a rotation is much easier to follow than the trigonometry it replaces.
 * Each rotation is wrapped in {@code save()} / {@code restore()} so it cannot
 * leak into whatever is drawn next.
 */
public class StopwatchDial extends StackPane {

    /** Angle covered by one second on the outer scale, in degrees. */
    private static final double DEGREES_PER_SECOND = 360.0 / 60;

    /** Milliseconds in one full sweep of the second hand. */
    private static final long SWEEP_MILLIS = 60_000L;

    /** Milliseconds in one full sweep of the minute hand. */
    private static final long MINUTE_HAND_MILLIS = 3_600_000L;

    // Radii, as a fraction of the dial's radius.
    private static final double RIM_NUMBER_RADIUS = 0.93;
    private static final double TICK_OUTER_RADIUS = 0.80;
    private static final double MAJOR_TICK_INNER_RADIUS = 0.68;
    private static final double MINOR_TICK_INNER_RADIUS = 0.74;
    private static final double SECOND_HAND_LENGTH = 0.78;
    private static final double MINUTE_HAND_LENGTH = 0.60;
    private static final double CENTRE_DISC_RADIUS = 0.44;

    private final Canvas canvas;
    private final TimerService timer;
    private final ObjectProperty<Theme> theme;

    /**
     * Builds the dial.
     *
     * @param timer the timer whose elapsed time is shown
     * @param theme the current theme; the dial repaints when it changes
     * @param size  the width and height of the face, in pixels
     */
    public StopwatchDial(TimerService timer, ObjectProperty<Theme> theme, double size) {
        this.timer = timer;
        this.theme = theme;
        this.canvas = new Canvas(size, size);

        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getChildren().add(canvas);

        // Repaint when the time moves, when the session state changes (so the
        // paused look appears immediately), and when the theme is switched.
        timer.elapsedMillisProperty().addListener((observable, old, current) -> draw());
        timer.stateProperty().addListener((observable, old, current) -> draw());
        theme.addListener((observable, old, current) -> draw());

        draw();
    }

    /** Repaints the whole face. */
    public final void draw() {
        Theme palette = theme.get();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        double size = canvas.getWidth();
        double centre = size / 2;
        double radius = size / 2;

        gc.clearRect(0, 0, size, size);

        drawFace(gc, centre, radius, palette);
        drawTicks(gc, centre, radius, palette);
        drawRimNumbers(gc, centre, radius, palette);
        drawHands(gc, centre, radius, palette);
        drawCentreDisc(gc, centre, radius, palette);
    }

    /** The flat background circle the rest is drawn on top of. */
    private void drawFace(GraphicsContext gc, double centre, double radius, Theme palette) {
        double faceRadius = radius * (TICK_OUTER_RADIUS + 0.02);
        gc.setFill(palette.dialFace());
        gc.fillOval(centre - faceRadius, centre - faceRadius, faceRadius * 2, faceRadius * 2);
    }

    /**
     * The sixty tick marks.
     *
     * <p>Every fifth tick is longer and heavier, which is what lets the eye find
     * the numbered positions without counting.
     */
    private void drawTicks(GraphicsContext gc, double centre, double radius, Theme palette) {
        for (int second = 0; second < 60; second++) {
            boolean major = second % 5 == 0;

            gc.save();
            gc.translate(centre, centre);
            gc.rotate(second * DEGREES_PER_SECOND);

            gc.setStroke(major ? palette.majorTick() : palette.minorTick());
            gc.setLineWidth(major ? radius * 0.018 : radius * 0.008);
            gc.setLineCap(StrokeLineCap.BUTT);

            // Straight up from the centre; the rotation above puts it in place.
            double inner = major ? MAJOR_TICK_INNER_RADIUS : MINOR_TICK_INNER_RADIUS;
            gc.strokeLine(0, -radius * TICK_OUTER_RADIUS, 0, -radius * inner);

            gc.restore();
        }
    }

    /**
     * The numbers 5 to 60 around the rim.
     *
     * <p>These cannot use the rotate trick: a rotated canvas would draw the text
     * lying on its side. So the position is computed with sine and cosine and
     * the text itself stays upright.
     */
    private void drawRimNumbers(GraphicsContext gc, double centre, double radius, Theme palette) {
        gc.setFill(palette.rimNumber());
        gc.setFont(Font.font("System", FontWeight.NORMAL, radius * 0.13));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);

        for (int second = 5; second <= 60; second += 5) {
            // Subtract a quarter turn because zero degrees points right, but the
            // top of the dial is where the count starts.
            double angle = Math.toRadians(second * DEGREES_PER_SECOND) - Math.PI / 2;

            double x = centre + Math.cos(angle) * radius * RIM_NUMBER_RADIUS;
            double y = centre + Math.sin(angle) * radius * RIM_NUMBER_RADIUS;

            gc.fillText(String.valueOf(second), x, y);
        }
    }

    /** The two hands: minutes underneath, sweeping seconds on top. */
    private void drawHands(GraphicsContext gc, double centre, double radius, Theme palette) {
        long millis = timer.getElapsedMillis();

        // Both hands move continuously rather than jumping between positions.
        // Using the raw milliseconds is what produces the smooth sweep.
        double minuteAngle = 360.0 * (millis % MINUTE_HAND_MILLIS) / MINUTE_HAND_MILLIS;
        double secondAngle = 360.0 * (millis % SWEEP_MILLIS) / SWEEP_MILLIS;

        drawHand(gc, centre, radius, minuteAngle,
                MINUTE_HAND_LENGTH, 0.030, palette.minuteHand());
        drawHand(gc, centre, radius, secondAngle,
                SECOND_HAND_LENGTH, 0.022, palette.secondHand());
    }

    /**
     * Draws one hand as a tapered triangle.
     *
     * <p>A plain line looks flat; widening the hand towards the tip gives it the
     * same weight as the reference stopwatch, and costs one polygon.
     *
     * @param angle     where the hand points, in degrees clockwise from the top
     * @param length    hand length as a fraction of the radius
     * @param thickness widest point as a fraction of the radius
     * @param color     the hand colour
     */
    private void drawHand(GraphicsContext gc,
                          double centre,
                          double radius,
                          double angle,
                          double length,
                          double thickness,
                          Color color) {
        gc.save();
        gc.translate(centre, centre);
        gc.rotate(angle);

        double tip = -radius * length;
        double wide = radius * thickness;
        double tail = radius * 0.06;

        gc.setFill(color);
        gc.fillPolygon(
                new double[]{0, wide, 0, -wide},
                new double[]{tip, tip + radius * 0.16, tail, tip + radius * 0.16},
                4);

        gc.restore();
    }

    /**
     * The raised disc in the middle.
     *
     * <p>Purely decorative: the actual button sits on top of it as a real JavaFX
     * control, so that clicking, hover effects and keyboard focus all work
     * normally instead of having to be reinvented on the canvas.
     */
    private void drawCentreDisc(GraphicsContext gc, double centre, double radius, Theme palette) {
        double outer = radius * CENTRE_DISC_RADIUS;
        double inner = outer * 0.78;

        gc.setFill(palette.centreDiscEdge());
        gc.fillOval(centre - outer, centre - outer, outer * 2, outer * 2);

        gc.setFill(palette.centreDisc());
        gc.fillOval(centre - inner, centre - inner, inner * 2, inner * 2);

        // A faint ring, so the disc reads as raised rather than as a flat blob.
        gc.setStroke(palette.centreDiscEdge());
        gc.setLineWidth(radius * 0.012);
        gc.strokeOval(centre - inner, centre - inner, inner * 2, inner * 2);

        // While paused, tint the disc with the accent colour as a quiet reminder
        // that the clock is not running.
        if (timer.getState() == SessionState.PAUSED) {
            gc.setStroke(palette.secondHand());
            gc.setLineWidth(radius * 0.016);
            gc.strokeOval(centre - outer, centre - outer, outer * 2, outer * 2);
        }
    }

    /** @return the radius of the centre disc, so the button can be sized to match */
    public double centreButtonDiameter() {
        return canvas.getWidth() * CENTRE_DISC_RADIUS * 0.78;
    }
}
