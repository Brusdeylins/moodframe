package art.moodframe.ui.component;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.service.ThumbnailManager;
import art.moodframe.service.ImageDragExporter;
import art.moodframe.service.ImageSizeCheck;
import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.util.I18nManager;
import art.moodframe.util.Subscription;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableNumberValue;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.io.File;

/**
 * Custom component for displaying a ChatEntry in the chat content area.
 *
 * <p>
 * This component shows the user prompt with "Reuse" and a menu, below it the
 * badges of mood, frame and model with status, date, number of images and cost
 * beside them, and the generated images in one row, each with an action bar
 * at its top while the pointer is over it.
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
 * @updated 2.0.0 - Badges for mood, frame and model, "Reuse", action bar over the images
 */
public class ChatEntryCard extends ManagedComponent {

    private static final Logger logger = LoggerFactory.getLogger(ChatEntryCard.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss");

    /*  The images of an entry share one row at the same width, each at most as wide and high as the image size of
        the chat header (400 px until bound), the action bar needs about 120 px  */
    private static final double IMAGE_GAP = 12.0;
    private static final double MAX_TILE_SIZE = 400.0;
    private static final double MIN_TILE_WIDTH = 96.0;
    private static final double TILE_RADIUS = 8.0;

    /*  The fields of a mood in the order of the tooltip of the mood badge  */
    private static final List<Mood.Field> BADGE_FIELDS = List.of(Mood.Field.STYLE, Mood.Field.ATMOSPHERE,
            Mood.Field.LIGHT, Mood.Field.COLOR, Mood.Field.CAMERA);

    /** What "Reuse" and the menu of an entry take over into the input and the right panel. */
    public enum Reuse {
        ALL, PROMPT, MOOD, FRAME
    }

    private final ChatEntry chatEntry;
    private final boolean isChatDeleted;
    private final Label promptLabel;
    private final Button reuseButton;
    private final MenuButton moreButton;
    private final HBox badgesBox;
    private final HBox statusIcon;
    private final Label infoLabel;
    private final Label errorDetailsLabel; // Multiline label for detailed error messages with solution hints
    private final HBox imagesContainer;
    private final ProgressIndicator progressIndicator;

    /*  The width the images of the entry share: the width of the card, or of the list holding it once bound  */
    private final DoubleProperty availableWidth = new SimpleDoubleProperty();
    private final DoubleProperty maxTileSize = new SimpleDoubleProperty(MAX_TILE_SIZE);
    private final DoubleBinding tileWidth;

    // Static counter for total loaded images across all cards
    private static final java.util.concurrent.atomic.AtomicInteger totalLoadedImages = new java.util.concurrent.atomic.AtomicInteger(0);

    /*  The action bar of each image by image id, to enable its actions once the file is there  */
    private final java.util.Map<String, ImageActionBar> imageBars = new java.util.HashMap<>();

    // Callback functions
    private Consumer<Long> onSeedCopy;
    private java.util.function.BiConsumer<ChatEntry, Reuse> onReuse;
    private java.util.function.BiConsumer<ChatEntry, Integer> onImageClick;
    private Consumer<String> onImageDownload; // Callback for downloading image by file path
    private Consumer<String> onImageEdit; // Callback for editing image by file path
    private Consumer<ChatEntry> onEntryDelete; // Callback for deleting entire entry
    private Consumer<GeneratedImage> onImageDelete; // Callback for deleting single image
    private java.util.function.BiConsumer<ChatEntry, Boolean> onMoodTakeOver;
    private Consumer<ChatEntry> onFrameSave;
    private Consumer<File> onMoodFromImage;
    private Consumer<ChatEntry> onImageMood;
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

        /*  The prompt row: the prompt, "Use all" and the menu with the parts to use, saving and "Delete entry"  */
        HBox promptContainer = new HBox(4);
        promptContainer.setAlignment(Pos.CENTER_LEFT);

        this.promptLabel = new Label();
        promptLabel.getStyleClass().addAll("text-default");
        promptLabel.setWrapText(false); // Disable wrapping for ellipsis
        promptLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        promptLabel.setMaxWidth(Double.MAX_VALUE);
        promptLabel.setMinHeight(javafx.scene.control.Control.USE_PREF_SIZE); // Force single line
        promptLabel.setMaxHeight(javafx.scene.control.Control.USE_PREF_SIZE); // Force single line
        HBox.setHgrow(promptLabel, Priority.ALWAYS);

        FontIcon reuseIcon = new FontIcon("mdi2c-content-copy");
        reuseIcon.setIconSize(14);
        this.reuseButton = new Button(null, reuseIcon);
        reuseButton.getStyleClass().add("icon-button");
        reuseButton.setMinWidth(Region.USE_PREF_SIZE);
        reuseButton.setOnAction((event) -> reuse(Reuse.ALL));

        FontIcon moreIcon = new FontIcon("mdi2d-dots-horizontal");
        moreIcon.setIconSize(14);
        MenuItem saveMood = menuItem("entry.mood.save", () -> {
            if (onMoodTakeOver != null)
                onMoodTakeOver.accept(chatEntry, true);
        });
        MenuItem saveFrame = menuItem("entry.frame.save", this::saveFrame);
        this.moreButton = new MenuButton(null, moreIcon, menuItem("entry.reuse.all", () -> reuse(Reuse.ALL)),
                menuItem("entry.reuse.prompt", () -> reuse(Reuse.PROMPT)),
                menuItem("entry.reuse.mood", () -> reuse(Reuse.MOOD)),
                menuItem("entry.reuse.frame", () -> reuse(Reuse.FRAME)), new SeparatorMenuItem(), saveMood, saveFrame,
                new SeparatorMenuItem(), menuItem("entry.delete", () -> {
                    if (onEntryDelete != null)
                        onEntryDelete.accept(chatEntry);
                }));
        moreButton.getStyleClass().addAll("icon-button", "no-arrow");
        moreButton.setMinWidth(Region.USE_PREF_SIZE);

        /*  Saving is available for an entry that carries a mood or a frame  */
        moreButton.setOnShowing((event) -> {
            saveMood.setDisable(onMoodTakeOver == null || !hasMood());
            saveFrame.setDisable(onFrameSave == null || chatEntry.getFrame() == null);
        });

        promptContainer.getChildren().addAll(promptLabel, reuseButton, moreButton);

        /*  Below the prompt the badges of mood, frame and model, beside them quietly status, date, images and cost  */
        this.badgesBox = new HBox(6);
        badgesBox.setAlignment(Pos.CENTER_LEFT);

        this.progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(14.0, 14.0);

        this.statusIcon = new HBox();
        statusIcon.setAlignment(Pos.CENTER);
        statusIcon.setMinWidth(Region.USE_PREF_SIZE);

        this.infoLabel = new Label();
        infoLabel.getStyleClass().addAll("text-muted", "text-small");

        HBox badgeRow = new HBox(8, badgesBox, statusIcon, infoLabel);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        // Error details label for multiline error messages with solution hints
        this.errorDetailsLabel = new Label();
        errorDetailsLabel.getStyleClass().addAll("text-small", "text-danger");
        errorDetailsLabel.setWrapText(true);
        errorDetailsLabel.setVisible(false);
        errorDetailsLabel.managedProperty().bind(errorDetailsLabel.visibleProperty());
        errorDetailsLabel.setStyle("-fx-padding: 8 0 0 0;"); // Top padding to separate from status row

        /*  The images in one row of the same width, without a card of their own; the row claims no minimum width,
            else it would keep the cell from shrinking to the width the images are sized for  */
        this.imagesContainer = new HBox(IMAGE_GAP);
        imagesContainer.setAlignment(Pos.TOP_LEFT);
        imagesContainer.setMinWidth(0);
        imagesContainer.setVisible(false);
        imagesContainer.managedProperty().bind(imagesContainer.visibleProperty());

        availableWidth.bind(widthProperty());
        this.tileWidth = Bindings.createDoubleBinding(() -> {
            int count = Math.max(1, chatEntry.getImages().size());
            double space = availableWidth.get() - getInsets().getLeft() - getInsets().getRight()
                    - IMAGE_GAP * (count - 1);
            return Math.max(MIN_TILE_WIDTH, Math.min(maxTileSize.get(), space / count));
        }, availableWidth, maxTileSize, chatEntry.getImages());

        /*  The context menu takes over the mood of the entry; on an image it offers "Use mood" or "Mood from image"  */
        promptContainer.setOnContextMenuRequested(this::showMoodMenu);
        badgeRow.setOnContextMenuRequested(this::showMoodMenu);

        getChildren().addAll(promptContainer, badgeRow, errorDetailsLabel, imagesContainer);
        updateTooltips();

        // Bind to the ChatEntry data
        updateContent();

        // Listen for changes in the ChatEntry
        setupBindings();
    }

