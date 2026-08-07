package com.study.organizer.ui;

import com.study.organizer.model.SessionState;
import com.study.organizer.service.TimerService;
import com.study.organizer.util.DurationFormatter;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;

/**
 * The stopwatch panel: a round dial in the middle, the hours and minutes read
 * out on the left, seconds and thousandths on the right, and the session
 * controls underneath.
 *
 * <p>The layout follows the classic stopwatch arrangement — the moving hand
 * gives you the rough position at a glance, and the digits beside it give the
 * exact figure when you want it.
 *
 * <p>This class draws the screen and nothing else. Every rule about what is
 * allowed when lives in {@link TimerService}; the buttons here simply bind their
 * {@code disable} property to the timer's state, so an action that would be
 * illegal is greyed out and cannot be clicked in the first place.
 */
public class TimerPane extends VBox {

    /** Diameter of the dial, in pixels. */
    private static final double DIAL_SIZE = 340;

    private final TimerService timer;

    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final Label hoursMinutesLabel = new Label("00:00");
    private final Label secondsLabel = new Label("00");
    private final Label millisLabel = new Label(".000");
    private final Label statusLabel = new Label();

    /**
     * Builds the timer panel.
     *
     * @param timer      the timer to display and control
     * @param categories the known category names, kept up to date by the caller
     * @param theme      the current theme, passed through to the dial
     * @param onFinish   run when the user finishes; the parent screen is
     *                   responsible for collecting the summary and saving
     */
    public TimerPane(TimerService timer,
                     ObservableList<String> categories,
                     ObjectProperty<Theme> theme,
                     Runnable onFinish) {
        this.timer = timer;

        setSpacing(14);
        setPadding(new Insets(20));
        setAlignment(Pos.CENTER);
        getStyleClass().add("timer-pane");

        bindReadouts();
        buildStatusLabel();
        buildCategoryBox(categories);

        getChildren().addAll(
                buildFace(theme),
                statusLabel,
                categoryBox,
                buildButtonRow(onFinish));
    }

    /**
     * Lays out the dial with the two readouts either side of it.
     *
     * @param theme the current theme, passed to the dial
     * @return the assembled face row
     */
    private HBox buildFace(ObjectProperty<Theme> theme) {
        StopwatchDial dial = new StopwatchDial(timer, theme, DIAL_SIZE);

        HBox row = new HBox(12,
                buildReadout("Hours / Minutes", hoursMinutesPanel(), Pos.CENTER_RIGHT),
                new StackPane(dial, buildCentreButton(dial)),
                buildReadout("Seconds", secondsPanel(), Pos.CENTER_LEFT));

        row.setAlignment(Pos.CENTER);
        return row;
    }

    /** One side readout: a small caption above its digits. */
    private VBox buildReadout(String caption, Region digits, Pos alignment) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("readout-caption");
        if (caption.equals("Seconds")) {
            // The active figure is highlighted, matching the reference stopwatch.
            captionLabel.getStyleClass().add("readout-caption-accent");
        }

