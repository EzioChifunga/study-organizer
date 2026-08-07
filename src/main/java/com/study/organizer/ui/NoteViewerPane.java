package com.study.organizer.ui;

import com.study.organizer.service.StudyDataService;
import com.study.organizer.vault.ObsidianLauncher;
import com.study.organizer.vault.ObsidianVault;
import com.study.organizer.vault.SessionNote;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Shows one note from the vault, with its links turned into buttons you can
 * follow.
 *
 * <h2>What this is for</h2>
 * Expanding a record in the history should let you read what you actually wrote,
 * and step through to whatever it referenced. So the note's text is shown as it
 * is stored, and every {@code [[wiki link]]} in it becomes a clickable link that
 * opens that note in the same panel.
 *
 * <p>A back stack keeps the trail, because following two or three links and then
 * wanting to return is the normal way of reading. The Obsidian button hands the
 * current note to Obsidian itself, for when you want to edit rather than read.
 *
 * <h2>Why the Markdown is not rendered</h2>
 * The text is shown as plain Markdown rather than formatted. Rendering it would
 * mean either a Markdown library or a hand-written parser, and neither earns its
 * place here: these notes are short, and the raw text is exactly what Obsidian
 * shows you in edit mode anyway. The links are the part that needed work, and
 * those are pulled out and presented properly.
 */
public class NoteViewerPane extends VBox {

    private final StudyDataService data;

    private final Label titleLabel = new Label();
    private final Label pathLabel = new Label();
    private final TextArea bodyArea = new TextArea();
    private final FlowPane linkBar = new FlowPane(8, 8);
    private final Button backButton = new Button("< Back");
    private final Button obsidianButton = new Button("Open in Obsidian");

    /** The trail of notes visited, so Back can retrace it. */
    private final Deque<String> history = new ArrayDeque<>();

    /** The note currently shown, as a vault-relative path. */
    private String currentPath;

    /**
     * Builds the viewer, initially empty.
     *
     * @param data the data service, used to read notes and follow links
     */
    public NoteViewerPane(StudyDataService data) {
        this.data = data;

        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("note-viewer");

        buildHeader();
        buildBody();

        getChildren().addAll(buildToolbar(), titleLabel, pathLabel, bodyArea, buildLinkSection());

        clear();
    }

    private void buildHeader() {
        titleLabel.getStyleClass().add("chart-title");
        titleLabel.setWrapText(true);

        pathLabel.getStyleClass().add("stat-caption");
    }

    private void buildBody() {
        bodyArea.setEditable(false);
        bodyArea.setWrapText(true);
        bodyArea.setPrefRowCount(12);
        bodyArea.getStyleClass().add("note-body");
        VBox.setVgrow(bodyArea, Priority.ALWAYS);
    }

    private HBox buildToolbar() {
        backButton.getStyleClass().add("action-button");
        backButton.setOnAction(event -> goBack());

        obsidianButton.getStyleClass().addAll("action-button", "primary-button");
        obsidianButton.setOnAction(event -> openCurrentInObsidian());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(8, backButton, spacer, obsidianButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox buildLinkSection() {
        Label caption = new Label("References");
        caption.getStyleClass().add("stat-caption");

        ScrollPane scroller = new ScrollPane(linkBar);
        scroller.setFitToWidth(true);
        scroller.setPrefHeight(70);

        return new VBox(4, caption, scroller);
    }

    /**
     * Opens a note.
     *
     * @param relativePath the note's path relative to the vault root
     */
    public void show(String relativePath) {
        if (currentPath != null && !currentPath.equals(relativePath)) {
            history.push(currentPath);
        }
        display(relativePath);
    }

    /** Opens a note without adding the previous one to the back stack. */
    private void display(String relativePath) {
        currentPath = relativePath;

        String markdown;
        try {
            markdown = data.readNote(relativePath);
        } catch (RuntimeException e) {
            titleLabel.setText("Could not open this note");
            pathLabel.setText(relativePath);
            bodyArea.setText(e.getMessage());
            linkBar.getChildren().clear();
            updateButtons();
            return;
        }

        titleLabel.setText(SessionNote.titleOf(fileNameOf(relativePath)));
        pathLabel.setText(relativePath);
        bodyArea.setText(markdown);
        bodyArea.positionCaret(0);

        buildLinks(ObsidianVault.categoryOf(relativePath),
                SessionNote.findWikiLinks(markdown));

        updateButtons();
    }

    /**
     * Turns the note's links into buttons.
     *
     * <p>A link is resolved against the note's own folder, which is the only
     * place it is allowed to point. A link that resolves to nothing is shown
     * disabled and labelled, rather than hidden — a broken link is worth seeing,
     * since it usually means a note was renamed in Obsidian.
     *
     * @param category the folder the note lives in
     * @param titles   the link titles found in the text
     */
    private void buildLinks(String category, List<String> titles) {
        linkBar.getChildren().clear();

        if (titles.isEmpty()) {
            Label none = new Label("This note does not reference anything.");
            none.getStyleClass().add("stat-caption");
            linkBar.getChildren().add(none);
            return;
        }

        for (String title : titles) {
            String target = data.resolveLink(category, title);

            Hyperlink link = new Hyperlink(title);
            link.getStyleClass().add("note-link");

            if (target == null) {
                link.setDisable(true);
                link.setText(title + "  (missing)");
                link.setTooltip(new javafx.scene.control.Tooltip(
                        "No note called \"" + title + "\" in the " + category + " folder. "
                                + "It may have been renamed or moved in Obsidian."));
            } else {
                link.setOnAction(event -> show(target));
            }

            linkBar.getChildren().add(link);
        }
    }

    private void goBack() {
        if (!history.isEmpty()) {
            display(history.pop());
        }
    }

    private void openCurrentInObsidian() {
        if (currentPath == null) {
            return;
        }

        String problem = ObsidianLauncher.openNote(data.getVault().getRoot().resolve(currentPath));
        if (problem != null) {
            Dialogs.showWarning("Could not open Obsidian", problem);
        }
    }

    /** Empties the viewer, for when no record is selected. */
    public final void clear() {
        currentPath = null;
        history.clear();

        titleLabel.setText("No session selected");
        pathLabel.setText("");
        bodyArea.setText("Select a session in the table above to read its note "
                + "and follow its references.");
        linkBar.getChildren().clear();

        updateButtons();
    }

    private void updateButtons() {
        backButton.setDisable(history.isEmpty());
        obsidianButton.setDisable(currentPath == null);
    }

    private static String fileNameOf(String relativePath) {
        int separator = relativePath.lastIndexOf('/');
        return separator < 0 ? relativePath : relativePath.substring(separator + 1);
    }
}
