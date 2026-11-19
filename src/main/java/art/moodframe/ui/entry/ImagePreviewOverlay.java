package art.moodframe.ui.entry;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.ui.support.FxI18n;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
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
 * @updated 2.1.0 - Poster and the video player of the system for a video JavaFX cannot play
 * @updated 2.1.0 - What helps under Linux to play videos, with a command to copy
 * @updated 2.1.1 - Slowly turning mood frame around image and video instead of the drop shadow
 * @updated 2.1.1 - Nearly opaque dark stage in all themes, also in the light ones
 * @updated 2.1.1 - Image and video fit anew on every resize, panning ends at the frame, round surface of the buttons
 * @updated 2.1.1 - Exactly the size of a shrunk window, image, video and frame on device pixels
 * @updated 2.1.1 - Image and video fit into the safe area between the buttons, panning ends at its edge
 */
public class ImagePreviewOverlay extends StackPane implements I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ImagePreviewOverlay.class);

    // UI Components - Two completely independent layers
    private StackPane displayLayer; // Black background + image (can zoom/pan)
    private StackPane navigationLayer; // Transparent + fixed buttons
    private AnchorPane navigationAnchor; // Absolute positioning container for buttons
    private ImageView imageView;

    /*  The slowly turning frame in the colors of the logo around the shown image or video  */
    private PreviewFrame moodFrame;

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

    /*  The playback of a video, with its fallback where JavaFX cannot play it  */
    private final VideoPlayback video;

    /*  the image decoded in the background that is shown once it is done, null while none is loading  */
    private Image loadingImage;

    /*  whether full-size images are decoded in the background; the tests of the layout decode them at once  */
    private final boolean decodeInBackground;

    // State
    private List<String> imagePaths;
    private List<String> captions; // the line of each image in the gallery, null when opened from a chat
    private int currentImageIndex = 0;

    /*  the zoom of the image and its limits  */
    private final ZoomLevel zoom = new ZoomLevel();
    private double lastPanX = 0;
    private double lastPanY = 0;
    private boolean isDragging = false;

    // Callbacks
    private Consumer<Void> onCloseRequested;
    private Runnable onPrevChatRequested; // Navigate to previous chat entry with images
    private Runnable onNextChatRequested; // Navigate to next chat entry with images
    private Consumer<String> onEditThisRequested; // Create new prompt from current image path
    private Consumer<String> onDownloadRequested; // Download the current image path
    private Consumer<String> onStatusMessage; // Forward status messages to the main window footer

    /*  tells whether the frame turns (may be null: it turns)  */
    private final ApplicationConfig config;

    /**
     * Creates a new image preview overlay whose frame turns.
     */
    public ImagePreviewOverlay() {
        this(null);
    }

    /**
     * Creates a new image preview overlay.
     *
     * @param config tells whether the frame turns, which the user decides in the settings (may be null: it turns)
     */
    public ImagePreviewOverlay(ApplicationConfig config) {
        this(config, true);
    }

    /**
     * Creates a new image preview overlay, which decodes a full-size image in the background or at once.
     *
     * @param config             tells whether the frame turns (may be null: it turns)
     * @param decodeInBackground true to decode in the background, false to show an image at once (for tests that
     *                           measure it)
     */
    ImagePreviewOverlay(ApplicationConfig config, boolean decodeInBackground) {
        this.config = config;
        this.decodeInBackground = decodeInBackground;
        getStyleClass().add("image-preview-overlay");

        setVisible(false);

        // =================== LAYER 1: DISPLAY LAYER ===================
        // This layer contains the black background and the image (can zoom/pan)
        displayLayer = new StackPane();

        /*  A dark stage in all themes, as in photo programs: the app behind it shows only faintly  */
        displayLayer.setStyle("-fx-background-color: rgba(0, 0, 0, 0.92);");

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

        /*  Image and video get both fit sizes, in their ratio and rounded to device pixels; preserving the ratio
            once more would shift an edge off the pixels again  */
        imageView = new ImageView();
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setCache(true);

        // CRITICAL: Prevent image from affecting layout calculations - force contained
        // rendering
        imageView.setPickOnBounds(true);
        imageView.setManaged(false); // This prevents the image from affecting parent layout

        /*  Panning moves the image by whole device pixels only  */
        imageView.translateXProperty().addListener((observable, was, now) -> placeImage());
        imageView.translateYProperty().addListener((observable, was, now) -> placeImage());

        /*  The mood frame lies behind the image, its glow takes the place of the former drop shadow  */
        moodFrame = new PreviewFrame();
        displayLayer.getChildren().add(moodFrame);

        displayLayer.getChildren().add(imageView);
        StackPane.setAlignment(imageView, Pos.CENTER);

        // The view of a video (initially hidden)
        video = new VideoPlayback(this, displayLayer, videoHost());
        MediaView mediaView = video.mediaView();
        displayLayer.getChildren().add(mediaView);
        StackPane.setAlignment(mediaView, Pos.CENTER);

        /*  The frame follows whichever of image and video is shown, also while zooming, panning and resizing
            (change listeners, as an invalidation listener on bounds nobody reads fires only once)  */
        javafx.beans.value.ChangeListener<Object> reframe = (observable, was, now) -> reframe();
        for (javafx.scene.Node view : List.of(imageView, mediaView)) {
            view.boundsInParentProperty().addListener(reframe);
            view.visibleProperty().addListener(reframe);
        }
        imageView.imageProperty().addListener(reframe);
        mediaView.mediaPlayerProperty().addListener(reframe);

        /*  The colors turn only while the preview is shown, unless the user switched that off  */
        visibleProperty().addListener((observable, was, shown) -> moodFrame.setRunning(shown && isFrameAnimated()));

        // =================== LAYER 2: NAVIGATION LAYER ===================
        // This layer uses AnchorPane for absolute positioning to prevent layout
        // interference
        navigationAnchor = new AnchorPane();
        navigationAnchor.setStyle("-fx-background-color: transparent;");
        navigationAnchor.setPickOnBounds(false);
        navigationAnchor.setMouseTransparent(false);

        /*  Without a minimum, overlay and buttons take exactly the size of the window: the buttons centered via their
            layout position made the minimum half the former size, so a shrunk window cut off the buttons  */
        navigationAnchor.setMinSize(0.0, 0.0);

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
        FxI18n.addChangeListener(this);
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
        closeButton.getStyleClass().addAll("icon-button", "preview-button");
        closeButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.close")));

        // Previous image button (left side)
        prevImageButton = new Button();
        FontIcon prevImageIcon = new FontIcon("mdi2c-chevron-left");
        prevImageIcon.setIconSize(24);
        prevImageIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        prevImageButton.setGraphic(prevImageIcon);
        prevImageButton.getStyleClass().addAll("icon-button", "preview-button");
        prevImageButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.prev.image")));

        // Next image button (right side)
        nextImageButton = new Button();
        FontIcon nextImageIcon = new FontIcon("mdi2c-chevron-right");
        nextImageIcon.setIconSize(24);
        nextImageIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        nextImageButton.setGraphic(nextImageIcon);
        nextImageButton.getStyleClass().addAll("icon-button", "preview-button");
        nextImageButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.next.image")));

        // Previous chat entry button (top center)
        prevChatButton = new Button();
        FontIcon prevChatIcon = new FontIcon("mdi2c-chevron-up");
        prevChatIcon.setIconSize(24);
        prevChatIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        prevChatButton.setGraphic(prevChatIcon);
        prevChatButton.getStyleClass().addAll("icon-button", "preview-button");
        prevChatButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.prev.chat")));

        // Next chat entry button (bottom center)
        nextChatButton = new Button();
        FontIcon nextChatIcon = new FontIcon("mdi2c-chevron-down");
        nextChatIcon.setIconSize(24);
        nextChatIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        nextChatButton.setGraphic(nextChatIcon);
        nextChatButton.getStyleClass().addAll("icon-button", "preview-button");
        nextChatButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.nav.next.chat")));

        // Download button (bottom left)
        downloadButton = new Button();
        FontIcon downloadIcon = new FontIcon("mdi2d-download");
        downloadIcon.setIconSize(20);
        downloadIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        downloadButton.setGraphic(downloadIcon);
        downloadButton.getStyleClass().addAll("icon-button", "preview-button");
        downloadButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.download.image")));

        // Edit This button (bottom right)
        editThisButton = new Button();
        FontIcon editIcon = new FontIcon("mdi2p-pencil");
        editIcon.setIconSize(20);
        editIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        editThisButton.setGraphic(editIcon);
        editThisButton.getStyleClass().addAll("icon-button", "preview-button");
        editThisButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.edit.image")));

        // Toggle Zoom button (100% <-> Fit to screen) - top right, next to close button
        zoom100Button = new Button();
        FontIcon zoomToggleIcon = new FontIcon("mdi2m-magnify-plus"); // Start with magnify-plus icon (means: will go to
                                                                      // 100%)
        zoomToggleIcon.setIconSize(20);
        zoomToggleIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        zoom100Button.setGraphic(zoomToggleIcon);
        zoom100Button.getStyleClass().addAll("icon-button", "preview-button");
        // Ensure button can receive mouse events
        zoom100Button.setMouseTransparent(false);
        zoom100Button.setPickOnBounds(true);

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
                zoom100Button, video.playPauseButton(), galleryInfo, video.fallback());

        // Set absolute positions using AnchorPane constraints
        setAbsoluteButtonPositions();
    }

    /**
     * Sets absolute button positions using AnchorPane constraints to prevent layout
     * interference.
     */
    private void setAbsoluteButtonPositions() {
        // Close button - top right corner, above the next image button (absolute position)
        AnchorPane.setTopAnchor(closeButton, 20.0);
        AnchorPane.setRightAnchor(closeButton, 30.0);

        // Toggle Zoom button - top right, next to close button (absolute position)
        AnchorPane.setTopAnchor(zoom100Button, 20.0);
        AnchorPane.setRightAnchor(zoom100Button, 80.0); // 30 + 40 + 10 margin from close button

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

        // Action buttons - top left, above the previous image button (absolute position)
        AnchorPane.setTopAnchor(downloadButton, 20.0);
        AnchorPane.setLeftAnchor(downloadButton, 30.0);

        AnchorPane.setTopAnchor(editThisButton, 20.0);
        AnchorPane.setLeftAnchor(editThisButton, 80.0); // 30 + 40 + 10 margin from download button

        // Play/Pause button - bottom center (positioned via layout listener)
        Button playPauseButton = video.playPauseButton();
        AnchorPane.setBottomAnchor(playPauseButton, 80.0);

        AnchorPane.setLeftAnchor(galleryInfo, 0.0);
        AnchorPane.setRightAnchor(galleryInfo, 0.0);
        AnchorPane.setBottomAnchor(galleryInfo, 20.0);

        AnchorPane.setLeftAnchor(video.fallback(), 0.0);
        AnchorPane.setRightAnchor(video.fallback(), 0.0);
        AnchorPane.setBottomAnchor(video.fallback(), 80.0);

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
            if (!video.isActive() &&
                    zoom.zoom() <= zoom.min() + 0.001 &&
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
            case SPACE -> { if (video.isActive()) video.toggle(); } // Space for play/pause
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

        // Don't allow dragging if image is smaller than the safe area (fit-to-screen)
        if (imageView.getImage() != null) {
            double imageWidth = imageView.getFitWidth();
            double imageHeight = imageView.getFitHeight();
            Bounds area = safeArea();

            // If image fits completely in the safe area, don't allow panning
            if (imageWidth <= area.getWidth() && imageHeight <= area.getHeight()) {
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
            Bounds area = safeArea();

            // If image fits completely in the safe area, show default cursor
            if (imageWidth <= area.getWidth() && imageHeight <= area.getHeight()) {
                setCursor(javafx.scene.Cursor.DEFAULT);
            } else {
                // Image is larger than the safe area, show hand cursor to indicate panning is
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
        if (zoom.zoomIn()) {
            applyZoom();
            constrainImagePosition();
        }
    }

    /**
     * Zooms the image out.
     */
    private void zoomOut() {
        if (zoom.zoomOut()) {
            applyZoom();
            constrainImagePosition();
        }
    }

    /**
     * Applies the current zoom level to the image using actual pixel dimensions.
     */
    private void applyZoom() {
        applyZoomAndSize();

        /*  The fitted image lies centered  */
        if (zoom.zoom() <= zoom.fit() + 0.01) {
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
     * Zoom 1.0 means original pixel size (1:1 ratio), the fit zoom scaled to fit the preview area.
     */
    private void applyZoomAndSize() {
        if (imageView.getImage() == null)
            return;

        // Get original image dimensions
        double originalWidth = imageView.getImage().getWidth();
        double originalHeight = imageView.getImage().getHeight();

        /*  The display size at the zoom level, rounded to whole device pixels  */
        double displayWidth = displayLayer.snapPositionX(originalWidth * zoom.zoom());
        double displayHeight = displayLayer.snapPositionY(originalHeight * zoom.zoom());

        // Set the ImageView to display at exact pixel size
        imageView.setFitWidth(displayWidth);
        imageView.setFitHeight(displayHeight);

        // Reset scale transforms (we're using fitWidth/fitHeight for sizing)
        imageView.setScaleX(1.0);
        imageView.setScaleY(1.0);

        // Manually center the image since setManaged(false) disables automatic
        // centering
        placeImage();

    }

    /*  The image lies centered in the safe area, moved by the pan, with its edges on device pixels (also at a screen
        scale of 2), so the frame lies flush against it and no seam of the dark stage shines through the smoothed
        edges  */
    private void placeImage() {
        double containerWidth = displayLayer.getWidth();
        double containerHeight = displayLayer.getHeight();
        if (containerWidth <= 0 || containerHeight <= 0)
            return;
        Bounds area = safeArea();
        double x = area.getCenterX() - imageView.getFitWidth() / 2 + imageView.getTranslateX();
        double y = area.getCenterY() - imageView.getFitHeight() / 2 + imageView.getTranslateY();
        imageView.setLayoutX(displayLayer.snapPositionX(x) - imageView.getTranslateX());
        imageView.setLayoutY(displayLayer.snapPositionY(y) - imageView.getTranslateY());
    }

    /*  The frame surrounds the video once its size is known, else the image, and nothing while neither shows  */
    private void reframe() {
        MediaView mediaView = video.mediaView();
        MediaPlayer player = mediaView.getMediaPlayer();
        javafx.scene.Node shown = null;
        if (mediaView.isVisible() && player != null && player.getMedia().getWidth() > 0)
            shown = mediaView;
        else if (imageView.isVisible() && imageView.getImage() != null)
            shown = imageView;
        moodFrame.frame(shown == null ? null : shown.localToParent(shown.getLayoutBounds()));
    }

    /*  JavaFX 23 does not tell whether the system reduces motion, so the user decides in the settings  */
    private boolean isFrameAnimated() {
        return config == null || config.ui().isPreviewFrameAnimated();
    }

    /*  The safe area of image and video keeps the same gap to the buttons on all four sides, which frame and glow
        fill; a hidden button does not count, but the image arrows always do, so paging never shifts the image  */
    private Bounds safeArea() {
        double gap    = PreviewFrame.EXTENT;
        double left   = AnchorPane.getLeftAnchor(prevImageButton) + prevImageButton.prefWidth(-1) + gap;
        double right  = AnchorPane.getRightAnchor(nextImageButton) + nextImageButton.prefWidth(-1) + gap;
        double top    = gap;
        double bottom = gap;
        for (Region node : List.of(downloadButton, editThisButton, zoom100Button, closeButton, prevChatButton))
            if (node.isVisible())
                top = Math.max(top, AnchorPane.getTopAnchor(node) + node.prefHeight(-1) + gap);
        for (Region node : List.of(nextChatButton, video.playPauseButton(), galleryInfo))
            if (node.isVisible())
                bottom = Math.max(bottom, AnchorPane.getBottomAnchor(node) + node.prefHeight(-1) + gap);

        /*  At least a pixel, also before the first layout, and centered between the buttons in a tiny window  */
        double width  = Math.max(1.0, getWidth() - left - right);
        double height = Math.max(1.0, getHeight() - top - bottom);
        return new BoundingBox((left + getWidth() - right - width) / 2, (top + getHeight() - bottom - height) / 2,
                width, height);
    }

    /**
     * Constrains the image position: a larger image pans at most until its edge reaches the safe area.
     * For images smaller than the safe area, prevents any panning.
     */
    private void constrainImagePosition() {
        if (imageView.getImage() == null) {
            return;
        }

        /*  Per axis, a larger image pans until its edge lies on the edge of the safe area, where frame and glow fill
            the gap to the buttons; along an axis it fits, it stays centered  */
        Bounds area = safeArea();
        imageView.setTranslateX(ZoomLevel.clampPan(imageView.getTranslateX(), imageView.getFitWidth(),
                area.getWidth()));
        imageView.setTranslateY(ZoomLevel.clampPan(imageView.getTranslateY(), imageView.getFitHeight(),
                area.getHeight()));
    }

    /**
     * Calculates zoom limits based on actual pixel sizes.
     * Max zoom = 1:1 pixel ratio (original size)
     * Min zoom = fit to preview area (unless image is smaller)
     */
    private void calculateZoomLimits(Image image) {
        if (image == null) {
            zoom.clear();
            return;
        }

        /*  The available preview area is the safe area between the buttons  */
        Bounds area = safeArea();
        zoom.limit(image.getWidth(), image.getHeight(), area.getWidth(), area.getHeight());
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
     * Downloads the current image to the system Downloads folder, named by the main window after its chat.
     */
    private void downloadCurrentImage() {
        if (imagePaths == null || imagePaths.isEmpty() || currentImageIndex < 0
                || currentImageIndex >= imagePaths.size()) {
            return;
        }

        if (onDownloadRequested != null)
            onDownloadRequested.accept(imagePaths.get(currentImageIndex));
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

        boolean canZoomTo100 = zoom.canZoomToOriginal();
        boolean canZoomToFit = zoom.canZoomToFit();

        // Determine toggle action based on current zoom and available operations
        if (zoom.isOriginal()) {
            // We're at 100%, switch to fit-to-screen if possible
            if (canZoomToFit) {
                zoomToFit();
            }
        } else if (zoom.isFit()) {
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
        zoom.toOriginal();
        applyZoom();
        constrainImagePosition();
        updateZoomButtonState();
    }

    /**
     * Zooms image to fit the screen (same as minimum zoom).
     */
    private void zoomToFit() {
        zoom.toFit();
        applyZoom();
        // Reset position when fitting to screen
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        updateZoomButtonState();
    }

    /**
     * Shows the icon and the tooltip of the zoom button: the next action, or a plain magnifier when there is none.
     *
     * @param icon       the literal of the icon
     * @param tooltipKey the key of the tooltip
     */
    private void showZoomButton(String icon, String tooltipKey) {
        zoom100Button.setText("");
        FontIcon zoomIcon;
        if (zoom100Button.getGraphic() instanceof FontIcon shown)
            zoomIcon = shown;
        else {
            zoomIcon = new FontIcon();
            zoomIcon.setIconSize(20);
            zoom100Button.setGraphic(zoomIcon);
        }
        zoomIcon.setIconLiteral(icon);

        /*  A new icon code resets the color  */
        zoomIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        zoom100Button.setTooltip(new Tooltip(I18nManager.getText(tooltipKey)));
    }

    /**
     * Updates the zoom button state (enabled/disabled) and icon based on current
     * zoom possibilities.
     * This method determines if zoom operations are possible and updates the button
     * accordingly.
     */
    private void updateZoomButtonState() {
        if (imageView.getImage() == null || video.isActive()) {
            zoom100Button.setDisable(true);
            zoom100Button.setVisible(false);
            return;
        }

        zoom100Button.setVisible(true);

        /*  Whether the button can show the image in its own pixels or fit it  */
        boolean canZoom = zoom.canZoomToOriginal() || zoom.canZoomToFit();

        if (!canZoom) {
            zoom100Button.setDisable(true);
            zoom100Button.setOpacity(0.5);
            showZoomButton("mdi2m-magnify", "tooltip.preview.zoom.unavailable");
        } else {
            zoom100Button.setDisable(false);
            zoom100Button.setOpacity(1.0);

            /*  At 100 % the next action fits the image to the screen, at any other zoom it shows 100 %  */
            if (zoom.isOriginal())
                showZoomButton("mdi2m-magnify-minus", "tooltip.preview.zoom.fit.screen");
            else
                showZoomButton("mdi2m-magnify-plus", "tooltip.preview.zoom.to.100");
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

                    /*  an image still loading is not shown anymore  */
                    loadingImage = null;
                    video.load(imageFile);
                } else {
                    // Stop any playing video first
                    video.stop();

                    /*  The full-size image is decoded in the background; the previous image stays until it is
                        done, then zoom and pan start anew from its pixel size  */
                    Image image = new Image(imageFile.toURI().toString(), decodeInBackground);
                    loadingImage = image;
                    if (image.getProgress() >= 1.0 || image.isError())
                        showLoaded(image, imagePath);
                    else
                        image.progressProperty().addListener((obs, oldProgress, progress) -> {
                            if (progress.doubleValue() >= 1.0)
                                showLoaded(image, imagePath);
                        });
                    image.errorProperty().addListener((obs, wasError, error) -> {
                        if (error)
                            showLoaded(image, imagePath);
                    });
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
     * Shows an image once it is decoded, unless another image or a video was asked for meanwhile.
     *
     * @param image     the decoded image, or one that failed
     * @param imagePath its path, for the log
     */
    private void showLoaded(Image image, String imagePath) {
        if (loadingImage != image)
            return;
        loadingImage = null;
        if (image.isError()) {
            logger.error("Failed to load media: {}", imagePath, image.getException());
            return;
        }
        imageView.setImage(image);

        // Show image, hide video
        imageView.setVisible(true);
        video.hide();

        // Calculate zoom limits based on actual pixel sizes
        calculateZoomLimits(image);

        // Reset zoom and pan
        resetImageTransform();

        // Update zoom button state for new image
        updateZoomButtonState();
    }

    /** Whether an image is still decoded in the background, for the tests. */
    boolean isLoading() {
        return loadingImage != null;
    }

    /*  The overlay shows the image of a video and its poster for the playback  */
    private VideoPlayback.Host videoHost() {
        return new VideoPlayback.Host() {
            @Override
            public Bounds safeArea() {
                return ImagePreviewOverlay.this.safeArea();
            }

            @Override
            public void hideImage() {
                imageView.setVisible(false);
                imageView.setImage(null);
            }

            @Override
            public void hideZoom() {
                zoom100Button.setVisible(false);
                zoom100Button.setDisable(true);
            }

            @Override
            public void showPoster(Image poster) {
                if (poster == null) {
                    imageView.setImage(null);
                    imageView.setVisible(true);
                    updateZoomButtonState();
                    return;
                }
                imageView.setImage(poster);
                calculateZoomLimits(poster);
                resetImageTransform();
            }

            @Override
            public void focus() {
                requestFocus();
            }

            @Override
            public void status(String message) {
                if (onStatusMessage != null)
                    onStatusMessage.accept(message);
            }
        };
    }

    /**
     * Resets image zoom and pan to default state (fit to preview area).
     */
    private void resetImageTransform() {
        /*  Start fitted into the preview area  */
        zoom.toFit();

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

        /*  The box of the gallery borders the safe area, which the next layout fits the image into anew  */
        requestLayout();
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

            logger.debug("Image preview overlay opened with {} images, starting at index {}",
                    imagePaths.size(), currentImageIndex);
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

        /*  The arrows of the chat border the safe area, which the next layout fits image and video into anew  */
        requestLayout();
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
        if (video.isActive()) {
            video.stop();
        }

        // Clear image to free memory, also one still loading
        loadingImage = null;
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
     * Sets the callback to be called when the download of the current image is requested.
     *
     * @param callback the download callback with image path parameter
     */
    public void setOnDownloadRequested(Consumer<String> callback) {
        this.onDownloadRequested = callback;
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

        // Refit image or video when overlay is resized
        if (isVisible()) {
            fitImageToOverlay();
            video.fit();
        }
    }

    /**
     * Fits image to overlay after resize by recalculating zoom limits: a fitted image is fitted anew, a zoomed one
     * keeps its zoom within the new limits and its position within the safe area.
     */
    private void fitImageToOverlay() {
        if (imageView.getImage() == null)
            return;

        Bounds area = safeArea();
        zoom.refit(imageView.getImage().getWidth(), imageView.getImage().getHeight(), area.getWidth(),
                area.getHeight());
        applyZoom();
        constrainImagePosition();
    }

    /*  The icons of the buttons are white on the dark overlay, whatever the theme says  */
    private void forceWhiteIcons() {
        for (Button button : List.of(closeButton, prevImageButton, nextImageButton, prevChatButton, nextChatButton,
                downloadButton, editThisButton, zoom100Button, video.playPauseButton()))
            if (button.getGraphic() instanceof FontIcon icon) {
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
            video.relabel();
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