package art.moodframe.ui.component;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.service.ImageDragExporter;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Full-screen image preview overlay component.
 *
 * <p>
 * This component provides a modal-like overlay for displaying images
 * in full-screen mode with zoom, pan, and navigation capabilities.
 * It covers the entire chat area and provides an immersive viewing experience.
 * </p>
 *
 * <p>
 * <strong>Features:</strong>
 * </p>
 * <ul>
 * <li>Full-screen overlay that adapts to window size</li>
 * <li>Zoom controls: mouse wheel, 100% max, zoom buttons</li>
 * <li>Pan image with mouse drag</li>
 * <li>Navigate between images and chat entries</li>
 * <li>Download images automatically as PNG to Downloads folder</li>
 * <li>"Edit This" functionality for prompt creation</li>
 * <li>Comprehensive keyboard shortcuts</li>
 * </ul>
 *
 * <p>
 * <strong>Keyboard Shortcuts:</strong>
 * </p>
 * <ul>
 * <li>ESC - Close overlay</li>
 * <li>Left/Right arrows - Navigate between images</li>
 * <li>Up/Down arrows - Navigate between chat entries (not from the images view)</li>
 * <li>+/- - Zoom in/out</li>
 * <li>F - Toggle zoom (100% ↔ Fit to screen)</li>
 * <li>D - Download current image</li>
 * <li>E - Edit this image (create new prompt)</li>
 * </ul>
 *
 * <p>
 * Opened from the images view, it pages through all images of the grid only, with a counter and the mood, frame
 * and model of each image below.
 * </p>
 *
 * @since 1.0.0
 * @updated 1.3.17 - Added I18n tooltip support with manual listener management
 * @updated 2.0.0 - Gallery of the images view
 */
