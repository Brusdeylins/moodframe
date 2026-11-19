package art.moodframe.ui.controller;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.AttachedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.ui.component.MultiImageAttachmentPanel;
import art.moodframe.ui.dnd.DragDropHandler;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * The attached images of the generation panel: the panel of the image squares (FLUX.2, FLUX 3, Gemini, OpenAI), the
 * single square with drag and drop, the file chooser and the attachment of an image by the edit pencil. The first
 * attached image is the one whose aspect ratio the frame may take.
 */
final class GenerationAttachments {

    private static final Logger logger = LoggerFactory.getLogger(GenerationAttachments.class);

    /** What the generation panel does when the attached images change. */
    interface Host {

        /** An image was attached: the count goes to one. */
        void imageAttached();

        /**
         * The attachment changed: the models that take images follow, and the frame may take the aspect ratio.
         *
         * @param hasAttachment whether an image is attached
         * @param firstImage    the image attached as the only one, else null
         */
        void attachmentChanged(boolean hasAttachment, File firstImage);

        /**
         * The images of the panel changed: the models follow their number, price and summary are updated.
         *
         * @param imageCount the number of attached images
         */
        void imagesChanged(int imageCount);

        /**
         * "Mood from image" at an attached image.
         *
         * @param image the image
         */
        void moodFromImage(File image);
    }

    /**
     * The nodes of {@code GenerationPanel.fxml} the part works on.
     */
    record Nodes(VBox multiImageAttachmentContainer, StackPane imagePreviewContainer, VBox noImagePlaceholder,
                 ImageView attachmentPreviewImage, Button uploadImageButton, Button removeImageButton) {
    }

    private final Nodes nodes;
    private final Supplier<GenerationModel> currentModel;
    private ImageAttachmentService imageAttachmentService;
    private StatusSink status;
    private Host host;

    /*  the first of the attached images, null without  */
    private File attachedImageFile;

    private MultiImageAttachmentPanel multiImagePanel;

    /**
     * Takes the nodes of the part.
     *
     * @param nodes        the nodes
     * @param currentModel the chosen model, whose provider limits the file size (may give null)
     */
    GenerationAttachments(Nodes nodes, Supplier<GenerationModel> currentModel) {
        this.nodes = nodes;
        this.currentModel = currentModel;
    }

    /**
     * Sets up drag and drop on the single square and the panel of the image squares.
     *
     * @param attachments reads and encodes the attached images
     * @param status      shows what the part did
     * @param host        follows the changes of the attached images
     */
    void setUp(ImageAttachmentService attachments, StatusSink status, Host host) {
        this.imageAttachmentService = attachments;
        this.status = status;
        this.host = host;
        setupDragAndDrop();
        setupMultiImageAttachmentPanel();
    }

    /** @return the first attached image, null without */
    File first() {
        return attachedImageFile;
    }

    /** @return the panel of the image squares, null before the set-up */
    MultiImageAttachmentPanel panel() {
        return multiImagePanel;
    }

    /** @return the number of attached images, which filters the models */
    int count() {
        if (attachedImageFile == null)
            return 0;
        return multiImagePanel != null ? Math.max(1, multiImagePanel.getAttachmentCount()) : 1;
    }

    /** @return the number of images in the squares of the panel, for the summary of the group */
    int panelCount() {
        return multiImagePanel != null ? multiImagePanel.getAttachmentCount() : 0;
    }

    /**
     * Tells the panel the chosen model, which limits the number of images.
     *
     * @param model the model
     */
    void setModel(GenerationModel model) {
        if (multiImagePanel != null && model != null)
            multiImagePanel.setCurrentModel(model);
    }

    /**
     * Removes all attached images, as a switch from Gemini to BFL does.
     *
     * @return whether there were images to remove
     */
    boolean clearAll() {
        if (multiImagePanel == null || !multiImagePanel.hasAttachments())
            return false;
        multiImagePanel.clearAllAttachments();
        attachedImageFile = null;
        return true;
    }

