package com.study.organizer.ui;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.PointsCalculator;
import com.study.organizer.service.StudyDataService;
import com.study.organizer.vault.SessionNote;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * The form for creating a session by hand or correcting an existing one.
 *
 * <p>Two jobs, one dialog. They ask for exactly the same fields, and the only
 * difference is whether the fields start blank or filled in — so splitting them
 * into two classes would mean maintaining the same form twice.
 *
 * <h2>Why creating past sessions matters</h2>
 * The stopwatch only records what you time live. Anything studied away from the
 * computer, or on a day the app was not open, would simply be missing. Being
 * able to enter it afterwards is what makes the totals and streaks describe your
 * actual studying rather than only the part that happened at the keyboard.
 *
 * <h2>What the user may and may not set</h2>
 * The date, start time, duration, category, summary and observations are all
 * theirs to set. The <b>points are not</b> — they are recalculated from the
 * duration every time, exactly as they are for a timed session, so a hand-made
 * record cannot be worth more than the time it represents.
 */
public class SessionEditorDialog extends Dialog<StudySession> {

    private static final int MAX_HOURS = 23;

    private final DatePicker datePicker = new DatePicker();
    private final Spinner<Integer> startHour = new Spinner<>(0, 23, 9);
    private final Spinner<Integer> startMinute = new Spinner<>(0, 59, 0);
    private final Spinner<Integer> durationHours = new Spinner<>(0, MAX_HOURS, 1);
    private final Spinner<Integer> durationMinutes = new Spinner<>(0, 59, 0);
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final TextArea summaryField = new TextArea();
    private final TextArea observationsField = new TextArea();
    private final Label pointsPreview = new Label();

    /** The session being corrected, or {@code null} when creating a new one. */
    private final StudySession existing;

    private final NotePickerPane notePicker;

    /**
     * The category the session started in, so a change can be detected.
     *
     * <p>Needed because references are folder-scoped: moving a session to
     * another category invalidates them.
     */
    private String originalCategory;

    /**
     * Opens the form for a brand-new past session.
     *
     * @param data the data service, for the category list and the folder's notes
     */
    public SessionEditorDialog(StudyDataService data) {
        this(null, data);
    }

    /**
     * Opens the form.
     *
     * @param existing the session to correct, or {@code null} to create a new one
     * @param data     the data service, for the category list and the folder's notes
     */
    public SessionEditorDialog(StudySession existing, StudyDataService data) {
        this.existing = existing;
        this.notePicker = new NotePickerPane(data);

        ObservableList<String> categories = data.getCategories();

        ThemeManager.styleDialog(getDialogPane());

        boolean editing = existing != null;
        setTitle(I18n.t(editing ? "editor.title.edit" : "editor.title.add"));
        setHeaderText(I18n.t(editing ? "editor.header.edit" : "editor.header.add"));

        buildFields(categories);
        fillFrom(existing);

        ButtonType saveButton = new ButtonType(
                I18n.t(editing ? "editor.save.edit" : "editor.save.add"), ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);
        getDialogPane().setContent(buildLayout());

        getDialogPane().lookupButton(saveButton).disableProperty().bind(invalidInput());

        setResultConverter(button -> button == saveButton ? confirmAndBuild() : null);
        setOnShown(event -> summaryField.requestFocus());
    }

    /**
     * Builds the session, warning first if changing the category would strand
     * its references.
     *
     * <p>References may only point inside the session's own folder. Moving a
     * session to a different category therefore invalidates every link it had,
     * because those notes are in the folder it just left. Rather than writing
     * links that would be broken in Obsidian, the user is told plainly what will
     * be lost and given the chance to back out.
     *
     * @return the session to save, or {@code null} if the user backed out
     */
    private StudySession confirmAndBuild() {
        String chosen = readCategory();

        boolean categoryChanged = originalCategory != null
                && chosen != null
                && !originalCategory.equalsIgnoreCase(chosen);

        boolean hadReferences = existing != null && !existing.getReferences().isEmpty();

        if (categoryChanged && hadReferences) {
            int count = existing.getReferences().size();
            String bodyKey = count == 1
                    ? "editor.moveConfirm.body.singular" : "editor.moveConfirm.body.plural";
            boolean proceed = Dialogs.confirm(
                    I18n.t("editor.moveConfirm.title", chosen),
                    I18n.t(bodyKey, count, originalCategory));

            if (!proceed) {
                return null;
            }
        }

        return buildSession();
    }

