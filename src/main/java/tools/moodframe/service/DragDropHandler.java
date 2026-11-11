package tools.moodframe.service;

import tools.moodframe.model.AttachedImage;
import tools.moodframe.util.I18nManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handles drag and drop operations for image attachments.
 * Provides visual feedback and integrates with ImageAttachmentService.
 */
public class DragDropHandler implements DragDrop {
    private static final Logger logger = LoggerFactory.getLogger(DragDropHandler.class);
    
    // Supported image formats
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");

    // Use DragState from interface
    private volatile DragState currentState = DragState.IDLE;

    private final ImageAttachmentService attachmentService;
    private final Node dropTarget;
    private final BooleanProperty isDragOver = new SimpleBooleanProperty(false);
    private final ExecutorService executorService = ForkJoinPool.commonPool();
    private final Supplier<Long> maxFileSizeSupplier;

    private Consumer<AttachedImage> onImageAttached;
    private Consumer<String> onError;
    private Consumer<DragState> onStateChanged;

    /**
     * Creates a new DragDropHandler.
     *
     * @param attachmentService the image attachment service
     * @param dropTarget the node to enable drag and drop on
     * @param onImageAttached callback when image is successfully attached
     * @param onError callback when an error occurs
     * @param maxFileSizeSupplier supplies the current max file size in bytes (provider-dependent)
     */
    public DragDropHandler(ImageAttachmentService attachmentService,
                          Node dropTarget,
                          Consumer<AttachedImage> onImageAttached,
                          Consumer<String> onError,
                          Supplier<Long> maxFileSizeSupplier) {
        this.attachmentService = attachmentService;
        this.dropTarget = dropTarget;
        this.onImageAttached = onImageAttached;
        this.onError = onError;
        this.maxFileSizeSupplier = maxFileSizeSupplier;
    }
    
    /**
     * Initializes drag and drop event handlers on the target node.
     */
    @Override
    public void initializeDragAndDrop() {
        if (dropTarget == null) {
            logger.warn("Cannot initialize drag and drop: drop target is null");
            return;
        }
        
        dropTarget.setOnDragOver(this::handleDragOver);
        dropTarget.setOnDragDropped(this::handleDragDropped);
        dropTarget.setOnDragEntered(this::handleDragEntered);
        dropTarget.setOnDragExited(this::handleDragExited);
        
        logger.info("Drag and drop initialized for image attachments");
    }
    
    /**
     * Sets the state change callback.
     * 
     * @param onStateChanged callback for state changes
     */
    @Override
    public void setOnStateChanged(Consumer<DragState> onStateChanged) {
        this.onStateChanged = onStateChanged;
    }
    
    /**
     * Handles drag over events.
     * Uses extension-only check so the OS allows the drop even for oversized files.
     * Full validation (incl. size) happens in handleDragDropped.
     */
    private void handleDragOver(DragEvent event) {
        logDragEvent(event, "OVER");

        if (event.getDragboard().hasFiles()) {
            List<File> files = event.getDragboard().getFiles();
            if (!files.isEmpty() && hasValidImageExtension(files.get(0))) {
                event.acceptTransferModes(TransferMode.COPY);
                updateVisualState(DragState.DRAG_OVER_VALID);
            }
            else {
                updateVisualState(DragState.DRAG_OVER_INVALID);
            }
        }
        event.consume();
    }
    
    /**
     * Handles drag dropped events.
     */
    private void handleDragDropped(DragEvent event) {
        logDragEvent(event, "DROPPED");
        
        Dragboard db = event.getDragboard();
        boolean success = false;
        
        if (db.hasFiles()) {
            List<File> files = db.getFiles();
            if (!files.isEmpty()) {
                File imageFile = files.get(0);
                if (isValidImageFile(imageFile)) {
                    processDroppedImage(imageFile);
                    success = true;
                } else {
                    showError(getInvalidFileError(imageFile));
                }
            }
        }
        
        event.setDropCompleted(success);
        event.consume();
        
        if (!success) {
            updateVisualState(DragState.IDLE);
        }
    }
    
    /**
     * Handles drag entered events.
     */
    private void handleDragEntered(DragEvent event) {
        logDragEvent(event, "ENTERED");
        isDragOver.set(true);

        if (event.getDragboard().hasFiles()) {
            List<File> files = event.getDragboard().getFiles();
            if (!files.isEmpty() && hasValidImageExtension(files.get(0))) {
                updateVisualState(DragState.DRAG_OVER_VALID);
            }
            else {
                updateVisualState(DragState.DRAG_OVER_INVALID);
            }
        }
        event.consume();
    }
    
