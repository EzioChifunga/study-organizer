package com.study.organizer.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The Preferences dialog: currently just the language picker, kept as its own
 * small dialog rather than a screen of its own so it stays out of the way of
 * the four data screens in the nav bar.
 *
 * <h2>Why the change needs a restart</h2>
 * Every screen builds its labels once, when it is constructed, from whichever
 * bundle {@link I18n} is holding at that moment - the same "read the setting
 * once at start-up" approach the vault picker already uses. Making every label
 * in the application re-read itself live would mean threading a listener
 * through every constructor for a setting that is changed rarely. Telling the
 * user plainly, right where they made the choice, is a fair trade for how much
 * simpler that keeps the rest of the interface.
 */
public class PreferencesDialog extends Dialog<Void> {

    public PreferencesDialog() {
        ThemeManager.styleDialog(getDialogPane());

        setTitle(I18n.t("prefs.title"));
        setHeaderText(null);

        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setContent(buildContent());
    }

    private VBox buildContent() {
        Label caption = new Label(I18n.t("prefs.language.label"));
        caption.getStyleClass().add("stat-caption");

        ComboBox<Language> languageBox = new ComboBox<>(
                FXCollections.observableArrayList(Language.values()));
        languageBox.setValue(I18n.getLanguage());
        languageBox.setMaxWidth(Double.MAX_VALUE);

        Label description = new Label(I18n.t("prefs.language.caption"));
        description.getStyleClass().add("stat-caption");
        description.setWrapText(true);

        Label restartNotice = new Label(I18n.t("prefs.language.restartNotice"));
        restartNotice.getStyleClass().add("stat-caption");
        restartNotice.setWrapText(true);
        restartNotice.setVisible(false);
        restartNotice.setManaged(false);

        languageBox.setOnAction(event -> {
            Language chosen = languageBox.getValue();
            if (chosen != I18n.getLanguage()) {
                I18n.setLanguage(chosen);
                restartNotice.setVisible(true);
                restartNotice.setManaged(true);
            }
        });

        VBox content = new VBox(4, description, caption, languageBox, restartNotice);
        content.setPadding(new Insets(14));
        content.setPrefWidth(360);
        return content;
    }
}