    /** Hands the entry to "Reuse" or to one of its single parts. */
    private void reuse(Reuse part) {
        if (onReuse != null)
            onReuse.accept(chatEntry, part);
    }

    /** Hands the entry to "Save as frame...". */
    private void saveFrame() {
        if (onFrameSave != null)
            onFrameSave.accept(chatEntry);
    }

    /** An item of the menu of the entry, which keeps the key of its text for a change of the language. */
    private static MenuItem menuItem(String key, Runnable action) {
        MenuItem item = new MenuItem(I18nManager.getText(key));
        item.setUserData(key);
        item.setOnAction((event) -> action.run());
        return item;
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

                        /*  The constructor showed the images of that time, so also the first placeholders of a new
                            request show at once  */
                        updateImagesDisplay();
                        updateBadgesDisplay();
                        updateInfoDisplay();
                    } else if (change.wasRemoved()) {
                        updateImagesDisplay();
                        updateBadgesDisplay();
                        updateInfoDisplay();
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
        updateBadgesDisplay();
        updateInfoDisplay();
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
     * Updates the status icon beside the badges, with the status in its tooltip; a failed entry shows its detailed
     * message with the solution hints below the row.
     */
    private void updateStatusDisplay() {
        // Ensure this runs on the FX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateStatusDisplay);
            return;
        }

        ChatEntry.Status status = chatEntry.getStatus();
        Node icon = switch (status) {
            case PENDING -> statusGlyph("mdi2c-clock-outline", "text-muted");
            case PROCESSING -> progressIndicator;
            case COMPLETED -> statusGlyph("mdi2c-check-circle", "text-success");
            case FAILED -> statusGlyph("mdi2a-alert-circle", "text-danger");
            case CANCELLED -> statusGlyph("mdi2c-cancel", "text-muted");
        };
        statusIcon.getChildren().setAll(icon);
        Tooltip.install(statusIcon, new Tooltip(I18nManager.getText(switch (status) {
            case PENDING -> "entry.status.pending";
            case PROCESSING -> "status.generating";
            case COMPLETED -> "entry.status.completed";
            case FAILED -> "entry.status.failed";
            case CANCELLED -> "entry.status.cancelled";
        })));

        String errorMsg = chatEntry.getErrorMessage();
        boolean showError = status == ChatEntry.Status.FAILED && errorMsg != null && !errorMsg.isEmpty();
        errorDetailsLabel.setText(showError ? errorMsg : "");
        errorDetailsLabel.setVisible(showError);
    }

