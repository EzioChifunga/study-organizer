package com.study.organizer.ui;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.FilteredSessionView;
import com.study.organizer.service.StudyDataService;
import com.study.organizer.service.TimerService;
import com.study.organizer.vault.ObsidianLauncher;
import com.study.organizer.vault.ObsidianVault;
import com.study.organizer.vault.VaultLocation;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Assembles the application window: the three tabs, the finish-and-save flow,
 * and the close confirmation.
 *
 * <p>This is kept separate from the entry points because there are two of them —
 * {@link com.study.organizer.MainApp}, which opens your vault, and
 * {@link com.study.organizer.DemoApp}, which runs on invented data. Both build
 * exactly the same window, so the window is built here once and the entry points
 * differ only in which repositories they hand over.
 */
public class MainWindow {

    private static final int WINDOW_WIDTH = 940;
    private static final int WINDOW_HEIGHT = 760;
    private static final int MIN_WIDTH = 760;
    private static final int MIN_HEIGHT = 600;

    private final TimerService timer;
    private final StudyDataService data;
    private final String bannerText;
    private final ThemeManager themes = new ThemeManager();

    /**
     * @param timer      the shared timer
     * @param data       the shared data service
     * @param bannerText a strip shown across the top, or {@code null} for none.
     *                   The demo uses it to make clear the data is not real.
     */
    public MainWindow(TimerService timer, StudyDataService data, String bannerText) {
        this.timer = timer;
        this.data = data;
        this.bannerText = bannerText;
    }

    /**
     * Puts the window on screen.
     *
     * @param stage the primary stage supplied by JavaFX
     * @param title the window title
     */
    public void show(Stage stage, String title) {
        stage.setTitle(title);
        stage.setScene(buildScene());
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setOnCloseRequest(this::confirmClose);
        stage.show();
    }

    /**
     * Builds the scene graph.
     *
     * @return the assembled scene, with the stylesheet applied
     */
    public Scene buildScene() {
        // One filter bar, one filtered view, three screens reading from it. That
        // is what keeps the table, the charts and the statistics describing the
        // same slice instead of each answering a different question.
        SessionFilterPane filters = new SessionFilterPane(data);
        FilteredSessionView view =
                new FilteredSessionView(data.getSessions(), filters.predicateProperty());

        Tab dashboardTab = new Tab("Dashboard",
                new DashboardView(timer, data, themes.themeProperty(), this::handleFinish));
        Tab historyTab = new Tab("History", new HistoryPane(data, filters, view));
        Tab chartsTab = new Tab("Charts", new ChartsPane(view, themes.themeProperty()));
        Tab statisticsTab = new Tab("Statistics", new StatisticsPane(view));

        TabPane tabs = new TabPane(dashboardTab, historyTab, chartsTab, statisticsTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane root = new BorderPane(tabs);
        root.setTop(new VBox(buildTopBar(), buildFilterBar(filters, tabs, dashboardTab)));

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(
                MainWindow.class.getResource("/styles.css").toExternalForm());

        // Applies the saved theme now, and re-applies on every later change.
        themes.applyTo(scene);

        return scene;
    }

    /**
     * Wraps the shared filter bar so it only appears where it means something.
     *
     * <p>The Dashboard is about now — today's time, the running streak, the live
     * clock — so a date range and a duration filter have nothing to act on
     * there. Showing them anyway would invite the user to set a filter and then
     * wonder why nothing moved.
     *
     * <p>{@code managed} is bound alongside {@code visible} so the hidden bar
     * gives its space back rather than leaving a gap.
     *
     * @param filters      the shared filter grid
     * @param tabs         the tab pane, watched for the selected tab
     * @param dashboardTab the tab the filters do not apply to
     * @return the filter bar, ready to place
     */
    private VBox buildFilterBar(SessionFilterPane filters, TabPane tabs, Tab dashboardTab) {
        VBox holder = new VBox(filters);
        holder.setPadding(new Insets(10, 12, 0, 12));

        var showing = tabs.getSelectionModel().selectedItemProperty().isNotEqualTo(dashboardTab);
        holder.visibleProperty().bind(showing);
        holder.managedProperty().bind(showing);

        return holder;
    }

    /** The strip above the tabs: the demo notice, if any, and the theme toggle. */
    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        if (bannerText != null) {
            Label banner = new Label(bannerText);
            banner.getStyleClass().add("demo-banner");
            bar.getChildren().add(banner);
            bar.getStyleClass().add("top-bar-demo");
        }

        // A spacer that soaks up the free width, pushing the toggle to the right.
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(spacer, buildVaultButton(), buildThemeToggle());
        return bar;
    }

