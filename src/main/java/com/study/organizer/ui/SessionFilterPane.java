package com.study.organizer.ui;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.StudyDataService;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The filter grid above the history table.
 *
 * <p>Search, category, a date range, a duration range and a sort order, laid out
 * as a grid so related controls sit together and the whole thing stays one
 * glance rather than a long row that runs off the edge.
 *
 * <p>The pane owns no data. It publishes two things — a {@link Predicate} saying
 * which sessions to show and a {@link Comparator} saying in what order — and the
 * history screen applies them. Keeping it that way means this class can be read
 * on its own, and the table does not have to know what a filter is.
 *
 * <h2>Why a duration range rather than a single minimum</h2>
 * Both ends are useful and they answer different questions. A minimum finds the
 * real study blocks among a lot of short ones; a maximum finds the sessions that
 * were too short to be worth much, which is usually what you want to look at
 * when a week's total disappoints.
 */
public class SessionFilterPane extends VBox {

    /** Shown in the category box to mean "do not filter". */
    public static final String ALL_CATEGORIES = "All categories";

    /** The largest value the duration spinners accept, in minutes. */
    private static final int MAX_MINUTES = 600;

    private final StudyDataService data;
    private final ZoneId zone = ZoneId.systemDefault();

    private final TextField searchField = new TextField();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final Spinner<Integer> minMinutes = new Spinner<>(0, MAX_MINUTES, 0, 5);
    private final Spinner<Integer> maxMinutes = new Spinner<>(0, MAX_MINUTES, MAX_MINUTES, 5);
    private final ComboBox<SortOrder> sortBox = new ComboBox<>();

    /** Which sessions to show. Replaced whenever any control changes. */
    private final ObjectProperty<Predicate<StudySession>> predicate =
            new SimpleObjectProperty<>(session -> true);

    /** What order to show them in. */
    private final ObjectProperty<Comparator<StudySession>> comparator =
            new SimpleObjectProperty<>(SortOrder.NEWEST.comparator());

    /**
     * The ready-made orderings offered in the sort box.
     *
     * <p>These are what "best ranked" means in practice: the longest sessions,
     * the highest-scoring ones, or simply the most recent. Clicking a column
     * header still works and takes precedence — this is the shortcut for the
     * orderings worth having a name.
     */
    public enum SortOrder {

        NEWEST("Newest first",
                Comparator.comparing(StudySession::getStartedAt).reversed()),

        OLDEST("Oldest first",
                Comparator.comparing(StudySession::getStartedAt)),

        LONGEST("Longest sessions",
                Comparator.comparingLong(StudySession::getDurationSeconds).reversed()),

        SHORTEST("Shortest sessions",
                Comparator.comparingLong(StudySession::getDurationSeconds)),

        MOST_POINTS("Most points",
                Comparator.comparingDouble(StudySession::getPoints).reversed()),

        BY_CATEGORY("Category (A-Z)",
                Comparator.comparing(StudySession::getCategory, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.comparing(StudySession::getStartedAt).reversed()));

        private final String label;
        private final Comparator<StudySession> comparator;

        SortOrder(String label, Comparator<StudySession> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        public Comparator<StudySession> comparator() {
            return comparator;
        }

        @Override
        public String toString() {
            // The combo box shows this, so the enum can be put in directly
            // rather than needing a cell factory.
            return label;
        }
    }

    /**
     * Builds the filter grid.
     *
     * @param data the data service, for the list of categories
     */
    public SessionFilterPane(StudyDataService data) {
        this.data = data;

        setSpacing(8);
        getStyleClass().add("filter-pane");
        setPadding(new Insets(12));

        buildControls();
        getChildren().add(buildGrid());

        rebuildCategoryChoices();
        data.getCategories().addListener(
                (javafx.collections.ListChangeListener<String>) change -> rebuildCategoryChoices());

        applyFilter();
    }

    private void buildControls() {
        searchField.setPromptText("Search summaries, observations and categories");
        searchField.textProperty().addListener((observable, old, current) -> applyFilter());

        categoryBox.setMaxWidth(Double.MAX_VALUE);
        categoryBox.setOnAction(event -> applyFilter());

        fromDate.setPromptText("Any date");
        toDate.setPromptText("Any date");
        fromDate.setMaxWidth(Double.MAX_VALUE);
        toDate.setMaxWidth(Double.MAX_VALUE);
        fromDate.valueProperty().addListener((observable, old, current) -> applyFilter());
        toDate.valueProperty().addListener((observable, old, current) -> applyFilter());

        for (Spinner<Integer> spinner : List.of(minMinutes, maxMinutes)) {
            spinner.setEditable(true);
            spinner.setMaxWidth(Double.MAX_VALUE);
            spinner.valueProperty().addListener((observable, old, current) -> applyFilter());
        }

        sortBox.setItems(FXCollections.observableArrayList(SortOrder.values()));
        sortBox.setValue(SortOrder.NEWEST);
        sortBox.setMaxWidth(Double.MAX_VALUE);
        sortBox.setOnAction(event -> comparator.set(sortBox.getValue().comparator()));
    }