    /** An icon of the status row. */
    private static FontIcon statusGlyph(String iconCode, String styleClass) {
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(14);
        icon.getStyleClass().add(styleClass);
        return icon;
    }

    /**
     * Updates the quiet info beside the badges: date, number of images and cost.
     */
    private void updateInfoDisplay() {
        List<String> parts = new ArrayList<>();
        parts.add(chatEntry.getCreatedAt() != null ? chatEntry.getCreatedAt().format(TIME_FORMATTER)
                : I18nManager.getText("chat.timestamp.none"));
        if (!chatEntry.getImages().isEmpty())
            parts.add(I18nManager.getText("params.image.count", chatEntry.getImages().size()));
        if (chatEntry.getCost() > 0)
            parts.add(String.format("%s $%.2f", I18nManager.getText("params.cost"), chatEntry.getCost()));
        infoLabel.setText(String.join(" · ", parts));
    }

    /**
     * Updates the badges of mood, frame and model. The tooltip of a badge shows its details: the building blocks of the
     * mood, target and result of the frame, the full name and price of the model with the safety level of a BFL model.
     */
    private void updateBadgesDisplay() {
        badgesBox.getChildren().clear();

        /*  An entry from before 2.0 may carry its style at its first image only (BFL)  */
        String style = chatEntry.getStyle();
        if (style == null && !chatEntry.getImages().isEmpty())
            style = chatEntry.getImages().get(0).getStylePreset();

        EntryBadge mood = EntryBadge.mood(chatEntry.getMood(), style);
        if (mood != null)
            badgesBox.getChildren().add(createBadge(mood, "mood", moodTooltip(chatEntry.getMood(), style)));
        EntryBadge frame = EntryBadge.frame(chatEntry.getFrame(), chatEntry.getSize());
        if (frame != null) {
            /*  The details are read anew on every showing, as the size of an image may be corrected later  */
            Tooltip details = new Tooltip(frameDetails());
            details.setOnShowing((event) -> details.setText(frameDetails()));
            Label badge = createBadge(frame, "frame", details);
            badge.setOnContextMenuRequested(this::showFrameMenu);
            badgesBox.getChildren().add(badge);
        }
        EntryBadge model = EntryBadge.model(chatEntry.getModel());
        if (model != null)
            badgesBox.getChildren().add(createBadge(model, "model", new Tooltip(modelDetails())));
    }

