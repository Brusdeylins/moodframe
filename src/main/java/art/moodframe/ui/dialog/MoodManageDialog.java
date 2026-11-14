package art.moodframe.ui.dialog;

import art.moodframe.config.ServiceFactory;
import art.moodframe.model.Mood;
import art.moodframe.service.FavoriteService;
import art.moodframe.service.MoodService;
import art.moodframe.service.TrashCenter;
import art.moodframe.service.TrashCleanupService;
import art.moodframe.service.TrashGroup;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Window for the moods of the user: order, rename, delete (into the central trash), favorite (by the star or the
 * context menu of a row), import and export as JSON, and a link to the moods in the trash. Every change is stored at
 * once.
 *
 * @since 2.0.0
 */
public class MoodManageDialog extends Stage {

    private static final double WINDOW_WIDTH = 460;
    private static final double WINDOW_HEIGHT = 420;

    private final MoodService moodService;
    private final ListView<Mood> list = new ListView<>();

    /**
     * Creates the window.
     *
     * @param owner the owner window
     * @param moodService where the moods are kept
     */
    public MoodManageDialog(Window owner, MoodService moodService) {
        this.moodService = moodService;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("mood.manage.title"));

        list.setPlaceholder(new Label(I18nManager.getText("mood.manage.placeholder")));
        FavoriteService favorites = ServiceFactory.getInstance().getService(FavoriteService.class);
        FavoriteStar.Favorites<Mood> starred = new FavoriteStar.Favorites<>(null, (mood) -> favorites != null
                && favorites.isFavorite(FavoriteService.Kind.MOOD, mood.getId()), (mood) -> {
                    if (favorites != null)
                        favorites.toggle(FavoriteService.Kind.MOOD, mood.getId());
                });
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
                boolean incomplete = !empty && mood != null && !moodService.trashedBlocks(mood).isEmpty();
                nameLabel.setText(empty || mood == null ? null : mood.getName());
                warning.setVisible(incomplete);
                star.show(empty ? null : mood);
                setText(null);
                setGraphic(empty || mood == null ? null : content);
                setTooltip(incomplete ? new Tooltip(I18nManager.getText("params.mood.incomplete.short")) : null);
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
     * @param moodService where the moods are kept
     * @param header the question above the field
     * @param suggestion the name the field starts with
     * @param ownName the name the mood has now, which counts as free; null for a new mood
     * @return the trimmed name, null if the user cancelled
     */
    public static String askName(Window owner, MoodService moodService, String header, String suggestion,
            String ownName) {
        String name = suggestion;
        while (true) {
            TextInputDialog dialog = new TextInputDialog(name);
            dialog.initOwner(owner);
            dialog.setTitle(I18nManager.getText("mood.name.title"));
            dialog.setHeaderText(header);
            dialog.setContentText(I18nManager.getText("mood.name.label"));
            String answer = dialog.showAndWait().orElse(null);
            if (answer == null)
                return null;
            name = answer.trim();
            if (!name.isEmpty() && (name.equals(ownName) || !moodService.exists(name)))
                return name;
            if (!name.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, I18nManager.getText("mood.name.taken", name));
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

    /** Loads the moods of the user again and marks the one with this id. */
    private void refresh(String selectId) {
        list.getItems().setAll(moodService.getCustom());
        list.getItems().stream().filter((Mood mood) -> mood.getId().equals(selectId)).findFirst()
                .ifPresent(list.getSelectionModel()::select);
    }

    /** Moves the marked mood one place up (-1) or down (1). */
    private void move(int delta) {
        int index = list.getSelectionModel().getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.getItems().size())
            return;
        List<Mood> moods = new ArrayList<>(list.getItems());
        moods.add(target, moods.remove(index));
        moodService.reorder(moods.stream().map(Mood::getId).toList());
        refresh(moods.get(target).getId());
    }

    private void rename() {
        Mood mood = list.getSelectionModel().getSelectedItem();
        String name = askName(this, moodService, I18nManager.getText("mood.name.rename"), mood.getName(),
                mood.getName());
        if (name == null || name.equals(mood.getName()))
            return;
        mood.setName(name);
        if (!moodService.update(mood))
            showMessage(Alert.AlertType.ERROR, I18nManager.getText("error.mood.save"));
        refresh(mood.getId());
    }

    /** Moves the marked mood to the central trash, where it can be restored. */
    private void delete() {
        Mood mood = list.getSelectionModel().getSelectedItem();
        if (!moodService.moveToTrash(mood.getId()))
            showMessage(Alert.AlertType.ERROR, I18nManager.getText("mood.manage.delete.failed", mood.getName()));
        refresh(null);
    }

    private void importMoods() {
        File file = fileChooser().showOpenDialog(this);
        if (file == null)
            return;
        int imported = moodService.importMoods(file);
        showMessage(imported < 0 ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION, imported < 0
                ? I18nManager.getText("mood.manage.import.failed") : I18nManager.getText("mood.manage.import.done", imported));
        refresh(null);
    }

    private void exportMoods() {
        FileChooser chooser = fileChooser();
        chooser.setInitialFileName("moods.json");
        File file = chooser.showSaveDialog(this);
        if (file != null && !moodService.exportMoods(file))
            showMessage(Alert.AlertType.ERROR, I18nManager.getText("mood.manage.export.failed"));
    }

    private static FileChooser fileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        return chooser;
    }

    /** Opens the moods in the trash; a restored mood shows up here once the trash is closed. */
    private void openTrash() {
        TrashWindow window = new TrashWindow(this, ServiceFactory.getInstance().getService(TrashCenter.class),
                ServiceFactory.getInstance().getService(TrashCleanupService.class));
        window.select(TrashGroup.Kind.MOODS, null);
        window.setOnHidden((event) -> refresh(null));
        window.show();
    }

    private void showMessage(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.initOwner(this);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
