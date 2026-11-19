package art.moodframe.ui.controller;

import art.moodframe.model.AttachedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.ui.component.MultiImageAttachmentPanel;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * The attached images of the generation panel: the panel of the image squares (FLUX.2, FLUX 3, Gemini, OpenAI), with
 * their file chooser and drag and drop, and the attachment of an image by the edit pencil. The first attached image is
 * the one whose aspect ratio the frame may take.
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

    /*  the node of {@code GenerationPanel.fxml} that holds the panel of the image squares  */
    private final VBox container;
    private final Supplier<GenerationModel> currentModel;
    private ImageAttachmentService imageAttachmentService;
    private StatusSink status;
    private Host host;

    /*  the first of the attached images, null without  */
    private File attachedImageFile;

    private MultiImageAttachmentPanel multiImagePanel;

    /**
     * Takes the node of the part.
     *
     * @param container    the node that holds the panel of the image squares
     * @param currentModel the chosen model, whose provider limits the number of images (may give null)
     */
    GenerationAttachments(VBox container, Supplier<GenerationModel> currentModel) {
        this.container = container;
        this.currentModel = currentModel;
    }

    /**
     * Sets up the panel of the image squares.
     *
     * @param attachments reads and encodes the attached images
     * @param status      shows what the part did
     * @param host        follows the changes of the attached images
     */
    void setUp(ImageAttachmentService attachments, StatusSink status, Host host) {
        this.imageAttachmentService = attachments;
        this.status = status;
        this.host = host;
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
     * Removes all attached images, as a switch to a model that cannot take them does.
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
        if (multiImagePanel != null)
            multiImagePanel.setDisable(disable);
        container.setDisable(disable);
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
     * Sets up the panel of the attached images.
     */
    private void setupMultiImageAttachmentPanel() {
        multiImagePanel = new MultiImageAttachmentPanel();
        multiImagePanel.setAttachmentService(imageAttachmentService);
        multiImagePanel.setOnError(status::failure);
        multiImagePanel.setOnMoodFromImage(host::moodFromImage);

        // When the first image is attached, the count, the models and the aspect ratio follow
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
            // The first image is the one whose aspect ratio the frame may take
            attachedImageFile = images != null && !images.isEmpty() ? images.get(0).getImageFile() : null;

            // Filter models based on current image count
            int imageCount = images != null ? (int) images.stream().filter(img -> img != null).count() : 0;
            Platform.runLater(() -> host.imagesChanged(imageCount));
        });
        GenerationModel model = currentModel.get();
        if (model != null)
            multiImagePanel.setCurrentModel(model);
        container.getChildren().setAll(multiImagePanel);
        logger.info("Multi-image attachment panel initialized");
    }

    /**
     * Updates the group once the attachment changed: the models that take images and the aspect ratio of the first
     * image (an added image keeps the aspect ratio of the first one).
     */
    private void updateAttachmentState() {
        boolean hasAttachment = attachedImageFile != null;
        int attachmentCount = multiImagePanel != null ? multiImagePanel.getAttachmentCount() : 1;
        host.attachmentChanged(hasAttachment, hasAttachment && attachmentCount == 1 ? attachedImageFile : null);
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
     * <p>The panel reads and shrinks the image in the background; the first attached image, the count and the models
     * follow once the panel holds it, so the panel is the one source of what is attached.</p>
     *
     * @return whether the image goes into a slot, false if the maximum number of images is reached
     */
    boolean attach(File imageFile) {
        int targetSlot = multiImagePanel.findTargetSlotForNewImage();
        if (targetSlot < 0) {
            logger.warn("Cannot attach image: maximum number of images reached");
            status.status(I18nManager.getText("attachment.max.reached", multiImagePanel.getAttachmentCount()));
            return false;
        }

        /*  The count goes to 1 for image-to-image, the model, the aspect ratio and the style follow the attachment;
            for the first image the panel tells it itself (onFirstImageAttached)  */
        boolean first = !multiImagePanel.hasAttachments();
        multiImagePanel.attachImageToSlot(targetSlot, imageFile).thenAccept((Boolean attached) -> {
            if (!attached || first)
                return;
            host.imageAttached();
            updateAttachmentState();
        });
        return true;
    }
}
