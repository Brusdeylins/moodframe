package art.moodframe.ui.component;

import art.moodframe.model.AttachedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.ImageAttachmentService;
import art.moodframe.util.I18nManager;
import art.moodframe.util.Subscription;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * A multi-image attachment panel that allows users to attach multiple images
 * for image-to-image generation with FLUX.2 and Gemini models.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Dynamic slots based on model capacity (10 for FLUX 3, 8 for FLUX.2, 14 for Gemini, 1 for Omni)</li>
 *   <li>Vertical layout for right sidebar integration</li>
 *   <li>Drag &amp; Drop support per slot</li>
 *   <li>Upload and remove controls per image</li>
 *   <li>"+" button to add more slots</li>
 * </ul>
 *
 * @since 1.5.0 - FLUX.2 Multi-Image Support
 */
public class MultiImageAttachmentPanel extends ManagedComponent {

    private static final Logger logger = LoggerFactory.getLogger(MultiImageAttachmentPanel.class);

    // Constants
    private static final int DEFAULT_MAX_IMAGES = 1;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");

    // State
    private final List<AttachedImage> attachedImages = new ArrayList<>();
    private final IntegerProperty maxImages = new SimpleIntegerProperty(DEFAULT_MAX_IMAGES);
    private final ObjectProperty<GenerationModel> currentModel = new SimpleObjectProperty<>();

    // UI components
    private VBox slotsContainer;
    private final List<ImageSlot> imageSlots = new ArrayList<>();
    private Label headerLabel;
    private Button pasteButton;
    private Timeline clipboardPoller;
    private Button addSlotButton;
    private Label maxReachedLabel;

    // Callbacks
    private Consumer<List<AttachedImage>> onImagesChanged;
    private Runnable onFirstImageAttached;
    private Runnable onAllImagesRemoved;
    private Consumer<String> onError;

    // Service
    private ImageAttachmentService attachmentService;

    /**
     * Creates a new MultiImageAttachmentPanel.
     */
    public MultiImageAttachmentPanel() {
        super();
        initializeUI();
        setupBindings();
        registerI18nListener(this);
        updateTexts();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setSpacing(10);
        setPadding(new Insets(0));

        // Header
        headerLabel = new Label();
        headerLabel.getStyleClass().add("text-bold");

        // Slots container - will hold image slots vertically
        slotsContainer = new VBox(8);
        slotsContainer.setAlignment(Pos.TOP_CENTER);

        // Add slot button ("+")
        addSlotButton = new Button();
        addSlotButton.getStyleClass().addAll("button-outlined");
        addSlotButton.setMaxWidth(Double.MAX_VALUE);
        addSlotButton.setOnAction(e -> addNewSlot());

        FontIcon addIcon = new FontIcon("mdi2p-plus");
        addIcon.setIconSize(16);
        addIcon.getStyleClass().add("ikonli-font-icon");
        addSlotButton.setGraphic(addIcon);

        // Max reached label (hidden by default)
        maxReachedLabel = new Label();
        maxReachedLabel.getStyleClass().addAll("text-small", "text-muted");
        maxReachedLabel.setVisible(false);
        maxReachedLabel.setManaged(false);

        // Clipboard paste button next to the header
        pasteButton = new Button();
        pasteButton.getStyleClass().addAll("button-icon", "flat");
        pasteButton.setOnAction((e) -> pasteFromClipboard());

        FontIcon pasteIcon = new FontIcon("mdi2c-clipboard-arrow-down-outline");
        pasteIcon.setIconSize(16);
        pasteIcon.getStyleClass().add("ikonli-font-icon");
        pasteButton.setGraphic(pasteIcon);

        /*  Enable the button only while the clipboard holds an image  */
        updatePasteButtonState();
        clipboardPoller = new Timeline(new KeyFrame(Duration.seconds(1), (e) -> updatePasteButtonState()));
        clipboardPoller.setCycleCount(Animation.INDEFINITE);
        clipboardPoller.play();

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerBox = new HBox(headerLabel, headerSpacer, pasteButton);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(headerBox, slotsContainer, addSlotButton, maxReachedLabel);

        // Create initial slot
        addNewSlot();
    }

    /**
     * Sets up property bindings.
     */
    private void setupBindings() {
        // Update UI when max images changes
        addSubscription(Subscription.of(maxImages, (obs, oldVal, newVal) -> {
            Platform.runLater(this::updateAddButtonVisibility);
        }));

        // Update max images when model changes
        addSubscription(Subscription.of(currentModel, (obs, oldVal, newVal) -> {
            Platform.runLater(() -> updateMaxImagesForModel(newVal));
        }));
    }

