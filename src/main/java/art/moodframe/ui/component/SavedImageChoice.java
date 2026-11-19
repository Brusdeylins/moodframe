package art.moodframe.ui.component;

import art.moodframe.model.SavedImage;
import art.moodframe.util.I18nManager;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The choice "Saved" over the attached images: a button that opens the saved images of the library as a list with a
 * search, picture, name, size and format, the favorites first. A click on an image attaches it, or removes it again if
 * it is attached already (marked by a check mark); an empty list tells how images are saved.
 *
 * @since 2.1.4
 */
public class SavedImageChoice extends Button {

    private static final double POPUP_WIDTH = 300;
    private static final double ROW_HEIGHT = 46;
    private static final int VISIBLE_ROWS = 6;

    /** Where the choice finds the saved images; read on the JavaFX thread when it opens. */
    public interface Source {

        /**
         * Lists the saved images in use, the favorites first.
         *
         * @return the images
         */
        List<SavedImage> list();

        /**
         * Gets the file of a saved image, for its picture.
         *
         * @param image the image
         * @return its file
         */
        Path fileOf(SavedImage image);

        /**
         * Tells which saved images are attached.
         *
         * @return their ids
         */
        Set<String> attached();
    }

    private final Source source;
    private final Consumer<SavedImage> onChoose;
    private final Popup popup = new Popup();
    private final TextField searchField = new TextField();
    private final ListView<SavedImage> list = new ListView<>();
    private final Label emptyLabel = new Label();
    private final Label hintLabel = new Label();
    private List<SavedImage> images = List.of();
    private Set<String> attached = Set.of();

    /**
     * Creates the choice.
     *
     * @param source   the saved images
     * @param onChoose takes the image clicked: it is attached, or removed if it is attached already
     */
    public SavedImageChoice(Source source, Consumer<SavedImage> onChoose) {
        this.source = source;
        this.onChoose = onChoose;

        FontIcon bookmark = new FontIcon("mdi2b-bookmark-multiple-outline");
        bookmark.setIconSize(14);
        bookmark.getStyleClass().add("ikonli-font-icon");
        setGraphic(bookmark);
        getStyleClass().addAll("button-outlined", "small");
        setContentDisplay(ContentDisplay.LEFT);
        setOnAction((event) -> open());

        searchField.textProperty().addListener((obs, oldText, newText) -> filter());
        searchField.setOnKeyPressed((event) -> {
            if (event.getCode() == KeyCode.DOWN && !list.getItems().isEmpty()) {
                list.requestFocus();
                list.getSelectionModel().selectFirst();
                event.consume();
            }
        });
        list.setCellFactory((view) -> new Row());
        list.setFixedCellSize(ROW_HEIGHT);
        list.setOnKeyPressed((event) -> {
            if (event.getCode() == KeyCode.ENTER && list.getSelectionModel().getSelectedItem() != null) {
                choose(list.getSelectionModel().getSelectedItem());
                event.consume();
            }
        });
        emptyLabel.setWrapText(true);
        emptyLabel.getStyleClass().addAll("text-small", "text-muted");
        hintLabel.setWrapText(true);
        hintLabel.getStyleClass().addAll("text-small", "text-muted");

        VBox box = new VBox(8.0, searchField, list, emptyLabel, hintLabel);
        box.setPadding(new Insets(8.0));
        box.setPrefWidth(POPUP_WIDTH);
        box.setMaxWidth(POPUP_WIDTH);
        box.setStyle("-fx-background-color: -color-bg-overlay; -fx-border-color: -color-border-default; "
                + "-fx-background-radius: 6px; -fx-border-radius: 6px; "
                + "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.25), 12, 0, 0, 3);");
        popup.getContent().add(box);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        updateTexts();
    }

    /** Shows the texts in the current language. */
    public void updateTexts() {
        setText(I18nManager.getText("saved.images.choice"));
        setTooltip(new Tooltip(I18nManager.getText("saved.images.choice.tooltip")));
        searchField.setPromptText(I18nManager.getText("saved.images.search"));
        hintLabel.setText(I18nManager.getText("saved.images.choice.hint"));
        filter();
    }

