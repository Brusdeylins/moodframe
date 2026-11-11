package art.moodframe.ui.component;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.service.ThumbnailManager;
import art.moodframe.service.ImageDragExporter;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.util.I18nManager;
import art.moodframe.util.Subscription;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.io.File;

/**
 * Custom component for displaying a ChatEntry in the chat content area.
 *
 * <p>
 * This component shows the user prompt, status, timestamps, generation
 * parameters, and generated images in a card-like layout.
 * </p>
 *
 * <p>
 * <strong>Memory Management:</strong> This component now extends
 * ManagedComponent to properly clean up all listeners and prevent
 * memory leaks. It automatically handles I18n listener cleanup and
 * property binding disposal.
 * </p>
 *
 * <p>
 * <strong>Performance Optimizations (v1.3.5):</strong>
 * </p>
 * <ul>
 * <li>Reduced thumbnail thread pool from 4 to 2 threads to prevent UI saturation</li>
 * <li>Optimized Platform.runLater() usage - check if already on FX thread before scheduling</li>
 * <li>Removed placeholder creation on unload - saves ~640KB per image</li>
 * <li>Fast path image loading from LRU cache (no disk I/O)</li>
 * </ul>
 *
 * @since 1.0.0
 * @updated 1.2.0 - Converted to ManagedComponent for memory leak prevention
 * @updated 1.3.5 - Performance optimizations for large chats (200+ images)
 */
public class ChatEntryCard extends ManagedComponent {

    private static final Logger logger = LoggerFactory.getLogger(ChatEntryCard.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss");

    private final ChatEntry chatEntry;
    private final boolean isChatDeleted;
    private final Label promptLabel;
    private final Label statusLabel;
    private final Label errorDetailsLabel; // Multiline label for detailed error messages with solution hints
    private final Label timestampLabel;
    private final Label parametersLabel;
    private final HBox statusIcon;
    private final VBox imagesContainer;
    private final ProgressIndicator progressIndicator;

    // Image loading state
    private boolean imagesCurrentlyLoaded = false;

    // Static counter for total loaded images across all cards
    private static final java.util.concurrent.atomic.AtomicInteger totalLoadedImages = new java.util.concurrent.atomic.AtomicInteger(0);

    // Buttons for tooltip updates (stored for I18n language changes)
    private Button deleteEntryButton;
    private Button copyPromptButton;
    private final java.util.List<Button> imageButtons = new java.util.ArrayList<>(); // Dynamically created per image

    // Button references per image for state updates
    private final java.util.Map<String, ImageCardButtons> imageButtonsMap = new java.util.HashMap<>();

    /**
     * Helper class to store button references for a single image card.
     * Used to enable/disable buttons when image download status changes.
     */
    private static class ImageCardButtons {
        Button downloadButton;
        Button editButton;
        Button previewButton;
        Button copySeedButton;
    }

    // Callback functions
    private Consumer<Long> onSeedCopy;
    private Consumer<String> onPromptCopy;
    private java.util.function.BiConsumer<ChatEntry, Integer> onImageClick;
    private Consumer<String> onImageDownload; // Callback for downloading image by file path
    private Consumer<String> onImageEdit; // Callback for editing image by file path
    private Consumer<ChatEntry> onEntryDelete; // Callback for deleting entire entry
    private Consumer<GeneratedImage> onImageDelete; // Callback for deleting single image
    private java.util.function.BiConsumer<ChatEntry, Boolean> onMoodTakeOver;
    private Consumer<File> onMoodFromImage;
    private java.util.function.Supplier<Boolean> isFluxModelSelected; // Check if FLUX model is currently selected

    /**
     * Creates a new ChatEntryCard for the given ChatEntry.
     *
     * @param chatEntry the ChatEntry to display
     */
    public ChatEntryCard(ChatEntry chatEntry) {
        this(chatEntry, false, null);
    }

    /**
     * Creates a new ChatEntryCard for the given ChatEntry.
     *
     * @param chatEntry     the ChatEntry to display
     * @param isChatDeleted whether the parent chat is deleted (in trash)
     */
    public ChatEntryCard(ChatEntry chatEntry, boolean isChatDeleted) {
        this(chatEntry, isChatDeleted, null);
    }

    /**
     * Creates a new ChatEntryCard for the given ChatEntry.
     *
     * @param chatEntry            the ChatEntry to display
     * @param isChatDeleted        whether the parent chat is deleted (in trash)
     * @param isFluxModelSelected  supplier that returns true if FLUX model is currently selected
     */
    public ChatEntryCard(ChatEntry chatEntry, boolean isChatDeleted,
                         java.util.function.Supplier<Boolean> isFluxModelSelected) {
        super(); // Initialize ManagedComponent
        this.isFluxModelSelected = isFluxModelSelected;
        this.chatEntry = chatEntry;
        this.isChatDeleted = isChatDeleted;

        // Only log if logging is enabled in config
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("ChatEntryCard created - Entry: {}, isChatDeleted: {}",
                    chatEntry.getId(), isChatDeleted);
        }

        // Register for I18n change notifications (managed automatically)
        registerI18nListener(this);

        // Initialize UI components - must be done in constructor because fields are
        // final
        // Load custom CSS for icon color overrides
        try {
            getStylesheets().add(getClass().getResource("/css/image-card-overrides.css").toExternalForm());
        } catch (Exception e) {
            logger.warn("Could not load image card CSS overrides", e);
        }

        // Configure the main container with subtle border
        setSpacing(8.0);
        setPadding(new Insets(16.0, 20.0, 16.0, 20.0));
        getStyleClass().addAll("bg-default");
        setStyle("-fx-background-radius: 6px; " +
                "-fx-border-color: -color-border-default; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 6px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 2, 0, 0, 1);");

        // Enable GPU caching for better scrolling performance
        // This caches the entire ChatEntryCard as a GPU texture
        setCache(true);
        setCacheHint(javafx.scene.CacheHint.SPEED);

        // Create prompt container with HBox for proper text ellipsis and button
        // positioning
        HBox promptContainer = new HBox(3); // 3px spacing between elements
        promptContainer.setAlignment(Pos.CENTER_LEFT);

        this.promptLabel = new Label();
        promptLabel.getStyleClass().addAll("text-default");
        promptLabel.setWrapText(false); // Disable wrapping for ellipsis
        promptLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        promptLabel.setMaxWidth(Double.MAX_VALUE);
        promptLabel.setMinHeight(javafx.scene.control.Control.USE_PREF_SIZE); // Force single line
        promptLabel.setMaxHeight(javafx.scene.control.Control.USE_PREF_SIZE); // Force single line
        HBox.setHgrow(promptLabel, Priority.ALWAYS);

        // Create delete entry button (store reference for I18n updates)
        this.deleteEntryButton = new Button();
        this.deleteEntryButton.getStyleClass().addAll("button-icon", "flat");
        FontIcon deleteIcon = new FontIcon("mdi2t-trash-can-outline");
        deleteIcon.setIconSize(12);
        deleteIcon.getStyleClass().add("text-danger-hover");
        this.deleteEntryButton.setGraphic(deleteIcon);
        this.deleteEntryButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.delete.entry")));
        this.deleteEntryButton.setPadding(new Insets(0, 2, 0, 2));
        this.deleteEntryButton.setMaxHeight(18);
        this.deleteEntryButton.setMinWidth(20);

        this.deleteEntryButton.setOnAction(e -> {
            if (onEntryDelete != null) {
                onEntryDelete.accept(chatEntry);
            }
        });

        // Create copy prompt button (store reference for I18n updates)
        this.copyPromptButton = new Button();
        this.copyPromptButton.getStyleClass().addAll("button-icon", "flat");
        FontIcon copyPromptIcon = new FontIcon("mdi2c-content-copy");
        copyPromptIcon.setIconSize(12);
        copyPromptIcon.getStyleClass().add("text-muted");
        this.copyPromptButton.setGraphic(copyPromptIcon);
        this.copyPromptButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.copy.prompt")));
        this.copyPromptButton.setPadding(new Insets(0, 2, 0, 2));
        this.copyPromptButton.setMaxHeight(18); // Match text line height
        this.copyPromptButton.setMinWidth(20); // Ensure button stays visible

        this.copyPromptButton.setOnAction(e -> {
            if (onPromptCopy != null) {
                onPromptCopy.accept(chatEntry.getUserPrompt());
            }
        });

        promptContainer.getChildren().addAll(promptLabel, deleteEntryButton, copyPromptButton);

        // Compact status row with icon, status, timestamp and parameters all in one
        // line
        this.statusIcon = new HBox(8.0);
        statusIcon.setAlignment(Pos.CENTER_LEFT);

        this.statusLabel = new Label();
        statusLabel.getStyleClass().addAll("text-small", "text-bold");

        // Error details label for multiline error messages with solution hints
        this.errorDetailsLabel = new Label();
        errorDetailsLabel.getStyleClass().addAll("text-small", "text-danger");
        errorDetailsLabel.setWrapText(true);
        errorDetailsLabel.setVisible(false);
        errorDetailsLabel.managedProperty().bind(errorDetailsLabel.visibleProperty());
        errorDetailsLabel.setStyle("-fx-padding: 8 0 0 0;"); // Top padding to separate from status row

        this.progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(16.0, 16.0);
        progressIndicator.setVisible(false);

        this.timestampLabel = new Label();
        timestampLabel.getStyleClass().addAll("text-muted", "text-small");

        this.parametersLabel = new Label();
        parametersLabel.getStyleClass().addAll("text-muted", "text-small");

        // Combine status, timestamp, and parameters in one compact row (left-aligned)
        statusIcon.getChildren().addAll(progressIndicator, statusLabel,
                createVerticalSeparator(), timestampLabel,
                createVerticalSeparator(), parametersLabel);

        // Images container
        this.imagesContainer = new VBox(8.0);
        imagesContainer.setVisible(false);
        imagesContainer.managedProperty().bind(imagesContainer.visibleProperty());

        /*  The context menu takes over the mood of the entry; on an image it offers "Mood from image"  */
        promptContainer.setOnContextMenuRequested(this::showMoodMenu);
        statusIcon.setOnContextMenuRequested(this::showMoodMenu);

        // Assemble the layout (metadataRow is now integrated into statusIcon)
        // errorDetailsLabel is shown below statusIcon when there's a detailed error message
        getChildren().addAll(promptContainer, statusIcon, errorDetailsLabel, imagesContainer);

        // Bind to the ChatEntry data
        updateContent();

        // Listen for changes in the ChatEntry
        setupBindings();
    }