    /**
     * A badge in the color of its group of the right panel: filled, or dashed and in italics for a mixture. Mood and
     * frame keep their full text; the model and the info beside them give way in a narrow window.
     */
    private static Label createBadge(EntryBadge badge, String kind, Tooltip tooltip) {
        Label label = new Label(badge.text());
        label.getStyleClass().addAll("entry-badge", "entry-badge-" + kind);
        if (badge.mixture())
            label.getStyleClass().add("entry-badge-mixture");
        if (!"model".equals(kind))
            label.setMinWidth(Region.USE_PREF_SIZE);
        tooltip.setShowDelay(Duration.millis(300));
        label.setTooltip(tooltip);
        return label;
    }

    /**
     * The building blocks of the mood of an entry, one row per field, a palette with its colors; a mixture names the
     * mood it started from and marks the changed fields. An entry from before 2.0 names its style.
     */
    private static Tooltip moodTooltip(EntryMood mood, String style) {
        VBox rows = new VBox(3);
        if (mood == null)
            rows.getChildren().add(new Label(I18nManager.getText("params.style") + " " + style));
        else {
            if (mood.baseName() != null && !mood.changed().isEmpty())
                rows.getChildren().add(new Label(I18nManager.getText("entry.badge.mood.base", mood.baseName())));
            for (Mood.Field field : BADGE_FIELDS) {
                EntryMood.Block block = mood.blocks().get(field);
                boolean changed = mood.changed().contains(field);
                if (block == null && !changed)
                    continue;
                Label row = new Label(fieldLabel(field) + " " + (block != null ? block.name() : "–")
                        + (changed ? " •" : ""));
                if (changed)
                    row.setStyle("-fx-font-weight: bold;");
                if (block != null && !block.colors().isEmpty()) {
                    HBox swatches = new HBox(2);
                    for (String hex : block.colors()) {
                        try {
                            Rectangle swatch = new Rectangle(10, 10, Color.web(hex));

                            /*  the fine border of the color list keeps a dark color visible on the dark tooltip  */
                            swatch.setStroke(Color.GRAY);
                            swatch.setStrokeWidth(0.5);
                            swatches.getChildren().add(swatch);
                        }
                        catch (IllegalArgumentException e) {

                            /*  a color that is no valid hex code is left out  */
                        }
                    }
                    row.setGraphic(swatches);
                    row.setContentDisplay(ContentDisplay.RIGHT);
                }
                rows.getChildren().add(row);
            }
        }
        Tooltip tooltip = new Tooltip();
        tooltip.setGraphic(rows);
        return tooltip;
    }

    /** The label of a mood field, for example "Style:". */
    private static String fieldLabel(Mood.Field field) {
        return I18nManager.getText(switch (field) {
            case STYLE -> "params.style";
            case ATMOSPHERE -> "params.atmosphere";
            case LIGHT -> "params.light";
            case COLOR -> "params.colorstyle";
            case CAMERA -> "params.camera";
        });
    }

    /** The target of the frame and what the model made of it: its aspect ratio and the pixel size of the images. */
    private String frameDetails() {
        EntryFrame frame = chatEntry.getFrame();
        String target = frame != null ? EntryBadge.join(frame.aspectRatio(), frame.resolution()) : chatEntry.getSize();
        GeneratedImage sized = chatEntry.getImages().stream()
                .filter((image) -> image.getWidth() > 0 && image.getHeight() > 0).findFirst().orElse(null);
        String result = EntryBadge.join(chatEntry.getSize(),
                sized != null ? sized.getWidth() + " × " + sized.getHeight() : null);
        return I18nManager.getText("entry.badge.frame.target", target) + "\n"
                + I18nManager.getText("entry.badge.frame.result", result);
    }