    /**
     * Updates the maximum images based on the selected model.
     */
    private void updateMaxImagesForModel(GenerationModel model) {
        if (model == null) {
            maxImages.set(DEFAULT_MAX_IMAGES);
            return;
        }

        int newMax = model.getMaxInputImages();
        if (newMax <= 0) {
            newMax = DEFAULT_MAX_IMAGES;
        }

        int oldMax = maxImages.get();
        maxImages.set(newMax);

        logger.debug("Updated max images for model {}: {} -> {}",
                    model.getApiModelId(), oldMax, newMax);

        // Remove excess images if we now have fewer slots
        while (attachedImages.size() > newMax) {
            int lastIndex = attachedImages.size() - 1;
            removeImageAt(lastIndex);
        }

        // Remove excess empty slots
        while (imageSlots.size() > Math.max(1, attachedImages.size() + 1) &&
               imageSlots.size() > newMax) {
            removeLastEmptySlot();
        }

        updateAddButtonVisibility();
    }

    /**
     * Adds a new empty slot for image attachment.
     * A new slot can only be added if all previous slots are filled with images.
     */
    private void addNewSlot() {
        if (imageSlots.size() >= maxImages.get()) {
            logger.debug("Cannot add slot: max images ({}) reached", maxImages.get());
            return;
        }

        // Check if all existing slots have images (except for the first slot which is always allowed)
        if (!imageSlots.isEmpty()) {
            boolean allFilled = imageSlots.stream().allMatch(ImageSlot::hasImage);
            if (!allFilled) {
                logger.debug("Cannot add slot: not all previous slots are filled");
                return;
            }
        }

        int slotIndex = imageSlots.size();
        ImageSlot slot = new ImageSlot(slotIndex);
        imageSlots.add(slot);
        slotsContainer.getChildren().add(slot);

        updateAddButtonVisibility();
        updateSlotNumbers();
    }

    /**
     * Removes the last empty slot (if any).
     */
    private void removeLastEmptySlot() {
        if (imageSlots.isEmpty()) return;

        ImageSlot lastSlot = imageSlots.get(imageSlots.size() - 1);
        if (!lastSlot.hasImage()) {
            imageSlots.remove(lastSlot);
            slotsContainer.getChildren().remove(lastSlot);
            lastSlot.dispose();
        }
    }

    /**
     * Updates the visibility of the add button.
     * The button is only shown when:
     * - Multi-image is supported (max > 1)
     * - Not at max capacity
     * - All existing slots are filled with images
     */
    private void updateAddButtonVisibility() {
        int max = maxImages.get();
        boolean hasCapacity = imageSlots.size() < max;
        boolean showMultiImage = max > 1;
        boolean allSlotsFilled = imageSlots.isEmpty() || imageSlots.stream().allMatch(ImageSlot::hasImage);

        // Show add button only if multi-image enabled, has capacity, and all slots filled
        boolean showAddButton = hasCapacity && showMultiImage && allSlotsFilled;
        addSlotButton.setVisible(showAddButton);
        addSlotButton.setManaged(showAddButton);

        // Show max reached label only when at capacity
        boolean atMaxCapacity = imageSlots.size() >= max && showMultiImage;
        maxReachedLabel.setVisible(atMaxCapacity);
        maxReachedLabel.setManaged(atMaxCapacity);
        maxReachedLabel.setText(I18nManager.getText("attachment.max.reached", max));
    }

    /**
     * Updates the slot number labels.
     */
    private void updateSlotNumbers() {
        boolean showNumbers = maxImages.get() > 1;
        for (int i = 0; i < imageSlots.size(); i++) {
            imageSlots.get(i).updateSlotNumber(i + 1, showNumbers);
        }
    }