    /**
     * Handles drag exited events.
     */
    private void handleDragExited(DragEvent event) {
        logDragEvent(event, "EXITED");
        isDragOver.set(false);
        updateVisualState(DragState.IDLE);
        event.consume();
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

        // Check file size against provider-dependent limit
        long maxSize = maxFileSizeSupplier.get();
        if (file.length() > maxSize) {
            logger.debug("File rejected - too large: {} bytes (max: {})", file.length(), maxSize);
            return false;
        }

        // Optional: verify MIME type
        try {
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType != null && !mimeType.startsWith("image/")) {
                logger.debug("File rejected - invalid MIME type: {}", mimeType);
                return false;
            }
        }
        catch (IOException e) {
            logger.warn("Could not determine MIME type for file: {}", file.getName(), e);
        }

        return true;
    }
    
    /**
     * Processes a dropped image file asynchronously.
     */
    private void processDroppedImage(File imageFile) {
        logger.info("Processing dropped image: {}", imageFile.getName());
        updateVisualState(DragState.SUCCESS);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                // Use existing ImageAttachmentService logic
                return attachmentService.attachImage(imageFile);
            } catch (Exception e) {
                logger.error("Failed to process dropped image", e);
                throw new CompletionException(e);
            }
        }, executorService)
        .thenAcceptAsync(attachedImage -> {
            if (onImageAttached != null) {
                onImageAttached.accept(attachedImage);
            }
            
            // Show success animation briefly, then reset
            PauseTransition pause = new PauseTransition(Duration.millis(300));
            pause.setOnFinished(e -> updateVisualState(DragState.IDLE));
            pause.play();
            
            logger.info("Successfully attached dropped image: {}", attachedImage.getFileName());
        }, Platform::runLater)
        .exceptionally(throwable -> {
            Platform.runLater(() -> {
                String errorMsg = I18nManager.getText("error.image.processing.failed");
                showError(errorMsg);
                updateVisualState(DragState.IDLE);
            });
            return null;
        });
    }
    
    /**
     * Updates the visual state of the drop target.
     */
    private void updateVisualState(DragState state) {
        if (currentState == state) {
            return;
        }
        
        currentState = state;
        
        Platform.runLater(() -> {
            // Remove all state classes
            dropTarget.getStyleClass().removeAll(
                "drag-over-valid", "drag-over-invalid", "drop-success"
            );
            
            // Add new state class
            if (!state.getCssClass().isEmpty()) {
                dropTarget.getStyleClass().add(state.getCssClass());
            }
            
            // Notify state change listener
            if (onStateChanged != null) {
                onStateChanged.accept(state);
            }
        });
        
        logger.debug("Visual state updated to: {}", state);
    }
    
    /**
     * Gets an appropriate error message for an invalid file.
     */
    private String getInvalidFileError(File file) {
        String fileName = file.getName().toLowerCase();

        // Check if it's a size issue
        long maxSize = maxFileSizeSupplier.get();
        if (file.length() > maxSize) {
            int maxMB = (int) (maxSize / (1024 * 1024));
            return String.format(I18nManager.getText("error.file.too.large"),
                                file.length() / (1024.0 * 1024.0), maxMB);
        }

        // Check if it's an extension issue
        boolean hasValidExtension = SUPPORTED_EXTENSIONS.stream()
            .anyMatch(fileName::endsWith);

        if (!hasValidExtension)
            return I18nManager.getText("error.unsupported.format") + " (PNG, JPEG, WebP)";

        // Generic error
        return I18nManager.getText("error.invalid.file");
    }
    
    /**
     * Shows an error message to the user.
     */
    private void showError(String message) {
        if (onError != null) {
            Platform.runLater(() -> onError.accept(message));
        }
        logger.error("Drag and drop error: {}", message);
    }
    
    /**
     * Logs drag event details for debugging.
     */
    private void logDragEvent(DragEvent event, String action) {
        if (logger.isDebugEnabled()) {
            Dragboard db = event.getDragboard();
            logger.debug("Drag {} - Coords: ({},{}), Has files: {}, File count: {}",
                action,
                event.getSceneX(), event.getSceneY(),
                db.hasFiles(),
                db.hasFiles() ? db.getFiles().size() : 0
            );
        }
    }
    
    /**
     * Gets the current drag state.
     */
    @Override
    public DragState getCurrentState() {
        return currentState;
    }
    
    /**
     * Checks if drag is currently over the target.
     */
    @Override
    public boolean isDragOver() {
        return isDragOver.get();
    }
    
    /**
     * Gets the drag over property for binding.
     */
    @Override
    public BooleanProperty dragOverProperty() {
        return isDragOver;
    }
    
    /**
     * Cleans up resources.
     */
    @Override
    public void cleanup() {
        if (dropTarget != null) {
            dropTarget.setOnDragOver(null);
            dropTarget.setOnDragDropped(null);
            dropTarget.setOnDragEntered(null);
            dropTarget.setOnDragExited(null);
        }
        logger.info("Drag and drop handler cleaned up");
    }
}