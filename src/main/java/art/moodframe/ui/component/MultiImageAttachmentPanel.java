package art.moodframe.ui.component;

import art.moodframe.model.AttachedImage;
import art.moodframe.model.GenerationModel;

import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.util.I18nManager;
import art.moodframe.ui.support.DiskWorker;
import art.moodframe.ui.support.Subscription;
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
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    // State
    private final List<AttachedImage> attachedImages = new ArrayList<>();
    final IntegerProperty maxImages = new SimpleIntegerProperty(DEFAULT_MAX_IMAGES);
    private final ObjectProperty<GenerationModel> currentModel = new SimpleObjectProperty<>();

    // UI components
    VBox slotsContainer;
    final List<ImageSlot> imageSlots = new ArrayList<>();
    private Label headerLabel;
    private Button pasteButton;
    private Timeline clipboardPoller;
    private Button addSlotButton;
    private Label maxReachedLabel;

    // Callbacks
    private Consumer<List<AttachedImage>> onImagesChanged;
    private Runnable onFirstImageAttached;
    private Runnable onAllImagesRemoved;
    Consumer<String> onError;
    Consumer<File> onMoodFromImage;

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
        pasteButton.getStyleClass().add("icon-button");
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
        ImageSlot slot = new ImageSlot(this, slotIndex);
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
    void updateAddButtonVisibility() {
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
    void updateSlotNumbers() {
        boolean showNumbers = maxImages.get() > 1;
        for (int i = 0; i < imageSlots.size(); i++) {
            imageSlots.get(i).updateSlotNumber(i + 1, showNumbers);
        }
    }

    /**
     * Attaches an image to a specific slot.
     *
     * @param slotIndex the slot
     * @param imageFile the image
     * @return completes on the JavaFX thread with whether the image is in the slot now
     */
    public CompletableFuture<Boolean> attachImageToSlot(int slotIndex, File imageFile) {
        if (slotIndex < 0 || slotIndex >= imageSlots.size()) {
            logger.warn("Invalid slot index: {}", slotIndex);
            return CompletableFuture.completedFuture(false);
        }

        if (attachmentService == null) {
            logger.error("ImageAttachmentService not set");
            return CompletableFuture.completedFuture(false);
        }

        /*  Images above the limit of the provider are shrunk by the service; async to keep the UI responsive  */
        CompletableFuture<Boolean> attached = new CompletableFuture<>();
        attachmentService.createAttachmentAsync(imageFile, getCurrentMaxFileSize())
            .thenAccept(attachment -> Platform.runLater(() -> {
                boolean valid = attachment != null && attachment.isValid();
                if (valid)
                    setImageAtSlot(slotIndex, attachment);
                attached.complete(valid);
            }))
            .exceptionally(e -> {
                logger.error("Failed to attach image: {}", e.getMessage(), e);
                Throwable cause = e;
                while (cause.getCause() != null)
                    cause = cause.getCause();
                String message = cause.getMessage();
                Platform.runLater(() -> {
                    if (onError != null)
                        onError.accept(message);
                    attached.complete(false);
                });
                return null;
            });
        return attached;
    }

    /**
     * Enables the paste button only if the system clipboard holds an image.
     */
    private void updatePasteButtonState() {
        pasteButton.setDisable(!Clipboard.getSystemClipboard().hasImage());
    }

    /**
     * Attaches the image currently on the system clipboard, if any. The pixels are copied at once on the JavaFX
     * thread; the PNG is written and compared with the attached images on a worker, then attached on the JavaFX
     * thread again.
     */
    private void pasteFromClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        Image image = clipboard.hasImage() ? clipboard.getImage() : null;

        if (image == null || image.getPixelReader() == null) {
            if (onError != null)
                onError.accept(I18nManager.getText("attachment.paste.empty"));
            return;
        }

        int width  = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        List<File> attached = getAttachedImages().stream().map(AttachedImage::getImageFile)
                .filter(java.util.Objects::nonNull).toList();

        CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                buffered.setRGB(0, 0, width, height, argb, 0, width);
                File tempFile = File.createTempFile("clipboard-", ".png");
                tempFile.deleteOnExit();
                ImageIO.write(buffered, "png", tempFile);

                /*  Skip if the same image is already attached  */
                byte[] newBytes = Files.readAllBytes(tempFile.toPath());
                for (File existingFile : attached)
                    if (existingFile.length() == newBytes.length
                            && Arrays.equals(Files.readAllBytes(existingFile.toPath()), newBytes)) {
                        tempFile.delete();
                        return null;
                    }
                return tempFile;
            }
            catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, DiskWorker.get()).whenComplete((tempFile, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                logger.error("Failed to paste image from clipboard: {}", cause.getMessage(), cause);
                if (onError != null)
                    onError.accept(cause.getMessage());
            }
            else if (tempFile == null) {
                if (onError != null)
                    onError.accept(I18nManager.getText("attachment.paste.duplicate"));
            }
            else
                attachPasted(tempFile);
        }));
    }

    /*  Attaches a pasted image, always appended: into the first empty slot, else a new slot  */
    private void attachPasted(File tempFile) {
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
    void removeImageAt(int slotIndex) {
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
     * Sets the callback of the context menu of an attached image, which opens the dialog "Mood from image" with it.
     */
    public void setOnMoodFromImage(Consumer<File> onMoodFromImage) {
        this.onMoodFromImage = onMoodFromImage;
    }

    /**
     * Gets the current max file size based on the selected model's provider.
     */
    private long getCurrentMaxFileSize() {
        GenerationModel model = currentModel.get();
        if (model != null)
            return GenerationModel.getMaxFileSize(model.getProvider());
        return ImageAttachmentService.DEFAULT_MAX_FILE_SIZE;
    }

    /**
     * Gets the parent window for file chooser dialogs.
     */
    Window getParentWindow() {
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
}
