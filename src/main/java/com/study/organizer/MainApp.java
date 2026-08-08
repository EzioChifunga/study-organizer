package com.study.organizer;

import com.study.organizer.service.StudyDataService;
import com.study.organizer.service.TimerService;
import com.study.organizer.ui.Dialogs;
import com.study.organizer.ui.I18n;
import com.study.organizer.ui.MainWindow;
import com.study.organizer.ui.ThemeManager;
import com.study.organizer.vault.ObsidianVault;
import com.study.organizer.vault.VaultLocation;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * The real application: sessions are stored as Markdown notes in an Obsidian
 * vault.
 *
 * <p>Run it with:
 *
 * <pre>
 *   ./mvnw javafx:run
 * </pre>
 *
 * <p>There is nothing to set up. If no vault has been chosen, a {@code vault}
 * folder is created next to the application and used. Point it at an existing
 * Obsidian vault from the toolbar whenever you like.
 *
 * <p>To try the interface on invented data instead, run {@link DemoApp}:
 *
 * <pre>
 *   ./mvnw javafx:run -Pdemo
 * </pre>
 *
 * <p>The window itself is built by {@link MainWindow}. All this class does is
 * open the vault and hand it over.
 */
public class MainApp extends Application {

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
        Path vaultFolder = VaultLocation.current();

        ObsidianVault vault;
        try {
            vault = new ObsidianVault(vaultFolder);
        } catch (RuntimeException e) {
            showStartupFailure(vaultFolder, e);
            return;
        }

        TimerService timer = new TimerService();
        data = new StudyDataService(vault);

        new MainWindow(timer, data, null).show(stage, I18n.t("app.title"));

        // Read the vault after the window is visible, so the application appears
        // immediately rather than while the folder is being scanned.
        data.reload(error -> Dialogs.showError(I18n.t("app.readError"), error));
    }

    /**
     * Explains that the vault folder could not be opened, then exits.
     *
     * <p>Almost always a permissions problem or a configured folder that has
     * since been deleted, so the message names the folder and says how to change
     * it rather than showing a stack trace.
     */
    private void showStartupFailure(Path folder, RuntimeException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        ThemeManager.styleDialog(alert.getDialogPane());
        alert.setTitle(I18n.t("app.startupFailure.title"));
        alert.setHeaderText(I18n.t("app.startupFailure.header"));
        alert.setContentText(I18n.t("app.startupFailure.body", folder, e.getMessage()));
        alert.getDialogPane().setPrefWidth(640);
        alert.showAndWait();
    }

    @Override
    public void init() {
        // An escape hatch for a saved vault path that no longer works, so the
        // application can always be brought back to a usable state without
        // editing preferences by hand.
        if (Boolean.getBoolean("vault.reset")) {
            VaultLocation.reset();
        }
    }

    @Override
    public void stop() {
        if (data != null) {
            data.shutdown();
        }
    }
}
