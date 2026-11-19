package art.moodframe.ui.dialog;

import art.moodframe.model.SavedImage;
import art.moodframe.service.library.SavedImageService;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.support.FxI18n;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Dialog "Save image permanently": a preview of the image, its name (required, unique among the saved images) and a
 * description of what it shows and when it fits. For a file saved already it tells so and only renames the saved
 * image; it also edits the name and the description of a saved image. Enter saves, Esc cancels.
 *
 * @since 2.1.4
 */
public class SavedImageSaveDialog extends Dialog<SavedImageSaveDialog.Result> implements I18nChangeListener {

    /** What the dialog does. */
    public enum Mode {
        /** Saves a new image with name and description. */
        NEW,
        /** The file is saved already: only its name can change. */
        SAVED,
        /** Edits the name and the description of a saved image. */
        EDIT
    }

    /**
     * What the user entered.
     *
     * @param name        the trimmed name
     * @param description the trimmed description, empty for none
     */
    public record Result(String name, String description) {
    }

    /**
     * What is known of a file before it is saved, found off the JavaFX thread.
     *
     * @param same the saved image in use with the same file, null for none
     * @param info its size, format and file size, e.g. {@code 512 × 512 · PNG · 48 KB}
     */
    public record Inspection(SavedImage same, String info) {
    }

    /** The saved images the dialog writes; every call completes on the JavaFX thread. */
    public interface Store {

        /**
         * Tells if a saved image in use has this name; a short read on the JavaFX thread.
         *
         * @param name the name
         * @return true if the name is taken
         */
        boolean exists(String name);

        /**
         * Finds the saved image with the same file and the size of the file.
         *
         * @param file the image file
         * @return completes with what is known of the file
         */
        CompletableFuture<Inspection> inspect(Path file);

        /**
         * Saves an image file in the library.
         *
         * @param file        the image file
         * @param name        the name
         * @param description the description, empty for none
         * @return completes with the outcome
         */
        CompletableFuture<SavedImageService.Saved> save(Path file, String name, String description);

        /**
         * Changes the name and the description of a saved image.
         *
         * @param image       the saved image
         * @param name        the new name
         * @param description the new description, empty for none
         * @return completes with true if both are stored
         */
        CompletableFuture<Boolean> edit(SavedImage image, String name, String description);
    }

    private final Mode mode;
    private final String ownName;
    private final String info;
    private final Predicate<String> taken;
    private final TextField nameField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final Label nameLabel = new Label();
    private final Label infoLabel = new Label();
    private final Label libraryLabel = new Label();
    private final Label validationLabel = new Label();
    private final Label descriptionLabel = new Label();
    private final Label noticeLabel = new Label();
    private final ButtonType saveButtonType = new ButtonType("", ButtonBar.ButtonData.OK_DONE);
    private final ButtonType cancelButtonType = DialogButtons.cancel();

    /**
     * Creates the dialog.
     *
     * @param owner       the owner window, null for none
     * @param mode        what the dialog does
     * @param file        the image file, shown as preview
     * @param info        its size, format and file size
     * @param name        the name it starts with: a suggestion, or the name of the saved image
     * @param description the description it starts with, null for none
     * @param taken       tells if a saved image in use has a name
     */
    public SavedImageSaveDialog(Window owner, Mode mode, Path file, String info, String name, String description,
            Predicate<String> taken) {
        this.mode = mode;
        this.ownName = mode == Mode.NEW ? null : name;
        this.info = info;
        this.taken = taken;
        if (owner != null)
            initOwner(owner);
        FxI18n.addChangeListener(this, this);
        getDialogPane().getButtonTypes().addAll(saveButtonType, cancelButtonType);

        /*  The preview: the image within a square on the inset color  */
        ImageView preview = new ImageView(new Image(file.toUri().toString(), 128, 128, true, true, true));
        preview.setFitWidth(128);
        preview.setFitHeight(128);
        preview.setPreserveRatio(true);
        StackPane previewBox = new StackPane(preview);
        previewBox.setMinSize(136, 136);
        previewBox.setMaxSize(136, 136);
        previewBox.setStyle("-fx-background-color: -color-bg-inset; -fx-background-radius: 6px;");

        nameLabel.getStyleClass().add("text-bold");
        nameField.setText(name);
        nameField.setPrefColumnCount(24);
        infoLabel.getStyleClass().addAll("text-small", "text-muted");
        libraryLabel.getStyleClass().addAll("text-small", "text-muted");
        libraryLabel.setVisible(mode == Mode.NEW);
        libraryLabel.setManaged(mode == Mode.NEW);
        validationLabel.getStyleClass().addAll("text-small", "danger");
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);
        VBox nameBox = new VBox(4.0, nameLabel, nameField, validationLabel, infoLabel, libraryLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        HBox top = new HBox(14.0, previewBox, nameBox);
        top.setAlignment(Pos.TOP_LEFT);

        /*  The description only for a new image and in editing; an image saved already is only renamed  */
        descriptionLabel.getStyleClass().add("text-bold");
        descriptionArea.setText(description);
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);
        boolean describes = mode != Mode.SAVED;
        descriptionLabel.setVisible(describes);
        descriptionLabel.setManaged(describes);
        descriptionArea.setVisible(describes);
        descriptionArea.setManaged(describes);