    /**
     * Enables or disables the attachment, as the input of the window is.
     *
     * @param disable whether to disable it
     */
    void setDisable(boolean disable) {
        nodes.uploadImageButton().setDisable(disable);
        nodes.removeImageButton().setDisable(disable);
        if (multiImagePanel != null)
            multiImagePanel.setDisable(disable);
        nodes.multiImageAttachmentContainer().setDisable(disable);
        nodes.noImagePlaceholder().setDisable(disable);
        nodes.imagePreviewContainer().setDisable(disable);
    }

    /** Releases the attachment panel, which watches the clipboard. */
    void dispose() {
        if (multiImagePanel != null)
            multiImagePanel.dispose();
    }

    /** The Base64 data of the attached images that are ready to be sent, in their order. */
    List<String> base64() {
        if (multiImagePanel == null || multiImagePanel.getAttachedImages() == null)
            return List.of();
        return multiImagePanel.getAttachedImages().stream()
                .filter((img) -> img != null && img.isReadyForTransmission())
                .map(AttachedImage::getBase64Data)
                .toList();
    }

    /**
     * Sets up drag and drop on the square of the single attachment.
     */
    private void setupDragAndDrop() {
        if (imageAttachmentService == null) {
            logger.warn("Cannot set up drag and drop: no ImageAttachmentService");
            return;
        }
        DragDropHandler dragDropHandler = new DragDropHandler(imageAttachmentService, nodes.imagePreviewContainer(),
                this::handleDroppedImage, status::failure, this::maxFileSize);
        dragDropHandler.initializeDragAndDrop();
        logger.info("Drag and drop initialized for image attachments");
    }

    /*  the file size limit of the provider of the chosen model, that of BFL without  */
    private long maxFileSize() {
        GenerationModel model = currentModel.get();
        return GenerationModel.getMaxFileSize(model != null ? model.getProvider() : ApiProvider.BFL);
    }

    /**
     * Sets up the panel of the attached images for FLUX.2 and Gemini.
     */
    private void setupMultiImageAttachmentPanel() {
        multiImagePanel = new MultiImageAttachmentPanel();
        multiImagePanel.setAttachmentService(imageAttachmentService);
        multiImagePanel.setOnError(status::failure);
        multiImagePanel.setOnMoodFromImage(host::moodFromImage);

        // When first image is attached, apply same logic as single image
        multiImagePanel.setOnFirstImageAttached(() -> Platform.runLater(() -> {
            updateAttachmentState();
            host.imageAttached();
        }));

        // When all images removed, reset state
        multiImagePanel.setOnAllImagesRemoved(() -> Platform.runLater(() -> {
            attachedImageFile = null;
            updateAttachmentState();
        }));

        multiImagePanel.setOnImagesChanged(images -> {
            // The first image is the one of the single attachment
            attachedImageFile = images != null && !images.isEmpty() ? images.get(0).getImageFile() : null;

            // Filter models based on current image count
            int imageCount = images != null ? (int) images.stream().filter(img -> img != null).count() : 0;
            Platform.runLater(() -> host.imagesChanged(imageCount));
        });
        GenerationModel model = currentModel.get();
        if (model != null)
            multiImagePanel.setCurrentModel(model);
        nodes.multiImageAttachmentContainer().getChildren().setAll(multiImagePanel);
        logger.info("Multi-image attachment panel initialized");
    }

    /**
     * Updates the group once the attachment changed: the remove button, the models that take images and the aspect
     * ratio of the first image (an added image keeps the aspect ratio of the first one).
     */
    private void updateAttachmentState() {
        boolean hasAttachment = attachedImageFile != null;
        nodes.removeImageButton().setDisable(!hasAttachment);
        int attachmentCount = multiImagePanel != null ? multiImagePanel.getAttachmentCount() : 1;
        host.attachmentChanged(hasAttachment, hasAttachment && attachmentCount == 1 ? attachedImageFile : null);
    }

    /*  shows an image in the single square and takes it as the first attached image  */
    private void showSingle(File imageFile) {
        // Load and display the image, decoded in the background
        Image image = new Image(imageFile.toURI().toString(), true);
        nodes.attachmentPreviewImage().setImage(image);

        // Show image preview, hide placeholder
        nodes.attachmentPreviewImage().setVisible(true);
        nodes.attachmentPreviewImage().setManaged(true);
        nodes.noImagePlaceholder().setVisible(false);
        nodes.noImagePlaceholder().setManaged(false);

        // Store the file reference for API use
        attachedImageFile = imageFile;

        // Auto-set image count to 1 for image-to-image generation
        host.imageAttached();

        // Update UI to reflect attachment
        updateAttachmentState();
    }