    /**
     * Attaches an image to a specific slot.
     */
    public void attachImageToSlot(int slotIndex, File imageFile) {
        if (slotIndex < 0 || slotIndex >= imageSlots.size()) {
            logger.warn("Invalid slot index: {}", slotIndex);
            return;
        }

        if (attachmentService == null) {
            logger.error("ImageAttachmentService not set");
            return;
        }

        /*  Images above the provider limit are shrunk by the service  */
        attachmentService.setMaxFileSize(getCurrentMaxFileSize());

        // Use async processing to avoid blocking UI
        attachmentService.createAttachmentAsync(imageFile)
            .thenAccept(attachment -> {
                if (attachment != null && attachment.isValid()) {
                    Platform.runLater(() -> setImageAtSlot(slotIndex, attachment));
                }
            })
            .exceptionally(e -> {
                logger.error("Failed to attach image: {}", e.getMessage(), e);
                Throwable cause = e;
                while (cause.getCause() != null)
                    cause = cause.getCause();
                String message = cause.getMessage();
                if (onError != null)
                    Platform.runLater(() -> onError.accept(message));
                return null;
            });
    }

    /**
     * Enables the paste button only if the system clipboard holds an image.
     */
    private void updatePasteButtonState() {
        pasteButton.setDisable(!Clipboard.getSystemClipboard().hasImage());
    }

    /**
     * Attaches the image currently on the system clipboard, if any.
     */
    private void pasteFromClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        Image image = clipboard.hasImage() ? clipboard.getImage() : null;

        if (image == null || image.getPixelReader() == null) {
            if (onError != null)
                onError.accept(I18nManager.getText("attachment.paste.empty"));
            return;
        }