    /**
     * Gets the ChatEntry associated with this card.
     */
    public ChatEntry getChatEntry() {
        return chatEntry;
    }

    /**
     * Updates this card with a new ChatEntry (used for object pool reuse).
     * 
     * <p>
     * This method efficiently reconfigures the card for a different
     * ChatEntry without recreating the entire UI hierarchy. It's used
     * by the ChatEntryCardPool for performance optimization.
     * </p>
     * 
     * @param newChatEntry the new ChatEntry to display (must not be null)
     * @throws IllegalArgumentException if newChatEntry is null
     */
    public void updateChatEntry(ChatEntry newChatEntry) {
        if (newChatEntry == null) {
            throw new IllegalArgumentException("ChatEntry cannot be null");
        }

        checkNotDisposed();

        logger.trace("Updating ChatEntryCard from entry {} to entry {}",
                this.chatEntry != null ? this.chatEntry.getId() : "null",
                newChatEntry.getId());

        // Clear existing subscriptions to prevent leaks
        // Note: This will clear all managed subscriptions but won't dispose the
        // component
        getSubscriptionCount(); // Just to log current count

        // Note: chatEntry is final and cannot be updated.
        // The card will continue to reference the original entry.
        // This is acceptable as cards are typically recreated rather than reused.

        // Re-setup bindings for the new entry
        setupBindings();

        // Update all displays
        updateContent();
    }

    /**
     * Manually refreshes the UI from the model data.
     * Useful for triggering updates when the underlying data has changed.
     */
    public void refreshFromModel() {
        Platform.runLater(() -> {
            updateContent();
        });
    }

