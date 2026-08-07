package com.study.organizer.ui;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.FilteredSessionView;
import com.study.organizer.service.PointsCalculator;
import com.study.organizer.service.StudyDataService;
import com.study.organizer.util.DurationFormatter;
import com.study.organizer.vault.ObsidianLauncher;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The history screen: every saved session in a table, with the ability to add,
 * edit and remove records.
 *
 * <h2>Why this screen exists</h2>
 * The dashboard and charts summarise. Summaries are useful, but they cannot show
 * you what you actually wrote about a particular Tuesday, and they give you no
 * way to fix a session you left running through lunch. This screen is the one
 * place the stored records are visible individually and can be corrected.
 *
 * <p>It is also where sessions studied away from the computer get entered, so
 * the totals describe all of your studying rather than only the timed part.
 */
public class HistoryPane extends VBox {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final StudyDataService data;
    private final TableView<StudySession> table = new TableView<>();
    private final SessionFilterPane filters;
    private final Label summaryLabel = new Label();
    private final NoteViewerPane noteViewer;

    /**
     * The list the table actually shows: the stored sessions, narrowed by the
     * shared filter bar, then sorted.
     *
     * <p>Owned by {@link FilteredSessionView}, not by this screen, because the
     * charts and the statistics read from the same one.
     */
    private final FilteredList<StudySession> filtered;

