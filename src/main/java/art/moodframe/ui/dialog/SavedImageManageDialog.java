package art.moodframe.ui.dialog;

import art.moodframe.model.SavedImage;
import art.moodframe.service.trash.TrashGroup;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.support.DiskWorker;
import art.moodframe.ui.support.SystemOpen;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Window "Saved Images", built like the windows of the moods and frames: the saved images as tiles with a search,
 * added like an attachment (file chooser, drag and drop, clipboard) and named in the dialog that saves them. Each
 * image goes into the attachments (also by a double click), is renamed and described, marked as favorite, shown in
 * the Finder or moved into the central trash, where it can be restored. Every change is stored off the JavaFX thread;
 * the window waits meanwhile.
 *
 * @since 2.1.4
 */
public class SavedImageManageDialog extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(SavedImageManageDialog.class);
    private static final double WINDOW_WIDTH = 660;
    private static final double WINDOW_HEIGHT = 560;
    private static final double TILE_WIDTH = 140;
    private static final double PICTURE_HEIGHT = 104;

    /** The saved images; a change completes on the JavaFX thread. */
    public interface Library extends SavedImageSaveDialog.Store {

        /**
         * Lists the saved images in use, the favorites first; a short read on the JavaFX thread.
         *
         * @return the images
         */
        List<SavedImage> list();

        /**
         * Gets the file of a saved image.
         *
         * @param image the image
         * @return its file
         */
        Path fileOf(SavedImage image);

        /**
         * Marks a saved image as favorite or not.
         *
         * @param image    the image
         * @param favorite whether it is a favorite
         * @return completes with true if it is stored
         */
        CompletableFuture<Boolean> setFavorite(SavedImage image, boolean favorite);

        /**
         * Moves a saved image to the trash.
         *
         * @param image the image
         * @return completes with true if it is in the trash
         */
        CompletableFuture<Boolean> delete(SavedImage image);
    }

    private final Library library;
    private final TrashWindow.Opener trash;
    private final Consumer<SavedImage> attach;
    private final FlowPane tiles = new FlowPane(10.0, 10.0);
    private final TextField searchField = new TextField();
    private final Label selectionLabel = new Label();
    private final HBox selectionActions;
    private final Button favoriteButton = new Button();

    /*  the saved images as loaded last, and the chosen one  */
    private List<SavedImage> images = List.of();
    private SavedImage selected;

    /**
     * Creates the window.
     *
     * @param owner   the owner window
     * @param library the saved images
     * @param attach  puts a saved image into the attachments of the order
     * @param trash   opens the trash on the saved images
     */
    public SavedImageManageDialog(Window owner, Library library, Consumer<SavedImage> attach,
            TrashWindow.Opener trash) {
        this.library = library;
        this.attach = attach;
        this.trash = trash;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("saved.images.manage.title"));

        Button addButton = new Button(I18nManager.getText("saved.images.manage.add"), icon("mdi2p-plus"));
        addButton.getStyleClass().add("accent");
        addButton.setOnAction((event) -> chooseFile());
        Button pasteButton = new Button(null, icon("mdi2c-clipboard-arrow-down-outline"));
        pasteButton.getStyleClass().add("icon-button");
        pasteButton.setTooltip(new Tooltip(I18nManager.getText("attachment.paste.tooltip")));
        pasteButton.setOnAction((event) -> paste());
        searchField.setPromptText(I18nManager.getText("saved.images.search"));
        searchField.setPrefColumnCount(16);
        searchField.textProperty().addListener((obs, oldText, newText) -> showTiles());
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox top = new HBox(6.0, addButton, pasteButton, topSpacer, searchField);
        top.setAlignment(Pos.CENTER_LEFT);

        tiles.setPadding(new Insets(4.0));
        ScrollPane scroll = new ScrollPane(tiles);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        /*  The actions of the chosen image  */
        Button attachButton = new Button(I18nManager.getText("saved.images.manage.attach"));
        Button editButton = new Button(I18nManager.getText("saved.images.manage.edit"));
        Button revealButton = new Button(I18nManager.getText("saved.images.manage.reveal"));
        Button deleteButton = new Button(I18nManager.getText("saved.images.manage.delete"));
        deleteButton.getStyleClass().addAll("button-outlined", "danger");
        attachButton.setOnAction((event) -> attachSelected());
        editButton.setOnAction((event) -> edit(selected));
        favoriteButton.setOnAction((event) -> toggleFavorite(selected));
        revealButton.setOnAction((event) -> SystemOpen.reveal(library.fileOf(selected)));
        deleteButton.setOnAction((event) -> delete(selected));
        selectionActions = new HBox(6.0, attachButton, editButton, favoriteButton, revealButton, deleteButton);
        selectionActions.setAlignment(Pos.CENTER_LEFT);
        selectionLabel.getStyleClass().add("text-bold");
        VBox selection = new VBox(6.0, selectionLabel, selectionActions);

        Hyperlink trashLink = new Hyperlink(I18nManager.getText("saved.images.manage.trash"));
        trashLink.setOnAction((event) -> openTrash());
        Button closeButton = new Button(I18nManager.getText("saved.images.manage.close"));
        closeButton.setCancelButton(true);
        closeButton.setOnAction((event) -> close());
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(6.0, trashLink, footerSpacer, closeButton);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10.0, top, scroll, selection, footer);
        content.setPadding(new Insets(12.0));
        Scene scene = new Scene(content, WINDOW_WIDTH, WINDOW_HEIGHT);
        setScene(scene);
        setMinWidth(520);
        setMinHeight(400);

        /*  An image dropped anywhere or pasted with the shortcut is added like one chosen  */
        content.setOnDragOver((event) -> {
            if (imageFile(event) != null)
                event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        content.setOnDragDropped((event) -> {
            File file = imageFile(event);
            event.setDropCompleted(file != null);
            event.consume();
            if (file != null)
                Platform.runLater(() -> add(file.toPath()));
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN), this::paste);

        refresh();
        if (owner != null) {
            setX(owner.getX() + (owner.getWidth() - WINDOW_WIDTH) / 2);
            setY(owner.getY() + (owner.getHeight() - WINDOW_HEIGHT) / 2);
        }
    }

    /** Loads the saved images again, e.g. after a change of another caller, keeping the chosen one. */
    public void refresh() {
        String id = selected != null ? selected.id() : null;
        selected = null;
        images = library.list();
        images.stream().filter((SavedImage image) -> image.id().equals(id)).findFirst()
                .ifPresent((SavedImage image) -> selected = image);
        showTiles();
    }

    /*  shows the tiles of the images that match the search, then the tile that adds one  */
    private void showTiles() {
        String search = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        tiles.getChildren().clear();
        for (SavedImage image : images)
            if (search.isEmpty() || matches(image, search))
                tiles.getChildren().add(tile(image));
        tiles.getChildren().add(addTile());
        showSelection();
    }

    private static boolean matches(SavedImage image, String search) {
        return image.name().toLowerCase(Locale.ROOT).contains(search) || image.description() != null
                && image.description().toLowerCase(Locale.ROOT).contains(search);
    }

    /*  a tile: the picture, the name, size and format, a star for a favorite; the chosen one framed  */
    private VBox tile(SavedImage image) {
        ImageView picture = new ImageView(new Image(library.fileOf(image).toUri().toString(), TILE_WIDTH * 2,
                PICTURE_HEIGHT * 2, true, true, true));
        picture.setFitWidth(TILE_WIDTH - 12);
        picture.setFitHeight(PICTURE_HEIGHT);
        picture.setPreserveRatio(true);
        StackPane frame = new StackPane(picture);
        frame.setMinHeight(PICTURE_HEIGHT + 8);
        frame.setStyle("-fx-background-color: -color-bg-inset; -fx-background-radius: 4px;");

        Label name = new Label(image.name());
        name.getStyleClass().add("text-bold");
        if (image.favorite())
            name.setGraphic(icon("mdi2s-star"));
        Label info = new Label(image.describe());
        info.getStyleClass().addAll("text-small", "text-muted");
        VBox tile = new VBox(4.0, frame, name, info);
        tile.setPadding(new Insets(6.0));
        tile.setPrefWidth(TILE_WIDTH);
        tile.setMaxWidth(TILE_WIDTH);
        boolean chosen = selected != null && selected.id().equals(image.id());
        tile.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 6px; -fx-border-radius: 6px; "
                + "-fx-border-width: 2px; -fx-border-color: " + (chosen ? "-color-accent-emphasis" : "transparent")
                + ";");
        if (image.description() != null)
            Tooltip.install(tile, new Tooltip(image.description()));

        tile.setOnMouseClicked((event) -> {
            if (event.getButton() != MouseButton.PRIMARY)
                return;
            select(image);
            if (event.getClickCount() == 2)
                attachSelected();
        });
        tile.setOnContextMenuRequested((event) -> {
            select(image);
            MenuItem attachItem = new MenuItem(I18nManager.getText("saved.images.manage.attach"));
            attachItem.setOnAction((action) -> attachSelected());
            MenuItem editItem = new MenuItem(I18nManager.getText("saved.images.manage.edit"));
            editItem.setOnAction((action) -> edit(image));
            MenuItem favoriteItem = new MenuItem(I18nManager.getText(image.favorite()
                    ? "saved.images.manage.unfavorite" : "saved.images.manage.favorite"));
            favoriteItem.setOnAction((action) -> toggleFavorite(image));
            MenuItem revealItem = new MenuItem(I18nManager.getText("saved.images.manage.reveal"));
            revealItem.setOnAction((action) -> SystemOpen.reveal(library.fileOf(image)));
            MenuItem deleteItem = new MenuItem(I18nManager.getText("saved.images.manage.delete"));
            deleteItem.setOnAction((action) -> delete(image));
            new ContextMenu(attachItem, editItem, favoriteItem, revealItem, deleteItem).show(tile,
                    event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return tile;
    }

    /*  the tile that adds an image: a click opens the file chooser, a drop or the clipboard adds one as well  */
    private VBox addTile() {
        FontIcon plus = icon("mdi2i-image-plus");
        plus.setIconSize(24);
        Label hint = new Label(I18nManager.getText("saved.images.manage.drop"));
        hint.getStyleClass().addAll("text-small", "text-muted");
        hint.setWrapText(true);
        hint.setAlignment(Pos.CENTER);
        hint.setMaxWidth(TILE_WIDTH - 20);
        hint.setStyle("-fx-text-alignment: center;");
        VBox tile = new VBox(6.0, plus, hint);
        tile.setAlignment(Pos.CENTER);
        tile.setPrefSize(TILE_WIDTH, PICTURE_HEIGHT + 60);
        tile.setStyle("-fx-border-color: -color-border-muted; -fx-border-style: dashed; -fx-border-width: 1px; "
                + "-fx-border-radius: 6px; -fx-cursor: hand;");
        tile.setOnMouseClicked((event) -> chooseFile());
        return tile;
    }

    private void select(SavedImage image) {
        selected = image;
        showTiles();
    }

    /*  names the chosen image above its actions, which need a chosen image  */
    private void showSelection() {
        selectionLabel.setText(selected != null ? I18nManager.getText("saved.images.manage.selection",
                selected.name()) : I18nManager.getText("saved.images.manage.selection.none"));
        selectionActions.setDisable(selected == null);
        favoriteButton.setText(I18nManager.getText(selected != null && selected.favorite()
                ? "saved.images.manage.unfavorite" : "saved.images.manage.favorite"));
    }

    private void attachSelected() {
        if (selected != null)
            attach.accept(selected);
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nManager.getText("filechooser.image.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                I18nManager.getText("filechooser.image.filter"), "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File file = chooser.showOpenDialog(this);
        if (file != null)
            add(file.toPath());
    }

    /*  an image file of a drag, null for anything else  */
    private static File imageFile(DragEvent event) {
        if (!event.getDragboard().hasFiles() || event.getDragboard().getFiles().isEmpty())
            return null;
        File file = event.getDragboard().getFiles().get(0);
        String name = file.getName().toLowerCase(Locale.ROOT);
        return file.isFile() && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".webp")) ? file : null;
    }

    /*  writes the image of the clipboard into a PNG on a worker and adds it  */
    private void paste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        Image image = clipboard.hasImage() ? clipboard.getImage() : null;
        if (image == null || image.getPixelReader() == null) {
            showMessage(Alert.AlertType.INFORMATION, I18nManager.getText("attachment.paste.empty"));
            return;
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        CompletableFuture<Path> written = CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                buffered.setRGB(0, 0, width, height, argb, 0, width);
                File file = File.createTempFile("clipboard-", ".png");
                file.deleteOnExit();
                ImageIO.write(buffered, "png", file);
                return file.toPath();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, DiskWorker.get()).handleAsync((Path file, Throwable failure) -> {
            if (failure != null)
                logger.error("Failed to write the image of the clipboard", failure);
            return file;
        }, Platform::runLater);
        change(written, (file, failure) -> {
            if (file != null)
                add(file);
        });
    }

    /*  saves an image file under the name the user gives it, or renames the saved image of the same file  */
    private void add(Path file) {
        String name = file.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        String suggestion = name.startsWith("clipboard-") ? I18nManager.getText("saved.image.default.name") : name;
        change(SavedImageSaveDialog.saveFile(this, library, file, suggestion,
                (message) -> showMessage(Alert.AlertType.ERROR, message)), (image, failure) -> {
            refresh();
            if (image != null)
                select(images.stream().filter((SavedImage saved) -> saved.id().equals(image.id())).findFirst()
                        .orElse(null));
        });
    }

    /*  changes the name and the description of a saved image in the dialog that saves them  */
    private void edit(SavedImage image) {
        if (image == null)
            return;
        String info = image.describe();
        SavedImageSaveDialog.Result result = new SavedImageSaveDialog(this, SavedImageSaveDialog.Mode.EDIT,
                library.fileOf(image), info, image.name(), image.description(), library::exists)
                .showAndWait().orElse(null);
        if (result == null)
            return;
        change(library.edit(image, result.name(), result.description()), (stored, failure) -> {
            if (!Boolean.TRUE.equals(stored))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("error.saved.image.save"));
            refresh();
        });
    }

    private void toggleFavorite(SavedImage image) {
        if (image == null)
            return;
        change(library.setFavorite(image, !image.favorite()), (stored, failure) -> refresh());
    }

    /*  moves an image to the central trash, where it can be restored  */
    private void delete(SavedImage image) {
        if (image == null)
            return;
        change(library.delete(image), (deleted, failure) -> {
            if (!Boolean.TRUE.equals(deleted))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("saved.images.manage.delete.failed",
                        image.name()));
            refresh();
        });
    }

    /*  waits for a change of the library, which completes on the JavaFX thread, and then shows its outcome  */
    private <R> void change(CompletableFuture<R> change, BiConsumer<R, Throwable> then) {
        getScene().getRoot().setDisable(true);
        change.whenComplete((result, failure) -> {
            getScene().getRoot().setDisable(false);
            if (failure != null)
                logger.error("A change of the saved images failed", failure);
            then.accept(result, failure);
        });
    }

    /*  opens the saved images in the trash; a restored image shows up here once the trash is closed  */
    private void openTrash() {
        TrashWindow window = trash.window(this, TrashGroup.Kind.SAVED_IMAGES, null);
        window.setOnHidden((event) -> refresh());
        window.show();
    }

    private static FontIcon icon(String literal) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize(16);
        icon.getStyleClass().add("ikonli-font-icon");
        return icon;
    }

    private void showMessage(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message, DialogButtons.ok());
        alert.initOwner(this);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