public class ImagePreviewOverlay extends StackPane implements I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ImagePreviewOverlay.class);

    // Zoom constants
    private static final double ZOOM_FACTOR = 1.2;

    // UI Components - Two completely independent layers
    private StackPane displayLayer; // Black background + image (can zoom/pan)
    private StackPane navigationLayer; // Transparent + fixed buttons
    private AnchorPane navigationAnchor; // Absolute positioning container for buttons
    private ImageView imageView;

    // Navigation buttons
    private Button closeButton;
    private Button prevImageButton;
    private Button nextImageButton;
    private Button prevChatButton; // Navigate to previous chat entry with images
    private Button nextChatButton; // Navigate to next chat entry with images

    // Action buttons
    private Button downloadButton; // Download image with format selection
    private Button editThisButton; // Create new prompt from current image

    // Zoom control buttons
    private Button zoom100Button; // 100% zoom (1:1 pixel ratio)

    /*  The counter and the line of the current image in the gallery of the images view  */
    private HBox galleryInfo;
    private Label counterLabel;
    private Label captionLabel;

    // Video playback components
    private MediaView mediaView;
    private MediaPlayer mediaPlayer;
    private Button playPauseButton;
    private boolean isVideoMode = false;

    // State
    private List<String> imagePaths;
    private List<String> captions; // the line of each image in the gallery, null when opened from a chat
    private int currentImageIndex = 0;
    private double currentZoom = 1.0;
    private double maxZoomForCurrentImage = 1.0;
    private double minZoomForCurrentImage = 0.1;
    private double baseScaleToFit = 1.0; // Scale factor to fit image in preview area
    private double lastPanX = 0;
    private double lastPanY = 0;
    private boolean isDragging = false;

    // Callbacks
    private Consumer<Void> onCloseRequested;
    private Runnable onPrevChatRequested; // Navigate to previous chat entry with images
    private Runnable onNextChatRequested; // Navigate to next chat entry with images
    private Consumer<String> onEditThisRequested; // Create new prompt from current image path
    private Consumer<String> onStatusMessage; // Forward status messages to the main window footer

    /**
     * Creates a new image preview overlay.
     */
    public ImagePreviewOverlay() {
        getStyleClass().add("image-preview-overlay");

        setVisible(false);

        // =================== LAYER 1: DISPLAY LAYER ===================
        // This layer contains the black background and the image (can zoom/pan)
        displayLayer = new StackPane();
        displayLayer.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8);");

        // CRITICAL: Enable clipping to prevent image overflow from affecting button
        // positions
        javafx.scene.shape.Rectangle clipRect = new javafx.scene.shape.Rectangle();
        displayLayer.setClip(clipRect);

        // Update clip size when displayLayer size changes
        displayLayer.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            clipRect.setWidth(newBounds.getWidth());
            clipRect.setHeight(newBounds.getHeight());

            // Re-center the image if it's loaded
            if (imageView.getImage() != null) {
                javafx.application.Platform.runLater(() -> applyZoomAndSize());
            }
        });

        // Create main image view with drop shadow
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        // CRITICAL: Prevent image from affecting layout calculations - force contained
        // rendering
        imageView.setPickOnBounds(true);
        imageView.setManaged(false); // This prevents the image from affecting parent layout

        // Add drop shadow directly to the image (10px offset, 10px blur, 0.4 opacity)
        javafx.scene.effect.DropShadow dropShadow = new javafx.scene.effect.DropShadow();
        dropShadow.setOffsetX(10);
        dropShadow.setOffsetY(10);
        dropShadow.setRadius(10);
        dropShadow.setColor(javafx.scene.paint.Color.BLACK.deriveColor(0, 1, 1, 0.4));
        imageView.setEffect(dropShadow);

        displayLayer.getChildren().add(imageView);
        StackPane.setAlignment(imageView, Pos.CENTER);

        // Create MediaView for video playback (initially hidden)
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.setSmooth(true);
        mediaView.setVisible(false);
        mediaView.setManaged(false);
        displayLayer.getChildren().add(mediaView);
        StackPane.setAlignment(mediaView, Pos.CENTER);

        // =================== LAYER 2: NAVIGATION LAYER ===================
        // This layer uses AnchorPane for absolute positioning to prevent layout
        // interference
        navigationAnchor = new AnchorPane();
        navigationAnchor.setStyle("-fx-background-color: transparent;");
        navigationAnchor.setPickOnBounds(false);
        navigationAnchor.setMouseTransparent(false);

        // Wrap in StackPane for compatibility with existing code
        navigationLayer = new StackPane();
        navigationLayer.setStyle("-fx-background-color: transparent;");
        navigationLayer.getChildren().add(navigationAnchor);

        // Create all navigation buttons
        createNavigationButtons();

        // =================== COMBINE LAYERS ===================
        // Add both layers to main overlay
        getChildren().addAll(displayLayer, navigationLayer);

        // CRITICAL: Ensure this overlay itself clips content and has fixed size
        // behavior
        this.setStyle("-fx-background-color: transparent;");
        this.setPickOnBounds(true); // Ensure consistent bounds calculation

        // Set up event handlers with proper layer separation
        setupEventHandlers();

        // Force white icons programmatically after all setup
        javafx.application.Platform.runLater(() -> forceWhiteIcons());

        // Register I18n listener for tooltip updates on language change
        I18nManager.addChangeListener(this);
    }

    /**
     * Creates all navigation buttons with white icons.
     */
    private void createNavigationButtons() {
        // Close button (top-right)
        closeButton = new Button();
        FontIcon closeIcon = new FontIcon("mdi2c-close");
        closeIcon.setIconSize(20);
        closeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        closeButton.setGraphic(closeIcon);
        closeButton.getStyleClass().add("icon-button");
        closeButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.close")));

        // Previous image button (left side)
        prevImageButton = new Button();
        FontIcon prevImageIcon = new FontIcon("mdi2c-chevron-left");
        prevImageIcon.setIconSize(24);
        prevImageIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        prevImageButton.setGraphic(prevImageIcon);
        prevImageButton.getStyleClass().add("icon-button");
        prevImageButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.prev.image")));

        // Next image button (right side)
        nextImageButton = new Button();
        FontIcon nextImageIcon = new FontIcon("mdi2c-chevron-right");
        nextImageIcon.setIconSize(24);
        nextImageIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        nextImageButton.setGraphic(nextImageIcon);
        nextImageButton.getStyleClass().add("icon-button");
        nextImageButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.next.image")));

        // Previous chat entry button (top center)
        prevChatButton = new Button();
        FontIcon prevChatIcon = new FontIcon("mdi2c-chevron-up");
        prevChatIcon.setIconSize(24);
        prevChatIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        prevChatButton.setGraphic(prevChatIcon);
        prevChatButton.getStyleClass().add("icon-button");
        prevChatButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.prev.chat")));

        // Next chat entry button (bottom center)
        nextChatButton = new Button();
        FontIcon nextChatIcon = new FontIcon("mdi2c-chevron-down");
        nextChatIcon.setIconSize(24);
        nextChatIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        nextChatButton.setGraphic(nextChatIcon);
        nextChatButton.getStyleClass().add("icon-button");
        nextChatButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.next.chat")));

        // Download button (bottom left)
        downloadButton = new Button();
        FontIcon downloadIcon = new FontIcon("mdi2d-download");
        downloadIcon.setIconSize(20);
        downloadIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        downloadButton.setGraphic(downloadIcon);
        downloadButton.getStyleClass().add("icon-button");
        downloadButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.download.image")));

        // Edit This button (bottom right)
        editThisButton = new Button();
        FontIcon editIcon = new FontIcon("mdi2p-pencil");
        editIcon.setIconSize(20);
        editIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        editThisButton.setGraphic(editIcon);
        editThisButton.getStyleClass().add("icon-button");
        editThisButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.edit.image")));

        // Toggle Zoom button (100% <-> Fit to screen) - top right, next to close button
        zoom100Button = new Button();
        FontIcon zoomToggleIcon = new FontIcon("mdi2m-magnify-plus"); // Start with magnify-plus icon (means: will go to
                                                                      // 100%)
        zoomToggleIcon.setIconSize(20);
        zoomToggleIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        zoom100Button.setGraphic(zoomToggleIcon);
        zoom100Button.getStyleClass().add("icon-button");
        // Ensure button can receive mouse events
        zoom100Button.setMouseTransparent(false);
        zoom100Button.setPickOnBounds(true);

        // Play/Pause button for video playback (bottom center, initially hidden)
        playPauseButton = new Button();
        FontIcon playIcon = new FontIcon("mdi2p-play");
        playIcon.setIconSize(24);
        playIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        playPauseButton.setGraphic(playIcon);
        playPauseButton.getStyleClass().add("icon-button");
        playPauseButton.setTooltip(new Tooltip("Play / Pause"));
        playPauseButton.setVisible(false);

        /*  The gallery shows the counter and the line of the image at the bottom center  */
        counterLabel = new Label();
        counterLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        captionLabel = new Label();
        captionLabel.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.8); -fx-font-size: 12px;");
        VBox galleryBox = new VBox(2.0, counterLabel, captionLabel);
        galleryBox.setAlignment(Pos.CENTER);
        galleryBox.setMaxWidth(Region.USE_PREF_SIZE);
        galleryBox.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-background-radius: 12; "
                + "-fx-padding: 6 14 6 14;");
        galleryInfo = new HBox(galleryBox);
        galleryInfo.setAlignment(Pos.CENTER);
        galleryInfo.setMouseTransparent(true);
        galleryInfo.setVisible(false);

        // Add all buttons to navigation anchor with absolute positioning
        navigationAnchor.getChildren().addAll(
                closeButton,
                prevImageButton, nextImageButton,
                prevChatButton, nextChatButton,
                downloadButton, editThisButton,
                zoom100Button, playPauseButton, galleryInfo);

        // Set absolute positions using AnchorPane constraints
        setAbsoluteButtonPositions();
    }

    /**
     * Sets absolute button positions using AnchorPane constraints to prevent layout
     * interference.
     */
    private void setAbsoluteButtonPositions() {
        // Close button - top right corner (absolute position)
        AnchorPane.setTopAnchor(closeButton, 20.0);
        AnchorPane.setRightAnchor(closeButton, 20.0);

        // Toggle Zoom button - top right, next to close button (absolute position)
        AnchorPane.setTopAnchor(zoom100Button, 20.0);
        AnchorPane.setRightAnchor(zoom100Button, 70.0); // 20 + 40 + 10 margin from close button

        // Image navigation - left and right sides (centered vertically)
        AnchorPane.setLeftAnchor(prevImageButton, 30.0);
        AnchorPane.setRightAnchor(nextImageButton, 30.0);

        // Center image navigation buttons vertically via layout listener
        navigationAnchor.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            // Center left/right navigation buttons vertically
            double centerY = (newBounds.getHeight() - prevImageButton.prefHeight(-1)) / 2;
            prevImageButton.setLayoutY(centerY);
            nextImageButton.setLayoutY(centerY);

            // Center chat navigation buttons horizontally
            double centerX = (newBounds.getWidth() - prevChatButton.prefWidth(-1)) / 2;
            prevChatButton.setLayoutX(centerX);
            nextChatButton.setLayoutX(centerX);
        });

        // Chat navigation - top and bottom center (will be positioned via layout
        // listener)
        AnchorPane.setTopAnchor(prevChatButton, 20.0);
        AnchorPane.setBottomAnchor(nextChatButton, 20.0);

        // Action buttons - top left (absolute position)
        AnchorPane.setTopAnchor(downloadButton, 20.0);
        AnchorPane.setLeftAnchor(downloadButton, 20.0);

        AnchorPane.setTopAnchor(editThisButton, 20.0);
        AnchorPane.setLeftAnchor(editThisButton, 70.0); // 20 + 40 + 10 margin from download button

        // Play/Pause button - bottom center (positioned via layout listener)
        AnchorPane.setBottomAnchor(playPauseButton, 80.0);

        AnchorPane.setLeftAnchor(galleryInfo, 0.0);
        AnchorPane.setRightAnchor(galleryInfo, 0.0);
        AnchorPane.setBottomAnchor(galleryInfo, 20.0);

        // Center play button horizontally via layout listener
        navigationAnchor.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            double centerX2 = (newBounds.getWidth() - playPauseButton.prefWidth(-1)) / 2;
            playPauseButton.setLayoutX(centerX2);
        });
    }

    /**
     * Sets up mouse and keyboard event handlers with proper layer separation.
     */
    private void setupEventHandlers() {
        // =================== NAVIGATION LAYER EVENTS ===================
        // Close button handler - Use only setOnAction to avoid double triggering
        closeButton.setOnAction(e -> {
            closeOverlay();
            requestFocus(); // Restore focus for keyboard shortcuts
        });

        // Image navigation button handlers - Use only setOnAction to avoid double
        // triggering
        prevImageButton.setOnAction(e -> {
            showPreviousImage();
            requestFocus(); // Restore focus for keyboard shortcuts
        });

        nextImageButton.setOnAction(e -> {
            showNextImage();
            requestFocus(); // Restore focus for keyboard shortcuts
        });

        // Chat navigation button handlers - Use only setOnAction to avoid double
        // triggering
        prevChatButton.setOnAction(e -> {
            showPreviousChat();
            requestFocus(); // Restore focus for keyboard shortcuts
        });

        nextChatButton.setOnAction(e -> {
            showNextChat();
            requestFocus(); // Restore focus for keyboard shortcuts
        });

        // Action button handlers - Use only setOnAction to avoid double triggering
        downloadButton.setOnAction(e -> {
            downloadCurrentImage();
            requestFocus(); // Restore focus for keyboard shortcuts
        });

        editThisButton.setOnAction(e -> {
            editCurrentImage();
            requestFocus(); // Restore focus for keyboard shortcuts
        });

        // Toggle Zoom button handlers - use only setOnAction for cleaner event handling
        zoom100Button.setOnAction(e -> {
            toggleZoom(); // Call the actual zoom toggle function
            requestFocus(); // Restore focus for keyboard shortcuts
            e.consume(); // Prevent event bubbling
        });

        // Play/Pause button handler for video
        playPauseButton.setOnAction(e -> {
            toggleVideoPlayback();
            requestFocus();
        });

        // =================== DISPLAY LAYER EVENTS ===================
        // Forward events from navigation layer to display layer for zoom/pan
        // BUT only if the event doesn't come from a button
        navigationLayer.setOnMousePressed(e -> {
            // Don't handle mouse events if they come from buttons
            if (!(e.getTarget() instanceof Button) &&
                    !(e.getTarget() instanceof javafx.scene.Node &&
                            ((javafx.scene.Node) e.getTarget()).getParent() instanceof Button)) {
                handleMousePressed(e);
            }
        });
        navigationLayer.setOnMouseDragged(e -> {
            if (!(e.getTarget() instanceof Button) &&
                    !(e.getTarget() instanceof javafx.scene.Node &&
                            ((javafx.scene.Node) e.getTarget()).getParent() instanceof Button)) {
                handleMouseDragged(e);
            }
        });
        navigationLayer.setOnMouseReleased(e -> {
            if (!(e.getTarget() instanceof Button) &&
                    !(e.getTarget() instanceof javafx.scene.Node &&
                            ((javafx.scene.Node) e.getTarget()).getParent() instanceof Button)) {
                handleMouseReleased(e);
            }
        });
        navigationLayer.setOnScroll(this::handleScroll);
        navigationLayer.setOnMouseMoved(this::handleMouseMoved);

        // Drag the current image out of the app (to Finder/Explorer or PowerPoint).
        // Only when fit-to-screen (not zoomed) so it doesn't conflict with pan-drag, and not for videos.
        navigationLayer.setOnDragDetected(e -> {
            if (!isVideoMode &&
                    currentZoom <= minZoomForCurrentImage + 0.001 &&
                    imagePaths != null && !imagePaths.isEmpty() &&
                    currentImageIndex >= 0 && currentImageIndex < imagePaths.size()) {
                File imageFile = new File(imagePaths.get(currentImageIndex));
                if (imageFile.exists())
                    ImageDragExporter.startDrag(imageView, imageFile);
            }
            e.consume();
        });

        // Remove general click handler - only ESC and X button should close the overlay
        // This prevents accidental closing when clicking on the image

        // =================== KEYBOARD EVENTS ===================
        // Keyboard shortcuts on the main overlay
        setFocusTraversable(true);
        setOnKeyPressed(this::handleKeyPress);
    }

    /**
     * Handles keyboard input for navigation and closing.
     */
    private void handleKeyPress(KeyEvent event) {
        switch (event.getCode()) {
            case ESCAPE -> closeOverlay();
            case LEFT -> showPreviousImage();
            case RIGHT -> showNextImage();
            case UP -> { if (captions == null) showPreviousChat(); }
            case DOWN -> { if (captions == null) showNextChat(); }
            case PLUS, ADD -> zoomIn();
            case MINUS, SUBTRACT -> zoomOut();
            case F -> toggleZoom(); // F key for toggle zoom
            case SPACE -> { if (isVideoMode) toggleVideoPlayback(); } // Space for play/pause
            case D -> downloadCurrentImage(); // D key for download
            case E -> editCurrentImage(); // E key for edit this
            default -> {
                /* No action */ }
        }
        event.consume();
    }

    /**
     * Handles mouse wheel scrolling for zoom.
     */
    private void handleScroll(ScrollEvent event) {
        if (event.getDeltaY() > 0) {
            zoomIn();
        } else {
            zoomOut();
        }
        event.consume();
    }

    /**
     * Handles mouse press for drag start.
     */
    private void handleMousePressed(MouseEvent event) {
        // Only start dragging if not clicking on fixed UI buttons
        // Check if we clicked on the fixed UI layer or its children (buttons)
        if (event.getTarget() instanceof Button ||
                (event.getTarget() instanceof javafx.scene.Node &&
                        ((javafx.scene.Node) event.getTarget()).getParent() instanceof Button)) {
            return; // Don't start dragging if clicking on buttons
        }

        // Don't allow dragging if image is smaller than overlay (fit-to-screen)
        if (imageView.getImage() != null) {
            double imageWidth = imageView.getFitWidth();
            double imageHeight = imageView.getFitHeight();
            double overlayWidth = getWidth();
            double overlayHeight = getHeight();

            // If image fits completely in the overlay, don't allow panning
            if (imageWidth <= overlayWidth && imageHeight <= overlayHeight) {
                return;
            }
        }

        lastPanX = event.getSceneX();
        lastPanY = event.getSceneY();
        isDragging = true;
        setCursor(javafx.scene.Cursor.MOVE);
        event.consume();
    }

    /**
     * Handles mouse drag for image panning.
     */
    private void handleMouseDragged(MouseEvent event) {
        if (isDragging) {
            double deltaX = event.getSceneX() - lastPanX;
            double deltaY = event.getSceneY() - lastPanY;

            imageView.setTranslateX(imageView.getTranslateX() + deltaX);
            imageView.setTranslateY(imageView.getTranslateY() + deltaY);

            // Constrain position to keep image partially visible
            constrainImagePosition();

            lastPanX = event.getSceneX();
            lastPanY = event.getSceneY();
        }
        event.consume();
    }

    /**
     * Handles mouse release to end dragging.
     */
    private void handleMouseReleased(MouseEvent event) {
        isDragging = false;
        updateCursor();
        event.consume();
    }

    /**
     * Handles mouse movement to update cursor based on pan availability.
     */
    private void handleMouseMoved(MouseEvent event) {
        if (!isDragging) {
            updateCursor();
        }
        event.consume();
    }

    /**
     * Updates the cursor based on whether panning is possible.
     */
    private void updateCursor() {
        if (imageView.getImage() != null) {
            double imageWidth = imageView.getFitWidth();
            double imageHeight = imageView.getFitHeight();
            double overlayWidth = getWidth();
            double overlayHeight = getHeight();

            // If image fits completely in the overlay, show default cursor
            if (imageWidth <= overlayWidth && imageHeight <= overlayHeight) {
                setCursor(javafx.scene.Cursor.DEFAULT);
            } else {
                // Image is larger than overlay, show hand cursor to indicate panning is
                // possible
                setCursor(javafx.scene.Cursor.OPEN_HAND);
            }
        } else {
            setCursor(javafx.scene.Cursor.DEFAULT);
        }
    }

    /**
     * Zooms the image in.
     */
    private void zoomIn() {
        if (currentZoom < maxZoomForCurrentImage) {
            currentZoom *= ZOOM_FACTOR;
            // Don't go above maximum zoom (1:1 pixel ratio)
            currentZoom = Math.min(currentZoom, maxZoomForCurrentImage);
            applyZoom();
            constrainImagePosition();
        }
    }

    /**
     * Zooms the image out.
     */
    private void zoomOut() {
        if (currentZoom > minZoomForCurrentImage) {
            currentZoom /= ZOOM_FACTOR;
            // Don't go below minimum zoom (fit-to-preview)
            currentZoom = Math.max(currentZoom, minZoomForCurrentImage);
            applyZoom();
            constrainImagePosition();
        }
    }

    /**
     * Applies the current zoom level to the image using actual pixel dimensions.
     */
    private void applyZoom() {
        applyZoomAndSize();

        // Reset pan position when at minimum zoom (fit-to-preview)
        if (currentZoom <= baseScaleToFit + 0.01) {
            imageView.setTranslateX(0);
            imageView.setTranslateY(0);
        }

        // Update toggle button state based on new zoom level
        updateZoomButtonState();

        // Update cursor based on new zoom level
        updateCursor();
    }

    /**
     * Applies zoom by setting actual pixel size of the image.
     * currentZoom = 1.0 means original pixel size (1:1 ratio)
     * currentZoom = baseScaleToFit means scaled to fit preview area
     */
    private void applyZoomAndSize() {
        if (imageView.getImage() == null)
            return;

        // Get original image dimensions
        double originalWidth = imageView.getImage().getWidth();
        double originalHeight = imageView.getImage().getHeight();

        // Calculate actual display size based on zoom level
        double displayWidth = originalWidth * currentZoom;
        double displayHeight = originalHeight * currentZoom;

        // Set the ImageView to display at exact pixel size
        imageView.setFitWidth(displayWidth);
        imageView.setFitHeight(displayHeight);

        // Reset scale transforms (we're using fitWidth/fitHeight for sizing)
        imageView.setScaleX(1.0);
        imageView.setScaleY(1.0);

        // Manually center the image since setManaged(false) disables automatic
        // centering
        double containerWidth = displayLayer.getWidth();
        double containerHeight = displayLayer.getHeight();
        if (containerWidth > 0 && containerHeight > 0) {
            imageView.setLayoutX((containerWidth - displayWidth) / 2);
            imageView.setLayoutY((containerHeight - displayHeight) / 2);
        }

    }

    /**
     * Constrains the image position to keep it always partially visible.
     * For images smaller than the overlay, prevents any panning.
     */
    private void constrainImagePosition() {
        if (imageView.getImage() == null) {
            return;
        }

        // Get current image display dimensions (already scaled via fitWidth/fitHeight)
        double imageWidth = imageView.getFitWidth();
        double imageHeight = imageView.getFitHeight();

        // Get overlay dimensions
        double overlayWidth = getWidth();
        double overlayHeight = getHeight();

        // If image is smaller than or equal to overlay, center it and prevent panning
        if (imageWidth <= overlayWidth && imageHeight <= overlayHeight) {
            imageView.setTranslateX(0);
            imageView.setTranslateY(0);
            return;
        }

        // For larger images, calculate maximum allowed translation to keep part of
        // image visible
        double maxTranslateX = Math.max(0, (imageWidth - overlayWidth / 2) / 2);
        double maxTranslateY = Math.max(0, (imageHeight - overlayHeight / 2) / 2);

        // Constrain X position using imageView
        double currentX = imageView.getTranslateX();
        if (currentX > maxTranslateX) {
            imageView.setTranslateX(maxTranslateX);
        } else if (currentX < -maxTranslateX) {
            imageView.setTranslateX(-maxTranslateX);
        }

        // Constrain Y position using imageView
        double currentY = imageView.getTranslateY();
        if (currentY > maxTranslateY) {
            imageView.setTranslateY(maxTranslateY);
        } else if (currentY < -maxTranslateY) {
            imageView.setTranslateY(-maxTranslateY);
        }
    }

    /**
     * Calculates zoom limits based on actual pixel sizes.
     * Max zoom = 1:1 pixel ratio (original size)
     * Min zoom = fit to preview area (unless image is smaller)
     */
    private void calculateZoomLimits(Image image) {
        if (image == null) {
            maxZoomForCurrentImage = 1.0;
            minZoomForCurrentImage = 0.1;
            baseScaleToFit = 1.0;
            return;
        }

        // Get image original pixel dimensions
        double originalWidth = image.getWidth();
        double originalHeight = image.getHeight();

        // Get available preview area (overlay size minus margins)
        double overlayWidth = getWidth() - 160; // Leave margins for nav buttons
        double overlayHeight = getHeight() - 120; // Leave space for info label

        // Calculate scale factor needed to fit image in preview area
        double scaleToFitWidth = overlayWidth / originalWidth;
        double scaleToFitHeight = overlayHeight / originalHeight;
        baseScaleToFit = Math.min(scaleToFitWidth, scaleToFitHeight);

        // Ensure we don't scale UP small images to fit
        baseScaleToFit = Math.min(1.0, baseScaleToFit);

        // Maximum zoom is 1.0 (original pixel size, 1:1 ratio)
        maxZoomForCurrentImage = 1.0;

        // Minimum zoom is the scale needed to fit in preview area
        // But never smaller than 0.1 for usability
        minZoomForCurrentImage = Math.max(0.1, baseScaleToFit);

    }

    /**
     * Shows the previous image in the list.
     */
    private void showPreviousImage() {
        if (imagePaths != null && !imagePaths.isEmpty() && currentImageIndex > 0) {
            currentImageIndex--;
            loadCurrentImage();
        }
    }

    /**
     * Shows the next image in the list.
     */
    private void showNextImage() {
        if (imagePaths != null && !imagePaths.isEmpty() && currentImageIndex < imagePaths.size() - 1) {
            currentImageIndex++;
            loadCurrentImage();
        }
    }

    /**
     * Shows the previous chat entry with images.
     */
    private void showPreviousChat() {
        if (onPrevChatRequested != null) {
            onPrevChatRequested.run();
        }
    }

    /**
     * Shows the next chat entry with images.
     */
    private void showNextChat() {
        if (onNextChatRequested != null) {
            onNextChatRequested.run();
        }
    }

    /**
     * Downloads the current image as PNG to the system Downloads folder.
     */
    private void downloadCurrentImage() {
        if (imagePaths == null || imagePaths.isEmpty() || currentImageIndex < 0
                || currentImageIndex >= imagePaths.size()) {
            return;
        }

        File sourceFile = new File(imagePaths.get(currentImageIndex));
        if (!sourceFile.exists()) {
            logger.warn("Cannot download image - file not found: {}", sourceFile);
            if (onStatusMessage != null) {
                onStatusMessage.accept(I18nManager.getText("download.image.notfound"));
            }
            return;
        }

        try {
            String filename = art.moodframe.util.DownloadsFolder.copy(sourceFile);
            if (onStatusMessage != null) {
                onStatusMessage.accept(java.text.MessageFormat.format(
                        I18nManager.getText("download.image.success"), filename));
            }
        } catch (Exception e) {
            logger.error("Failed to download image to Downloads folder", e);
            if (onStatusMessage != null) {
                onStatusMessage.accept(java.text.MessageFormat.format(
                        I18nManager.getText("download.image.error"), e.getMessage()));
            }
        }
    }

    /**
     * Initiates edit mode for the current image.
     */
    private void editCurrentImage() {
        if (imagePaths == null || imagePaths.isEmpty() || currentImageIndex < 0
                || currentImageIndex >= imagePaths.size()) {
            return;
        }

        String currentImagePath = imagePaths.get(currentImageIndex);

        if (onEditThisRequested != null) {
            onEditThisRequested.accept(currentImagePath);
        }

        // Close overlay after initiating edit
        closeOverlay();
    }

    /**
     * Toggles between 100% zoom and fit-to-screen zoom.
     * Only performs the toggle if the zoom button is enabled (zoom operations are
     * possible).
     */
    private void toggleZoom() {

        // Don't perform toggle if zoom button is disabled
        if (zoom100Button.isDisabled()) {
            return;
        }

        // Check if we can zoom to 100% (1.0)
        boolean canZoomTo100 = (1.0 <= maxZoomForCurrentImage) && (Math.abs(currentZoom - 1.0) > 0.05);

        // Check if we can zoom to fit-to-screen (baseScaleToFit)
        boolean canZoomToFit = (baseScaleToFit >= minZoomForCurrentImage)
                && (Math.abs(currentZoom - baseScaleToFit) > 0.05);

        // Determine toggle action based on current zoom and available operations
        if (Math.abs(currentZoom - 1.0) < 0.05) {
            // We're at 100%, switch to fit-to-screen if possible
            if (canZoomToFit) {
                zoomToFit();
            }
        } else if (Math.abs(currentZoom - baseScaleToFit) < 0.05) {
            // We're at fit-to-screen, switch to 100% if possible
            if (canZoomTo100) {
                zoomTo100();
            }
        } else {
            // We're at some other zoom level, prioritize going to 100% if possible
            if (canZoomTo100) {
                zoomTo100();
            } else if (canZoomToFit) {
                zoomToFit();
            }
        }

        // Update button state after toggle
        updateZoomButtonState();
    }

    /**
     * Zooms image to 100% (1:1 pixel ratio).
     */
    private void zoomTo100() {
        currentZoom = 1.0;
        applyZoom();
        constrainImagePosition();
        updateZoomButtonState();
    }

    /**
     * Zooms image to fit the screen (same as minimum zoom).
     */
    private void zoomToFit() {
        currentZoom = baseScaleToFit;
        applyZoom();
        // Reset position when fitting to screen
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        updateZoomButtonState();
    }

    /**
     * Updates the zoom toggle button icon based on current state.
     * 
     * @param showZoomInIcon true to show zoom-in icon (next action: zoom to 100%),
     *                       false to show fit-to-page icon (next action: fit to
     *                       screen)
     */
    private void updateZoomButtonIcon(boolean showZoomInIcon) {
        zoom100Button.setText(""); // Always clear text

        // Get the existing FontIcon or create a new one if it doesn't exist
        FontIcon zoomIcon;
        if (zoom100Button.getGraphic() instanceof FontIcon) {
            zoomIcon = (FontIcon) zoom100Button.getGraphic();
        } else {
            // This should only happen on first initialization
            zoomIcon = new FontIcon();
            zoomIcon.setIconSize(20);
            zoomIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            zoom100Button.setGraphic(zoomIcon);
        }

        if (showZoomInIcon) {
            // Update to magnify-plus icon (next action will be zoom to 100%)
            zoomIcon.setIconLiteral("mdi2m-magnify-plus");
            zoom100Button.setTooltip(new javafx.scene.control.Tooltip(I18nManager.getText("tooltip.preview.zoom.to.100")));
        } else {
            // Update to magnify-minus icon (next action will be fit-to-screen)
            zoomIcon.setIconLiteral("mdi2m-magnify-minus");
            zoom100Button.setTooltip(new javafx.scene.control.Tooltip(I18nManager.getText("tooltip.preview.zoom.fit.screen")));
        }

        // Ensure icon color remains white after icon code change
        zoomIcon.setIconColor(javafx.scene.paint.Color.WHITE);
    }

    /**
     * Updates the zoom button to show a simple magnify icon when no zoom operations
     * are possible.
     */
    private void updateZoomButtonIconSimple() {
        zoom100Button.setText(""); // Always clear text

        // Get the existing FontIcon or create a new one if it doesn't exist
        FontIcon zoomIcon;
        if (zoom100Button.getGraphic() instanceof FontIcon) {
            zoomIcon = (FontIcon) zoom100Button.getGraphic();
        } else {
            // This should only happen on first initialization
            zoomIcon = new FontIcon();
            zoomIcon.setIconSize(20);
            zoomIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            zoom100Button.setGraphic(zoomIcon);
        }

        // Set simple magnify icon without plus or minus
        zoomIcon.setIconLiteral("mdi2m-magnify");
        zoom100Button.setTooltip(new javafx.scene.control.Tooltip(I18nManager.getText("tooltip.preview.zoom.unavailable")));

        // Ensure icon color remains white after icon code change
        zoomIcon.setIconColor(javafx.scene.paint.Color.WHITE);
    }

    /**
     * Updates the zoom button state (enabled/disabled) and icon based on current
     * zoom possibilities.
     * This method determines if zoom operations are possible and updates the button
     * accordingly.
     */
    private void updateZoomButtonState() {
        if (imageView.getImage() == null) {
            zoom100Button.setDisable(true);
            zoom100Button.setVisible(false);
            return;
        }

        zoom100Button.setVisible(true);

        // Check if we can zoom to 100% (1.0)
        boolean canZoomTo100 = (1.0 <= maxZoomForCurrentImage) && (Math.abs(currentZoom - 1.0) > 0.05);

        // Check if we can zoom to fit-to-screen (baseScaleToFit)
        boolean canZoomToFit = (baseScaleToFit >= minZoomForCurrentImage)
                && (Math.abs(currentZoom - baseScaleToFit) > 0.05);

        // Check if any zoom operation is possible
        boolean canZoom = canZoomTo100 || canZoomToFit;

        if (!canZoom) {
            // No zoom operations possible - show simple magnify icon and disable
            zoom100Button.setDisable(true);
            zoom100Button.setOpacity(0.5);
            updateZoomButtonIconSimple(); // Show simple magnify icon
        } else {
            // Zoom operations possible - enable and show appropriate icon
            zoom100Button.setDisable(false);
            zoom100Button.setOpacity(1.0);

            // Determine icon based on current zoom state
            if (Math.abs(currentZoom - 1.0) < 0.05) {
                // We're at 100%, next action would be fit-to-screen
                updateZoomButtonIcon(false); // Show magnify-minus icon (fit-to-screen)
            } else {
                // We're at any other zoom level (including fit-to-screen), next action would be
                // 100%
                updateZoomButtonIcon(true); // Show magnify-plus icon (zoom to 100%)
            }
        }
    }

    /**
     * Loads and displays the current image or video.
     */
    private void loadCurrentImage() {
        if (imagePaths == null || imagePaths.isEmpty() || currentImageIndex < 0
                || currentImageIndex >= imagePaths.size()) {
            return;
        }

        String imagePath = imagePaths.get(currentImageIndex);
        File imageFile = new File(imagePath);

        if (imageFile.exists()) {
            try {
                // Check if this is a video file
                if (imagePath.toLowerCase().endsWith(".mp4")) {
                    loadVideo(imageFile);
                } else {
                    // Stop any playing video first
                    stopVideo();

                    Image image = new Image(imageFile.toURI().toString());
                    imageView.setImage(image);

                    // Show image, hide video
                    imageView.setVisible(true);
                    mediaView.setVisible(false);
                    isVideoMode = false;
                    playPauseButton.setVisible(false);

                    // Calculate zoom limits based on actual pixel sizes
                    calculateZoomLimits(image);

                    // Reset zoom and pan
                    resetImageTransform();

                    // Update zoom button state for new image
                    updateZoomButtonState();
                }

                // Update navigation state
                updateNavigationState();

            } catch (Exception e) {
                logger.error("Failed to load media: {}", imagePath, e);
            }
        } else {
            logger.warn("Media file not found: {}", imagePath);
        }
    }

    /**
     * Loads a video file for playback using MediaView.
     *
     * @param videoFile the video file to play
     */
    private void loadVideo(File videoFile) {
        // Stop any previous video
        stopVideo();

        try {
            Media media = new Media(videoFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            // Hide image, show video
            imageView.setVisible(false);
            imageView.setImage(null);
            mediaView.setVisible(true);
            isVideoMode = true;

            // Fit video to overlay
            double overlayWidth = getWidth() - 160;
            double overlayHeight = getHeight() - 120;
            mediaView.setFitWidth(overlayWidth > 0 ? overlayWidth : 800);
            mediaView.setFitHeight(overlayHeight > 0 ? overlayHeight : 600);

            // Center the video manually since managed=false
            mediaPlayer.setOnReady(() -> {
                double containerWidth = displayLayer.getWidth();
                double containerHeight = displayLayer.getHeight();
                double videoWidth = mediaView.getFitWidth();
                double videoHeight = mediaView.getFitHeight();

                // Recalculate with actual media dimensions to maintain aspect ratio
                double mediaWidth = media.getWidth();
                double mediaHeight = media.getHeight();
                if (mediaWidth > 0 && mediaHeight > 0) {
                    double scaleW = (containerWidth - 160) / mediaWidth;
                    double scaleH = (containerHeight - 120) / mediaHeight;
                    double scale = Math.min(scaleW, scaleH);
                    scale = Math.min(scale, 1.0); // Don't upscale
                    videoWidth = mediaWidth * scale;
                    videoHeight = mediaHeight * scale;
                    mediaView.setFitWidth(videoWidth);
                    mediaView.setFitHeight(videoHeight);
                }

                mediaView.setLayoutX((containerWidth - videoWidth) / 2);
                mediaView.setLayoutY((containerHeight - videoHeight) / 2);
            });

            // Show play/pause button, hide zoom button
            playPauseButton.setVisible(true);
            zoom100Button.setVisible(false);
            zoom100Button.setDisable(true);

            // Update play/pause icon when status changes
            mediaPlayer.setOnPlaying(() -> updatePlayPauseIcon(true));
            mediaPlayer.setOnPaused(() -> updatePlayPauseIcon(false));
            mediaPlayer.setOnStopped(() -> updatePlayPauseIcon(false));
            mediaPlayer.setOnEndOfMedia(() -> {
                mediaPlayer.seek(javafx.util.Duration.ZERO);
                mediaPlayer.pause();
                updatePlayPauseIcon(false);
            });

            // Auto-play
            mediaPlayer.play();

            logger.info("Video loaded for playback: {}", videoFile.getName());

        } catch (Exception e) {
            logger.error("Failed to load video: {}", videoFile.getAbsolutePath(), e);
            // Fallback to showing error in image view
            imageView.setVisible(true);
            mediaView.setVisible(false);
            isVideoMode = false;
            playPauseButton.setVisible(false);
        }
    }

    /**
     * Stops and disposes the current video playback.
     */
    private void stopVideo() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        mediaView.setMediaPlayer(null);
        mediaView.setVisible(false);
        isVideoMode = false;
        playPauseButton.setVisible(false);
    }

    /**
     * Toggles video playback between play and pause.
     */
    private void toggleVideoPlayback() {
        if (mediaPlayer == null) return;

        MediaPlayer.Status status = mediaPlayer.getStatus();
        if (status == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }

    /**
     * Updates the play/pause button icon.
     *
     * @param isPlaying true if video is currently playing
     */
    private void updatePlayPauseIcon(boolean isPlaying) {
        javafx.application.Platform.runLater(() -> {
            if (playPauseButton.getGraphic() instanceof FontIcon icon) {
                icon.setIconLiteral(isPlaying ? "mdi2p-pause" : "mdi2p-play");
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
            }
        });
    }

    /**
     * Resets image zoom and pan to default state (fit to preview area).
     */
    private void resetImageTransform() {
        // Start with fit-to-preview-area zoom level
        currentZoom = baseScaleToFit;

        // Reset pan position - center the image
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);

        // Apply the initial scale and size
        applyZoomAndSize();
    }

    /**
     * Updates navigation button states based on current position.
     */
    private void updateNavigationState() {
        if (imagePaths == null || imagePaths.isEmpty()) {
            prevImageButton.setVisible(false);
            nextImageButton.setVisible(false);
            return;
        }

        prevImageButton.setVisible(currentImageIndex > 0);
        nextImageButton.setVisible(currentImageIndex < imagePaths.size() - 1);

        // Enable/disable buttons instead of hiding for single image
        prevImageButton.setDisable(currentImageIndex <= 0);
        nextImageButton.setDisable(currentImageIndex >= imagePaths.size() - 1);

        galleryInfo.setVisible(captions != null);
        if (captions != null) {
            counterLabel.setText((currentImageIndex + 1) + " / " + imagePaths.size());
            captionLabel.setText(captions.get(currentImageIndex));
        }
    }

    // Image info label removed - navigation is now via buttons only

    /**
     * Shows the overlay with the specified images.
     * 
     * @param imagePaths list of image file paths to display
     * @param startIndex index of the image to start with
     */
    public void showImages(List<String> imagePaths, int startIndex) {
        open(imagePaths, null, startIndex);
    }

    /**
     * Shows the overlay with the images of the images view, which it pages through only left and right, with a
     * counter and the line of each image.
     *
     * @param imagePaths the image file paths in the order of the grid
     * @param captions the line of each image: mood, frame and model
     * @param startIndex index of the image to start with
     */
    public void showGallery(List<String> imagePaths, List<String> captions, int startIndex) {
        open(imagePaths, captions, startIndex);
    }

    private void open(List<String> imagePaths, List<String> captions, int startIndex) {
        this.imagePaths = imagePaths;
        this.captions = captions;
        this.currentImageIndex = Math.max(0, Math.min(startIndex, imagePaths.size() - 1));

        if (!imagePaths.isEmpty()) {
            loadCurrentImage();
            setVisible(true);
            requestFocus(); // Enable keyboard shortcuts

            // Initially hide chat navigation buttons - will be updated by controller
            updateChatNavigationButtons(false, false);

            ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Image preview overlay opened with {} images, starting at index {}",
                        imagePaths.size(), currentImageIndex);
            }
        }
    }

    /**
     * Updates the visibility of chat navigation buttons.
     * Call this after showing images to indicate if there are previous/next chats
     * with images.
     * 
     * @param hasPrevious true if there's a previous chat entry with images
     * @param hasNext     true if there's a next chat entry with images
     */
    public void updateChatNavigationButtons(boolean hasPrevious, boolean hasNext) {
        prevChatButton.setVisible(hasPrevious);
        prevChatButton.setManaged(hasPrevious);
        nextChatButton.setVisible(hasNext);
        nextChatButton.setManaged(hasNext);
    }

    /**
     * Shows the overlay with a single image.
     * 
     * @param imagePath path to the image file
     */
    public void showImage(String imagePath) {
        showImages(List.of(imagePath), 0);
    }

    /**
     * Closes the overlay.
     */
    public void closeOverlay() {
        setVisible(false);

        // Stop video if playing
        if (isVideoMode) {
            stopVideo();
        }

        // Clear image to free memory
        imageView.setImage(null);
        imagePaths = null;
        captions = null;
        galleryInfo.setVisible(false);
        currentImageIndex = 0;

        // Notify callback
        if (onCloseRequested != null) {
            onCloseRequested.accept(null);
        }
    }

    /**
     * Sets the callback to be called when the overlay is closed.
     * 
     * @param callback the close callback
     */
    public void setOnCloseRequested(Consumer<Void> callback) {
        this.onCloseRequested = callback;
    }

    /**
     * Sets the callback to be called when previous chat navigation is requested.
     * 
     * @param callback the previous chat callback
     */
    public void setOnPrevChatRequested(Runnable callback) {
        this.onPrevChatRequested = callback;
    }

    /**
     * Sets the callback to be called when next chat navigation is requested.
     * 
     * @param callback the next chat callback
     */
    public void setOnNextChatRequested(Runnable callback) {
        this.onNextChatRequested = callback;
    }

    /**
     * Sets the callback to be called when "Edit This" is requested.
     * 
     * @param callback the edit this callback with image path parameter
     */
    public void setOnEditThisRequested(Consumer<String> callback) {
        this.onEditThisRequested = callback;
    }

    /**
     * Sets the callback used to forward status messages to the main window footer.
     *
     * @param callback the status message callback
     */
    public void setOnStatusMessage(Consumer<String> callback) {
        this.onStatusMessage = callback;
    }

    /**
     * Handles overlay resize to adjust image fitting.
     */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        // Refit image when overlay is resized
        if (isVisible() && imageView.getImage() != null) {
            fitImageToOverlay();
        }
    }

    /**
     * Fits image to overlay after resize by recalculating zoom limits.
     */
    private void fitImageToOverlay() {
        if (imageView.getImage() == null)
            return;

        // Recalculate zoom limits based on new overlay size
        calculateZoomLimits(imageView.getImage());

        // If current zoom is below new minimum, adjust to fit
        if (currentZoom < minZoomForCurrentImage) {
            currentZoom = minZoomForCurrentImage;
            applyZoomAndSize();
            constrainImagePosition();
        }

        // If we're at fit-to-preview zoom, update to new fit size
        if (Math.abs(currentZoom - baseScaleToFit) < 0.01) {
            currentZoom = baseScaleToFit;
            applyZoomAndSize();
            // Reset position for fit-to-preview
            imageView.setTranslateX(0);
            imageView.setTranslateY(0);
        }

        // Update zoom button state after window resize
        updateZoomButtonState();
    }

    /**
     * Forces all button icons to be white using direct programmatic approach.
     * This method is called after UI setup to override any theme styles.
     */
    private void forceWhiteIcons() {
        // Force close button icon to white
        if (closeButton.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) closeButton.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }

        // Force prev image button icon to white
        if (prevImageButton.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) prevImageButton.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }

        // Force next image button icon to white
        if (nextImageButton.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) nextImageButton.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }

        // Force prev chat button icon to white
        if (prevChatButton.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) prevChatButton.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }

        // Force next chat button icon to white
        if (nextChatButton.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) nextChatButton.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }

        // Force download button icon to white
        if (downloadButton.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) downloadButton.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }

        // Force edit this button icon to white
        if (editThisButton.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) editThisButton.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }

        // Force zoom100 button icon to white
        if (zoom100Button.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) zoom100Button.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }

        // Force play/pause button icon to white
        if (playPauseButton.getGraphic() instanceof FontIcon) {
            FontIcon icon = (FontIcon) playPauseButton.getGraphic();
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
            icon.setStyle("-fx-icon-color: white; -ikonli-icon-color: white;");
        }
    }

    /**
     * Called when the application language changes.
     * Updates all button tooltips with new translations.
     */
    @Override
    public void onLanguageChanged() {
        try {
            updateTooltips();
        } catch (Exception e) {
            logger.warn("Error during language change update for ImagePreviewOverlay", e);
        }
    }

    /**
     * Updates all button tooltips with current I18n translations.
     */
    private void updateTooltips() {
        if (closeButton != null) {
            closeButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.close")));
        }
        if (prevImageButton != null) {
            prevImageButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.prev.image")));
        }
        if (nextImageButton != null) {
            nextImageButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.next.image")));
        }
        if (prevChatButton != null) {
            prevChatButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.prev.chat")));
        }
        if (nextChatButton != null) {
            nextChatButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.next.chat")));
        }
        if (downloadButton != null) {
            downloadButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.download.image")));
        }
        if (editThisButton != null) {
            editThisButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.edit.image")));
        }

        // Zoom button tooltip is dynamic - update current state
        updateZoomButtonState();
    }
}