    /** Opens the list below the button, right-aligned with it, with the saved images as they are now. */
    public void open() {
        load();
        Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds == null || getScene() == null)
            return;
        popup.show(this, bounds.getMaxX() - POPUP_WIDTH, bounds.getMaxY() + 2);
        searchField.requestFocus();
    }

    /** Reads the saved images and which of them are attached, and starts a new search. */
    public void load() {
        images = source.list();
        attached = source.attached();
        searchField.clear();
        filter();
    }

    /** @return the images the list shows, for the tests */
    List<SavedImage> shown() {
        return List.copyOf(list.getItems());
    }

    /** @return the popup of the list, for the tests */
    Popup popup() {
        return popup;
    }

    /**
     * Takes an image as clicked: closes the list and hands it on.
     *
     * @param image the image
     */
    void choose(SavedImage image) {
        popup.hide();
        onChoose.accept(image);
    }

    /*  the images whose name or description holds the search; without images the hint how to save one  */
    private void filter() {
        String search = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        list.getItems().setAll(images.stream().filter((SavedImage image) -> search.isEmpty()
                || image.name().toLowerCase(Locale.ROOT).contains(search) || image.description() != null
                && image.description().toLowerCase(Locale.ROOT).contains(search)).toList());
        list.setPrefHeight(Math.max(1, Math.min(VISIBLE_ROWS, list.getItems().size())) * ROW_HEIGHT + 2);
        boolean none = images.isEmpty();
        emptyLabel.setText(I18nManager.getText(none ? "saved.images.empty" : "saved.images.none.found"));
        emptyLabel.setVisible(list.getItems().isEmpty());
        emptyLabel.setManaged(list.getItems().isEmpty());
        list.setVisible(!list.getItems().isEmpty());
        list.setManaged(!list.getItems().isEmpty());
        searchField.setVisible(!none);
        searchField.setManaged(!none);
        hintLabel.setVisible(!none);
        hintLabel.setManaged(!none);
    }

    /*  a row: picture, name with a star for a favorite, size and format (and "attached"), a check mark if attached  */
    private class Row extends ListCell<SavedImage> {

        private final ImageView picture = new ImageView();
        private final Label name = new Label();
        private final Label info = new Label();
        private final FontIcon check = new FontIcon("mdi2c-check");
        private final HBox content;

        Row() {
            picture.setFitWidth(34);
            picture.setFitHeight(34);
            picture.setPreserveRatio(true);
            StackPane frame = new StackPane(picture);
            frame.setMinSize(36, 36);
            frame.setMaxSize(36, 36);
            frame.setStyle("-fx-background-color: -color-bg-inset; -fx-background-radius: 4px;");
            info.getStyleClass().addAll("text-small", "text-muted");
            check.setIconSize(16);
            check.getStyleClass().addAll("ikonli-font-icon", "accent");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            VBox texts = new VBox(1.0, name, info);
            texts.setAlignment(Pos.CENTER_LEFT);
            content = new HBox(8.0, frame, texts, spacer, check);
            content.setAlignment(Pos.CENTER_LEFT);
            setOnMouseClicked((event) -> {
                if (event.getButton() == MouseButton.PRIMARY && getItem() != null)
                    choose(getItem());
            });
        }

        @Override
        protected void updateItem(SavedImage image, boolean empty) {
            super.updateItem(image, empty);
            setText(null);
            if (empty || image == null) {
                setGraphic(null);
                setTooltip(null);
                picture.setImage(null);
                return;
            }
            picture.setImage(new Image(source.fileOf(image).toUri().toString(), 68, 68, true, true, true));
            name.setText(image.name());
            FontIcon star = image.favorite() ? new FontIcon("mdi2s-star") : null;
            if (star != null) {
                star.setIconSize(12);
                star.getStyleClass().addAll("ikonli-font-icon", "warning");
            }
            name.setGraphic(star);
            boolean isAttached = attached.contains(image.id());
            info.setText(image.describe() + (isAttached ? " · " + I18nManager.getText("saved.images.attached") : ""));
            check.setVisible(isAttached);
            setTooltip(image.description() != null ? new Tooltip(image.description()) : null);
            setGraphic(content);
        }
    }
}
