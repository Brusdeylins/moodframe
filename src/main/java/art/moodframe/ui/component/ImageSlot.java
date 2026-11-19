package art.moodframe.ui.component;

import art.moodframe.model.AttachedImage;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/**
 * Represents a single image slot of the {@link MultiImageAttachmentPanel}.
 *
 * <p>Compact layout design:</p>
 * <ul>
 *   <li>Header: Left="Bild 1", Right="1024x1024 (2,4 MB)" when image present</li>
 *   <li>Square preview area (height = width), clickable for upload</li>
 *   <li>Remove button as overlay, only visible on hover when image present</li>
 *   <li>No separate upload button or info label</li>
 * </ul>
 */
class ImageSlot extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(ImageSlot.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");

    private final MultiImageAttachmentPanel panel;

    private final int index;
    private AttachedImage attachment;

    // UI components
    private HBox headerRow;
    private Label slotLabel;
    private Label dimensionsLabel;
    private StackPane previewContainer;
    private VBox placeholder;
    private ImageView imageView;
    private Button removeButton;

    ImageSlot(MultiImageAttachmentPanel panel, int index) {
        this.panel = panel;
        this.index = index;
        initSlotUI();
    }

    private void initSlotUI() {
        setSpacing(4);
        setAlignment(Pos.TOP_LEFT);
        getStyleClass().add("image-slot");

        // Header row - split layout: left=slot number, right=dimensions
        headerRow = new HBox();
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setMaxWidth(Double.MAX_VALUE);
        headerRow.setVisible(false);
        headerRow.setManaged(false);

        slotLabel = new Label();
        slotLabel.getStyleClass().addAll("text-small", "text-muted");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        dimensionsLabel = new Label();
        dimensionsLabel.getStyleClass().addAll("text-small", "text-muted");

        headerRow.getChildren().addAll(slotLabel, spacer, dimensionsLabel);

        /*  Preview container, clickable: a square whose height follows the width the layout gives it, so slot,
            panel and group around it grow with it (a height bound to its own width came too late in the layout
            pass and left the group shorter)  */
        previewContainer = new StackPane() {
            @Override
            public Orientation getContentBias() {
                return Orientation.HORIZONTAL;
            }

            @Override
            protected double computePrefHeight(double width) {
                return width >= 0 ? width : super.computePrefHeight(width);
            }
        };
        previewContainer.setMaxWidth(Double.MAX_VALUE);
        previewContainer.setStyle("-fx-background-color: " + emptySlotBackground() + "; " +
                "-fx-background-radius: 4px; " +
                "-fx-border-color: -color-border-muted; " +
                "-fx-border-width: 1px; " +
                "-fx-border-style: dashed; " +
                "-fx-border-radius: 4px; " +
                "-fx-cursor: hand;");

        // Make entire container clickable for upload
        previewContainer.setOnMouseClicked(e -> {
            if (!hasImage()) {
                openFileChooser();
            }
        });

        /*  An attached image offers "Mood from image" in its context menu  */
        previewContainer.setOnContextMenuRequested((event) -> {
            File imageFile = hasImage() ? attachment.getImageFile() : null;
            if (imageFile == null || panel.onMoodFromImage == null)
                return;
            MenuItem fromImage = new MenuItem(I18nManager.getText("mood.image.menu"));
            fromImage.setOnAction((action) -> panel.onMoodFromImage.accept(imageFile));
            new ContextMenu(fromImage).show(previewContainer, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        // Placeholder content (shown when no image)
        placeholder = new VBox(3);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setMouseTransparent(true); // Let clicks pass through to container

        FontIcon placeholderIcon = new FontIcon("mdi2i-image-plus");
        placeholderIcon.setIconSize(24);
        placeholderIcon.getStyleClass().addAll("ikonli-font-icon", "text-muted");

        Label placeholderLabel = new Label(I18nManager.getText("attachment.drop.or.click"));
        placeholderLabel.getStyleClass().addAll("text-muted", "text-small");

        placeholder.getChildren().addAll(placeholderIcon, placeholderLabel);

        // Image view (shown when image present)
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setVisible(false);
        imageView.setManaged(false);

        // Bind image view size to container (with small margin)
        imageView.fitWidthProperty().bind(previewContainer.widthProperty().subtract(4));
        imageView.fitHeightProperty().bind(previewContainer.heightProperty().subtract(4));

        // Remove button - overlay at top right, only visible on hover
        removeButton = new Button();
        removeButton.getStyleClass().addAll("icon-button", "text-danger-hover");
        removeButton.setVisible(false);
        removeButton.setOnAction(e -> {
            // Hide button immediately before removal
            removeButton.setVisible(false);
            removeImage();
        });

        FontIcon removeIcon = new FontIcon("mdi2d-delete");
        removeIcon.setIconSize(14);
        removeIcon.getStyleClass().addAll("ikonli-font-icon");
        removeButton.setGraphic(removeIcon);
        removeButton.setTooltip(new Tooltip(I18nManager.getText("attachment.remove")));

        /*  Over the image the button sits on the dark bar of the actions of an image, shown with the button  */
        HBox removeBar = new HBox(removeButton);
        removeBar.getStyleClass().add("image-action-bar");
        removeBar.getStylesheets().add(
                MultiImageAttachmentPanel.class.getResource("/css/image-action-bar.css").toExternalForm());
        removeBar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        removeBar.visibleProperty().bind(removeButton.visibleProperty());

        StackPane.setAlignment(removeBar, Pos.TOP_RIGHT);
        StackPane.setMargin(removeBar, new Insets(4));

        previewContainer.getChildren().addAll(placeholder, imageView, removeBar);

        // Hover effect: show remove button only when image present (but not during drag)
        previewContainer.setOnMouseEntered(e -> {
            if (hasImage() && !isDragging) {
                removeButton.setVisible(true);
            }
        });
        previewContainer.setOnMouseExited(e -> removeButton.setVisible(false));

        // Drag & drop support
        setupDragAndDrop();

        getChildren().addAll(headerRow, previewContainer);
    }

    private boolean isDragging = false;

    /**
     * Sets up drag & drop handlers for this slot.
     */
    private void setupDragAndDrop() {
        previewContainer.setOnDragOver(this::handleDragOver);
        previewContainer.setOnDragDropped(this::handleDragDropped);
        previewContainer.setOnDragEntered(this::handleDragEntered);
        previewContainer.setOnDragExited(this::handleDragExited);
    }

    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            List<File> files = event.getDragboard().getFiles();
            if (!files.isEmpty() && hasValidImageExtension(files.get(0)))
                event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleDragEntered(DragEvent event) {
        isDragging = true;
        removeButton.setVisible(false);

        if (event.getDragboard().hasFiles()) {
            List<File> files = event.getDragboard().getFiles();
            if (!files.isEmpty() && hasValidImageExtension(files.get(0)))
                previewContainer.getStyleClass().add("drag-over-valid");
            else
                previewContainer.getStyleClass().add("drag-over-invalid");
        }
        event.consume();
    }

    private void handleDragExited(DragEvent event) {
        isDragging = false;
        previewContainer.getStyleClass().removeAll("drag-over-valid", "drag-over-invalid", "drop-success");
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasFiles()) {
            List<File> files = db.getFiles();
            if (!files.isEmpty()) {
                File imageFile = files.get(0);
                if (isValidImageFile(imageFile)) {
                    previewContainer.getStyleClass().removeAll("drag-over-valid", "drag-over-invalid");
                    previewContainer.getStyleClass().add("drop-success");
                    panel.attachImageToSlot(index, imageFile);
                    success = true;
                }
                else {
                    // Show error for invalid files (oversized ones are shrunk on attach)
                    if (!hasValidImageExtension(imageFile)) {
                        String errorMsg = I18nManager.getText("error.unsupported.format") + " (PNG, JPEG, WebP)";
                        if (panel.onError != null)
                            Platform.runLater(() -> panel.onError.accept(errorMsg));
                    }
                }
            }
        }

        event.setDropCompleted(success);
        event.consume();
        isDragging = false;
    }

    /**
     * Checks only file extension (not size). Used during dragOver/dragEntered
     * so the OS allows the drop and size validation happens in the drop handler.
     */
    private boolean hasValidImageExtension(File file) {
        if (file == null || !file.exists() || !file.isFile())
            return false;
        String fileName = file.getName().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /**
     * Validates if a file is a supported image format and within size limits.
     */
    private boolean isValidImageFile(File file) {
        if (!hasValidImageExtension(file))
            return false;

        // Verify MIME type
        try {
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType != null && !mimeType.startsWith("image/"))
                return false;
        }
        catch (IOException e) {
            logger.warn("Could not determine MIME type for file: {}", file.getName());
        }

        return true;
    }

    void updateSlotNumber(int number, boolean show) {
        updateHeaderLabel(number, show);
    }

    /**
     * Updates the header with slot number (left) and dimensions (right).
     */
    private void updateHeaderLabel(int number, boolean show) {
        // Left: "Bild 1"
        slotLabel.setText(I18nManager.getText("attachment.slot", number));

        // Right: "1024x1024 (2,4 MB)" or empty
        if (attachment != null) {
            dimensionsLabel.setText(String.format("%s (%s)",
                    attachment.getFormattedDimensions(),
                    attachment.getFormattedFileSize()));
        } else {
            dimensionsLabel.setText("");
        }

        headerRow.setVisible(show);
        headerRow.setManaged(show);
    }

    void setAttachment(AttachedImage attachment) {
        this.attachment = attachment;
        updateUI();
    }

    void clearAttachment() {
        this.attachment = null;
        updateUI();
    }

    boolean hasImage() {
        return attachment != null;
    }

    private void updateUI() {
        boolean hasImage = attachment != null;

        placeholder.setVisible(!hasImage);
        placeholder.setManaged(!hasImage);

        imageView.setVisible(hasImage);
        imageView.setManaged(hasImage);

        // Update cursor based on state
        if (hasImage) {
            previewContainer.setStyle("-fx-background-color: -color-bg-inset; " +
                    "-fx-background-radius: 4px; " +
                    "-fx-border-color: -color-border-muted; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-style: solid; " +
                    "-fx-border-radius: 4px;");
        } else {
            previewContainer.setStyle("-fx-background-color: " + emptySlotBackground() + "; " +
                    "-fx-background-radius: 4px; " +
                    "-fx-border-color: -color-border-muted; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-style: dashed; " +
                    "-fx-border-radius: 4px; " +
                    "-fx-cursor: hand;");
        }

        if (hasImage) {
            loadPreviewImage();
        } else {
            imageView.setImage(null);
        }

        // Update header with dimensions if image present
        int slotNumber = panel.imageSlots.indexOf(this) + 1;
        boolean showHeader = panel.maxImages.get() > 1;
        updateHeaderLabel(slotNumber, showHeader);
    }

    private void loadPreviewImage() {
        if (attachment == null || attachment.getImageFile() == null) return;

        /*  Decoded in the background with reasonable size, preserving aspect ratio; the view shows it when done  */
        Image image = new Image(attachment.getImageFile().toURI().toString(), 400, 400, true, true, true);
        image.errorProperty().addListener((obs, wasError, error) -> {
            if (error)
                logger.warn("Failed to load preview image: {}", image.getException() != null
                        ? image.getException().getMessage() : attachment);
        });
        imageView.setImage(image);
    }

    private void openFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18nManager.getText("filechooser.image.title"));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        I18nManager.getText("filechooser.image.filter"),
                        "*.png", "*.jpg", "*.jpeg", "*.webp"
                )
        );

        Window window = panel.getParentWindow();
        File selectedFile = fileChooser.showOpenDialog(window);

        if (selectedFile != null) {
            panel.attachImageToSlot(index, selectedFile);
        }
    }

    private void removeImage() {
        int currentIndex = panel.imageSlots.indexOf(this);
        if (currentIndex >= 0) {
            panel.removeImageAt(currentIndex);

            // Remove this slot from UI if it's not the first one
            // The first slot always stays (even if empty)
            if (currentIndex > 0) {
                panel.imageSlots.remove(this);
                panel.slotsContainer.getChildren().remove(this);
                dispose();
                panel.updateSlotNumbers();
            }

            panel.updateAddButtonVisibility();
        }
    }

    void updateTexts() {
        removeButton.setTooltip(new Tooltip(I18nManager.getText("attachment.remove")));
        // Update header label
        int slotNumber = panel.imageSlots.indexOf(this) + 1;
        boolean showHeader = panel.maxImages.get() > 1;
        updateHeaderLabel(slotNumber, showHeader);
    }

    void dispose() {
        imageView.setImage(null);
        attachment = null;
    }

    /*  an empty slot uses the field color of its panel, which is translucent over the window cloud  */
    private static String emptySlotBackground() {
        return "-color-bg-default";
    }
}