    /** The model under its full name with its price per image, a BFL model with its safety level. */
    private String modelDetails() {
        GenerationModel known = GenerationModel.fromDisplayName(chatEntry.getModel());
        String name = known != null ? known.getLocalizedDisplayName() : chatEntry.getModel();
        if (chatEntry.getSafetyTolerance() >= 0 && known != null && known.getProvider() == ApiProvider.BFL)
            return name + "\n" + I18nManager.getText("params.safety",
                    getSafetyToleranceDescription(chatEntry.getSafetyTolerance()));
        return name;
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
     * Updates the images display: one row of tiles of the same width.
     */
    private void updateImagesDisplay() {
        imagesContainer.getChildren().clear();
        imageBars.clear();

        if (chatEntry.getImages().isEmpty()) {
            imagesContainer.setVisible(false);
            return;
        }

        imagesContainer.setVisible(true);
        for (int i = 0; i < chatEntry.getImages().size(); i++)
            imagesContainer.getChildren().add(createImageTile(chatEntry.getImages().get(i), i));
    }

    /**
     * Creates the tile of an image: the image with rounded corners, a muted placeholder with a progress while it is
     * generated or downloaded, or with an icon once it failed, and the action bar at its top and its size at its bottom
     * while the pointer is over it. Seed and costs show in its tooltip, "Use mood" or "Mood from image" and "Copy seed"
     * in its context menu.
     *
     * @param generatedImage the generated image
     * @param imageIndex     the index of this image in the chat entry
     * @return the tile
     */
    private StackPane createImageTile(GeneratedImage generatedImage, int imageIndex) {
        ImageView imageView = new ImageView();
        imageView.fitWidthProperty().bind(tileWidth);
        imageView.fitHeightProperty().bind(maxTileSize);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        // Create loading indicator for each image
        ProgressIndicator imageProgressIndicator = new ProgressIndicator();
        imageProgressIndicator.setMaxSize(32, 32);
        imageProgressIndicator.setVisible(false); // Initially hidden

        /*  The placeholder covers the whole tile  */
        VBox loadingOverlay = new VBox(8.0);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setVisible(false); // Initially hidden
        loadingOverlay.getStyleClass().add("entry-image-placeholder");
        loadingOverlay.getChildren().add(imageProgressIndicator);

        // Check if image is available for actions
        boolean imageAvailable = generatedImage.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED &&
                generatedImage.getLocalFilePath() != null &&
                !generatedImage.getLocalFilePath().isEmpty();

        ImageActionBar actionBar = new ImageActionBar((action) -> onImageAction(action, generatedImage, imageIndex));
        actionBar.setAvailable(imageAvailable, generatedImage.isVideo());
        actionBar.setMoodSaved(chatEntry.getMood() != null);
        imageBars.put(generatedImage.getId(), actionBar);

        // Set up image based on download status
        updateImageDisplay(generatedImage, imageView, loadingOverlay, imageProgressIndicator);

        /*  The tile is as large as the image, its corners rounded by a clip; in the aspect ratio of the image, the
            placeholder already has the size of the finished image, so the entry keeps its height  */
        StackPane tile = new StackPane(imageView, loadingOverlay);
        tile.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Double ratio = ratioOf(generatedImage);
        if (ratio != null || generatedImage.getDownloadStatus() != GeneratedImage.DownloadStatus.CACHED) {
            /*  The shape follows the size of the image, which its first display may still correct from the file  */
            DoubleBinding shape = Bindings.createDoubleBinding(() -> {
                Double known = ratioOf(generatedImage);
                return known != null ? known : 1.0;
            }, generatedImage.widthProperty(), generatedImage.heightProperty(), generatedImage.imageWidthProperty(),
                    generatedImage.imageHeightProperty());
            DoubleBinding width = Bindings.createDoubleBinding(
                    () -> Math.min(tileWidth.get(), maxTileSize.get() * shape.get()), tileWidth, maxTileSize, shape);
            tile.prefWidthProperty().bind(width);
            tile.prefHeightProperty().bind(width.divide(shape));
            tile.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        }
        Rectangle clip = new Rectangle();
        clip.setArcWidth(2 * TILE_RADIUS);
        clip.setArcHeight(2 * TILE_RADIUS);
        clip.widthProperty().bind(tile.widthProperty());
        clip.heightProperty().bind(tile.heightProperty());
        tile.setClip(clip);
        actionBar.attachTo(tile);
        ImageSizeLabel size = new ImageSizeLabel(generatedImage);
        size.attachTo(tile, null);

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

        installDetails(tile, imageView, generatedImage, size);

        /*  The menu checks for the file itself, as the image may still be loading  */
        if (!generatedImage.isVideo())
            imageView.setOnContextMenuRequested((event) -> showImageMenu(generatedImage, event));

        return tile;
    }

    /**
     * The aspect ratio of the tile of an image: its pixel size once known, else the ratio it is generated in, of the
     * image itself, of the model or of the frame of the entry.
     *
     * @return width by height, null if unknown
     */
    private Double ratioOf(GeneratedImage image) {
        if (image.getWidth() > 0 && image.getHeight() > 0)
            return (double) image.getWidth() / image.getHeight();
        String ratio = image.getAspectRatio() != null ? image.getAspectRatio()
                : chatEntry.getSize() != null ? chatEntry.getSize()
                : chatEntry.getFrame() != null ? chatEntry.getFrame().aspectRatio() : null;
        return ratio != null ? AspectRatios.value(ratio) : null;
    }

    /** Carries out an action of the action bar of an image. */
    private void onImageAction(ImageActionBar.Action action, GeneratedImage generatedImage, int imageIndex) {
        String path = action == ImageActionBar.Action.VIEW || action == ImageActionBar.Action.DELETE ? null
                : getActualImagePath(generatedImage, false);
        switch (action) {
            case VIEW -> {
                if (onImageClick != null)
                    onImageClick.accept(chatEntry, imageIndex);
            }
            case EDIT -> {
                if (onImageEdit != null && path != null)
                    onImageEdit.accept(path);
            }
            case DOWNLOAD -> {
                if (onImageDownload != null && path != null)
                    onImageDownload.accept(path);
            }
            case MOOD -> {
                /*  An entry with its building blocks hands over its mood, an older one has the image analyzed  */
                if (chatEntry.getMood() != null) {
                    if (onImageMood != null)
                        onImageMood.accept(chatEntry);
                }
                else if (onMoodFromImage != null && path != null && new File(path).exists())
                    onMoodFromImage.accept(new File(path));
            }
            case DELETE -> {
                if (onImageDelete != null)
                    onImageDelete.accept(generatedImage);
            }
        }
    }

    /**
     * Installs the tooltip of an image, read anew whenever the pointer enters its tile: the size line over the image
     * may give way and the size be corrected meanwhile. An image without any details gets no tooltip.
     *
     * @param tile  the pane holding the image, with its size line attached
     * @param view  the view of the image
     * @param image the image
     * @param size  the size line of the image
     */
    static void installDetails(Pane tile, Node view, GeneratedImage image, ImageSizeLabel size) {
        Tooltip details = new Tooltip();
        details.setShowDelay(Duration.millis(500));
        tile.hoverProperty().addListener((obs, wasOver, isOver) -> {
            if (isOver) {
                details.setText(imageDetails(image, !size.isVisible()));
                Tooltip.uninstall(view, details);
                if (!details.getText().isEmpty())
                    Tooltip.install(view, details);
            }
        });
    }

    /**
     * The details of an image for its tooltip: its seed, of a video its resolution, duration and audio; below the
     * cost of a Gemini image with web search or reasoning. The size and format of an image lead only on request.
     *
     * @param generatedImage the generated image
     * @param withSize       whether to name the size and format of an image, as its size line gives way
     * @return the details, one per line, empty if there are none
     */
    static String imageDetails(GeneratedImage generatedImage, boolean withSize) {
        List<String> details = new ArrayList<>();
        if (generatedImage.isVideo()) {
            String resolution = generatedImage.getVideoResolution() != null ? generatedImage.getVideoResolution() : "";
            details.add(String.format("MP4 • %s • %ds%s", resolution, generatedImage.getVideoDurationSeconds(),
                    generatedImage.hasAudio() ? " • Audio" : ""));
        } else {
            String size = ImageSizeLabel.text(generatedImage);
            if (withSize && size != null)
                details.add(size);

            // Hide seed information for Gemini images (seed = 0)
            // FLUX seeds can be negative (XOR of random longs), so check != 0
            long seed = generatedImage.getSeed();
            if (seed != 0)
                details.add(String.format("Seed: %d", seed));
        }

        /*  Gemini: the cost of web search and reasoning beside the base cost, if any  */
        double searchCost = generatedImage.getSearchCost() != null ? generatedImage.getSearchCost() : 0.0;
        double reasoningCost = generatedImage.getReasoningCost() != null ? generatedImage.getReasoningCost() : 0.0;
        if (searchCost > 0 || reasoningCost > 0) {
            double baseCost = generatedImage.getBaseCost() != null ? generatedImage.getBaseCost() : 0.0;
            if (baseCost > 0)
                details.add(String.format("Base Generation: $%.2f", baseCost));
            if (searchCost > 0)
                details.add(String.format("Google Search: $%.2f", searchCost));
            if (reasoningCost > 0) {
                Integer thinkingTokens = generatedImage.getThinkingTokens();
                details.add(String.format("Reasoning: $%.2f%s", reasoningCost,
                        thinkingTokens != null && thinkingTokens > 0 ? String.format(" (%,d tokens)", thinkingTokens)
                                : ""));
            }
            details.add(String.format("Total: $%.2f", baseCost + searchCost + reasoningCost));
        }
        return String.join("\n", details);
    }

    /**
     * Why an image failed, for the tooltip of its tile.
     *
     * @param generatedImage the failed image
     * @return its error message or reason, else the failed status
     */
    static String failure(GeneratedImage generatedImage) {
        String message = generatedImage.getErrorMessage();
        if (message == null || message.isBlank())
            message = generatedImage.getFailureReason();
        return message != null && !message.isBlank() ? message : I18nManager.getText("entry.status.failed");
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

        /*  The first display corrects a missing or wrong size from the file, the tile and tooltip follow it  */
        ImageSizeCheck sizeCheck = ServiceFactory.getInstance().getService(ImageSizeCheck.class);
        if (sizeCheck != null)
            sizeCheck.check(generatedImage, localPath);

        if (thumbnailManager == null) {
            // Fallback to original image loading if ThumbnailManager not available
            loadOriginalImage(generatedImage, imageView, imageIndex);
            return;
        }

        // Get the actual thumbnail path (considering trash)
        String thumbnailPath = getActualImagePath(generatedImage, true);

        /*  A thumbnail too small for its tile on the screen is created anew at full size  */
        SharpThumbnail.follow(imageView, localPath, thumbnailManager);

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
                        : new javafx.scene.image.Image(imageFile.toURI().toString(),
                                ThumbnailManager.MAX_THUMBNAIL_SIZE, ThumbnailManager.MAX_THUMBNAIL_SIZE, true, true,
                                false);

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
            case PENDING, DOWNLOADING -> {
                /*  A muted placeholder with a quiet progress while the image is generated or downloaded  */
                loadingOverlay.setVisible(true);
                progressIndicator.setVisible(true);
                progressIndicator.setProgress(-1.0); // Indeterminate progress
                // Remove click handler and cursor for pending images
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
                /*  An error tile in place of the image, the message in its tooltip  */
                FontIcon failedIcon = new FontIcon("mdi2a-alert-circle-outline");
                failedIcon.setIconSize(24);
                loadingOverlay.getChildren().setAll(failedIcon);
                loadingOverlay.getStyleClass().add("entry-image-failed");
                Tooltip.install(loadingOverlay, new Tooltip(failure(generatedImage)));
                loadingOverlay.setVisible(true);
                // Remove click handler and cursor for failed images
                imageView.setOnMouseClicked(null);
                imageView.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        }
    }

    /**
     * Enables the actions of an image once its file is there.
     *
     * @param generatedImage the generated image to check availability for
     */
    private void updateImageButtonStates(GeneratedImage generatedImage) {
        ImageActionBar actionBar = imageBars.get(generatedImage.getId());
        if (actionBar == null)
            return;
        boolean imageAvailable = generatedImage.getDownloadStatus() == GeneratedImage.DownloadStatus.CACHED
                && generatedImage.getLocalFilePath() != null
                && !generatedImage.getLocalFilePath().isEmpty();
        actionBar.setAvailable(imageAvailable, generatedImage.isVideo());
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

    /**
     * Sets the callback for when a seed copy button is clicked.
     *
     * @param onSeedCopy the callback that receives the seed value
     */
    public void setOnSeedCopy(Consumer<Long> onSeedCopy) {
        this.onSeedCopy = onSeedCopy;
    }

    /**
     * Sets the callback of "Reuse" and of the single parts in the menu of the entry.
     *
     * @param onReuse the callback that receives the entry and what to take over
     */
    public void setOnReuse(java.util.function.BiConsumer<ChatEntry, Reuse> onReuse) {
        this.onReuse = onReuse;
    }

    /**
     * Lets the images share the width of the cell holding the card, less its insets, which a reused cell knows before
     * the card is laid out, so that the card has its height from the start.
     *
     * @param cell the cell holding the card
     */
    public void bindAvailableWidth(Region cell) {
        availableWidth.bind(Bindings.createDoubleBinding(
                () -> cell.getWidth() - cell.getInsets().getLeft() - cell.getInsets().getRight(),
                cell.widthProperty(), cell.insetsProperty()));
    }

    /**
     * Limits the width and height of the images to the image size chosen in the chat header.
     *
     * @param size the largest size of an image in screen pixels
     */
    public void bindTileSize(ObservableNumberValue size) {
        maxTileSize.bind(size);
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
     * Shows the context menu with "Use mood" and "Save as mood...", available for an entry with building blocks
     * or with a former look style (1.x), which lives on as the standard mood of its name.
     */
    private void showMoodMenu(ContextMenuEvent event) {
        if (onMoodTakeOver == null)
            return;
        boolean available = hasMood();
        MenuItem takeOver = new MenuItem(I18nManager.getText("entry.reuse.mood"));
        takeOver.setOnAction((action) -> onMoodTakeOver.accept(chatEntry, false));
        takeOver.setDisable(!available);
        MenuItem save = new MenuItem(I18nManager.getText("entry.mood.save"));
        save.setOnAction((action) -> onMoodTakeOver.accept(chatEntry, true));
        save.setDisable(!available);
        new ContextMenu(takeOver, save).show(this, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    /** Whether the entry carries building blocks or a former look style (1.x), which lives on as a standard mood. */
    private boolean hasMood() {
        return chatEntry.getMood() != null || art.moodframe.service.MoodService.isLegacyLook(chatEntry.getStyle());
    }

    /**
     * Sets the callback of "Save as frame...", which stores aspect ratio and resolution of the entry as a new frame.
     *
     * @param onFrameSave the callback that receives the entry
     */
    public void setOnFrameSave(Consumer<ChatEntry> onFrameSave) {
        this.onFrameSave = onFrameSave;
    }

    /**
     * Shows the context menu of the frame badge with "Use frame" and "Save as frame...", the latter available
     * for an entry that carries its frame.
     */
    private void showFrameMenu(ContextMenuEvent event) {
        MenuItem takeOver = new MenuItem(I18nManager.getText("entry.reuse.frame"));
        takeOver.setOnAction((action) -> reuse(Reuse.FRAME));
        MenuItem save = new MenuItem(I18nManager.getText("entry.frame.save"));
        save.setOnAction((action) -> saveFrame());
        save.setDisable(onFrameSave == null || chatEntry.getFrame() == null);
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

    /**
     * Sets the callback of "Use mood" at an image, which takes over the mood saved at the entry without analyzing the
     * image; an entry without building blocks offers "Mood from image" instead.
     *
     * @param onImageMood the callback that receives the entry
     */
    public void setOnImageMood(Consumer<ChatEntry> onImageMood) {
        this.onImageMood = onImageMood;
    }

    /**
     * Shows the context menu of an image with "Use mood" or "Mood from image..." and, for a FLUX image with a seed,
     * "Copy seed", which takes effect while a FLUX model is chosen.
     */
    private void showImageMenu(GeneratedImage generatedImage, ContextMenuEvent event) {
        ContextMenu menu = new ContextMenu();
        String path = getActualImagePath(generatedImage, false);
        if (chatEntry.getMood() != null) {
            if (onImageMood != null)
                menu.getItems().add(menuItem("entry.reuse.mood", () -> onImageMood.accept(chatEntry)));
        }
        else if (onMoodFromImage != null && path != null && new File(path).exists()) {
            MenuItem fromImage = new MenuItem(I18nManager.getText("mood.image.menu"));
            fromImage.setOnAction((action) -> onMoodFromImage.accept(new File(path)));
            menu.getItems().add(fromImage);
        }

        /*  FLUX seeds can be negative (XOR of random longs), so check != 0  */
        String imageModel = generatedImage.getModel();
        if (onSeedCopy != null && generatedImage.getSeed() != 0 && imageModel != null
                && !imageModel.toLowerCase().contains("gemini")) {
            MenuItem copySeed = new MenuItem(I18nManager.getText("tooltip.copy.seed"));
            copySeed.setOnAction((action) -> onSeedCopy.accept(generatedImage.getSeed()));
            copySeed.setDisable(isFluxModelSelected == null || !isFluxModelSelected.get());
            menu.getItems().add(copySeed);
        }
        if (menu.getItems().isEmpty())
            return;
        menu.show(this, event.getScreenX(), event.getScreenY());
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

        // Rebuild the images display
        updateImagesDisplay();
    }

    /**
     * Gets the total number of loaded images across all ChatEntryCards.
     */
    public static int getTotalLoadedImages() {
        return totalLoadedImages.get();
    }

    @Override
    protected void onDispose() {
        // CRITICAL: Force unload all images regardless of tracking state
        // This ensures image counter is decremented even if images were never marked as loaded
        forceUnloadAllImages();

        /*  The shared width listens to the images of the entry, the list holding the card and the image size  */
        tileWidth.dispose();
        availableWidth.unbind();
        maxTileSize.unbind();

        // Clear callback references to prevent potential memory leaks
        onSeedCopy = null;
        onReuse = null;
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
        for (Node tile : imagesContainer.getChildren()) {
            if (tile instanceof StackPane stackPane && stackPane.getChildren().get(0) instanceof ImageView imageView) {
                if (imageView.getImage() != null) {
                    imageView.setImage(null);
                    if (totalLoadedImages.get() > 0)
                        totalLoadedImages.decrementAndGet();
                }
                imageView.setOnMouseClicked(null);
                imageView.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        }

        // Clear action bar references map to prevent memory leaks
        imageBars.clear();
    }

    @Override
    public void onLanguageChanged() {
        if (isDisposed()) {
            return; // Don't update if disposed
        }

        try {
            updateStatusDisplay();
            updateBadgesDisplay();
            updateInfoDisplay();
            updateTooltips();
        } catch (Exception e) {
            logger.warn("Error during language change update for ChatEntryCard", e);
        }
    }

    /**
     * Sets "Reuse", the menu of the entry and the action bars of the images in the current language.
     */
    private void updateTooltips() {
        reuseButton.setTooltip(new Tooltip(I18nManager.getText("entry.reuse.all")));
        moreButton.setTooltip(new Tooltip(I18nManager.getText("entry.more.tooltip")));
        moreButton.getItems().forEach((item) -> {
            if (item.getUserData() instanceof String key)
                item.setText(I18nManager.getText(key));
        });
        imageBars.values().forEach(ImageActionBar::updateTexts);
    }

}