    private GridPane buildGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        // Four equal columns, so the controls line up in a grid rather than
        // each taking whatever width its contents happen to need.
        for (int i = 0; i < 4; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }

        // Row 0 - the two broadest filters, search spanning half the width.
        grid.add(labelled("Search", searchField), 0, 0, 2, 1);
        grid.add(labelled("Category", categoryBox), 2, 0);
        grid.add(labelled("Sort by", sortBox), 3, 0);

        // Row 1 - the two ranges, each pair of ends side by side.
        grid.add(labelled("From", fromDate), 0, 1);
        grid.add(labelled("To", toDate), 1, 1);
        grid.add(labelled("Min minutes", minMinutes), 2, 1);
        grid.add(labelled("Max minutes", maxMinutes), 3, 1);

        grid.add(buildClearRow(), 0, 2, 4, 1);
        return grid;
    }

    /** One captioned control, so every cell in the grid has the same shape. */
    private VBox labelled(String caption, javafx.scene.Node control) {
        Label label = new Label(caption);
        label.getStyleClass().add("stat-caption");

        VBox box = new VBox(3, label, control);
        box.setFillWidth(true);
        return box;
    }

    private HBox buildClearRow() {
        Button clear = new Button("Clear filters");
        clear.getStyleClass().add("action-button");
        clear.setOnAction(event -> clearFilters());

        HBox row = new HBox(clear);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    /** Puts every control back to the state that shows everything. */
    public void clearFilters() {
        searchField.clear();
        categoryBox.getSelectionModel().select(ALL_CATEGORIES);
        fromDate.setValue(null);
        toDate.setValue(null);
        minMinutes.getValueFactory().setValue(0);
        maxMinutes.getValueFactory().setValue(MAX_MINUTES);
        sortBox.setValue(SortOrder.NEWEST);

        comparator.set(SortOrder.NEWEST.comparator());
        applyFilter();
    }

    /** Keeps the category box in step with the folders that actually exist. */
    private void rebuildCategoryChoices() {
        String previous = categoryBox.getValue();

        var choices = FXCollections.observableArrayList(ALL_CATEGORIES);
        choices.addAll(data.getCategories());
        categoryBox.setItems(choices);

        // Select through the selection model rather than setValue: replacing the
        // items clears the selection, and setting the value the property already
        // holds fires no change, leaving the box looking blank.
        categoryBox.getSelectionModel().select(
                previous != null && choices.contains(previous) ? previous : ALL_CATEGORIES);
    }

    /**
     * Rebuilds the predicate from every control at once.
     *
     * <p>Building the whole thing on each change, rather than composing
     * predicates as controls are touched, keeps the rules in one readable place
     * and removes any question of the filters getting out of step with the
     * boxes.
     */
    private void applyFilter() {
        String search = searchField.getText() == null
                ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);

        String category = categoryBox.getValue();
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();

        long minSeconds = valueOf(minMinutes, 0) * 60L;
        long maxSeconds = valueOf(maxMinutes, MAX_MINUTES) * 60L;

        // A max at the top of the spinner's range means "no upper limit", so a
        // long session is not hidden just because the spinner cannot go higher.
        boolean noUpperLimit = valueOf(maxMinutes, MAX_MINUTES) >= MAX_MINUTES;

        predicate.set(session -> {
            if (category != null && !ALL_CATEGORIES.equals(category)
                    && !category.equals(session.getCategory())) {
                return false;
            }

            LocalDate date = session.getStudyDate(zone);
            if (from != null && date.isBefore(from)) {
                return false;
            }
            if (to != null && date.isAfter(to)) {
                return false;
            }

            long seconds = session.getDurationSeconds();
            if (seconds < minSeconds) {
                return false;
            }
            if (!noUpperLimit && seconds > maxSeconds) {
                return false;
            }

            if (search.isEmpty()) {
                return true;
            }
            return contains(session.getSummary(), search)
                    || contains(session.getObservations(), search)
                    || contains(session.getCategory(), search);
        });
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    /** Reads a spinner defensively - an editable spinner can hold null mid-typing. */
    private static int valueOf(Spinner<Integer> spinner, int fallback) {
        SpinnerValueFactory<Integer> factory = spinner.getValueFactory();
        Integer value = factory == null ? null : factory.getValue();
        return value == null ? fallback : value;
    }

    /** @return the category currently filtered on, or {@code null} for all of them */
    public String getSelectedCategory() {
        String category = categoryBox.getValue();
        return category == null || ALL_CATEGORIES.equals(category) ? null : category;
    }

    /** @return which sessions to show */
    public ReadOnlyObjectProperty<Predicate<StudySession>> predicateProperty() {
        return predicate;
    }

    /** @return what order to show them in */
    public ReadOnlyObjectProperty<Comparator<StudySession>> comparatorProperty() {
        return comparator;
    }
}