    private void buildFields(ObservableList<String> categories) {
        datePicker.setValue(LocalDate.now());
        datePicker.setPrefWidth(180);

        // A future date would break every "days since" calculation and cannot
        // describe something already studied, so it simply cannot be picked.
        datePicker.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });

        for (Spinner<Integer> spinner : new Spinner[]{
                startHour, startMinute, durationHours, durationMinutes}) {
            spinner.setEditable(true);
            spinner.setPrefWidth(85);
        }

        categoryBox.setItems(categories);
        categoryBox.setEditable(true);
        categoryBox.setPromptText(I18n.t("editor.category.prompt"));
        categoryBox.setPrefWidth(260);

        // The picker must always show the notes of whichever folder is currently
        // chosen, so it follows the category rather than being set once.
        categoryBox.valueProperty().addListener(
                (observable, old, current) -> notePicker.setCategory(current));
        categoryBox.getEditor().textProperty().addListener(
                (observable, old, current) -> notePicker.setCategory(readCategory()));

        summaryField.setPromptText(I18n.t("common.summary.prompt"));
        summaryField.setWrapText(true);
        summaryField.setPrefRowCount(4);

        observationsField.setPromptText(I18n.t("common.observations.prompt"));
        observationsField.setWrapText(true);
        observationsField.setPrefRowCount(3);

        pointsPreview.getStyleClass().add("points-preview");
        bindPointsPreview();
    }

    /**
     * Keeps the points figure in step with the duration spinners.
     *
     * <p>Showing the score as it is typed makes it obvious that points come from
     * the duration and are not something the user is expected to enter.
     */
    private void bindPointsPreview() {
        pointsPreview.textProperty().bind(Bindings.createStringBinding(
                () -> I18n.t("editor.pointsPreview", PointsCalculator.format(
                        PointsCalculator.fromSeconds(durationInSeconds()))),
                durationHours.valueProperty(), durationMinutes.valueProperty()));
    }

    /** Fills the form from an existing session, or leaves sensible defaults. */
    private void fillFrom(StudySession session) {
        if (session == null) {
            return;
        }

        this.originalCategory = session.getCategory();

        // A session's own note sits in the folder it links into, so without this
        // the picker would offer it and let the session reference itself.
        notePicker.setExcludedNote(titleOfNote(session.getId()));

        LocalDateTime start = LocalDateTime.ofInstant(session.getStartedAt(), ZoneId.systemDefault());
        datePicker.setValue(start.toLocalDate());
        startHour.getValueFactory().setValue(start.getHour());
        startMinute.getValueFactory().setValue(start.getMinute());

        long totalMinutes = session.getDurationSeconds() / 60;
        durationHours.getValueFactory().setValue((int) Math.min(MAX_HOURS, totalMinutes / 60));
        durationMinutes.getValueFactory().setValue((int) (totalMinutes % 60));

        categoryBox.setValue(session.getCategory());
        summaryField.setText(session.getSummary());
        observationsField.setText(session.getObservations());

        // The picker's list must exist before ticks can be applied to it.
        notePicker.setCategory(session.getCategory());
        notePicker.setSelected(session.getReferences());
    }

    /**
     * The note title behind a session's id.
     *
     * <p>The id is a path relative to the vault root, such as
     * {@code Java/2026-08-07 1346 Interfaces.md}; the title is the file name
     * without its folder or extension.
     *
     * @param id the session's id, or {@code null} for an unsaved session
     * @return the title, or {@code null} if there is no note yet
     */
    private static String titleOfNote(String id) {
        if (id == null) {
            return null;
        }
        int separator = id.lastIndexOf('/');
        String fileName = separator < 0 ? id : id.substring(separator + 1);
        return SessionNote.titleOf(fileName);
    }

    private VBox buildLayout() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label(I18n.t("editor.label.date")), 0, 0);
        grid.add(datePicker, 1, 0);

        grid.add(new Label(I18n.t("editor.label.startedAt")), 0, 1);
        grid.add(timeRow(startHour, startMinute,
                I18n.t("common.unit.hours"), I18n.t("common.unit.minutes")), 1, 1);

        grid.add(new Label(I18n.t("editor.label.duration")), 0, 2);
        grid.add(timeRow(durationHours, durationMinutes,
                I18n.t("common.unit.hours"), I18n.t("common.unit.minutes")), 1, 2);

        grid.add(new Label(""), 0, 3);
        grid.add(pointsPreview, 1, 3);

        grid.add(new Label(I18n.t("editor.label.category")), 0, 4);
        grid.add(categoryBox, 1, 4);

        VBox content = new VBox(12,
                grid,
                new Label(I18n.t("common.summary.label")), summaryField,
                new Label(I18n.t("common.observations.label")), observationsField,
                notePicker);
        content.setPadding(new Insets(14));
        content.setPrefWidth(540);
        return content;
    }

    private HBox timeRow(Spinner<Integer> first, Spinner<Integer> second,
                         String firstUnit, String secondUnit) {
        HBox row = new HBox(6, first, new Label(firstUnit), second, new Label(secondUnit));
        HBox.setHgrow(row, Priority.NEVER);
        return row;
    }

    /**
     * The conditions that make the form unsubmittable.
     *
     * <p>Bound to the save button, so the requirements are visible before the
     * user tries rather than being reported as an error afterwards.
     *
     * @return true while the form cannot be saved
     */
    private BooleanBinding invalidInput() {
        // A summary is required, exactly as it is when finishing a live session.
        BooleanBinding summaryMissing = summaryField.textProperty().isEmpty();

        // A zero-length session is not a session.
        BooleanBinding zeroDuration = Bindings.createBooleanBinding(
                () -> durationInSeconds() <= 0,
                durationHours.valueProperty(), durationMinutes.valueProperty());

        BooleanBinding categoryMissing = Bindings.createBooleanBinding(
                () -> readCategory() == null,
                categoryBox.valueProperty(), categoryBox.getEditor().textProperty());

        BooleanBinding dateMissing = datePicker.valueProperty().isNull();

        return summaryMissing.or(zeroDuration).or(categoryMissing).or(dateMissing);
    }

    private long durationInSeconds() {
        int hours = valueOf(durationHours);
        int minutes = valueOf(durationMinutes);
        return (hours * 60L + minutes) * 60L;
    }

    /** Reads a spinner defensively - an editable spinner can hold null mid-typing. */
    private static int valueOf(Spinner<Integer> spinner) {
        SpinnerValueFactory<Integer> factory = spinner.getValueFactory();
        Integer value = factory == null ? null : factory.getValue();
        return value == null ? 0 : value;
    }

    /**
     * Reads the chosen category from either the selection or the typed text.
     *
     * <p>Same reasoning as on the timer screen: text typed into an editable
     * combo box without pressing Enter never becomes the selected value, so both
     * places have to be checked.
     *
     * @return the category, or {@code null} if none was given
     */
    private String readCategory() {
        String typed = categoryBox.getEditor().getText();
        if (typed != null && !typed.isBlank()) {
            return typed.trim();
        }
        String selected = categoryBox.getValue();
        return selected == null || selected.isBlank() ? null : selected.trim();
    }

    /**
     * Builds the session the form describes.
     *
     * <p>When editing, the original id is carried over — that is what makes the
     * save an update rather than a second copy.
     *
     * @return the session to store
     */
    private StudySession buildSession() {
        LocalDateTime start = LocalDateTime.of(
                datePicker.getValue(),
                LocalTime.of(valueOf(startHour), valueOf(startMinute)));

        Instant startedAt = start.atZone(ZoneId.systemDefault()).toInstant();
        long durationSeconds = durationInSeconds();

        return new StudySession(
                existing == null ? null : existing.getId(),
                readCategory(),
                startedAt,
                startedAt.plusSeconds(durationSeconds),
                durationSeconds,
                // Never taken from the form. Always derived, like every other session.
                PointsCalculator.fromSeconds(durationSeconds),
                summaryField.getText().trim(),
                observationsField.getText().trim(),
                // The picker has already dropped anything outside the chosen
                // folder, so whatever it returns is safe to write as links.
                notePicker.getSelected());
    }

    /**
     * Shows the form and waits for the user.
     *
     * @return the session they described, or empty if they cancelled
     */
    public Optional<StudySession> prompt() {
        return showAndWait();
    }
}