    /**
     * Builds the history screen.
     *
     * @param data    the shared data service, for saving and for the vault
     * @param filters the shared filter bar, which lives above the tabs
     * @param view    the sessions currently in view, after those filters
     */
    public HistoryPane(StudyDataService data, SessionFilterPane filters, FilteredSessionView view) {
        this.data = data;
        this.filters = filters;
        this.filtered = view.getSessions();
        this.noteViewer = new NoteViewerPane(data);

        setSpacing(12);
        setPadding(new Insets(20));

        buildTable();

        // The table and the note viewer share the height, so both stay usable
        // when the window is resized.
        SplitPane split = new SplitPane(new VBox(6, table, summaryLabel), noteViewer);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        VBox.setVgrow(split, Priority.ALWAYS);

        // Selecting a row opens its note below, which is what "expand to see the
        // record" means here.
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, old, current) -> showNoteFor(current));

        getChildren().addAll(buildToolbar(), split);

        // The filtered list changes both when the filter moves and when a
        // session is saved or deleted, so one listener covers the summary.
        filtered.addListener(
                (javafx.collections.ListChangeListener<StudySession>) change -> updateSummary());
        updateSummary();
    }

    // ------------------------------------------------------------------ table

    private void buildTable() {
        table.setItems(sortedView());
        table.setPlaceholder(buildPlaceholder());
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        // Columns share the available width rather than leaving a gap on the right.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("history-table");

        // The resize policy shares the window's width in proportion to these
        // preferred widths, so they are set to reflect how much room each column
        // deserves rather than an exact pixel count. Free text gets the most.
        table.getColumns().setAll(
                textColumn("Date", 130, session ->
                        session.getStartedAt().atZone(ZoneId.systemDefault()).format(DATE_FORMAT)),
                textColumn("Start", 60, session ->
                        session.getStartedAt().atZone(ZoneId.systemDefault()).format(TIME_FORMAT)),
                textColumn("Category", 110, StudySession::getCategory),
                durationColumn(),
                pointsColumn(),
                wrappingColumn("Summary", 330, StudySession::getSummary),
                wrappingColumn("Observations", 230, StudySession::getObservations));

        // Double-clicking a row is the fastest way to open it, and it is what
        // people try first in any table of records.
        table.setRowFactory(view -> {
            var row = new javafx.scene.control.TableRow<StudySession>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    editSession(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * Wraps the filtered list so it can be ordered.
     *
     * <p>Two things can decide the order: the "Sort by" box, and clicking a
     * column header. The header wins when it is in use — a click is a direct,
     * momentary instruction, so it should override the standing preference —
     * and the box takes over again as soon as the header sort is cleared.
     */
    private SortedList<StudySession> sortedView() {
        SortedList<StudySession> sorted = new SortedList<>(filtered);

        sorted.comparatorProperty().bind(Bindings.createObjectBinding(
                () -> table.getComparator() != null
                        ? table.getComparator()
                        : filters.comparatorProperty().get(),
                table.comparatorProperty(), filters.comparatorProperty()));

        return sorted;
    }

    private Label buildPlaceholder() {
        Label placeholder = new Label(
                "No sessions yet. Time one on the Dashboard, or use \"Add past session\" "
                        + "to record studying that was not timed.");
        placeholder.getStyleClass().add("empty-state");
        placeholder.setWrapText(true);
        return placeholder;
    }

    /** A plain text column. */
    private TableColumn<StudySession, String> textColumn(
            String title, double width, java.util.function.Function<StudySession, String> reader) {

        TableColumn<StudySession, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(reader.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /**
     * A text column whose contents may be long.
     *
     * <p>Summaries and observations are free text and can run to several lines.
     * They are truncated in the cell and shown in full in a tooltip, which keeps
     * every row the same height while leaving the whole text reachable.
     */
    private TableColumn<StudySession, String> wrappingColumn(
            String title, double width, java.util.function.Function<StudySession, String> reader) {

        TableColumn<StudySession, String> column = textColumn(title, width, reader);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(value);
                    Tooltip tooltip = new Tooltip(value);
                    tooltip.setWrapText(true);
                    tooltip.setMaxWidth(420);
                    setTooltip(tooltip);
                }
            }
        });
        return column;
    }

    /**
     * The duration column.
     *
     * <p>Sorted on the raw seconds, not the text: sorting "1h 5m" and "45m"
     * alphabetically would put the shorter session first.
     */
    private TableColumn<StudySession, Long> durationColumn() {
        TableColumn<StudySession, Long> column = new TableColumn<>("Duration");
        column.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getDurationSeconds()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Long seconds, boolean empty) {
                super.updateItem(seconds, empty);
                setText(empty || seconds == null ? null : DurationFormatter.formatShort(seconds));
            }
        });
        column.setPrefWidth(90);
        return column;
    }

    /** The points column, sorted numerically for the same reason as duration. */
    private TableColumn<StudySession, Double> pointsColumn() {
        TableColumn<StudySession, Double> column = new TableColumn<>("Points");
        column.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getPoints()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Double points, boolean empty) {
                super.updateItem(points, empty);
                setText(empty || points == null ? null : PointsCalculator.format(points));
            }
        });
        column.setPrefWidth(80);
        return column;
    }

    // -------------------------------------------------------------- toolbars

    private HBox buildToolbar() {
        Label heading = new Label("Session history");
        heading.getStyleClass().add("chart-title");

        Button addButton = new Button("Add past session");
        addButton.getStyleClass().addAll("action-button", "primary-button");
        addButton.setOnAction(event -> addSession());

        Button editButton = new Button("Edit");
        editButton.getStyleClass().add("action-button");
        editButton.setOnAction(event -> editSession(selected()));

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().addAll("action-button", "danger-button");
        deleteButton.setOnAction(event -> deleteSession(selected()));

        Button openFolderButton = new Button("Open folder");
        openFolderButton.getStyleClass().add("action-button");
        openFolderButton.setTooltip(new Tooltip(
                "Open this category's folder in Obsidian, or the whole vault if "
                        + "no category is filtered."));
        openFolderButton.setOnAction(event -> openFolderInObsidian());

        // Edit and delete act on a row, so they stay unavailable until one is
        // chosen rather than failing with "nothing selected" when pressed.
        var nothingSelected = table.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(nothingSelected);
        deleteButton.disableProperty().bind(nothingSelected);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10,
                heading, spacer, openFolderButton, addButton, editButton, deleteButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /**
     * Opens a folder in Obsidian.
     *
     * <p>Which folder depends on what you are looking at: with a category
     * filtered, that category's folder; otherwise the whole vault. That matches
     * what "open this" would mean given what is on screen.
     */
    private void openFolderInObsidian() {
        String category = filters.getSelectedCategory();

        var folder = category == null
                ? data.getVault().getRoot()
                : data.getVault().categoryFolder(category);

        String problem = ObsidianLauncher.openFolder(folder);
        if (problem != null) {
            Dialogs.showWarning("Could not open Obsidian", problem);
        }
    }

    /** Opens the selected session's note in the viewer below the table. */
    private void showNoteFor(StudySession session) {
        if (session == null || session.getId() == null) {
            noteViewer.clear();
        } else {
            noteViewer.show(session.getId());
        }
    }

    /**
     * The summary line under the table, describing whatever is currently shown.
     *
     * <p>It reports on the <b>filtered</b> set, not the whole history, which is
     * what makes the filters useful for more than finding one row: narrow to a
     * category and a month and this line answers "how much, how often, how long
     * on average" for exactly that slice.
     */
    private void updateSummary() {
        summaryLabel.getStyleClass().setAll("stat-caption");

        if (filtered.isEmpty()) {
            summaryLabel.setText("No sessions match these filters.");
            return;
        }

        long totalSeconds = filtered.stream()
                .mapToLong(StudySession::getDurationSeconds)
                .sum();
        long averageSeconds = totalSeconds / filtered.size();

        String text = String.format(
                "%d session%s  -  %s total  -  %s points  -  %s average",
                filtered.size(),
                filtered.size() == 1 ? "" : "s",
                DurationFormatter.formatShort(totalSeconds),
                PointsCalculator.format(PointsCalculator.fromSeconds(totalSeconds)),
                DurationFormatter.formatShort(averageSeconds));

        // Naming the leading category is only informative when more than one is
        // in view; with a category filtered it would just repeat the filter.
        summaryLabel.setText(text + topCategoryOf().map(top -> "  -  mostly " + top).orElse(""));
    }

    /**
     * The category with the most time in the filtered set, when there is more
     * than one to choose between.
     *
     * @return the leading category, or empty if the answer would be trivial
     */
    private Optional<String> topCategoryOf() {
        Map<String, Long> byCategory = new HashMap<>();
        for (StudySession session : filtered) {
            byCategory.merge(session.getCategory(), session.getDurationSeconds(), Long::sum);
        }

        if (byCategory.size() < 2) {
            return Optional.empty();
        }
        return byCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    // --------------------------------------------------------------- actions

    private StudySession selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    /** Opens a blank form and saves whatever the user describes. */
    private void addSession() {
        Optional<StudySession> created =
                new SessionEditorDialog(data).prompt();

        created.ifPresent(session -> data.saveSession(
                session,
                () -> { },
                error -> Dialogs.showError("Could not add the session", error)));
    }

    /** Opens the form filled in, and overwrites the record on save. */
    private void editSession(StudySession session) {
        if (session == null) {
            return;
        }

        Optional<StudySession> edited =
                new SessionEditorDialog(session, data).prompt();

        edited.ifPresent(updated -> data.updateSession(
                updated,
                () -> { },
                error -> Dialogs.showError("Could not save your changes", error)));
    }

    /** Removes a session, after confirming, since it cannot be undone. */
    private void deleteSession(StudySession session) {
        if (session == null) {
            return;
        }

        String when = session.getStartedAt().atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
        boolean confirmed = Dialogs.confirm(
                "Delete this session?",
                session.getCategory() + " on " + when + ", "
                        + DurationFormatter.formatShort(session.getDurationSeconds())
                        + ". This cannot be undone.");

        if (confirmed) {
            data.deleteSession(
                    session.getId(),
                    () -> { },
                    error -> Dialogs.showError("Could not delete the session", error));
        }
    }
}
