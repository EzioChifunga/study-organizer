package com.study.organizer;

import com.study.organizer.demo.DemoVault;
import com.study.organizer.service.StudyDataService;
import com.study.organizer.service.TimerService;
import com.study.organizer.ui.Dialogs;
import com.study.organizer.ui.I18n;
import com.study.organizer.ui.MainWindow;
import com.study.organizer.ui.ThemeManager;
import com.study.organizer.vault.ObsidianVault;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * The demo application: the same window, working on a throwaway vault full of
 * invented notes.
 *
 * <p>Run it with:
 *
 * <pre>
 *   ./mvnw javafx:run -Pdemo
 * </pre>
 *
 * <p>Your own vault is not touched. A temporary folder is filled with sample
 * categories, background notes and sessions, and deleted when the window closes.
 *
 * <p>Everything works for real here — starting sessions, editing them, following
 * references, opening a folder in Obsidian. The demo writes actual Markdown
 * files through exactly the same code the real application uses, so it is a
 * genuine rehearsal rather than a mock-up.
 *
 * <p>The only difference from {@link MainApp} is which vault is opened.
 */
public class DemoApp extends Application {

    /** Shown across the top so demo data is never mistaken for real data. */
    private static final String BANNER = I18n.t("app.demo.banner");

    private StudyDataService data;

    /**
     * Standard Java entry point.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        ObsidianVault vault;
        try {
            vault = DemoVault.create();
        } catch (IOException | RuntimeException e) {
            showStartupFailure(e);
            return;
        }

        TimerService timer = new TimerService();
        data = new StudyDataService(vault);

        new MainWindow(timer, data, BANNER).show(stage, I18n.t("app.demo.title"));

        data.reload(error -> Dialogs.showError(I18n.t("app.demo.readError"), error));
    }

    private void showStartupFailure(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        ThemeManager.styleDialog(alert.getDialogPane());
        alert.setTitle(I18n.t("app.demo.startupFailure.title"));
        alert.setHeaderText(I18n.t("app.demo.startupFailure.header"));
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (data != null) {
            data.shutdown();
        }
    }
}