    /**
     * The vault button: shows where notes are being stored, and lets you change it.
     *
     * <p>Kept visible at all times rather than hidden in a settings screen,
     * because "which folder am I writing to?" is the single most important thing
     * to be sure of before starting a session.
     */
    private Button buildVaultButton() {
        Button button = new Button();
        button.getStyleClass().add("theme-toggle");

        Path root = data.getVault().getRoot();
        button.setText("Vault: " + root.getFileName());
        button.setTooltip(new Tooltip(root + System.lineSeparator()
                + System.lineSeparator()
                + "Click to open it in Obsidian, or to choose a different vault."));

        button.setOnAction(event -> showVaultMenu(button));
        return button;
    }

    private void showVaultMenu(Button anchor) {
        MenuItem open = new MenuItem("Open vault in Obsidian");
        open.setOnAction(event -> {
            String problem = ObsidianLauncher.openFolder(data.getVault().getRoot());
            if (problem != null) {
                Dialogs.showWarning("Could not open Obsidian", problem);
            }
        });

        MenuItem change = new MenuItem("Choose a different vault...");
        change.setOnAction(event -> chooseVault(anchor));

        ContextMenu menu = new ContextMenu(open, change);

        // A context menu is its own window and cannot see the main scene's
        // stylesheet, so it has to be given one or it appears in JavaFX's
        // default white theme.
        menu.show(anchor, Side.BOTTOM, 0, 0);
        ThemeManager.styleContextMenu(menu);
    }

    /**
     * Lets the user point the application at another vault folder.
     *
     * <p>The change takes effect on the next launch rather than immediately.
     * Swapping the vault under a running window would mean rebuilding every
     * screen and deciding what to do with a session in progress; restarting is
     * both simpler and easier to reason about, and this is a rare action.
     */
    private void chooseVault(Button anchor) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose your Obsidian vault folder");

        Path current = data.getVault().getRoot();
        if (Files.isDirectory(current)) {
            chooser.setInitialDirectory(current.toFile());
        }

        File chosen = chooser.showDialog(anchor.getScene().getWindow());
        if (chosen == null) {
            return;
        }

        Path folder = chosen.toPath();
        String problem = ObsidianVault.describeProblem(folder);
        if (problem != null) {
            Dialogs.showWarning("That folder cannot be used", problem);
            return;
        }

        VaultLocation.set(folder);
        Dialogs.showWarning("Vault changed",
                "Sessions will be stored in:" + System.lineSeparator()
                        + folder + System.lineSeparator() + System.lineSeparator()
                        + "Restart the application for the change to take effect.");
    }

    /**
     * The light/dark switch.
     *
     * <p>The label names the theme you would switch <i>to</i>, not the one you
     * are in, so the button describes what pressing it does.
     */
    private Button buildThemeToggle() {
        Button toggle = new Button();
        toggle.getStyleClass().add("theme-toggle");

        toggle.textProperty().bind(Bindings.createStringBinding(
                () -> themes.getTheme() == Theme.DARK ? "☀  Light" : "☽  Dark",
                themes.themeProperty()));

        toggle.setOnAction(event -> themes.toggle());
        return toggle;
    }

    /**
     * Handles the Finish button: collect the summary, save, then reset the timer.
     *
     * <p>The order matters. The timer is only reset <b>after</b> the save has
     * succeeded, so a failure leaves the session intact and the user can simply
     * try again. If the summary dialog is cancelled, nothing happens at all and
     * the clock is still holding the session.
     */
    private void handleFinish() {
        FinishSessionDialog dialog = new FinishSessionDialog(
                timer.getCurrentCategory(), timer.getElapsedSeconds(), data);

        Optional<FinishSessionDialog.Result> result = dialog.prompt();
        if (result.isEmpty()) {
            return;   // Cancelled - the session is untouched.
        }

        StudySession timed = timer.finish(result.get().summary(), result.get().observations());

        // The timer knows nothing about notes, so the references are attached here.
        StudySession session = new StudySession(
                timed.getId(), timed.getCategory(), timed.getStartedAt(), timed.getEndedAt(),
                timed.getDurationSeconds(), timed.getPoints(), timed.getSummary(),
                timed.getObservations(), result.get().references());

        data.saveSession(
                session,
                timer::reset,
                error -> Dialogs.showError(
                        "Could not write the session note. It has not been lost - press "
                                + "Finish again once the problem is fixed.", error));
    }

    /** Asks for confirmation before closing while a session is still active. */
    private void confirmClose(WindowEvent event) {
        if (timer.isSessionActive()) {
            boolean quit = Dialogs.confirm("A session is still running",
                    "If you close now the session will be lost. Close anyway?");
            if (!quit) {
                event.consume();   // Cancels the close.
            }
        }
    }
}
