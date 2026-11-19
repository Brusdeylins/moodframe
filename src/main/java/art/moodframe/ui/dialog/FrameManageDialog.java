package art.moodframe.ui.dialog;

import art.moodframe.model.Frame;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.trash.TrashGroup;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.component.FavoriteStar;
import art.moodframe.util.I18nManager;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Window for the frames of the user: order, rename, delete (into the central trash), favorite (by the star or the
 * context menu of a row), import and export as JSON, and a link to the frames in the trash. Every change is stored
 * at once, off the JavaFX thread; the window waits meanwhile, so no second change starts from a list that is not
 * loaded again yet.
 *
 * @since 2.0.0
 */
public class FrameManageDialog extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(FrameManageDialog.class);
    private static final double WINDOW_WIDTH = 460;
    private static final double WINDOW_HEIGHT = 420;

    /** The frames of the user. A change completes on the JavaFX thread. */
    public interface Library {

        /**
         * Lists the frames of the user.
         *
         * @return the frames in their order
         */
        List<Frame> list();

        /**
         * Tells if a frame in use has this name.
         *
         * @param name the name
         * @return true if the name is taken
         */
        boolean exists(String name);

        /**
         * Stores the order of the frames.
         *
         * @param frames the frames in their new order
         * @return completes with true if the order is stored
         */
        CompletableFuture<Boolean> reorder(List<Frame> frames);

        /**
         * Gives a frame a new name.
         *
         * @param frame the frame
         * @param name  the new name, which no other frame has
         * @return completes with true if it is stored
         */
        CompletableFuture<Boolean> rename(Frame frame, String name);

        /**
         * Moves a frame to the trash.
         *
         * @param frame the frame
         * @return completes with true if it is in the trash
         */
        CompletableFuture<Boolean> delete(Frame frame);

        /**
         * Adds the frames of a JSON file behind those of the user.
         *
         * @param file the source file
         * @return completes with the number of added frames, -1 if the file cannot be read
         */
        CompletableFuture<Integer> importFrom(File file);

        /**
         * Writes the frames of the user into a JSON file.
         *
         * @param file the target file
         * @return completes with true if it was written
         */
        CompletableFuture<Boolean> exportTo(File file);
    }

    private final Library library;
    private final TrashWindow.Opener trash;
    private final ListView<Frame> list = new ListView<>();

    /**
     * Creates the window.
     *
     * @param owner the owner window
     * @param library the frames of the user
     * @param starred the favorites, which the star of a row toggles
     * @param trash opens the trash on the frames
     */
    public FrameManageDialog(Window owner, Library library, FavoriteStar.Favorites<Frame> starred,
            TrashWindow.Opener trash) {
        this.library = library;
        this.trash = trash;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("frame.manage.title"));

        list.setPlaceholder(new Label(I18nManager.getText("frame.manage.placeholder")));
        list.setCellFactory((view) -> new ListCell<>() {
            private final Label nameLabel = new Label();
            private final Region spacer = new Region();
            private final FavoriteStar<Frame> star = new FavoriteStar<>(this, starred);
            private final HBox content = new HBox(6.0, nameLabel, spacer, star);

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                content.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Frame frame, boolean empty) {
                super.updateItem(frame, empty);
                nameLabel.setText(empty || frame == null ? null : frame.getName());
                star.show(empty ? null : frame);
                setText(null);
                setGraphic(empty || frame == null ? null : content);
                setTooltip(empty || frame == null ? null : new Tooltip(FrameService.describe(frame)));
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Button upButton = iconButton("mdi2a-arrow-up", "frame.manage.up");
        Button downButton = iconButton("mdi2a-arrow-down", "frame.manage.down");
        Button renameButton = new Button(I18nManager.getText("frame.manage.rename"));
        Button deleteButton = new Button(I18nManager.getText("frame.manage.delete"));
        deleteButton.getStyleClass().addAll("button-outlined", "danger");
        upButton.setOnAction((event) -> move(-1));
        downButton.setOnAction((event) -> move(1));
        renameButton.setOnAction((event) -> rename());
        deleteButton.setOnAction((event) -> delete());
        for (Button button : List.of(upButton, downButton, renameButton, deleteButton))
            button.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        HBox itemActions = new HBox(6.0, upButton, downButton, renameButton, deleteButton);
        itemActions.setAlignment(Pos.CENTER_LEFT);

        Button importButton = new Button(I18nManager.getText("frame.manage.import"));
        Button exportButton = new Button(I18nManager.getText("frame.manage.export"));
        Hyperlink trashLink = new Hyperlink(I18nManager.getText("frame.manage.trash"));
        Button closeButton = new Button(I18nManager.getText("frame.manage.close"));
        closeButton.setDefaultButton(true);
        importButton.setOnAction((event) -> importFrames());
        exportButton.setOnAction((event) -> exportFrames());
        exportButton.disableProperty().bind(Bindings.isEmpty(list.getItems()));
        trashLink.setOnAction((event) -> openTrash());
        closeButton.setOnAction((event) -> close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox fileActions = new HBox(6.0, importButton, exportButton, trashLink, spacer, closeButton);
        fileActions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10.0, list, itemActions, fileActions);
        content.setPadding(new Insets(10.0));
        setScene(new Scene(content, WINDOW_WIDTH, WINDOW_HEIGHT));
        setMinWidth(400);
        setMinHeight(300);
        refresh(null);

        if (owner != null) {
            setX(owner.getX() + (owner.getWidth() - WINDOW_WIDTH) / 2);
            setY(owner.getY() + (owner.getHeight() - WINDOW_HEIGHT) / 2);
        }
    }

    /**
     * Asks for the name of a frame until it is free or the user cancels.
     *
     * @param owner the owner window
     * @param taken tells if a frame in use has a name
     * @param header the question above the field
     * @param suggestion the name the field starts with
     * @param ownName the name the frame has now, which counts as free; null for a new frame
     * @return the trimmed name, null if the user cancelled
     */
    public static String askName(Window owner, Predicate<String> taken, String header, String suggestion,
            String ownName) {
        String name = suggestion;
        while (true) {
            TextInputDialog dialog = new TextInputDialog(name);
            dialog.getDialogPane().getButtonTypes().setAll(DialogButtons.ok(), DialogButtons.cancel());
            dialog.initOwner(owner);
            dialog.setTitle(I18nManager.getText("frame.name.title"));
            dialog.setHeaderText(header);
            dialog.setContentText(I18nManager.getText("frame.name.label"));
            String answer = dialog.showAndWait().orElse(null);
            if (answer == null)
                return null;
            name = answer.trim();
            if (!name.isEmpty() && (name.equals(ownName) || !taken.test(name)))
                return name;
            if (!name.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, I18nManager.getText("frame.name.taken", name),
                        DialogButtons.ok());
                alert.initOwner(owner);
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        }
    }

    private static Button iconButton(String iconLiteral, String tooltipKey) {
        Button button = new Button(null, new FontIcon(iconLiteral));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(I18nManager.getText(tooltipKey)));
        return button;
    }

    /*  loads the frames of the user again and marks the one with this id  */
    private void refresh(String selectId) {
        list.getItems().setAll(library.list());
        list.getItems().stream().filter((Frame frame) -> frame.getId().equals(selectId)).findFirst()
                .ifPresent(list.getSelectionModel()::select);
    }

    /*  moves the marked frame one place up (-1) or down (1)  */
    private void move(int delta) {
        int index = list.getSelectionModel().getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.getItems().size())
            return;
        List<Frame> frames = new ArrayList<>(list.getItems());
        frames.add(target, frames.remove(index));
        String id = frames.get(target).getId();
        change(library.reorder(frames), (stored, failure) -> {
            if (!Boolean.TRUE.equals(stored))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("error.frame.save"));
            refresh(id);
        });
    }

    private void rename() {
        Frame frame = list.getSelectionModel().getSelectedItem();
        String name = askName(this, library::exists, I18nManager.getText("frame.name.rename"), frame.getName(),
                frame.getName());
        if (name == null || name.equals(frame.getName()))
            return;
        change(library.rename(frame, name), (stored, failure) -> {
            if (!Boolean.TRUE.equals(stored))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("error.frame.save"));
            refresh(frame.getId());
        });
    }

    /*  moves the marked frame to the central trash, where it can be restored  */
    private void delete() {
        Frame frame = list.getSelectionModel().getSelectedItem();
        change(library.delete(frame), (deleted, failure) -> {
            if (!Boolean.TRUE.equals(deleted))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("frame.manage.delete.failed",
                        frame.getName()));
            refresh(null);
        });
    }

    private void importFrames() {
        File file = fileChooser().showOpenDialog(this);
        if (file == null)
            return;
        change(library.importFrom(file), (imported, failure) -> {
            boolean read = imported != null && imported >= 0;
            refresh(null);
            showMessage(read ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, read
                    ? I18nManager.getText("frame.manage.import.done", imported)
                    : I18nManager.getText("frame.manage.import.failed"));
        });
    }

    private void exportFrames() {
        FileChooser chooser = fileChooser();
        chooser.setInitialFileName("frames.json");
        File file = chooser.showSaveDialog(this);
        if (file == null)
            return;
        change(library.exportTo(file), (written, failure) -> {
            if (!Boolean.TRUE.equals(written))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("frame.manage.export.failed"));
        });
    }

    /*  waits for a change of the library, which completes on the JavaFX thread, and then shows its outcome  */
    private <R> void change(CompletableFuture<R> change, BiConsumer<R, Throwable> then) {
        getScene().getRoot().setDisable(true);
        change.whenComplete((result, failure) -> {
            getScene().getRoot().setDisable(false);
            if (failure != null)
                logger.error("A change of the frames failed", failure);
            then.accept(result, failure);
        });
    }

    private static FileChooser fileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        return chooser;
    }

    /*  opens the frames in the trash; a restored frame shows up here once the trash is closed  */
    private void openTrash() {
        TrashWindow window = trash.window(this, TrashGroup.Kind.FRAMES, null);
        window.setOnHidden((event) -> refresh(null));
        window.show();
    }

    private void showMessage(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message, DialogButtons.ok());
        alert.initOwner(this);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
