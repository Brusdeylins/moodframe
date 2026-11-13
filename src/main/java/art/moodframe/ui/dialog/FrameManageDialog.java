package art.moodframe.ui.dialog;

import art.moodframe.config.ServiceFactory;
import art.moodframe.model.Frame;
import art.moodframe.service.FrameService;
import art.moodframe.service.TrashCenter;
import art.moodframe.service.TrashCleanupService;
import art.moodframe.service.TrashGroup;
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
 * Window for the frames of the user: order, rename, delete (into the central trash), import and export as JSON,
 * and a link to the frames in the trash. Every change is stored at once.
 *
 * @since 2.0.0
 */
public class FrameManageDialog extends Stage {

    private static final double WINDOW_WIDTH = 460;
    private static final double WINDOW_HEIGHT = 420;

    private final FrameService frameService;
    private final ListView<Frame> list = new ListView<>();

    /**
     * Creates the window.
     *
     * @param owner the owner window
     * @param frameService where the frames are kept
     */
    public FrameManageDialog(Window owner, FrameService frameService) {
        this.frameService = frameService;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("frame.manage.title"));

        list.setPlaceholder(new Label(I18nManager.getText("frame.manage.placeholder")));
        list.setCellFactory((view) -> new ListCell<>() {
            @Override
            protected void updateItem(Frame frame, boolean empty) {
                super.updateItem(frame, empty);
                setText(empty || frame == null ? null : frame.getName());
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
     * @param frameService where the frames are kept
     * @param header the question above the field
     * @param suggestion the name the field starts with
     * @param ownName the name the frame has now, which counts as free; null for a new frame
     * @return the trimmed name, null if the user cancelled
     */
    public static String askName(Window owner, FrameService frameService, String header, String suggestion,
            String ownName) {
        String name = suggestion;
        while (true) {
            TextInputDialog dialog = new TextInputDialog(name);
            dialog.initOwner(owner);
            dialog.setTitle(I18nManager.getText("frame.name.title"));
            dialog.setHeaderText(header);
            dialog.setContentText(I18nManager.getText("frame.name.label"));
            String answer = dialog.showAndWait().orElse(null);
            if (answer == null)
                return null;
            name = answer.trim();
            if (!name.isEmpty() && (name.equals(ownName) || !frameService.exists(name)))
                return name;
            if (!name.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, I18nManager.getText("frame.name.taken", name));
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

    /** Loads the frames of the user again and marks the one with this id. */
    private void refresh(String selectId) {
        list.getItems().setAll(frameService.getCustom());
        list.getItems().stream().filter((Frame frame) -> frame.getId().equals(selectId)).findFirst()
                .ifPresent(list.getSelectionModel()::select);
    }

    /** Moves the marked frame one place up (-1) or down (1). */
    private void move(int delta) {
        int index = list.getSelectionModel().getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.getItems().size())
            return;
        List<Frame> frames = new ArrayList<>(list.getItems());
        frames.add(target, frames.remove(index));
        frameService.reorder(frames.stream().map(Frame::getId).toList());
        refresh(frames.get(target).getId());
    }

    private void rename() {
        Frame frame = list.getSelectionModel().getSelectedItem();
        String name = askName(this, frameService, I18nManager.getText("frame.name.rename"), frame.getName(),
                frame.getName());
        if (name == null || name.equals(frame.getName()))
            return;
        frame.setName(name);
        if (!frameService.update(frame))
            showMessage(Alert.AlertType.ERROR, I18nManager.getText("error.frame.save"));
        refresh(frame.getId());
    }

    /** Moves the marked frame to the central trash, where it can be restored. */
    private void delete() {
        Frame frame = list.getSelectionModel().getSelectedItem();
        if (!frameService.moveToTrash(frame.getId()))
            showMessage(Alert.AlertType.ERROR, I18nManager.getText("frame.manage.delete.failed", frame.getName()));
        refresh(null);
    }

    private void importFrames() {
        File file = fileChooser().showOpenDialog(this);
        if (file == null)
            return;
        int imported = frameService.importFrames(file);
        showMessage(imported < 0 ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION, imported < 0
                ? I18nManager.getText("frame.manage.import.failed")
                : I18nManager.getText("frame.manage.import.done", imported));
        refresh(null);
    }

    private void exportFrames() {
        FileChooser chooser = fileChooser();
        chooser.setInitialFileName("frames.json");
        File file = chooser.showSaveDialog(this);
        if (file != null && !frameService.exportFrames(file))
            showMessage(Alert.AlertType.ERROR, I18nManager.getText("frame.manage.export.failed"));
    }

    private static FileChooser fileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        return chooser;
    }

    /** Opens the frames in the trash; a restored frame shows up here once the trash is closed. */
    private void openTrash() {
        TrashWindow window = new TrashWindow(this, ServiceFactory.getInstance().getService(TrashCenter.class),
                ServiceFactory.getInstance().getService(TrashCleanupService.class));
        window.select(TrashGroup.Kind.FRAMES, null);
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