    /**
     * Sets up bindings to automatically update the UI when ChatEntry changes.
     * All listeners are managed automatically and will be cleaned up on disposal.
     */
    private void setupBindings() {
        // Update when status changes
        addSubscription(Subscription.of(
                chatEntry.statusProperty(),
                (obs, oldStatus, newStatus) -> Platform.runLater(this::updateStatusDisplay)));

        // Update when images are added/removed
        javafx.collections.ListChangeListener<GeneratedImage> imagesListener = change -> {
            javafx.application.Platform.runLater(() -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        // Add property listeners to newly added images
                        for (GeneratedImage addedImage : change.getAddedSubList()) {
                            setupImagePropertyListeners(addedImage);
                        }

                        // Skip rebuild if this is initial load (images going from 0 to N)
                        // Constructor already called updateContent() -> updateImagesDisplay()
                        if (change.getFrom() > 0 || imagesCurrentlyLoaded) {
                            // Real runtime change - rebuild display
                            updateImagesDisplay();
                            updateParametersDisplay(); // Update parameters to show style if first image was added
                        }
                    } else if (change.wasRemoved()) {
                        updateImagesDisplay();
                        updateParametersDisplay(); // Update parameters in case style was removed
                    }
                }
            });
        };
        chatEntry.getImages().addListener(imagesListener);

        // Add cleanup for images listener
        addSubscription(Subscription.of(
                () -> chatEntry.getImages().removeListener(imagesListener),
                "ChatEntry images collection listener"));

        // Setup property listeners for existing images
        for (GeneratedImage image : chatEntry.getImages()) {
            setupImagePropertyListeners(image);
        }

        // Update when prompt changes (for real-time editing)
        addSubscription(Subscription.of(
                chatEntry.userPromptProperty(),
                (obs, oldPrompt, newPrompt) -> Platform.runLater(this::updatePromptDisplay)));
    }


    /**
     * Sets up property listeners for a single GeneratedImage.
     * This ensures the UI updates when image properties change (e.g., downloadStatus).
     *
     * @param image the GeneratedImage to monitor
     */
    private void setupImagePropertyListeners(GeneratedImage image) {
        // Listen for downloadStatus changes to update the image card display
        addSubscription(Subscription.of(
                image.downloadStatusProperty(),
                (obs, oldStatus, newStatus) -> {
                    Platform.runLater(() -> {
                        // Rebuild the entire images display to reflect status change
                        // This ensures loading indicators are replaced with error/success states
                        updateImagesDisplay();
                    });
                }));
    }

    /**
     * Updates all content in the card.
     */
    private void updateContent() {
        updatePromptDisplay();
        updateStatusDisplay();
        updateTimestampDisplay();
        updateParametersDisplay();
        updateImagesDisplay();
    }

    /**
     * Updates the prompt display.
     */
    private void updatePromptDisplay() {
        String prompt = chatEntry.getUserPrompt();
        if (prompt != null && !prompt.isEmpty()) {
            // Replace all line breaks with spaces to ensure single-line display
            String singleLinePrompt = prompt.replaceAll("\\r?\\n", " ");
            promptLabel.setText("\"" + singleLinePrompt + "\"");
        } else {
            promptLabel.setText("[No prompt]");
            promptLabel.getStyleClass().add("text-muted");
        }
    }

    /**
     * Updates the status display with appropriate styling and icons.
     */
    private void updateStatusDisplay() {
        // Ensure this runs on the FX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateStatusDisplay);
            return;
        }

        ChatEntry.Status status = chatEntry.getStatus();

        // Clear existing style classes
        statusLabel.getStyleClass().removeAll("text-success", "text-danger", "text-warning", "text-muted");

        // Clear and rebuild the complete status row
        statusIcon.getChildren().clear();

        // Hide error details by default (will be shown for FAILED status with details)
        errorDetailsLabel.setVisible(false);

        switch (status) {
            case PENDING -> {
                FontIcon icon = new FontIcon("mdi2c-clock-outline");
                icon.setIconSize(16);
                icon.getStyleClass().add("text-muted");
                statusIcon.getChildren().add(icon);
                statusLabel.setText("Pending");
                statusLabel.getStyleClass().add("text-muted");
                progressIndicator.setVisible(false);
            }
            case PROCESSING -> {
                progressIndicator.setVisible(true);
                statusIcon.getChildren().add(progressIndicator);
                statusLabel.setText(I18nManager.getText("status.generating"));
                statusLabel.getStyleClass().add("text-warning");
            }
            case COMPLETED -> {
                FontIcon icon = new FontIcon("mdi2c-check-circle");
                icon.setIconSize(16);
                icon.getStyleClass().add("text-success");
                statusIcon.getChildren().add(icon);
                statusLabel.setText(I18nManager.getText("entry.status.completed"));
                statusLabel.getStyleClass().add("text-success");
                progressIndicator.setVisible(false);
            }
            case FAILED -> {
                FontIcon icon = new FontIcon("mdi2a-alert-circle");
                icon.setIconSize(16);
                icon.getStyleClass().add("text-danger");
                statusIcon.getChildren().add(icon);
                String errorMsg = chatEntry.getErrorMessage();

                // Show short status in the status row, detailed message in errorDetailsLabel
                statusLabel.setText(I18nManager.getText("entry.status.failed"));
                statusLabel.getStyleClass().add("text-danger");

                // Show full error message with solution hints in a separate multiline label
                if (errorMsg != null && !errorMsg.isEmpty()) {
                    errorDetailsLabel.setText(errorMsg);
                    errorDetailsLabel.setVisible(true);
                }

                progressIndicator.setVisible(false);
            }
            case CANCELLED -> {
                FontIcon icon = new FontIcon("mdi2c-cancel");
                icon.setIconSize(16);
                icon.getStyleClass().add("text-muted");
                statusIcon.getChildren().add(icon);
                statusLabel.setText(I18nManager.getText("entry.status.cancelled"));
                statusLabel.getStyleClass().add("text-muted");
                progressIndicator.setVisible(false);
            }
        }

        // Add all elements back to the status row: icon/progress, status, separator,
        // timestamp, parameters (left-aligned)
        statusIcon.getChildren().addAll(statusLabel, createVerticalSeparator(), timestampLabel,
                createVerticalSeparator(), parametersLabel);
    }

    /**
     * Updates the timestamp display.
     */
    private void updateTimestampDisplay() {
        if (chatEntry.getCreatedAt() != null) {
            String timestamp = chatEntry.getCreatedAt().format(TIME_FORMATTER);
            timestampLabel.setText(timestamp);
        } else {
            timestampLabel.setText(I18nManager.getText("chat.timestamp.none"));
        }
    }

    /**
     * Updates the parameters display.
     */
    private void updateParametersDisplay() {
        StringBuilder params = new StringBuilder();

        // Get model for display name and provider checks
        art.moodframe.model.GenerationModel model = null;
        if (chatEntry.getModel() != null) {
            // Convert stored model name to localized display name if possible
            model = art.moodframe.model.GenerationModel.fromDisplayName(chatEntry.getModel());
            String modelDisplayName = (model != null) ? model.getLocalizedDisplayName() : chatEntry.getModel();
            params.append(I18nManager.getText("params.model")).append(" ").append(modelDisplayName);
        }

        if (chatEntry.getSize() != null) {
            if (params.length() > 0)
                params.append(" • ");
            params.append(I18nManager.getText("params.aspect.ratio")).append(" ").append(chatEntry.getSize());
        }

        // Safety tolerance is only for BFL models, not Gemini
        if (chatEntry.getSafetyTolerance() >= 0 && model != null &&
            model.getProvider() == art.moodframe.model.ApiProvider.BFL) {
            if (params.length() > 0)
                params.append(" • ");
            params.append(I18nManager.getText("params.safety",
                    getSafetyToleranceDescription(chatEntry.getSafetyTolerance())));
        }

        // Display style preset - check ChatEntry first (for all providers), fallback to GeneratedImage (legacy BFL)
        String stylePreset = chatEntry.getStyle();
        if (stylePreset == null && !chatEntry.getImages().isEmpty()) {
            // Fallback: Check if any image has a style preset (legacy BFL entries)
            GeneratedImage firstImage = chatEntry.getImages().get(0);
            stylePreset = firstImage.getStylePreset();
        }

        /*  An entry generated with a mood shows the mood instead of the style  */
        String moodName = chatEntry.getMood() != null ? chatEntry.getMood().moodName() : null;
        if (moodName != null) {
            if (params.length() > 0)
                params.append(" • ");
            params.append(I18nManager.getText("params.mood")).append(" ").append(moodName);
        }
        else if (stylePreset != null && !stylePreset.trim().isEmpty() && !"None".equals(stylePreset)) {
            if (params.length() > 0)
                params.append(" • ");

            /*  A former look style (1.x) lives on as the standard mood of the same name  */
            params.append(I18nManager.getText(art.moodframe.service.MoodService.isLegacyLook(stylePreset)
                    ? "params.mood" : "params.style")).append(" ").append(stylePreset);
        }

        // Add cost to the parameters line
        double cost = chatEntry.getCost();
        if (cost > 0) {
            if (params.length() > 0)
                params.append(" • ");
            params.append(String.format("%s $%.2f", I18nManager.getText("params.cost"), cost));
        }

        // Note: Seed is not shown here as each image has its own unique seed
        // Seeds are displayed per image in the image metadata

        parametersLabel.setText(params.length() > 0 ? params.toString() : "No parameters");
    }

    /**
     * Gets a user-friendly description for safety tolerance values.
     * 
     * @param value the safety tolerance value (0-6)
     * @return descriptive text for the tolerance level
     */
    private String getSafetyToleranceDescription(int value) {
        return switch (value) {
            case 0, 1, 2 -> I18nManager.getText("params.safety.strict");
            case 3 -> I18nManager.getText("params.safety.moderate");
            case 4, 5, 6 -> I18nManager.getText("params.safety.relaxed");
            default -> "Unknown";
        };
    }

    /**
     * Updates the images display.
     */
    private void updateImagesDisplay() {
        // Clear image buttons list when rebuilding (for I18n tooltip updates)
        imageButtons.clear();

        imagesContainer.getChildren().clear();
        imagesCurrentlyLoaded = false;

        if (chatEntry.getImages().isEmpty()) {
            imagesContainer.setVisible(false);
            return;
        }

        imagesContainer.setVisible(true);

        // Add images title
        Label imagesTitle = new Label(
                String.format("%s (%d)", I18nManager.getText("entry.images.title"), chatEntry.getImages().size()));
        imagesTitle.getStyleClass().addAll("text-default", "text-bold");
        imagesContainer.getChildren().add(imagesTitle);

        // Create responsive image grid using FlowPane
        FlowPane imageGrid = new FlowPane(12.0, 12.0);
        imageGrid.setAlignment(Pos.CENTER_LEFT);
        imageGrid.setPrefWrapLength(850.0); // Wrap after ~850px (2 images at 400px + spacing)
        imageGrid.setMaxWidth(Double.MAX_VALUE);

        for (int i = 0; i < chatEntry.getImages().size(); i++) {
            GeneratedImage generatedImage = chatEntry.getImages().get(i);
            VBox imageCard = createImageCard(generatedImage, i);
            imageGrid.getChildren().add(imageCard);
        }

        imagesContainer.getChildren().add(imageGrid);

        // Mark as loaded
        imagesCurrentlyLoaded = true;
    }

    /**
     * Updates only the images title text for I18n changes.
     * Does NOT rebuild the entire images display to avoid listener leaks.
     */
    private void updateImagesTitleOnly() {
        if (imagesContainer == null || imagesContainer.getChildren().isEmpty()) {
            return;
        }

        // First child is the title label
        if (imagesContainer.getChildren().get(0) instanceof Label) {
            Label titleLabel = (Label) imagesContainer.getChildren().get(0);
            titleLabel.setText(String.format("%s (%d)",
                I18nManager.getText("entry.images.title"),
                chatEntry.getImages().size()));
        }
    }

    /**
     * Creates an image card for a GeneratedImage with loading animation support.
     *
     * @param generatedImage the generated image
     * @param imageIndex     the index of this image in the chat entry
     * @return the image card
     */
    private VBox createImageCard(GeneratedImage generatedImage, int imageIndex) {
        VBox imageCard = new VBox(4.0);
        imageCard.setAlignment(Pos.CENTER);
        imageCard.setPadding(new Insets(8.0));
        imageCard.setStyle("-fx-background-color: black; " +
                "-fx-background-radius: 4px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 3, 10);");
        // Add style class to override icon colors for black background
        imageCard.getStyleClass().add("black-image-card");

        // Create image container with loading support
        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(400, 400);
        imageContainer.setMaxSize(400, 400);
        imageContainer.setMinSize(400, 400);
        imageContainer.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 4, 0, 0, 2);");

        ImageView imageView = new ImageView();
        imageView.setFitWidth(400);
        imageView.setFitHeight(400);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        // Add background to ensure shadow is visible
        imageView.setStyle("-fx-background-color: -color-bg-default;");

        // Add click handler for image preview only if image is available
        // This will be set conditionally in updateImageDisplay

        // Create loading indicator for each image
        ProgressIndicator imageProgressIndicator = new ProgressIndicator();
        imageProgressIndicator.setMaxSize(40, 40);
        imageProgressIndicator.setVisible(false); // Initially hidden

        // Create loading overlay
        VBox loadingOverlay = new VBox(8.0);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setVisible(false); // Initially hidden
        loadingOverlay.getStyleClass().add("bg-default");
        loadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");

        Label loadingLabel = new Label(I18nManager.getText("status.generating"));
        loadingLabel.getStyleClass().addAll("text-default", "text-small");
        loadingLabel.setStyle("-fx-text-fill: white;");

        loadingOverlay.getChildren().addAll(imageProgressIndicator, loadingLabel);

        // Set up image based on download status
        updateImageDisplay(generatedImage, imageView, loadingOverlay, imageProgressIndicator);

        // Stack only the image and loading overlay (no buttons over the image)
        imageContainer.getChildren().addAll(imageView, loadingOverlay);

        // Check if image is available for actions
        boolean imageAvailable = generatedImage.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED &&
                generatedImage.getLocalFilePath() != null &&
                !generatedImage.getLocalFilePath().isEmpty();

        // Enable dragging the finished image out of the app (to Finder/Explorer or PowerPoint)
        if (imageAvailable) {
            imageView.setCursor(Cursor.OPEN_HAND);
            imageView.setOnDragDetected(event -> {
                String path = getActualImagePath(generatedImage, false); // full-resolution original, not thumbnail
                if (path != null) {
                    File imageFile = new File(path);
                    if (imageFile.exists())
                        ImageDragExporter.startDrag(imageView, imageFile);
                }
                event.consume();
            });
        }

        /*  The menu checks for the file itself, as the image may still be loading  */
        if (!generatedImage.isVideo())
            imageView.setOnContextMenuRequested((event) -> showImageMenu(generatedImage, event));

        // Create action buttons area (left side)
        HBox actionButtonsArea = new HBox(4);
        actionButtonsArea.setAlignment(Pos.CENTER_LEFT);
        actionButtonsArea.setStyle("-fx-padding: 4; -fx-background-color: rgba(0, 0, 0, 0.8); -fx-background-radius: 4;");

        // Create preview button area (right side)
        HBox previewButtonArea = new HBox();
        previewButtonArea.setAlignment(Pos.CENTER_RIGHT);
        previewButtonArea.setStyle("-fx-padding: 4; -fx-background-color: rgba(0, 0, 0, 0.8); -fx-background-radius: 4;");

        // Create image metadata container (label + copy seed button, centered as group)
        HBox imageMetadataContainer = new HBox(4);
        imageMetadataContainer.setAlignment(Pos.CENTER);

        Label imageInfo = new Label();
        imageInfo.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");

        imageMetadataContainer.getChildren().add(imageInfo);

        // Create buttons immediately (simpler, stable layout)
        // copySeedButton is created inside createImageButtons and added to imageMetadataContainer
        createImageButtons(actionButtonsArea, previewButtonArea, imageMetadataContainer, generatedImage, imageIndex, imageAvailable);

        // Update metadata display
        updateImageMetadata(generatedImage, imageInfo);

        // Get reference to copySeedButton for hover effect (it's the last child if present)
        Button copySeedBtn = null;
        if (imageMetadataContainer.getChildren().size() > 1 &&
            imageMetadataContainer.getChildren().get(1) instanceof Button) {
            copySeedBtn = (Button) imageMetadataContainer.getChildren().get(1);
            copySeedBtn.setOpacity(0); // Initially hidden
        }
        final Button copySeedBtnFinal = copySeedBtn;

        // Button row - always visible, opacity changes on hover
        HBox buttonRowContainer = new HBox();
        buttonRowContainer.setAlignment(Pos.CENTER);
        buttonRowContainer.setOpacity(0); // Hidden but takes space

        // Create spacer for layout
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        buttonRowContainer.getChildren().addAll(actionButtonsArea, spacer, previewButtonArea);

        // Show/hide buttons on hover (including seed copy button)
        imageCard.setOnMouseEntered(e -> {
            buttonRowContainer.setOpacity(1);
            if (copySeedBtnFinal != null && copySeedBtnFinal.isVisible()) {
                copySeedBtnFinal.setOpacity(1);
            }
        });
        imageCard.setOnMouseExited(e -> {
            buttonRowContainer.setOpacity(0);
            if (copySeedBtnFinal != null) {
                copySeedBtnFinal.setOpacity(0);
            }
        });

        imageCard.getChildren().addAll(buttonRowContainer, imageContainer, imageMetadataContainer);

        // Gemini Phase 2: Add cost breakdown if applicable
        VBox costBreakdownBox = createCostBreakdownBox(generatedImage);
        if (costBreakdownBox != null) {
            imageCard.getChildren().add(costBreakdownBox);
        }

        return imageCard;
    }

    /**
     * Creates a cost breakdown display box for Gemini Phase 2 advanced features.
     * Shows base cost, search cost, and reasoning cost if applicable.
     *
     * @param generatedImage the generated image with cost data
     * @return VBox with cost breakdown, or null if no additional costs
     */
    private VBox createCostBreakdownBox(GeneratedImage generatedImage) {
        // Only show cost breakdown if there are additional costs (search or reasoning)
        double searchCost = generatedImage.getSearchCost() != null ? generatedImage.getSearchCost() : 0.0;
        double reasoningCost = generatedImage.getReasoningCost() != null ? generatedImage.getReasoningCost() : 0.0;

        if (searchCost == 0.0 && reasoningCost == 0.0) {
            return null; // No additional costs to display
        }

        VBox costBox = new VBox(3);
        costBox.setAlignment(Pos.CENTER_LEFT);
        costBox.setPadding(new Insets(4, 8, 4, 8));
        costBox.setStyle("-fx-background-color: rgba(74, 144, 226, 0.15); " +
                        "-fx-background-radius: 4px; " +
                        "-fx-border-color: rgba(74, 144, 226, 0.3); " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px;");

        Label costTitle = new Label("💰 Cost Breakdown");
        costTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #4a90e2;");

        VBox costDetails = new VBox(2);

        // Get base generation cost from stored value
        // Note: We use the stored baseCost instead of looking up from GenerationModel
        // because some models (e.g., Gemini 3 Pro 2K/4K) share the same API ID
        // but have different costs depending on imageSize parameter
        double baseCost = generatedImage.getBaseCost() != null ? generatedImage.getBaseCost() : 0.0;

        // Show base generation cost
        if (baseCost > 0) {
            Label baseLabel = new Label(String.format("  Base Generation: $%.2f", baseCost));
            baseLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: white;");
            costDetails.getChildren().add(baseLabel);
        }

        // Show Google Search cost if applicable
        if (searchCost > 0) {
            Label searchLabel = new Label(String.format("  Google Search: $%.2f", searchCost));
            searchLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: white;");
            costDetails.getChildren().add(searchLabel);
        }

        // Show reasoning cost if applicable
        if (reasoningCost > 0) {
            Integer thinkingTokens = generatedImage.getThinkingTokens();
            String tokenInfo = thinkingTokens != null && thinkingTokens > 0
                ? String.format(" (%,d tokens)", thinkingTokens)
                : "";
            Label reasoningLabel = new Label(String.format("  Reasoning: $%.2f%s", reasoningCost, tokenInfo));
            reasoningLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: white;");
            costDetails.getChildren().add(reasoningLabel);
        }

        // Show grand total
        double grandTotal = baseCost + searchCost + reasoningCost;
        Label totalLabel = new Label(String.format("  Total: $%.2f", grandTotal));
        totalLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #4a90e2;");
        costDetails.getChildren().add(totalLabel);

        costBox.getChildren().addAll(costTitle, costDetails);
        return costBox;
    }

    /**
     * Gets the actual file path for an image, considering if it's in trash.
     *
     * @param generatedImage the generated image
     * @param isThumbnail    whether to get thumbnail path instead of image path
     * @return the actual path (either active or trash path)
     */
    private String getActualImagePath(GeneratedImage generatedImage, boolean isThumbnail) {
        String originalPath = generatedImage.getLocalFilePath();
        if (originalPath == null)
            return null;

        // Check if the chat or image is deleted and should use trash path
        boolean useTrashPath = isChatDeleted;

        // If not already marked as deleted, check if file exists in original location
        if (!useTrashPath) {
            java.io.File originalFile = new java.io.File(originalPath);
            if (!originalFile.exists()) {
                // File might be in trash, check there
                useTrashPath = true;
            }
        }

        if (useTrashPath) {
            // Get TrashManager to compute trash path
            art.moodframe.service.TrashManager trashManager = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.TrashManager.class);

            if (trashManager != null) {
                String pathToCheck = originalPath;

                // If we need thumbnail path, convert to thumbnail first
                if (isThumbnail) {
                    art.moodframe.service.ThumbnailManager thumbnailManager = art.moodframe.config.ServiceFactory
                            .getInstance()
                            .getService(art.moodframe.service.ThumbnailManager.class);
                    if (thumbnailManager != null) {
                        pathToCheck = thumbnailManager.getThumbnailPath(originalPath);
                    }
                }

                String trashPath = trashManager.getTrashPath(pathToCheck, isThumbnail);
                if (new java.io.File(trashPath).exists()) {
                    return trashPath;
                }
            }
        }

        // For active images, return thumbnail path if requested
        if (isThumbnail && !useTrashPath) {
            art.moodframe.service.ThumbnailManager thumbnailManager = art.moodframe.config.ServiceFactory.getInstance()
                    .getService(art.moodframe.service.ThumbnailManager.class);
            if (thumbnailManager != null) {
                return thumbnailManager.getThumbnailPath(originalPath);
            }
        }

        return originalPath;
    }

    /**
     * Loads thumbnail image asynchronously for optimal performance.
     * 
     * @param generatedImage    the generated image
     * @param imageView         the image view component
     * @param loadingOverlay    the loading overlay
     * @param progressIndicator the progress indicator
     * @param imageIndex        the image index for click handling
     */
    private void loadThumbnailImage(GeneratedImage generatedImage, ImageView imageView,
            VBox loadingOverlay, ProgressIndicator progressIndicator, int imageIndex) {

        String localPath = getActualImagePath(generatedImage, false);
        ThumbnailManager thumbnailManager = getThumbnailManager();

        if (thumbnailManager == null) {
            // Fallback to original image loading if ThumbnailManager not available
            loadOriginalImage(generatedImage, imageView, imageIndex);
            return;
        }

        // Get the actual thumbnail path (considering trash)
        String thumbnailPath = getActualImagePath(generatedImage, true);

        // FAST PATH: Check if thumbnail is already in memory cache (no disk I/O!)
        if (thumbnailPath != null) {
            javafx.scene.image.Image cachedImage = thumbnailManager.getFromCacheOnly(thumbnailPath);
            if (cachedImage != null) {
                // Image is in cache - set immediately, no disk access needed
                setImageWithHandlers(imageView, cachedImage, imageIndex);
                return;
            }
        }

        // SLOW PATH: Not in cache, need to load from disk (async)
        if (isChatDeleted) {
            // For deleted chats, load original image from trash (async)
            loadOriginalImage(generatedImage, imageView, imageIndex);
            return;
        }

        // Load thumbnail async - the cache will handle whether file exists
        CompletableFuture.runAsync(() -> {
            try {
                // First try loading thumbnail directly via cache (handles file.exists internally)
                if (thumbnailPath != null) {
                    javafx.scene.image.Image image = thumbnailManager.getCachedImage(thumbnailPath);
                    if (image != null) {
                        Platform.runLater(() -> setImageWithHandlers(imageView, image, imageIndex));
                        return;
                    }
                }

                // Thumbnail doesn't exist - generate it
                CompletableFuture<File> thumbnailFuture = thumbnailManager.getThumbnail(localPath);
                File thumbnailFile = thumbnailFuture.join(); // Wait for generation

                if (thumbnailFile != null && thumbnailFile.exists()) {
                    javafx.scene.image.Image image = thumbnailManager.getCachedImage(thumbnailFile.getAbsolutePath());
                    Platform.runLater(() -> {
                        loadingOverlay.setVisible(false);
                        progressIndicator.setVisible(false);
                        if (image != null) {
                            setImageWithHandlers(imageView, image, imageIndex);
                        } else {
                            loadOriginalImage(generatedImage, imageView, imageIndex);
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        loadingOverlay.setVisible(false);
                        progressIndicator.setVisible(false);
                        loadOriginalImage(generatedImage, imageView, imageIndex);
                    });
                }
            } catch (Exception e) {
                logger.warn("Failed to load thumbnail for: {}", localPath, e);
                Platform.runLater(() -> {
                    loadingOverlay.setVisible(false);
                    progressIndicator.setVisible(false);
                    loadOriginalImage(generatedImage, imageView, imageIndex);
                });
            }
        });
    }

    /**
     * Loads an image from the specified file path.
     * 
     * @param imagePath  the path to the image file
     * @param imageView  the image view component
     * @param imageIndex the image index for click handling
     */
    // Cached reference to ThumbnailManager - avoid ServiceFactory lookup on every call
    private static ThumbnailManager cachedThumbnailManager;

    private static ThumbnailManager getThumbnailManager() {
        if (cachedThumbnailManager == null) {
            cachedThumbnailManager = ServiceFactory.getInstance().getService(ThumbnailManager.class);
        }
        return cachedThumbnailManager;
    }

    private void loadImageFromPath(String imagePath, ImageView imageView, int imageIndex) {
        try {
            ThumbnailManager thumbnailManager = getThumbnailManager();

            // FAST PATH: Check cache first - no disk I/O, no thread switching
            javafx.scene.image.Image cachedImage = thumbnailManager != null
                    ? thumbnailManager.getFromCacheOnly(imagePath) : null;

            if (cachedImage != null) {
                // Image is in cache - set immediately (no disk I/O, no blocking)
                setImageWithHandlers(imageView, cachedImage, imageIndex);
                return;
            }

            // SLOW PATH: Not in cache - check file exists and load async
            // This only happens on first load, not during scrolling
            CompletableFuture.runAsync(() -> {
                File imageFile = new File(imagePath);
                if (!imageFile.exists()) {
                    Platform.runLater(() -> {
                        createPlaceholderImage(null, imageView, "File Not Found", 0.3);
                        imageView.setOnMouseClicked(null);
                        imageView.setCursor(javafx.scene.Cursor.DEFAULT);
                    });
                    return;
                }

                javafx.scene.image.Image image = thumbnailManager != null
                        ? thumbnailManager.getCachedImage(imagePath)
                        : new javafx.scene.image.Image(imageFile.toURI().toString(), 400, 400, true, true, false);

                Platform.runLater(() -> {
                    if (image != null) {
                        setImageWithHandlers(imageView, image, imageIndex);
                    } else {
                        createPlaceholderImage(null, imageView, "Load Error", 0.3);
                        imageView.setOnMouseClicked(null);
                        imageView.setCursor(javafx.scene.Cursor.DEFAULT);
                    }
                });
            });
        } catch (Exception e) {
            logger.error("Failed to load image from: {}", imagePath, e);
            createPlaceholderImage(null, imageView, "Load Error", 0.3);
            imageView.setOnMouseClicked(null);
            imageView.setCursor(javafx.scene.Cursor.DEFAULT);
        }
    }

    /**
     * Sets an image on the ImageView and configures click handlers.
     */
    private void setImageWithHandlers(ImageView imageView, javafx.scene.image.Image image, int imageIndex) {
        // CRITICAL: Dispose old image before loading new one
        javafx.scene.image.Image oldImage = imageView.getImage();
        boolean hadImage = oldImage != null;
        if (hadImage) {
            imageView.setImage(null); // Clear reference to allow GC
        }

        imageView.setImage(image);

        // Track loaded image - only increment if we didn't already have an image
        if (!hadImage) {
            totalLoadedImages.incrementAndGet();
        }

        // Enable click handler and cursor for successfully loaded images; a right click opens the context menu
        imageView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && onImageClick != null && imageIndex >= 0) {
                onImageClick.accept(chatEntry, imageIndex);
            }
        });
        imageView.setCursor(javafx.scene.Cursor.HAND);
    }

    /**
     * Fallback method to load original image (used when thumbnail system
     * unavailable).
     * 
     * @param generatedImage the generated image
     * @param imageView      the image view component
     * @param imageIndex     the image index for click handling
     */
    private void loadOriginalImage(GeneratedImage generatedImage, ImageView imageView, int imageIndex) {
        // Use getActualImagePath to handle trash directory properly
        String localPath = getActualImagePath(generatedImage, false);
        if (localPath == null) {
            createPlaceholderImage(generatedImage, imageView, "Image not found", 0.3);
            return;
        }

        // Delegate to loadImageFromPath which uses the LRU cache
        // This avoids loading full-size originals (which was causing performance issues)
        loadImageFromPath(localPath, imageView, imageIndex);
    }

    /**
     * Updates the image display based on download status.
     * 
     * @param generatedImage    the generated image
     * @param imageView         the image view component
     * @param loadingOverlay    the loading overlay
     * @param progressIndicator the progress indicator
     */
    private void updateImageDisplay(GeneratedImage generatedImage, ImageView imageView,
            VBox loadingOverlay, ProgressIndicator progressIndicator) {
        GeneratedImage.DownloadStatus status = generatedImage.getDownloadStatus();

        // Get the image index for click handler
        int imageIndex = chatEntry.getImages().indexOf(generatedImage);

        switch (status) {
            case PENDING -> {
                // Show placeholder without text (status shown under animation)
                createPlaceholderImage(generatedImage, imageView, "", 0.4);
                loadingOverlay.setVisible(true);
                progressIndicator.setVisible(true);
                progressIndicator.setProgress(-1.0); // Indeterminate progress for generation
                // Update the loading label text
                VBox overlayVBox = (VBox) loadingOverlay;
                if (overlayVBox.getChildren().size() > 1 && overlayVBox.getChildren().get(1) instanceof Label) {
                    Label loadingLabel = (Label) overlayVBox.getChildren().get(1);
                    loadingLabel.setText(I18nManager.getText("status.generating"));
                }
                // Remove click handler and cursor for pending images
                imageView.setOnMouseClicked(null);
                imageView.setCursor(javafx.scene.Cursor.DEFAULT);
            }

            case DOWNLOADING -> {
                // Show placeholder without text (status shown under animation)
                createPlaceholderImage(generatedImage, imageView, "", 0.3);
                loadingOverlay.setVisible(true);
                progressIndicator.setVisible(true);
                progressIndicator.setProgress(-1.0); // Indeterminate progress
                // Update the loading label text
                VBox overlayVBox = (VBox) loadingOverlay;
                if (overlayVBox.getChildren().size() > 1 && overlayVBox.getChildren().get(1) instanceof Label) {
                    Label loadingLabel = (Label) overlayVBox.getChildren().get(1);
                    loadingLabel.setText("Downloading...");
                }
                // Remove click handler and cursor for downloading images
                imageView.setOnMouseClicked(null);
                imageView.setCursor(javafx.scene.Cursor.DEFAULT);
            }

            case CACHED -> {
                // Load actual image from local file path
                loadingOverlay.setVisible(false);
                progressIndicator.setVisible(false);

                // Update button states when image becomes available
                updateImageButtonStates(generatedImage);

                String localPath = generatedImage.getLocalFilePath();
                if (localPath != null && !localPath.isEmpty()) {
                    if (generatedImage.isVideo()) {
                        // Video: Show video placeholder with play icon
                        createVideoPlaceholder(generatedImage, imageView, imageIndex);
                    } else {
                        // Image: Load thumbnail for better performance
                        loadThumbnailImage(generatedImage, imageView, loadingOverlay, progressIndicator, imageIndex);
                    }
                } else {
                    // No local path available yet
                    createPlaceholderImage(generatedImage, imageView, "No File Path", 0.5);
                    // Remove click handler and cursor for missing path
                    imageView.setOnMouseClicked(null);
                    imageView.setCursor(javafx.scene.Cursor.DEFAULT);
                }
            }

            case FAILED -> {
                // Show error state with specific error message if available
                String errorMsg = "Load Failed";
                if (generatedImage.getErrorMessage() != null && !generatedImage.getErrorMessage().trim().isEmpty()) {
                    errorMsg = generatedImage.getErrorMessage();
                } else if (generatedImage.getFailureReason() != null
                        && !generatedImage.getFailureReason().trim().isEmpty()) {
                    errorMsg = generatedImage.getFailureReason();
                }
                createPlaceholderImage(generatedImage, imageView, errorMsg, 0.2);
                loadingOverlay.setVisible(false);
                progressIndicator.setVisible(false);
                // Remove click handler and cursor for failed images
                imageView.setOnMouseClicked(null);
                imageView.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        }
    }

    /**
     * Updates button enabled/disabled states based on image availability.
     * Called when image status changes to CACHED or when buttons need refresh.
     *
     * @param generatedImage the generated image to check availability for
     */
    private void updateImageButtonStates(GeneratedImage generatedImage) {
        ImageCardButtons buttons = imageButtonsMap.get(generatedImage.getId());
        if (buttons == null) {
            return; // No buttons stored yet
        }

        boolean imageAvailable = generatedImage.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED
                && generatedImage.getLocalFilePath() != null
                && !generatedImage.getLocalFilePath().isEmpty();

        Platform.runLater(() -> {
            buttons.downloadButton.setDisable(!imageAvailable);
            buttons.editButton.setDisable(!imageAvailable);
            buttons.previewButton.setDisable(!imageAvailable);

            // Update seed copy button visibility - seed may have arrived from API response
            // Check if this image was generated with a FLUX model (not Gemini)
            // Note: For Gemini images, the button is not added to the container at all
            if (buttons.copySeedButton != null) {
                boolean hasSeed = generatedImage.getSeed() != 0;
                String imageModel = generatedImage.getModel();
                boolean isFluxGeneratedImage = imageModel != null &&
                    !imageModel.toLowerCase().contains("gemini");
                // Check if a FLUX model is currently selected
                boolean fluxModelSelected = isFluxModelSelected != null && isFluxModelSelected.get();
                // Button visible only when: FLUX image + has seed + FLUX model selected
                boolean showSeedButton = hasSeed && isFluxGeneratedImage && fluxModelSelected;
                buttons.copySeedButton.setVisible(showSeedButton);
                buttons.copySeedButton.setManaged(showSeedButton);
                buttons.copySeedButton.setOpacity(0); // Hidden until hover
                buttons.copySeedButton.setDisable(!imageAvailable || !hasSeed);
            }
        });
    }

    /**
     * Creates a placeholder image with status text.
     *
     * @param generatedImage the generated image
     * @param imageView      the image view to update
     * @param statusText     the status text to display
     * @param opacity        the opacity level (0.0 to 1.0)
     */
    private void createPlaceholderImage(GeneratedImage generatedImage, ImageView imageView,
            String statusText, double opacity) {
        // Ensure this runs on FX Application Thread
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform
                    .runLater(() -> createPlaceholderImage(generatedImage, imageView, statusText, opacity));
            return;
        }

        try {
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(400, 400);
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

            // Set background color based on image ID hash for variety
            int hash = generatedImage.getId().hashCode();
            double hue = ((hash % 360) + 360) % 360; // Ensure positive
            javafx.scene.paint.Color baseColor = javafx.scene.paint.Color.hsb(hue, 0.3, 0.8);
            javafx.scene.paint.Color color = baseColor.deriveColor(0, 1.0, 1.0, opacity);
            gc.setFill(color);
            gc.fillRect(0, 0, 400, 400);

            // Add status text only if not empty
            if (statusText != null && !statusText.trim().isEmpty()) {
                // Use darker color for error messages for better readability
                boolean isError = statusText.toLowerCase().contains("error") ||
                        statusText.toLowerCase().contains("failed") ||
                        statusText.toLowerCase().contains("blocked");
                gc.setFill(isError ? javafx.scene.paint.Color.DARKRED : javafx.scene.paint.Color.DARKGRAY);
                gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
                gc.setFont(javafx.scene.text.Font.font("System", 14));

                // Split text at periods for multi-line display
                String[] lines = statusText.split("\\. ");
                double lineHeight = 20;
                double totalHeight = lines.length * lineHeight;
                double startY = 200 - (totalHeight / 2) + (lineHeight / 2);

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    // Add period back if it was removed by split (except for last line if it didn't
                    // have one)
                    if (!line.endsWith(".") && (i < lines.length - 1 || statusText.endsWith("."))) {
                        line += ".";
                    }
                    gc.fillText(line, 200, startY + (i * lineHeight));
                }
            }

            javafx.scene.image.WritableImage placeholder = new javafx.scene.image.WritableImage(400, 400);
            canvas.snapshot(null, placeholder);
            imageView.setImage(placeholder);

        } catch (Exception e) {
            logger.warn("Failed to create image placeholder for status: {}", statusText, e);
        }
    }

    /**
     * Creates a video placeholder with a play icon for video entries.
     * Shows a dark background with a centered play triangle and video metadata.
     *
     * @param generatedImage the video GeneratedImage
     * @param imageView      the image view to set the placeholder on
     * @param imageIndex     the image index for click handling
     */
    private void createVideoPlaceholder(GeneratedImage generatedImage, ImageView imageView, int imageIndex) {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(() -> createVideoPlaceholder(generatedImage, imageView, imageIndex));
            return;
        }

        try {
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(400, 400);
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

            // Dark gradient background
            gc.setFill(javafx.scene.paint.Color.rgb(20, 20, 30));
            gc.fillRect(0, 0, 400, 400);

            // Subtle gradient overlay
            javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                    0, 0, 0, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.rgb(40, 40, 60, 0.5)),
                    new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.rgb(10, 10, 20, 0.8)));
            gc.setFill(gradient);
            gc.fillRect(0, 0, 400, 400);

            // Play button circle
            gc.setFill(javafx.scene.paint.Color.rgb(255, 255, 255, 0.15));
            gc.fillOval(150, 150, 100, 100);
            gc.setStroke(javafx.scene.paint.Color.rgb(255, 255, 255, 0.6));
            gc.setLineWidth(2);
            gc.strokeOval(150, 150, 100, 100);

            // Play triangle
            gc.setFill(javafx.scene.paint.Color.rgb(255, 255, 255, 0.9));
            double[] xPoints = {185, 185, 225};
            double[] yPoints = {175, 225, 200};
            gc.fillPolygon(xPoints, yPoints, 3);

            // Video info text
            gc.setFill(javafx.scene.paint.Color.rgb(200, 200, 200));
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            gc.setFont(javafx.scene.text.Font.font("System", 13));

            String resolution = generatedImage.getVideoResolution() != null ? generatedImage.getVideoResolution() : "";
            int duration = generatedImage.getVideoDurationSeconds();
            boolean audio = generatedImage.hasAudio();

            String info = String.format("MP4 • %s • %ds%s", resolution, duration, audio ? " • Audio" : "");
            gc.fillText(info, 200, 280);

            javafx.scene.image.WritableImage placeholder = new javafx.scene.image.WritableImage(400, 400);
            canvas.snapshot(null, placeholder);
            setImageWithHandlers(imageView, placeholder, imageIndex);

        } catch (Exception e) {
            logger.warn("Failed to create video placeholder", e);
            createPlaceholderImage(generatedImage, imageView, "Video", 0.3);
        }

        // The poster (a frame of the video) replaces the drawn placeholder as soon as it exists
        String videoPath = getActualImagePath(generatedImage, false);
        if (videoPath != null) {
            art.moodframe.util.VideoPoster.load(java.nio.file.Path.of(videoPath))
                    .thenAccept((javafx.scene.image.Image poster) -> Platform.runLater(() ->
                            setImageWithHandlers(imageView, withPlayOverlay(poster, generatedImage), imageIndex)))
                    .exceptionally(error -> {
                        logger.debug("No poster for video {}: {}", videoPath, error.getMessage());
                        return null;
                    });
        }
    }

    /** Draws the play button and the video info over a poster frame. */
    private javafx.scene.image.Image withPlayOverlay(javafx.scene.image.Image poster, GeneratedImage video) {
        double width = poster.getWidth();
        double height = poster.getHeight();
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(width, height);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.drawImage(poster, 0, 0);

        gc.setFill(new javafx.scene.paint.LinearGradient(0, 0, 0, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0.6, javafx.scene.paint.Color.rgb(0, 0, 0, 0)),
                new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.rgb(0, 0, 0, 0.7))));
        gc.fillRect(0, 0, width, height);

        double cx = width / 2;
        double cy = height / 2;
        gc.setFill(javafx.scene.paint.Color.rgb(0, 0, 0, 0.45));
        gc.fillOval(cx - 40, cy - 40, 80, 80);
        gc.setStroke(javafx.scene.paint.Color.rgb(255, 255, 255, 0.8));
        gc.setLineWidth(2);
        gc.strokeOval(cx - 40, cy - 40, 80, 80);
        gc.setFill(javafx.scene.paint.Color.rgb(255, 255, 255, 0.95));
        gc.fillPolygon(new double[] {cx - 12, cx - 12, cx + 20}, new double[] {cy - 20, cy + 20, cy}, 3);

        gc.setFill(javafx.scene.paint.Color.rgb(230, 230, 230));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setFont(javafx.scene.text.Font.font("System", 13));
        String resolution = video.getVideoResolution() != null ? video.getVideoResolution() : "";
        gc.fillText(String.format("MP4 • %s • %ds%s", resolution, video.getVideoDurationSeconds(),
                video.hasAudio() ? " • Audio" : ""), cx, height - 12);

        javafx.scene.image.WritableImage result = new javafx.scene.image.WritableImage((int) width, (int) height);
        javafx.scene.SnapshotParameters parameters = new javafx.scene.SnapshotParameters();
        parameters.setFill(javafx.scene.paint.Color.BLACK);
        canvas.snapshot(parameters, result);
        return result;
    }

    private void updateImageMetadata(GeneratedImage generatedImage, Label imageInfo) {
        if (generatedImage.isVideo()) {
            // Video metadata: resolution, duration, audio, format
            String resolution = generatedImage.getVideoResolution() != null ? generatedImage.getVideoResolution() : "";
            int duration = generatedImage.getVideoDurationSeconds();
            boolean audio = generatedImage.hasAudio();
            imageInfo.setText(String.format("MP4 • %s • %ds%s",
                    resolution, duration, audio ? " • Audio" : ""));
        } else {
            // Hide seed information for Gemini images (seed = 0)
            // FLUX seeds can be negative (XOR of random longs), so check != 0
            long seed = generatedImage.getSeed();
            boolean hasSeed = seed != 0;

            String seedInfo = hasSeed ? String.format(" • Seed: %d", seed) : "";

            // Use stored values from GeneratedImage - NO disk I/O here for performance!
            int width = generatedImage.getWidth();
            int height = generatedImage.getHeight();
            String format = generatedImage.getFormat();

            // Determine format from file extension if not stored
            if (format == null || format.isEmpty() || "UNKNOWN".equals(format)) {
                String localPath = generatedImage.getLocalFilePath();
                if (localPath != null) {
                    String fileName = localPath.toLowerCase();
                    if (fileName.endsWith(".png")) {
                        format = "PNG";
                    } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                        format = "JPEG";
                    } else if (fileName.endsWith(".webp")) {
                        format = "WEBP";
                    } else {
                        format = "IMG";
                    }
                } else {
                    format = "IMG";
                }
            }

            // Show size, format, and optionally seed in metadata
            imageInfo.setText(String.format("%dx%d • %s%s",
                    width, height,
                    format.toUpperCase(), seedInfo));
        }

        // Update styling based on status
        if (generatedImage.getDownloadStatus() == GeneratedImage.DownloadStatus.FAILED) {
            // Use red for failed status
            imageInfo.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 11px;");
        } else {
            // Normal white text on black background
            imageInfo.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
        }
    }

    /**
     * Creates image action buttons for the image card.
     */
    private void createImageButtons(HBox actionButtonsArea, HBox previewButtonArea, HBox imageMetadataContainer,
                                    GeneratedImage generatedImage, int imageIndex, boolean imageAvailable) {
        // Download button
        Button downloadBtn = new Button();
        downloadBtn.getStyleClass().addAll("button-icon", "flat");
        FontIcon downloadIcon = new FontIcon("mdi2d-download");
        downloadIcon.setIconSize(12);
        downloadIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        downloadBtn.setGraphic(downloadIcon);
        downloadBtn.setTooltip(new Tooltip(I18nManager.getText("tooltip.download.image")));
        downloadBtn.setPadding(new Insets(2, 2, 2, 2));
        downloadBtn.setDisable(!imageAvailable);
        downloadBtn.setOnAction(e -> {
            String actualPath = getActualImagePath(generatedImage, false);
            if (onImageDownload != null && actualPath != null) {
                onImageDownload.accept(actualPath);
            }
        });

        // Edit button
        Button editBtn = new Button();
        editBtn.getStyleClass().addAll("button-icon", "flat");
        FontIcon editIcon = new FontIcon("mdi2p-pencil");
        editIcon.setIconSize(12);
        editIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        editBtn.setGraphic(editIcon);
        editBtn.setTooltip(new Tooltip(I18nManager.getText("tooltip.edit.image")));
        editBtn.setPadding(new Insets(2, 2, 2, 2));
        editBtn.setDisable(!imageAvailable);
        editBtn.setOnAction(e -> {
            String actualPath = getActualImagePath(generatedImage, false);
            if (onImageEdit != null && actualPath != null) {
                onImageEdit.accept(actualPath);
            }
        });

        actionButtonsArea.getChildren().addAll(downloadBtn, editBtn);

        // Delete button
        Button deleteBtn = new Button();
        deleteBtn.getStyleClass().addAll("button-icon", "flat");
        FontIcon deleteIcon = new FontIcon("mdi2t-trash-can-outline");
        deleteIcon.setIconSize(12);
        deleteIcon.setIconColor(javafx.scene.paint.Color.rgb(255, 100, 100));
        deleteBtn.setGraphic(deleteIcon);
        deleteBtn.setTooltip(new Tooltip(I18nManager.getText("tooltip.delete.image")));
        deleteBtn.setPadding(new Insets(2, 2, 2, 2));
        deleteBtn.setOnAction(e -> {
            if (onImageDelete != null) {
                onImageDelete.accept(generatedImage);
            }
        });

        // Preview button
        Button previewBtn = new Button();
        previewBtn.getStyleClass().addAll("button-icon", "flat");
        FontIcon previewIcon = new FontIcon("mdi2e-eye");
        previewIcon.setIconSize(12);
        previewIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        previewBtn.setGraphic(previewIcon);
        previewBtn.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.image")));
        previewBtn.setPadding(new Insets(2, 2, 2, 2));
        previewBtn.setDisable(!imageAvailable);
        previewBtn.setOnAction(e -> {
            if (onImageClick != null) {
                onImageClick.accept(chatEntry, imageIndex);
            }
        });

        previewButtonArea.getChildren().addAll(deleteBtn, previewBtn);

        // Seed copy button (icon only) - only visible for FLUX images with seed > 0
        // Added to imageMetadataContainer (bottom right, after seed text) for better UX
        Button copySeedBtn = new Button();
        copySeedBtn.getStyleClass().addAll("button-icon", "flat");
        FontIcon copySeedIcon = new FontIcon("mdi2c-content-copy");
        copySeedIcon.setIconSize(10);
        copySeedIcon.getStyleClass().add("text-muted");
        copySeedBtn.setGraphic(copySeedIcon);
        copySeedBtn.setTooltip(new Tooltip(I18nManager.getText("tooltip.copy.seed")));
        copySeedBtn.setPadding(new Insets(0, 2, 0, 2));
        copySeedBtn.setOnAction(e -> {
            if (onSeedCopy != null) {
                onSeedCopy.accept(generatedImage.getSeed());
            }
        });

        // Add to metadata container ONLY for FLUX-generated images with a seed AND when FLUX model is selected
        // FLUX seeds can be negative (XOR of random longs), so check != 0
        boolean hasSeed = generatedImage.getSeed() != 0;
        // Check if this image was generated with a FLUX model (not Gemini)
        String imageModel = generatedImage.getModel();
        boolean isFluxGeneratedImage = imageModel != null &&
            !imageModel.toLowerCase().contains("gemini");
        // Check if a FLUX model is currently selected (not Gemini)
        boolean fluxModelSelected = isFluxModelSelected != null && isFluxModelSelected.get();
        // Button visible only when: FLUX image + has seed + FLUX model selected
        boolean showSeedButton = hasSeed && isFluxGeneratedImage && fluxModelSelected;
        copySeedBtn.setVisible(showSeedButton);
        copySeedBtn.setManaged(showSeedButton);
        // Only add button to container if it's a FLUX image with seed (button may be hidden based on model selection)
        if (hasSeed && isFluxGeneratedImage) {
            imageMetadataContainer.getChildren().add(copySeedBtn);
        }

        // Store references for I18n updates
        imageButtons.add(downloadBtn);
        imageButtons.add(editBtn);
        imageButtons.add(deleteBtn);
        imageButtons.add(previewBtn);
        imageButtons.add(copySeedBtn);

        // Store button references for state updates when image becomes available
        ImageCardButtons buttons = new ImageCardButtons();
        buttons.downloadButton = downloadBtn;
        buttons.editButton = editBtn;
        buttons.previewButton = previewBtn;
        buttons.copySeedButton = copySeedBtn;
        imageButtonsMap.put(generatedImage.getId(), buttons);
    }

    /**
     * Updates the metadata (dimensions and format) for a specific image card.
     * This is called when the image status changes to CACHED to refresh the display.
     *
     * @param imageCard the VBox containing the image card UI elements
     * @param generatedImage the generated image with updated metadata
     */
    private void updateMetadataForImageCard(VBox imageCard, GeneratedImage generatedImage) {
        try {
            // Image card structure (from createImageCard):
            // children[0] = buttonRowContainer
            // children[1] = imageContainer (StackPane)
            // children[2] = imageMetadataContainer (HBox with imageInfo Label + copySeedButton)
            // children[3] = costBreakdownBox (optional)

            if (imageCard.getChildren().size() < 3) {
                logger.warn("Image card has unexpected structure, cannot update metadata");
                return;
            }

            javafx.scene.Node metadataNode = imageCard.getChildren().get(2);
            if (!(metadataNode instanceof HBox)) {
                logger.warn("Image metadata container is not an HBox, cannot update metadata");
                return;
            }

            HBox imageMetadataContainer = (HBox) metadataNode;
            if (imageMetadataContainer.getChildren().isEmpty()) {
                logger.warn("Image metadata container is empty, cannot update metadata");
                return;
            }

            // First child should be the imageInfo Label
            javafx.scene.Node labelNode = imageMetadataContainer.getChildren().get(0);
            if (!(labelNode instanceof Label)) {
                logger.warn("First child of metadata container is not a Label, cannot update metadata");
                return;
            }

            Label imageInfo = (Label) labelNode;

            // Now update the metadata using the existing method
            updateImageMetadata(generatedImage, imageInfo);

            // Update or add cost breakdown box if needed
            VBox costBreakdownBox = createCostBreakdownBox(generatedImage);

            // Check if cost breakdown already exists (children[3])
            if (imageCard.getChildren().size() > 3) {
                // Replace existing cost breakdown
                if (costBreakdownBox != null) {
                    imageCard.getChildren().set(3, costBreakdownBox);
                } else {
                    // Remove cost breakdown if no longer needed
                    imageCard.getChildren().remove(3);
                }
            } else {
                // Add cost breakdown if it doesn't exist yet
                if (costBreakdownBox != null) {
                    imageCard.getChildren().add(costBreakdownBox);
                }
            }

            logger.debug("Updated metadata for image {}: {}x{} {} (cost breakdown: {})",
                        generatedImage.getId().substring(0, Math.min(8, generatedImage.getId().length())),
                        generatedImage.getWidth(),
                        generatedImage.getHeight(),
                        generatedImage.getFormat(),
                        costBreakdownBox != null ? "added" : "none");

        } catch (Exception e) {
            logger.error("Failed to update metadata for image card", e);
        }
    }

    /**
     * Sets the callback for when a seed copy button is clicked.
     *
     * @param onSeedCopy the callback that receives the seed value
     */
    public void setOnSeedCopy(Consumer<Long> onSeedCopy) {
        this.onSeedCopy = onSeedCopy;
    }

    /**
     * Sets the callback for when the prompt copy button is clicked.
     * 
     * @param onPromptCopy the callback that receives the prompt text
     */
    public void setOnPromptCopy(Consumer<String> onPromptCopy) {
        this.onPromptCopy = onPromptCopy;
    }

    /**
     * Sets the callback for when an image is clicked.
     * 
     * @param onImageClick the callback that receives the ChatEntry and image index
     */
    public void setOnImageClick(java.util.function.BiConsumer<ChatEntry, Integer> onImageClick) {
        this.onImageClick = onImageClick;
    }

    /**
     * Sets the callback to be called when an image download button is clicked.
     * 
     * @param onImageDownload the callback that receives the image file path
     */
    public void setOnImageDownload(Consumer<String> onImageDownload) {
        this.onImageDownload = onImageDownload;
    }

    /**
     * Sets the callback to be called when an image edit button is clicked.
     * 
     * @param onImageEdit the callback that receives the image file path
     */
    public void setOnImageEdit(Consumer<String> onImageEdit) {
        this.onImageEdit = onImageEdit;
    }

    /**
     * Sets the callback for when the entry delete button is clicked.
     * 
     * @param onEntryDelete the callback that receives the entry ID
     */
    public void setOnEntryDelete(Consumer<String> onEntryDelete) {
        // Convert to accept ChatEntry but pass just the ID
        this.onEntryDelete = entry -> {
            if (onEntryDelete != null) {
                onEntryDelete.accept(entry.getId());
            }
        };
    }

    /**
     * Sets the callback for when an image delete button is clicked.
     *
     * @param onImageDelete the callback that receives entry ID and image ID
     */
    public void setOnImageDelete(java.util.function.BiConsumer<String, String> onImageDelete) {
        // Convert to accept GeneratedImage but pass entry and image IDs
        this.onImageDelete = image -> {
            if (onImageDelete != null && image != null) {
                String imageId = image.getId();
                logger.debug("setOnImageDelete callback invoked - entryId: {}, imageId: {} (length: {})",
                        chatEntry.getId(), imageId, imageId != null ? imageId.length() : "null");
                onImageDelete.accept(chatEntry.getId(), imageId);
            }
        };
    }

    /**
     * Sets the callback of the context menu of the entry, which takes over the mood and the building blocks of the
     * entry into the panel.
     *
     * @param onMoodTakeOver the callback that receives the entry and whether to save the blocks as a new mood
     */
    public void setOnMoodTakeOver(java.util.function.BiConsumer<ChatEntry, Boolean> onMoodTakeOver) {
        this.onMoodTakeOver = onMoodTakeOver;
    }

    /**
     * Shows the context menu with "Take over mood" and "Save as mood...", available for an entry with building blocks
     * or with a former look style (1.x), which lives on as the standard mood of its name.
     */
    private void showMoodMenu(ContextMenuEvent event) {
        if (onMoodTakeOver == null)
            return;
        boolean available = chatEntry.getMood() != null
                || art.moodframe.service.MoodService.isLegacyLook(chatEntry.getStyle());
        MenuItem takeOver = new MenuItem(I18nManager.getText("entry.mood.take.over"));
        takeOver.setOnAction((action) -> onMoodTakeOver.accept(chatEntry, false));
        takeOver.setDisable(!available);
        MenuItem save = new MenuItem(I18nManager.getText("entry.mood.save"));
        save.setOnAction((action) -> onMoodTakeOver.accept(chatEntry, true));
        save.setDisable(!available);
        new ContextMenu(takeOver, save).show(this, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    /**
     * Sets the callback of the context menu of an image, which opens the dialog "Mood from image" with it.
     *
     * @param onMoodFromImage the callback that receives the image file in full resolution
     */
    public void setOnMoodFromImage(Consumer<File> onMoodFromImage) {
        this.onMoodFromImage = onMoodFromImage;
    }

    /** Shows the context menu of an image with "Mood from image...". */
    private void showImageMenu(GeneratedImage generatedImage, ContextMenuEvent event) {
        String path = getActualImagePath(generatedImage, false);
        if (onMoodFromImage == null || path == null || !new File(path).exists())
            return;
        MenuItem fromImage = new MenuItem(I18nManager.getText("mood.image.menu"));
        fromImage.setOnAction((action) -> onMoodFromImage.accept(new File(path)));
        new ContextMenu(fromImage).show(this, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    /**
     * Shows the chat the entry was generated in above its prompt, as a link to that chat, for the view by mood.
     *
     * @param chatName the name of the chat
     * @param onOpen opens the chat
     */
    public void showOrigin(String chatName, Runnable onOpen) {
        FontIcon chatIcon = new FontIcon("mdi2c-chat-outline");
        chatIcon.setIconSize(12);
        Hyperlink originLink = new Hyperlink(chatName, chatIcon);
        originLink.getStyleClass().add("text-small");
        originLink.setPadding(Insets.EMPTY);
        originLink.setTooltip(new Tooltip(I18nManager.getText("entry.origin.tooltip")));
        originLink.setOnAction((event) -> onOpen.run());
        getChildren().add(0, originLink);
    }

    /**
     * Sets the supplier that checks if a FLUX model is currently selected.
     * Used to show/hide seed copy button (only available when FLUX model selected).
     *
     * @param isFluxModelSelected supplier that returns true if FLUX model is selected
     */
    public void setIsFluxModelSelected(java.util.function.Supplier<Boolean> isFluxModelSelected) {
        this.isFluxModelSelected = isFluxModelSelected;
    }

    /**
     * Refreshes the images display after an image has been deleted.
     * This method rebuilds the images container to reflect current state.
     */
    public void refreshImages() {
        // Clear and rebuild the images container
        imagesContainer.getChildren().clear();
        imagesCurrentlyLoaded = false;

        // Rebuild the images display
        updateImagesDisplay();
    }

    /**
     * Gets the total number of loaded images across all ChatEntryCards.
     */
    public static int getTotalLoadedImages() {
        return totalLoadedImages.get();
    }

    /**
     * Creates a vertical separator for inline metadata.
     * 
     * @return a vertical separator
     */
    private Separator createVerticalSeparator() {
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        separator.setMaxHeight(12.0);
        separator.setPrefHeight(12.0);
        separator.getStyleClass().add("text-muted");
        return separator;
    }

    @Override
    protected void onDispose() {
        // CRITICAL: Force unload all images regardless of tracking state
        // This ensures image counter is decremented even if images were never marked as loaded
        forceUnloadAllImages();

        // Clear callback references to prevent potential memory leaks
        onSeedCopy = null;
        onPromptCopy = null;
        onImageClick = null;
        onImageDownload = null;
        onImageEdit = null;

        logger.trace("ChatEntryCard disposed for entry: {}", chatEntry.getId());
    }

    /**
     * Forces unload of all images without checking tracking state.
     * Used during disposal to ensure image counter is always accurate.
     */
    private void forceUnloadAllImages() {
        // Force unload all image views
        if (imagesContainer != null && imagesContainer.getChildren().size() > 1) {
            if (imagesContainer.getChildren().get(1) instanceof FlowPane) {
                FlowPane imageGrid = (FlowPane) imagesContainer.getChildren().get(1);

                for (int i = 0; i < imageGrid.getChildren().size(); i++) {
                    if (imageGrid.getChildren().get(i) instanceof VBox) {
                        VBox imageCard = (VBox) imageGrid.getChildren().get(i);
                        // Inline unload logic - find ImageView and clear it
                        for (javafx.scene.Node node : imageCard.getChildren()) {
                            if (node instanceof StackPane stackPane) {
                                for (javafx.scene.Node child : stackPane.getChildren()) {
                                    if (child instanceof ImageView imageView) {
                                        javafx.scene.image.Image oldImage = imageView.getImage();
                                        if (oldImage != null) {
                                            imageView.setImage(null);
                                            int currentCount = totalLoadedImages.get();
                                            if (currentCount > 0) {
                                                totalLoadedImages.decrementAndGet();
                                            }
                                        }
                                        imageView.setOnMouseClicked(null);
                                        imageView.setCursor(javafx.scene.Cursor.DEFAULT);
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Clear button references map to prevent memory leaks
        imageButtonsMap.clear();

        imagesCurrentlyLoaded = false;
    }

    @Override
    public void onLanguageChanged() {
        if (isDisposed()) {
            return; // Don't update if disposed
        }

        try {
            // Update status display
            updateStatusDisplay();

            // Update parameters display
            updateParametersDisplay();

            // Update only the images title, not the entire display
            updateImagesTitleOnly();

            // Update timestamp display (for "No timestamp" text)
            updateTimestampDisplay();

            // Update all button tooltips
            updateTooltips();

        } catch (Exception e) {
            logger.warn("Error during language change update for ChatEntryCard", e);
        }
    }

    /**
     * Updates all button tooltips with current language translations.
     * Called when language changes via onLanguageChanged().
     */
    private void updateTooltips() {
        // Update entry-level button tooltips
        if (deleteEntryButton != null) {
            deleteEntryButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.delete.entry")));
        }
        if (copyPromptButton != null) {
            copyPromptButton.setTooltip(new Tooltip(I18nManager.getText("tooltip.copy.prompt")));
        }

        // Update image button tooltips
        // Note: imageButtons list contains buttons in order: download, edit, delete, preview, copySeed (per image)
        int buttonIndex = 0;
        for (Button button : imageButtons) {
            int type = buttonIndex % 5; // 5 buttons per image
            switch (type) {
                case 0 -> button.setTooltip(new Tooltip(I18nManager.getText("tooltip.download.image")));
                case 1 -> button.setTooltip(new Tooltip(I18nManager.getText("tooltip.edit.image")));
                case 2 -> button.setTooltip(new Tooltip(I18nManager.getText("tooltip.delete.image")));
                case 3 -> button.setTooltip(new Tooltip(I18nManager.getText("tooltip.preview.image")));
                case 4 -> button.setTooltip(new Tooltip(I18nManager.getText("tooltip.copy.seed")));
            }
            buttonIndex++;
        }
    }

}