        noticeLabel.setWrapText(true);
        noticeLabel.setMaxWidth(Double.MAX_VALUE);
        noticeLabel.setStyle("-fx-background-color: -color-warning-subtle; -fx-border-color: -color-warning-muted; "
                + "-fx-background-radius: 6px; -fx-border-radius: 6px; -fx-padding: 8px 10px 8px 10px; "
                + "-fx-text-fill: -color-warning-fg;");
        noticeLabel.setVisible(mode == Mode.SAVED);
        noticeLabel.setManaged(mode == Mode.SAVED);

        VBox content = new VBox(10.0, top, descriptionLabel, descriptionArea, noticeLabel);
        content.setPadding(new Insets(16, 16, 8, 16));
        content.setPrefWidth(480);
        getDialogPane().setContent(content);

        updateTexts();
        validate();
        nameField.textProperty().addListener((obs, oldText, newText) -> validate());
        Platform.runLater(() -> {
            nameField.requestFocus();
            nameField.selectAll();
        });

        setResultConverter((buttonType) -> buttonType == saveButtonType
                ? new Result(nameField.getText().trim(), descriptionArea.getText() != null
                        ? descriptionArea.getText().trim() : "") : null);
    }

    /*  the name must not be empty and no other saved image may have it  */
    private void validate() {
        String name = nameField.getText().trim();
        boolean isTaken = !name.isEmpty() && !name.equals(ownName) && taken.test(name);
        getDialogPane().lookupButton(saveButtonType).setDisable(name.isEmpty() || isTaken);
        validationLabel.setText(isTaken ? I18nManager.getText("saved.image.dialog.taken") : "");
        validationLabel.setVisible(isTaken);
        validationLabel.setManaged(isTaken);
    }

    /*  the texts in the current language  */
    private void updateTexts() {
        setTitle(I18nManager.getText(mode == Mode.EDIT ? "saved.image.dialog.edit.title"
                : "saved.image.dialog.title"));
        setHeaderText(null);
        nameLabel.setText(I18nManager.getText("saved.image.dialog.name"));
        infoLabel.setText(info);
        libraryLabel.setText(I18nManager.getText("saved.image.dialog.library"));
        descriptionLabel.setText(I18nManager.getText("saved.image.dialog.description"));
        descriptionArea.setPromptText(I18nManager.getText("saved.image.dialog.description.placeholder"));
        noticeLabel.setText(ownName != null ? I18nManager.getText("saved.image.dialog.exists", ownName) : "");
        ((Button) getDialogPane().lookupButton(saveButtonType)).setText(I18nManager.getText(mode == Mode.SAVED
                ? "saved.image.dialog.rename" : "saved.image.dialog.save"));
        ((Button) getDialogPane().lookupButton(cancelButtonType)).setText(I18nManager.getText("button.cancel"));
        validate();
    }

    @Override
    public void onLanguageChanged() {
        Platform.runLater(this::updateTexts);
    }

    /**
     * Saves an image file in the library: finds out off the JavaFX thread whether it is saved already, asks for its
     * name and description, or for a file saved already offers to rename it, and stores what the user entered.
     *
     * @param owner      the owner window of the dialog
     * @param store      the saved images
     * @param file       the image file
     * @param suggestion the name the dialog suggests for a new image
     * @param onError    shows a failure, e.g. in the status bar
     * @return completes on the JavaFX thread with the saved image, null if the user cancelled or it failed
     */
    public static CompletableFuture<SavedImage> saveFile(Window owner, Store store, Path file, String suggestion,
            Consumer<String> onError) {
        return store.inspect(file).thenCompose((Inspection inspection) -> {
            SavedImage same = inspection.same();
            Mode mode = same != null ? Mode.SAVED : Mode.NEW;
            Result result = new SavedImageSaveDialog(owner, mode, file, inspection.info(),
                    same != null ? same.name() : suggestion, null, store::exists).showAndWait().orElse(null);
            if (result == null)
                return CompletableFuture.completedFuture(same);
            if (same != null)
                return result.name().equals(same.name()) ? CompletableFuture.completedFuture(same)
                        : store.edit(same, result.name(), same.description() != null ? same.description() : "")
                                .thenApply((Boolean done) -> fail(done ? new SavedImage(same.id(), result.name(),
                                        same.file(), same.width(), same.height(), same.sha256(), same.description(),
                                        same.favorite(), same.createdAt(), null) : null, onError));
            return store.save(file, result.name(), result.description())
                    .thenApply((SavedImageService.Saved saved) -> switch (saved.outcome()) {
                        case SAVED, EXISTS -> saved.image();
                        case NAME_TAKEN -> {
                            onError.accept(I18nManager.getText("error.saved.image.taken", result.name()));
                            yield null;
                        }
                        case FAILED -> fail(null, onError);
                    });
        });
    }

    /*  the image, or null after telling the failure  */
    private static SavedImage fail(SavedImage image, Consumer<String> onError) {
        if (image == null)
            onError.accept(I18nManager.getText("error.saved.image.save"));
        return image;
    }
}
