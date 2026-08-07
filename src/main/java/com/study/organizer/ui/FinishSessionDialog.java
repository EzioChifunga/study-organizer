package com.study.organizer.ui;

import com.study.organizer.service.StudyDataService;
import com.study.organizer.util.DurationFormatter;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * The dialog shown when a session is finished, asking for the study summary and
 * any notes to reference.
 *
 * <p>The specification requires that a session only counts as completed once a
 * summary has been written. That is enforced in two ways: the Save button stays
 * disabled while the summary is empty, and the dialog is <b>modal</b>, so the
 * session cannot be quietly abandoned by clicking elsewhere.
 *
 * <p>If the user closes the dialog without saving, nothing is lost: the timer
 * still holds the session, and they can press Finish again.
 *
 * <p>What they write here becomes a Markdown note in the category's folder. Any
 * notes ticked in the picker become {@code [[wiki links]]} inside it — real
 * links, clickable both here and in Obsidian.
 */
public class FinishSessionDialog extends Dialog<FinishSessionDialog.Result> {

    /**
     * What the user wrote.
     *
     * @param summary      the required study summary
     * @param observations optional extra notes
     * @param references   titles of notes in the same folder to link to
     */
    public record Result(String summary, String observations, List<String> references) {
    }

    private final TextArea summaryField = new TextArea();
    private final TextArea observationsField = new TextArea();
    private final NotePickerPane notePicker;

    /**
     * Builds the dialog.
     *
     * @param category        the category of the session being finished
     * @param durationSeconds how long was studied, shown so the user has context
     * @param data            the data service, used to list the folder's notes
     */
    public FinishSessionDialog(String category, long durationSeconds, StudyDataService data) {
        ThemeManager.styleDialog(getDialogPane());

        this.notePicker = new NotePickerPane(data);
        notePicker.setCategory(category);

        setTitle("Finish session");
        setHeaderText("You studied " + category + " for "
                + DurationFormatter.format(durationSeconds));

        ButtonType saveButton = new ButtonType("Save session", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        getDialogPane().setContent(buildContent());

        // The summary is mandatory, so Save is unavailable until something is
        // written. Binding the button rather than validating on click means the
        // requirement is visible before the user tries.
        getDialogPane().lookupButton(saveButton).disableProperty()
                .bind(summaryField.textProperty().isEmpty());

        setResultConverter(button ->
                button == saveButton
                        ? new Result(
                                summaryField.getText().trim(),
                                observationsField.getText().trim(),
                                notePicker.getSelected())
                        : null);

        // Put the cursor straight in the summary field when the dialog opens.
        setOnShown(event -> summaryField.requestFocus());
    }

    private VBox buildContent() {
        summaryField.setPromptText("What did you study? What did you learn?");
        summaryField.setWrapText(true);
        summaryField.setPrefRowCount(4);

        observationsField.setPromptText("Anything to revisit, or how the session went (optional)");
        observationsField.setWrapText(true);
        observationsField.setPrefRowCount(3);

        VBox content = new VBox(8,
                new Label("Study summary (required)"), summaryField,
                new Label("Observations (optional)"), observationsField,
                notePicker);
        content.setPadding(new Insets(12));
        content.setPrefWidth(500);
        return content;
    }

    /**
     * Shows the dialog and waits for the user.
     *
     * @return what the user wrote, or empty if they cancelled
     */
    public Optional<Result> prompt() {
        return showAndWait();
    }
}
