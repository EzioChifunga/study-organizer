package com.study.organizer.ui;

import com.study.organizer.service.StudyDataService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Picks notes to reference, from one category folder only.
 *
 * <p>Type to narrow the list by title, tick the ones to link. The selection
 * survives searching — ticking a note, clearing the search and ticking another
 * keeps both — because the ticks live in a set of titles rather than in the
 * visible rows.
 *
 * <h2>Why the folder matters</h2>
 * The candidates come from {@link StudyDataService#listNoteTitles(String)},
 * which lists exactly one folder. That is how "a session cannot reference
 * another category" is enforced: not by validating the result, but by never
 * offering a note from anywhere else. Change the category and the list is
 * rebuilt from the new folder.
 */
public class NotePickerPane extends VBox {

    private final StudyDataService data;

    /** Every note title in the current category folder. */
    private final ObservableList<String> available = FXCollections.observableArrayList();

    /** The subset matching the search box. */
    private final FilteredList<String> visible = new FilteredList<>(available, title -> true);

    /**
     * The titles the user has ticked.
     *
     * <p>A {@link LinkedHashSet} so that ticking the same note twice cannot
     * duplicate it, and so the order the user chose them in is kept.
     */
    private final Set<String> selected = new LinkedHashSet<>();

    private final TextField searchField = new TextField();
    private final ListView<String> list = new ListView<>();
    private final Label statusLabel = new Label();

    private String category;

    /**
     * A note title to keep out of the list, or {@code null} for none.
     *
     * <p>Used when editing a session: its own note is in the same folder, so the
     * picker would otherwise offer it and let the session link to itself. That
     * reference means nothing and produces a loop in the note viewer.
     */
    private String excludedTitle;

    /**
     * Builds the picker.
     *
     * @param data the data service, used to list the folder's notes
     */
    public NotePickerPane(StudyDataService data) {
        this.data = data;

        setSpacing(6);

        Label caption = new Label(I18n.t("notepicker.caption"));
        caption.getStyleClass().add("stat-caption");

        searchField.setPromptText(I18n.t("notepicker.search.prompt"));
        searchField.textProperty().addListener((observable, old, current) -> applySearch());

        list.setItems(visible);
        list.setPrefHeight(130);
        list.setCellFactory(view -> new CheckBoxCell());
        list.getStyleClass().add("note-picker-list");

        statusLabel.getStyleClass().add("stat-caption");

        getChildren().addAll(caption, searchField, list, statusLabel);
        setPadding(new Insets(4, 0, 0, 0));

        updateStatus();
    }

    /**
     * Points the picker at a category folder.
     *
     * <p>Selections that do not exist in the new folder are dropped, because a
     * link may not cross folders. That is the one place the rule has to be
     * applied rather than simply arising from what is offered.
     *
     * @param category the category whose notes may be referenced
     */
    public void setCategory(String category) {
        this.category = category;

        if (category == null || category.isBlank()) {
            available.clear();
            selected.clear();
            updateStatus();
            return;
        }

        List<String> candidates = new ArrayList<>(data.listNoteTitles(category));
        if (excludedTitle != null) {
            candidates.removeIf(title -> title.equalsIgnoreCase(excludedTitle));
        }

        available.setAll(candidates);
        selected.retainAll(new ArrayList<>(available));

        applySearch();
        updateStatus();
    }

    /**
     * Keeps one note out of the list.
     *
     * <p>Call this before {@link #setCategory(String)} so the exclusion is in
     * place when the folder is first read.
     *
     * @param title the note title to hide, or {@code null} to hide none
     */
    public void setExcludedNote(String title) {
        this.excludedTitle = title;
    }

    /**
     * Pre-ticks a set of references, keeping only those present in the folder.
     *
     * @param references the titles to tick
     */
    public void setSelected(List<String> references) {
        selected.clear();
        for (String reference : references) {
            if (available.contains(reference)) {
                selected.add(reference);
            }
        }
        list.refresh();
        updateStatus();
    }

    /**
     * @return the ticked note titles, in the order they were chosen
     */
    public List<String> getSelected() {
        return List.copyOf(selected);
    }

    private void applySearch() {
        String search = searchField.getText() == null
                ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);

        visible.setPredicate(title ->
                search.isEmpty() || title.toLowerCase(Locale.ROOT).contains(search));
    }

    private void updateStatus() {
        if (category == null || category.isBlank()) {
            statusLabel.setText(I18n.t("notepicker.status.noCategory"));
            return;
        }
        if (available.isEmpty()) {
            statusLabel.setText(I18n.t("notepicker.status.empty", category));
            return;
        }
        statusLabel.setText(
                I18n.t("notepicker.status.count", selected.size(), available.size(), category));
    }

    /**
     * A row with a tick box.
     *
     * <p>JavaFX has a {@code CheckBoxListCell}, but it needs each item to expose
     * a boolean property. The items here are plain strings, so a small custom
     * cell backed by the selection set is simpler than wrapping every title in a
     * holder object.
     */
    private class CheckBoxCell extends javafx.scene.control.ListCell<String> {

        private final CheckBox checkBox = new CheckBox();

        CheckBoxCell() {
            checkBox.setOnAction(event -> {
                String title = getItem();
                if (title == null) {
                    return;
                }
                if (checkBox.isSelected()) {
                    selected.add(title);
                } else {
                    selected.remove(title);
                }
                updateStatus();
            });
        }

        @Override
        protected void updateItem(String title, boolean empty) {
            super.updateItem(title, empty);

            if (empty || title == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            checkBox.setText(title);
            checkBox.setSelected(selected.contains(title));
            setGraphic(checkBox);
            setText(null);
        }
    }
}
