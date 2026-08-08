package com.study.organizer.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;

/**
 * Small helpers for the standard pop-up messages.
 *
 * <p>Collected in one place so every dialog in the application looks and behaves
 * the same, and so the views are not cluttered with {@link Alert} setup code.
 */
public final class Dialogs {

    private Dialogs() {
        throw new AssertionError("Dialogs is a utility class and cannot be instantiated.");
    }

    /**
     * Shows a warning about something the user needs to correct.
     *
     * @param header  a short title, e.g. "Choose a category"
     * @param message what to do about it
     */
    public static void showWarning(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        ThemeManager.styleDialog(alert.getDialogPane());
        alert.setTitle(I18n.t("common.appName"));
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an error, with the technical detail tucked into an expandable area.
     *
     * <p>The plain-language message goes at the top; the exception text is kept
     * available underneath rather than thrown away, because it is what makes a
     * connection problem diagnosable.
     *
     * @param header a short title, e.g. "Could not save"
     * @param error  the underlying failure
     */
    public static void showError(String header, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        ThemeManager.styleDialog(alert.getDialogPane());
        alert.setTitle(I18n.t("common.appName"));
        alert.setHeaderText(header);
        alert.setContentText(error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage());

        TextArea details = new TextArea(describe(error));
        details.setEditable(false);
        details.setWrapText(true);
        alert.getDialogPane().setExpandableContent(details);

        alert.showAndWait();
    }

    /**
     * Asks the user to confirm an action that cannot be undone.
     *
     * @param header  a short question, e.g. "Cancel this session?"
     * @param message what will happen if they confirm
     * @return {@code true} if the user chose OK
     */
    public static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        ThemeManager.styleDialog(alert.getDialogPane());
        alert.setTitle(I18n.t("common.appName"));
        alert.setHeaderText(header);
        alert.setContentText(message);

        return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }

    /** Builds a readable chain of "caused by" messages from an exception. */
    private static String describe(Throwable error) {
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            text.append(current.getClass().getName())
                    .append(": ")
                    .append(current.getMessage())
                    .append(System.lineSeparator());
            current = current.getCause();
        }
        return text.toString();
    }
}
