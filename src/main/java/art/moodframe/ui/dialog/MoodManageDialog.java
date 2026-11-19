package art.moodframe.ui.dialog;

import art.moodframe.model.Mood;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Window for the moods of the user: order, rename, delete (into the central trash), favorite (by the star or the
 * context menu of a row), import and export as JSON, and a link to the moods in the trash. Every change is stored at
 * once, off the JavaFX thread; the window waits meanwhile, so no second change starts from a list that is not loaded
 * again yet. Which moods have a block in the trash the library finds once per load of the list.
 *
 * @since 2.0.0
 */
public class MoodManageDialog extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(MoodManageDialog.class);
    private static final double WINDOW_WIDTH = 460;
    private static final double WINDOW_HEIGHT = 420;

    /** The moods of the user. A change completes on the JavaFX thread. */
    public interface Library {

        /**
         * Lists the moods of the user.
         *
         * @return the moods in their order
         */
        List<Mood> list();

        /**
         * Tells if a mood in use has this name.
         *
         * @param name the name
         * @return true if the name is taken
         */
        boolean exists(String name);

        /**
         * Finds the moods with a block in the trash, off the JavaFX thread.
         *
         * @param moods the moods of the list
         * @return completes with their ids
         */
        CompletableFuture<Set<String>> incomplete(List<Mood> moods);

        /**
         * Stores the order of the moods.
         *
         * @param moods the moods in their new order
         * @return completes with true if the order is stored
         */
        CompletableFuture<Boolean> reorder(List<Mood> moods);

        /**
         * Gives a mood a new name.
         *
         * @param mood the mood
         * @param name the new name, which no other mood has
         * @return completes with true if it is stored
         */
        CompletableFuture<Boolean> rename(Mood mood, String name);

        /**
         * Moves a mood to the trash.
         *
         * @param mood the mood
         * @return completes with true if it is in the trash
         */
        CompletableFuture<Boolean> delete(Mood mood);

        /**
         * Adds the moods of a JSON file behind those of the user.
         *
         * @param file the source file
         * @return completes with the number of added moods, -1 if the file cannot be read
         */
        CompletableFuture<Integer> importFrom(File file);

        /**
         * Writes the moods of the user into a JSON file.
         *
         * @param file the target file
         * @return completes with true if it was written
         */
        CompletableFuture<Boolean> exportTo(File file);
    }

    private final Library library;
    private final TrashWindow.Opener trash;
    private final ListView<Mood> list = new ListView<>();

    /*  the ids of the moods with a block in the trash, as found for the latest load of the list  */
    private Set<String> incomplete = Set.of();
    private int loads;

    /**
     * Creates the window.
     *
     * @param owner the owner window
     * @param library the moods of the user
     * @param starred the favorites, which the star of a row toggles
     * @param trash opens the trash on the moods
     */
    public MoodManageDialog(Window owner, Library library, FavoriteStar.Favorites<Mood> starred,
            TrashWindow.Opener trash) {
        this.library = library;
        this.trash = trash;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("mood.manage.title"));

        list.setPlaceholder(new Label(I18nManager.getText("mood.manage.placeholder")));
        list.setCellFactory((view) -> new ListCell<>() {
            private final Label nameLabel = new Label();
            private final FontIcon warning = new FontIcon("mdi2a-alert-circle-outline");
            private final Region spacer = new Region();
            private final FavoriteStar<Mood> star = new FavoriteStar<>(this, starred);
            private final HBox content = new HBox(6.0, nameLabel, warning, spacer, star);

            {
                warning.getStyleClass().add("warning");
                HBox.setHgrow(spacer, Priority.ALWAYS);
                content.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Mood mood, boolean empty) {
                super.updateItem(mood, empty);
                boolean trashed = !empty && mood != null && incomplete.contains(mood.getId());
                nameLabel.setText(empty || mood == null ? null : mood.getName());
                warning.setVisible(trashed);
                star.show(empty ? null : mood);
                setText(null);
                setGraphic(empty || mood == null ? null : content);
                setTooltip(trashed ? new Tooltip(I18nManager.getText("params.mood.incomplete.short")) : null);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Button upButton = iconButton("mdi2a-arrow-up", "mood.manage.up");
        Button downButton = iconButton("mdi2a-arrow-down", "mood.manage.down");
        Button renameButton = new Button(I18nManager.getText("mood.manage.rename"));
        Button deleteButton = new Button(I18nManager.getText("mood.manage.delete"));
        deleteButton.getStyleClass().addAll("button-outlined", "danger");
        upButton.setOnAction((event) -> move(-1));
        downButton.setOnAction((event) -> move(1));
        renameButton.setOnAction((event) -> rename());
        deleteButton.setOnAction((event) -> delete());
        for (Button button : List.of(upButton, downButton, renameButton, deleteButton))
            button.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        HBox itemActions = new HBox(6.0, upButton, downButton, renameButton, deleteButton);
        itemActions.setAlignment(Pos.CENTER_LEFT);

        Button importButton = new Button(I18nManager.getText("mood.manage.import"));
        Button exportButton = new Button(I18nManager.getText("mood.manage.export"));
        Hyperlink trashLink = new Hyperlink(I18nManager.getText("mood.manage.trash"));
        Button closeButton = new Button(I18nManager.getText("mood.manage.close"));
        closeButton.setDefaultButton(true);
        importButton.setOnAction((event) -> importMoods());
        exportButton.setOnAction((event) -> exportMoods());
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
     * Asks for the name of a mood until it is free or the user cancels.
     *
     * @param owner the owner window
     * @param taken tells if a mood in use has a name
     * @param header the question above the field
     * @param suggestion the name the field starts with
     * @param ownName the name the mood has now, which counts as free; null for a new mood
     * @return the trimmed name, null if the user cancelled
     */
    public static String askName(Window owner, Predicate<String> taken, String header, String suggestion,
            String ownName) {
        String name = suggestion;
        while (true) {
            TextInputDialog dialog = new TextInputDialog(name);
            dialog.getDialogPane().getButtonTypes().setAll(DialogButtons.ok(), DialogButtons.cancel());
            dialog.initOwner(owner);
            dialog.setTitle(I18nManager.getText("mood.name.title"));
            dialog.setHeaderText(header);
            dialog.setContentText(I18nManager.getText("mood.name.label"));
            String answer = dialog.showAndWait().orElse(null);
            if (answer == null)
                return null;
            name = answer.trim();
            if (!name.isEmpty() && (name.equals(ownName) || !taken.test(name)))
                return name;
            if (!name.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, I18nManager.getText("mood.name.taken", name),
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

    /*  loads the moods of the user again and marks the one with this id; the warnings follow from the library  */
    private void refresh(String selectId) {
        List<Mood> moods = library.list();
        list.getItems().setAll(moods);
        list.getItems().stream().filter((Mood mood) -> mood.getId().equals(selectId)).findFirst()
                .ifPresent(list.getSelectionModel()::select);
        int load = ++loads;
        library.incomplete(moods).whenComplete((ids, failure) -> {
            if (failure != null)
                logger.error("Failed to find the moods with a block in the trash", failure);
            else if (load == loads) {
                incomplete = ids;
                list.refresh();
            }
        });
    }

    /*  moves the marked mood one place up (-1) or down (1)  */
    private void move(int delta) {
        int index = list.getSelectionModel().getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.getItems().size())
            return;
        List<Mood> moods = new ArrayList<>(list.getItems());
        moods.add(target, moods.remove(index));
        String id = moods.get(target).getId();
        change(library.reorder(moods), (stored, failure) -> {
            if (!Boolean.TRUE.equals(stored))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("error.mood.save"));
            refresh(id);
        });
    }

    private void rename() {
        Mood mood = list.getSelectionModel().getSelectedItem();
        String name = askName(this, library::exists, I18nManager.getText("mood.name.rename"), mood.getName(),
                mood.getName());
        if (name == null || name.equals(mood.getName()))
            return;
        change(library.rename(mood, name), (stored, failure) -> {
            if (!Boolean.TRUE.equals(stored))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("error.mood.save"));
            refresh(mood.getId());
        });
    }

    /*  moves the marked mood to the central trash, where it can be restored  */
    private void delete() {
        Mood mood = list.getSelectionModel().getSelectedItem();
        change(library.delete(mood), (deleted, failure) -> {
            if (!Boolean.TRUE.equals(deleted))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("mood.manage.delete.failed", mood.getName()));
            refresh(null);
        });
    }

    private void importMoods() {
        File file = fileChooser().showOpenDialog(this);
        if (file == null)
            return;
        change(library.importFrom(file), (imported, failure) -> {
            boolean read = imported != null && imported >= 0;
            refresh(null);
            showMessage(read ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, read
                    ? I18nManager.getText("mood.manage.import.done", imported)
                    : I18nManager.getText("mood.manage.import.failed"));
        });
    }

    private void exportMoods() {
        FileChooser chooser = fileChooser();
        chooser.setInitialFileName("moods.json");
        File file = chooser.showSaveDialog(this);
        if (file == null)
            return;
        change(library.exportTo(file), (written, failure) -> {
            if (!Boolean.TRUE.equals(written))
                showMessage(Alert.AlertType.ERROR, I18nManager.getText("mood.manage.export.failed"));
        });
    }

    /*  waits for a change of the library, which completes on the JavaFX thread, and then shows its outcome  */
    private <R> void change(CompletableFuture<R> change, BiConsumer<R, Throwable> then) {
        getScene().getRoot().setDisable(true);
        change.whenComplete((result, failure) -> {
            getScene().getRoot().setDisable(false);
            if (failure != null)
                logger.error("A change of the moods failed", failure);
            then.accept(result, failure);
        });
    }

    private static FileChooser fileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        return chooser;
    }

    /*  opens the moods in the trash; a restored mood shows up here once the trash is closed  */
    private void openTrash() {
        TrashWindow window = trash.window(this, TrashGroup.Kind.MOODS, null);
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
