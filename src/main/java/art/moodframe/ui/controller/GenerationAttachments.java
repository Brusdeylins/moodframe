package art.moodframe.ui.controller;

import art.moodframe.model.AttachedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.SavedImage;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.library.SavedImageService;
import art.moodframe.ui.component.MultiImageAttachmentPanel;
import art.moodframe.ui.component.SavedImageChoice;
import art.moodframe.ui.dialog.SavedImageManageDialog;
import art.moodframe.ui.dialog.SavedImageSaveDialog;
import art.moodframe.ui.dialog.TrashWindow;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The attached images of the generation panel: the panel of the image squares (FLUX.2, FLUX 3, Gemini, OpenAI), with
 * their file chooser and drag and drop, and the attachment of an image by the edit pencil. The first attached image is
 * the one whose aspect ratio the frame may take, unless it is a saved image of the library (e.g. a logo).
 *
 * <p>The saved images of the library: the choice "Saved" over the squares attaches them, the bookmark of a square saves
 * its image, the cog opens the window of the saved images.</p>
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

    /*  the first of the attached images, null without, and whether it is a saved image  */
    private File attachedImageFile;
    private boolean firstSaved;

    private MultiImageAttachmentPanel multiImagePanel;

    /*  the saved images, null until they are used; their names by id, for the chips of the squares  */
    private SavedImageService savedImages;
    private SavedImageStore savedImageStore;
    private TrashWindow.Opener trash;
    private SavedImageChoice savedImageChoice;
    private Button manageSavedButton;
    private SavedImageManageDialog manageDialog;
    private final Map<String, String> savedNames = new HashMap<>();

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
            firstSaved = attachedImageFile != null && images.get(0).getSavedImageId() != null;

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
        host.attachmentChanged(hasAttachment, hasAttachment && attachmentCount == 1 && !firstSaved
                ? attachedImageFile : null);
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

    /*  ===== Saved images =====  */

    /**
     * Offers the saved images of the library: the choice "Saved" and the cog of their window in the header of the
     * squares, the bookmark of a square, which saves its image.
     *
     * @param service the saved images
     * @param worker  the worker of the window, which writes the library
     * @param trash   opens the trash on the saved images
     */
    void useSavedImages(SavedImageService service, Executor worker, TrashWindow.Opener trash) {
        this.savedImages = service;
        this.savedImageStore = new SavedImageStore(service, worker);
        this.trash = trash;
        savedImageChoice = new SavedImageChoice(new SavedImageChoice.Source() {
            @Override
            public List<SavedImage> list() {
                return savedImageStore.list();
            }

            @Override
            public Path fileOf(SavedImage image) {
                return savedImageStore.fileOf(image);
            }

            @Override
            public Set<String> attached() {
                return Set.copyOf(savedImageIds());
            }
        }, this::toggleSaved);

        FontIcon cog = new FontIcon("mdi2c-cog-outline");
        cog.setIconSize(16);
        cog.getStyleClass().add("ikonli-font-icon");
        manageSavedButton = new Button(null, cog);
        manageSavedButton.getStyleClass().addAll("icon-button", "panel-group-action");
        manageSavedButton.setOnAction((event) -> openManageDialog());
        multiImagePanel.addHeaderActions(savedImageChoice, manageSavedButton);
        multiImagePanel.setSavedImageNames(savedNames::get);
        multiImagePanel.setOnSaveImage(this::saveAttached);
        updateTexts();
        savedImagesChanged();
    }

    /** Shows the texts of the saved images in the current language. */
    void updateTexts() {
        if (savedImageChoice == null)
            return;
        savedImageChoice.updateTexts();
        manageSavedButton.setTooltip(new Tooltip(I18nManager.getText("saved.images.manage.tooltip")));
    }

    /** Shows a change of the saved images, whoever made it: the names of the squares and the open window. */
    void savedImagesChanged() {
        if (savedImages == null)
            return;
        savedNames.clear();
        for (SavedImage image : savedImageStore.list())
            savedNames.put(image.id(), image.name());
        multiImagePanel.refreshSavedImages();
        if (manageDialog != null)
            manageDialog.refresh();
    }

    /** @return the ids of the attached saved images, in the order of the squares */
    List<String> savedImageIds() {
        return multiImagePanel != null ? multiImagePanel.savedImageIds() : List.of();
    }

    /** @return the choice of the saved images, null until they are used; for the tests */
    SavedImageChoice savedImageChoice() {
        return savedImageChoice;
    }

    /**
     * Attaches the saved images of a reused entry again, those not attached yet, one after the other; their files are
     * kept in the library, so they come back when the file first attached is gone.
     *
     * @param ids the ids of the saved images of the entry
     * @return how many of them are no longer in use
     */
    int reattach(List<String> ids) {
        if (savedImages == null || ids.isEmpty())
            return 0;
        int missing = 0;
        List<String> attached = savedImageIds();
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (String id : ids) {
            SavedImage image = savedImages.find(id);
            if (image == null)
                missing++;
            else if (!attached.contains(id))
                chain = chain.thenCompose((Boolean done) -> attachSaved(image));
        }
        return missing;
    }

    /*  a click in the choice attaches a saved image, or removes it if it is attached  */
    private void toggleSaved(SavedImage image) {
        if (savedImageIds().contains(image.id()))
            multiImagePanel.removeSavedImage(image.id());
        else
            attachSaved(image);
    }

    /**
     * Attaches a saved image into the next free square, as long as the model takes more images.
     *
     * @param image the saved image
     * @return completes on the JavaFX thread with whether it is attached
     */
    CompletableFuture<Boolean> attachSaved(SavedImage image) {
        Path file = savedImages.fileOf(image);
        if (!Files.isRegularFile(file)) {
            status.failure(I18nManager.getText("error.saved.image.missing", image.name()));
            return CompletableFuture.completedFuture(false);
        }

        /*  for the first image the panel tells it itself (onFirstImageAttached), as for the edit pencil  */
        boolean first = !multiImagePanel.hasAttachments();
        return multiImagePanel.attachSavedImage(file.toFile(), image.id()).thenApply((Boolean attached) -> {
            if (attached && !first) {
                host.imageAttached();
                updateAttachmentState();
            }
            return attached;
        });
    }

    /*  the bookmark of a square saves its image under the name the user gives it, or renames the saved image  */
    private void saveAttached(AttachedImage attachment) {
        File source = attachment.getSourceFile().isFile() ? attachment.getSourceFile() : attachment.getImageFile();
        String name = source.getName().replaceFirst("\\.[^.]*$", "");
        String suggestion = name.startsWith("clipboard-") || name.startsWith("attachment-")
                ? I18nManager.getText("saved.image.default.name") : name;
        SavedImageSaveDialog.saveFile(window(), savedImageStore, source.toPath(), suggestion, status::failure)
                .thenAccept((SavedImage saved) -> {
                    if (saved == null)
                        return;
                    savedNames.put(saved.id(), saved.name());
                    multiImagePanel.markSaved(attachment, saved.id());
                    status.status(I18nManager.getText("status.saved.image.saved", saved.name()));
                });
    }

    /*  opens the window of the saved images, or brings it to the front  */
    private void openManageDialog() {
        if (manageDialog != null) {
            manageDialog.toFront();
            return;
        }
        manageDialog = new SavedImageManageDialog(window(), savedImageStore, (SavedImage image) -> {
            if (!savedImageIds().contains(image.id()))
                attachSaved(image);
        }, trash);
        manageDialog.setOnHidden((event) -> manageDialog = null);
        manageDialog.show();
    }

    private Window window() {
        return container.getScene() != null ? container.getScene().getWindow() : null;
    }
}