        try {
            int width  = (int) image.getWidth();
            int height = (int) image.getHeight();
            PixelReader reader = image.getPixelReader();
            BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    buffered.setRGB(x, y, reader.getArgb(x, y));

            File tempFile = File.createTempFile("clipboard-", ".png");
            tempFile.deleteOnExit();
            ImageIO.write(buffered, "png", tempFile);

            /*  Skip if the same image is already attached  */
            byte[] newBytes = Files.readAllBytes(tempFile.toPath());
            for (AttachedImage existing : getAttachedImages()) {
                File existingFile = existing.getImageFile();
                if (existingFile != null && existingFile.length() == newBytes.length
                        && Arrays.equals(Files.readAllBytes(existingFile.toPath()), newBytes)) {
                    tempFile.delete();
                    if (onError != null)
                        onError.accept(I18nManager.getText("attachment.paste.duplicate"));
                    return;
                }
            }

            /*  Always append: first empty slot, else a new slot  */
            int slotIndex = -1;
            for (int i = 0; i < imageSlots.size() && slotIndex < 0; i++)
                if (!imageSlots.get(i).hasImage())
                    slotIndex = i;
            if (slotIndex < 0 && imageSlots.size() < maxImages.get()) {
                addNewSlot();
                slotIndex = imageSlots.size() - 1;
            }
            if (slotIndex < 0) {
                tempFile.delete();
                if (onError != null)
                    onError.accept(I18nManager.getText("attachment.max.reached", maxImages.get()));
                return;
            }
            attachImageToSlot(slotIndex, tempFile);
        }
        catch (IOException e) {
            logger.error("Failed to paste image from clipboard: {}", e.getMessage(), e);
            if (onError != null)
                onError.accept(e.getMessage());
        }
    }

    /**
     * Sets an image at a specific slot.
     */
    private void setImageAtSlot(int slotIndex, AttachedImage attachment) {
        // Ensure we have enough entries in the list
        while (attachedImages.size() <= slotIndex) {
            attachedImages.add(null);
        }

        boolean wasEmpty = attachedImages.stream().allMatch(img -> img == null);
        attachedImages.set(slotIndex, attachment);

        // Update the slot UI
        if (slotIndex < imageSlots.size()) {
            imageSlots.get(slotIndex).setAttachment(attachment);
        }

        // Callbacks
        if (wasEmpty && onFirstImageAttached != null) {
            onFirstImageAttached.run();
        }

        notifyImagesChanged();

        // Update add button visibility (now that a slot is filled, we might show the add button)
        updateAddButtonVisibility();
    }

    /**
     * Removes an image at a specific slot index.
     */
    private void removeImageAt(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= attachedImages.size()) {
            return;
        }

        attachedImages.set(slotIndex, null);

        if (slotIndex < imageSlots.size()) {
            imageSlots.get(slotIndex).clearAttachment();
        }

        // Compact the list (remove trailing nulls)
        while (!attachedImages.isEmpty() && attachedImages.get(attachedImages.size() - 1) == null) {
            attachedImages.remove(attachedImages.size() - 1);
        }

        boolean allRemoved = attachedImages.stream().allMatch(img -> img == null);
        if (allRemoved && onAllImagesRemoved != null) {
            onAllImagesRemoved.run();
        }

        notifyImagesChanged();
    }

    /**
     * Clears all attached images.
     */
    public void clearAllAttachments() {
        attachedImages.clear();
        for (ImageSlot slot : imageSlots) {
            slot.clearAttachment();
        }

        // Keep only one empty slot
        while (imageSlots.size() > 1) {
            ImageSlot slot = imageSlots.remove(imageSlots.size() - 1);
            slotsContainer.getChildren().remove(slot);
            slot.dispose();
        }

        if (onAllImagesRemoved != null) {
            onAllImagesRemoved.run();
        }

        notifyImagesChanged();
        updateAddButtonVisibility();
    }

    /**
     * Notifies listeners that images have changed.
     */
    private void notifyImagesChanged() {
        if (onImagesChanged != null) {
            List<AttachedImage> validImages = attachedImages.stream()
                    .filter(img -> img != null)
                    .toList();
            onImagesChanged.accept(validImages);
        }
    }

    /**
     * Gets all attached images (non-null entries only).
     */
    public List<AttachedImage> getAttachedImages() {
        return attachedImages.stream()
                .filter(img -> img != null)
                .toList();
    }

    /**
     * Gets the count of attached images.
     */
    public int getAttachmentCount() {
        return (int) attachedImages.stream().filter(img -> img != null).count();
    }

    /**
     * Checks if there are any attachments.
     */
    public boolean hasAttachments() {
        return getAttachmentCount() > 0;
    }

    /**
     * Finds the target slot for a new image based on current state.
     *
     * Behavior:
     * 1) If only one image attached with no empty slot: return slot 0 (replace it)
     * 2) If there's an empty slot: return that slot index
     * 3) If 2+ images and no empty slot: create new slot and return its index
     * 4) If at max capacity: return -1
     *
     * @return the target slot index, or -1 if max capacity reached
     */
    public int findTargetSlotForNewImage() {
        int attachmentCount = getAttachmentCount();
        int slotCount = imageSlots.size();
        int max = maxImages.get();

        // Find first empty slot
        for (int i = 0; i < slotCount; i++) {
            if (!imageSlots.get(i).hasImage()) {
                return i;
            }
        }

        // No empty slot found
        if (attachmentCount == 1 && slotCount == 1) {
            // Case 1: Only one image, no empty slot -> replace it
            return 0;
        }

        // Case 3: Multiple images, no empty slot -> create new slot if under max
        if (slotCount < max) {
            addNewSlot();
            return imageSlots.size() - 1; // Return the new slot index
        }

        // Case 4: At max capacity
        return -1;
    }

    /**
     * Sets the current model to determine max images.
     */
    public void setCurrentModel(GenerationModel model) {
        currentModel.set(model);
    }

    /**
     * Sets the image attachment service.
     */
    public void setAttachmentService(ImageAttachmentService service) {
        this.attachmentService = service;
    }

    /**
     * Sets the callback for when images change.
     */
    public void setOnImagesChanged(Consumer<List<AttachedImage>> callback) {
        this.onImagesChanged = callback;
    }

    /**
     * Sets the callback for when the first image is attached.
     */
    public void setOnFirstImageAttached(Runnable callback) {
        this.onFirstImageAttached = callback;
    }

    /**
     * Sets the callback for when all images are removed.
     */
    public void setOnAllImagesRemoved(Runnable callback) {
        this.onAllImagesRemoved = callback;
    }

    /**
     * Sets the error callback for displaying validation errors in the status bar.
     */
    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    /**
     * Gets the current max file size based on the selected model's provider.
     */
    private long getCurrentMaxFileSize() {
        GenerationModel model = currentModel.get();
        if (model != null)
            return GenerationModel.getMaxFileSize(model.getProvider());
        return 10L * 1024 * 1024;
    }

    /**
     * Gets the parent window for file chooser dialogs.
     */
    private Window getParentWindow() {
        return getScene() != null ? getScene().getWindow() : null;
    }

    @Override
    public void onLanguageChanged() {
        if (isDisposed()) return;
        Platform.runLater(this::updateTexts);
    }

    /**
     * Updates all text labels for i18n.
     */
    private void updateTexts() {
        headerLabel.setText(I18nManager.getText("params.image.attachment"));
        addSlotButton.setText(I18nManager.getText("attachment.add"));
        pasteButton.setTooltip(new Tooltip(I18nManager.getText("attachment.paste.tooltip")));
        maxReachedLabel.setText(I18nManager.getText("attachment.max.reached", maxImages.get()));

        for (ImageSlot slot : imageSlots) {
            slot.updateTexts();
        }
    }

    @Override
    protected void onDispose() {
        if (clipboardPoller != null)
            clipboardPoller.stop();
        for (ImageSlot slot : imageSlots) {
            slot.dispose();
        }
        imageSlots.clear();
        attachedImages.clear();
    }

    // ========================================================================
    // Inner Class: ImageSlot
    // ========================================================================

    /**
     * Represents a single image slot in the multi-image panel.
     *
     * <p>Compact layout design:</p>
     * <ul>
     *   <li>Header: Left="Bild 1", Right="1024x1024 (2,4 MB)" when image present</li>
     *   <li>Square preview area (height = width), clickable for upload</li>
     *   <li>Remove button as overlay, only visible on hover when image present</li>
     *   <li>No separate upload button or info label</li>
     * </ul>
     */
    private class ImageSlot extends VBox {

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

        ImageSlot(int index) {
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

            // Preview container - square (height = width), clickable
            previewContainer = new StackPane();
            previewContainer.setMaxWidth(Double.MAX_VALUE);
            previewContainer.setStyle("-fx-background-color: -color-bg-inset; " +
                    "-fx-background-radius: 4px; " +
                    "-fx-border-color: -color-border-muted; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-style: dashed; " +
                    "-fx-border-radius: 4px; " +
                    "-fx-cursor: hand;");

            // Bind height to width to make it square
            previewContainer.prefHeightProperty().bind(previewContainer.widthProperty());
            previewContainer.minHeightProperty().bind(previewContainer.widthProperty());
            previewContainer.maxHeightProperty().bind(previewContainer.widthProperty());

            // Make entire container clickable for upload
            previewContainer.setOnMouseClicked(e -> {
                if (!hasImage()) {
                    openFileChooser();
                }
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
            removeButton.getStyleClass().addAll("button-icon", "flat");
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
            removeButton.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-background-radius: 4px;");

            StackPane.setAlignment(removeButton, Pos.TOP_RIGHT);
            StackPane.setMargin(removeButton, new Insets(4));

            previewContainer.getChildren().addAll(placeholder, imageView, removeButton);

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
                        attachImageToSlot(index, imageFile);
                        success = true;
                    }
                    else {
                        // Show error for invalid files (oversized ones are shrunk on attach)
                        if (!hasValidImageExtension(imageFile)) {
                            String errorMsg = I18nManager.getText("error.unsupported.format") + " (PNG, JPEG, WebP)";
                            if (onError != null)
                                Platform.runLater(() -> onError.accept(errorMsg));
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
                previewContainer.setStyle("-fx-background-color: -color-bg-inset; " +
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
            int slotNumber = imageSlots.indexOf(this) + 1;
            boolean showHeader = maxImages.get() > 1;
            updateHeaderLabel(slotNumber, showHeader);
        }

        private void loadPreviewImage() {
            if (attachment == null || attachment.getImageFile() == null) return;

            try (FileInputStream fis = new FileInputStream(attachment.getImageFile())) {
                // Load with reasonable size, preserving aspect ratio
                Image image = new Image(fis, 400, 400, true, true);
                imageView.setImage(image);
            } catch (Exception e) {
                logger.warn("Failed to load preview image: {}", e.getMessage());
            }
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

            Window window = getParentWindow();
            File selectedFile = fileChooser.showOpenDialog(window);

            if (selectedFile != null) {
                attachImageToSlot(index, selectedFile);
            }
        }

        private void removeImage() {
            int currentIndex = imageSlots.indexOf(this);
            if (currentIndex >= 0) {
                removeImageAt(currentIndex);

                // Remove this slot from UI if it's not the first one
                // The first slot always stays (even if empty)
                if (currentIndex > 0) {
                    imageSlots.remove(this);
                    slotsContainer.getChildren().remove(this);
                    dispose();
                    updateSlotNumbers();
                }

                updateAddButtonVisibility();
            }
        }

        void updateTexts() {
            removeButton.setTooltip(new Tooltip(I18nManager.getText("attachment.remove")));
            // Update header label
            int slotNumber = imageSlots.indexOf(this) + 1;
            boolean showHeader = maxImages.get() > 1;
            updateHeaderLabel(slotNumber, showHeader);
        }

        void dispose() {
            imageView.setImage(null);
            attachment = null;
        }
    }
}