        VBox box = new VBox(2, captionLabel, digits);
        box.setAlignment(alignment);
        box.setMinWidth(190);
        box.setPrefWidth(190);
        return box;
    }

    private Region hoursMinutesPanel() {
        hoursMinutesLabel.getStyleClass().add("readout-dim");
        HBox box = new HBox(hoursMinutesLabel);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    /**
     * The seconds readout.
     *
     * <p>Two sizes in one line — large whole seconds, small thousandths — so the
     * digits that matter are readable at a glance while the fast-moving ones
     * stay out of the way. A {@link TextFlow} lets two differently sized labels
     * sit on the same baseline.
     */
    private Region secondsPanel() {
        secondsLabel.getStyleClass().add("readout-seconds");
        millisLabel.getStyleClass().add("readout-millis");

        TextFlow flow = new TextFlow(secondsLabel, millisLabel);
        flow.setPrefWidth(Region.USE_COMPUTED_SIZE);

        HBox box = new HBox(flow);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Keeps the three digit groups in step with the timer.
     *
     * <p>Each is derived from the same millisecond value, so they can never
     * disagree with each other or with the hands on the dial.
     */
    private void bindReadouts() {
        hoursMinutesLabel.textProperty().bind(Bindings.createStringBinding(
                () -> DurationFormatter.formatHoursMinutes(timer.getElapsedMillis()),
                timer.elapsedMillisProperty()));

        secondsLabel.textProperty().bind(Bindings.createStringBinding(
                () -> DurationFormatter.formatSecondsPart(timer.getElapsedMillis()),
                timer.elapsedMillisProperty()));

        millisLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "." + DurationFormatter.formatMillisPart(timer.getElapsedMillis()),
                timer.elapsedMillisProperty()));
    }

    /**
     * The round button in the middle of the dial.
     *
     * <p>One button that changes meaning with the state: start when idle, pause
     * while running, resume when paused. That is how a physical stopwatch works,
     * and it keeps the most-used action under the cursor without hunting.
     */
    private Button buildCentreButton(StopwatchDial dial) {
        Button button = new Button();
        button.getStyleClass().add("dial-button");

        double diameter = dial.centreButtonDiameter();
        button.setMinSize(diameter, diameter);
        button.setPrefSize(diameter, diameter);
        button.setMaxSize(diameter, diameter);

        button.textProperty().bind(Bindings.createStringBinding(
                () -> switch (timer.getState()) {
                    // Geometric shapes rather than words, so the button reads the
                    // same at a glance regardless of language.
                    case RUNNING -> "‖";     // pause bars
                    case PAUSED -> "▶";      // play triangle
                    default -> "▶";
                },
                timer.stateProperty()));

        button.setOnAction(event -> handleCentreButton());
        return button;
    }

    /** Routes the centre button to whichever action the current state allows. */
    private void handleCentreButton() {
        switch (timer.getState()) {
            case IDLE, FINISHED -> handleStart();
            case RUNNING -> timer.pause();
            case PAUSED -> timer.resume();
        }
    }

    /** A short line describing what the timer is doing right now. */
    private void buildStatusLabel() {
        statusLabel.getStyleClass().add("status-text");
        statusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> switch (timer.getState()) {
                    case IDLE -> "Ready to start";
                    case RUNNING -> "Studying " + timer.getCurrentCategory();
                    case PAUSED -> "Paused - " + timer.getCurrentCategory();
                    case FINISHED -> "Session finished";
                },
                timer.stateProperty(), timer.currentCategoryProperty()));
    }

    /**
     * The category picker.
     *
     * <p>It is <b>editable</b>, which is what makes categories "created on the
     * fly": the user can pick an existing name or just type a new one, and the
     * new name is remembered when the session is saved.
     */
    private void buildCategoryBox(ObservableList<String> categories) {
        categoryBox.setItems(categories);
        categoryBox.setEditable(true);
        categoryBox.setPromptText("What are you studying?");
        categoryBox.setPrefWidth(280);
        categoryBox.setMaxWidth(280);

        // The category is fixed for the duration of a session, so lock it while
        // one is active. Changing it mid-session would make the record ambiguous.
        categoryBox.disableProperty().bind(
                timer.stateProperty().isNotEqualTo(SessionState.IDLE));
    }

    /**
     * The Finish and Cancel buttons.
     *
     * <p>Start, pause and resume live on the dial's centre button, so only the
     * two actions that end a session need a place here.
     */
    private HBox buildButtonRow(Runnable onFinish) {
        Button finishButton = new Button("Finish");
        Button cancelButton = new Button("Cancel");

        finishButton.getStyleClass().addAll("action-button", "primary-button");
        cancelButton.getStyleClass().addAll("action-button", "danger-button");

        // Both are meaningless without a session, so both follow the same rule.
        finishButton.disableProperty().bind(
                timer.stateProperty().isEqualTo(SessionState.IDLE));
        cancelButton.disableProperty().bind(
                timer.stateProperty().isEqualTo(SessionState.IDLE));

        finishButton.setOnAction(event -> onFinish.run());
        cancelButton.setOnAction(event -> handleCancel());

        HBox row = new HBox(10, finishButton, cancelButton);
        row.setAlignment(Pos.CENTER);
        HBox.setHgrow(row, Priority.NEVER);
        return row;
    }

    /** Starts a session, reporting a missing category as a friendly message. */
    private void handleStart() {
        String category = readSelectedCategory();
        if (category == null || category.isBlank()) {
            Dialogs.showWarning("Choose a category",
                    "Pick a category from the list, or type a new one, before starting.");
            return;
        }
        timer.start(category);
    }

    /** Cancels the session after confirming, since the time would be lost. */
    private void handleCancel() {
        boolean confirmed = Dialogs.confirm("Cancel this session?",
                "The elapsed time will be discarded and nothing will be saved.");
        if (confirmed) {
            timer.cancel();
            categoryBox.getEditor().clear();
            categoryBox.getSelectionModel().clearSelection();
        }
    }

    /**
     * Reads the chosen category.
     *
     * <p>An editable {@link ComboBox} has two places a value can live: the
     * selected item, and whatever text the user has typed into the editor. Text
     * typed but not committed with Enter never becomes the selected item, so
     * both must be checked or newly typed categories would be silently lost.
     *
     * @return the category name, or {@code null} if none was given
     */
    private String readSelectedCategory() {
        String typed = categoryBox.getEditor().getText();
        if (typed != null && !typed.isBlank()) {
            return typed.trim();
        }
        return categoryBox.getValue();
    }
}