    /**
     * Handles a successfully dropped image.
     *
     * @param attachedImage the attached image with metadata
     */
    private void handleDroppedImage(AttachedImage attachedImage) {
        if (attachedImage == null || attachedImage.getImageFile() == null) {
            logger.error("Invalid attached image received from drag and drop");
            return;
        }

        Platform.runLater(() -> {
            try {
                File imageFile = attachedImage.getImageFile();
                showSingle(imageFile);
                status.status(I18nManager.getText("status.image.attached") + ": " + imageFile.getName());
                logger.info("Image attached via drag and drop: {}", imageFile.getName());

            } catch (Exception e) {
                logger.error("Failed to process dropped image", e);
                status.failure(I18nManager.getText("error.image.processing.failed"));
            }
        });
    }

    /**
     * Chooses an image to attach in a file chooser, as the attach button does.
     */
    void choose() {
        status.status(I18nManager.getText("status.selecting.image"));

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image to Attach");

        // Set file filters for supported image formats
        FileChooser.ExtensionFilter imageFilter = new FileChooser.ExtensionFilter(
                "Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp");
        fileChooser.getExtensionFilters().add(imageFilter);

        // Show the file picker dialog
        Stage stage = (Stage) nodes.uploadImageButton().getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            try {
                // Validate file size against provider-dependent limit
                long maxSize = maxFileSize();
                if (selectedFile.length() > maxSize) {
                    int maxMB = (int) (maxSize / (1024 * 1024));
                    status.error(String.format(I18nManager.getText("status.error.file.too.large"), maxMB));
                    return;
                }
                showSingle(selectedFile);
                status.status(I18nManager.getText("status.image.attached"));

            } catch (Exception e) {
                status.error(I18nManager.getText("status.error.image.load"));
                logger.error("Failed to load selected image: {}", selectedFile.getPath(), e);
            }
        } else {
            status.status(I18nManager.getText("status.error.no.image"));
        }
    }

    /**
     * Removes the image of the single square, as the remove button does.
     */
    void remove() {

        // Clear the attached image
        attachedImageFile = null;
        nodes.attachmentPreviewImage().setImage(null);

        // Show placeholder, hide image preview
        nodes.attachmentPreviewImage().setVisible(false);
        nodes.attachmentPreviewImage().setManaged(false);
        nodes.noImagePlaceholder().setVisible(true);
        nodes.noImagePlaceholder().setManaged(true);

        // Update UI state
        updateAttachmentState();

        status.status(I18nManager.getText("status.image.removed"));
    }

    /**
     * Attaches an image file directly without showing file dialog.
     * Used for Edit functionality where the image is already known.
     *
     * Behavior:
     * 1) If only one image attached with no empty slot: replace it
     * 2) If there's an empty slot: fill it with the new image
     * 3) If 2+ images and no empty slot: create new slot and attach (if under max limit)
     *
     * @return whether the image was attached, false if the maximum number of images is reached
     */
    boolean attach(File imageFile) {
        // Use the MultiImageAttachmentPanel for image attachment (it shrinks images above the size limit)
        if (multiImagePanel != null) {
            int targetSlot = multiImagePanel.findTargetSlotForNewImage();
            if (targetSlot >= 0) {
                multiImagePanel.attachImageToSlot(targetSlot, imageFile);
            } else {
                // Max images reached - show warning
                logger.warn("Cannot attach image: maximum number of images reached");
                status.status(I18nManager.getText("attachment.max.reached",
                    multiImagePanel.getAttachmentCount()));
                return false;
            }
        }

        // Store the file reference for API use (legacy support)
        attachedImageFile = imageFile;

        // Auto-set image count to 1 for image-to-image generation
        host.imageAttached();

        // Update UI to reflect attachment (auto-adjusts model, aspect ratio, style)
        updateAttachmentState();
        return true;
    }
}
