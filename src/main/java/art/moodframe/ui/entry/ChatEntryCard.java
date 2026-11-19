package art.moodframe.ui.entry;

import art.moodframe.model.AspectRatios;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ModelLock;
import art.moodframe.service.generation.ModelLabels;
import art.moodframe.ui.component.ManagedComponent;
import art.moodframe.util.I18nManager;
import art.moodframe.ui.support.SafetyTolerance;
import art.moodframe.ui.support.Subscription;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
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

    /** What the card asks of its host: the actions at the entry and at its images. */
    public interface Actions {

        /** Does nothing, e.g. in a test. */
        Actions NONE = new Actions() {
            @Override
            public void reuse(ChatEntry entry, Reuse part) {
            }

            @Override
            public void open(ChatEntry entry, int imageIndex) {
            }

            @Override
            public void edit(String imagePath) {
            }

            @Override
            public void download(String imagePath) {
            }

            @Override
            public void deleteEntry(ChatEntry entry) {
            }

            @Override
            public void deleteImage(ChatEntry entry, GeneratedImage image) {
            }

            @Override
            public void copySeed(long seed) {
            }

            @Override
            public boolean seedApplies() {
                return false;
            }

            @Override
            public boolean usesSeeds(String modelId) {
                return false;
            }

            @Override
            public void takeOverMood(ChatEntry entry, boolean save) {
            }

            @Override
            public void saveFrame(ChatEntry entry) {
            }

            @Override
            public void moodFromImage(File image) {
            }

            @Override
            public void useImageMood(ChatEntry entry) {
            }
        };

        /**
         * Takes over the entry, or one of its parts, into the input and the right panel ("Reuse").
         *
         * @param entry the entry
         * @param part  what to take over
         */
        void reuse(ChatEntry entry, Reuse part);

        /**
         * Opens the full view on an image.
         *
         * @param entry      the entry
         * @param imageIndex the index of the image in the entry
         */
        void open(ChatEntry entry, int imageIndex);

        /**
         * Attaches an image to edit it.
         *
         * @param imagePath the file of the image
         */
        void edit(String imagePath);

        /**
         * Copies an image into the downloads.
         *
         * @param imagePath the file of the image
         */
        void download(String imagePath);

        /**
         * Deletes the entry with its images.
         *
         * @param entry the entry
         */
        void deleteEntry(ChatEntry entry);

        /**
         * Deletes one image of the entry.
         *
         * @param entry the entry
         * @param image the image
         */
        void deleteImage(ChatEntry entry, GeneratedImage image);

        /**
         * Takes over the seed of an image.
         *
         * @param seed the seed
         */
        void copySeed(long seed);

        /**
         * Whether a seed applies to the chosen model, a model of a provider with seeds; asked whenever the menu of an
         * image opens.
         */
        boolean seedApplies();

        /**
         * Whether the provider of a model gives its images seeds that repeat them.
         *
         * @param modelId the API model id of an image
         * @return true if the image offers its seed
         */
        boolean usesSeeds(String modelId);

        /**
         * Takes over the mood of the entry into the panel, or saves it as a new mood first.
         *
         * @param entry the entry
         * @param save  whether to save the building blocks as a new mood
         */
        void takeOverMood(ChatEntry entry, boolean save);

        /**
         * Saves aspect ratio and resolution of the entry as a new frame.
         *
         * @param entry the entry
         */
        void saveFrame(ChatEntry entry);

        /**
         * Opens "Mood from image" on an image.
         *
         * @param image the image file in full resolution
         */
        void moodFromImage(File image);

        /**
         * "Use mood" at an image: takes over the mood saved at the entry without analyzing the image.
         *
         * @param entry the entry
         */
        void useImageMood(ChatEntry entry);
    }

    private final ChatEntry chatEntry;
    private final boolean isChatDeleted;

    /*  finds the files of the images, in the trash for a chat there; null where the card shows no stored chat  */
    private final ChatHistoryService history;
    private final EntryTilePictures pictures;
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

    /*  The number of images, set on the JavaFX thread: the list of the entry changes on other threads as well, so the
        width of the tiles must not follow it directly  */
    private final IntegerProperty imageCount = new SimpleIntegerProperty();


    /*  The action bar of each image by image id, to enable its actions once the file is there  */
    private final java.util.Map<String, ImageActionBar> imageBars = new java.util.HashMap<>();

    /*  carries out the actions at the entry and its images  */
    private final Actions actions;

    /**
     * Creates the card of an entry, which shows the thumbnails of its images.
     *
     * @param chatEntry     the entry
     * @param isChatDeleted whether its chat lies in the trash
     * @param history       finds the files of the images, in the trash for a chat there (may be null: the files where
     *                      they were generated)
     * @param sources       the thumbnails and the size check of the images
     * @param actions       carries out the actions at the entry and its images
     */
    public ChatEntryCard(ChatEntry chatEntry, boolean isChatDeleted, ChatHistoryService history, ImageSources sources,
                         Actions actions) {
        this.history = history;
        this.actions = actions;
        this.pictures = new EntryTilePictures(chatEntry, isChatDeleted, history, sources,
                (int index) -> actions.open(chatEntry, index));
        this.chatEntry = chatEntry;
        this.isChatDeleted = isChatDeleted;

        logger.debug("ChatEntryCard created - Entry: {}, isChatDeleted: {}",
                chatEntry.getId(), isChatDeleted);

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
        MenuItem saveMood = EntryMenus.item("entry.mood.save", () -> actions.takeOverMood(chatEntry, true));
        MenuItem saveFrame = EntryMenus.item("entry.frame.save", this::saveFrame);
        this.moreButton = new MenuButton(null, moreIcon, EntryMenus.item("entry.reuse.all", () -> reuse(Reuse.ALL)),
                EntryMenus.item("entry.reuse.prompt", () -> reuse(Reuse.PROMPT)),
                EntryMenus.item("entry.reuse.mood", () -> reuse(Reuse.MOOD)),
                EntryMenus.item("entry.reuse.frame", () -> reuse(Reuse.FRAME)), new SeparatorMenuItem(), saveMood, saveFrame,
                new SeparatorMenuItem(), EntryMenus.item("entry.delete", () -> actions.deleteEntry(chatEntry)));
        moreButton.getStyleClass().addAll("icon-button", "no-arrow");
        moreButton.setMinWidth(Region.USE_PREF_SIZE);

        /*  Saving is available for an entry that carries a mood or a frame  */
        moreButton.setOnShowing((event) -> {
            saveMood.setDisable(!EntryMenus.hasMood(chatEntry));
            saveFrame.setDisable(chatEntry.getFrame() == null);
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
        imageCount.set(ChatLibrary.imagesOf(chatEntry).size());
        this.tileWidth = Bindings.createDoubleBinding(() -> {
            int count = Math.max(1, imageCount.get());
            double space = availableWidth.get() - getInsets().getLeft() - getInsets().getRight()
                    - IMAGE_GAP * (count - 1);
            return Math.max(MIN_TILE_WIDTH, Math.min(maxTileSize.get(), space / count));
        }, availableWidth, maxTileSize, imageCount);

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
        actions.reuse(chatEntry, part);
    }

    /** Hands the entry to "Save as frame...". */
    private void saveFrame() {
        actions.saveFrame(chatEntry);
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
            /*  The change is read here, on the thread that changes the list under the lock of the chat models; the
                JavaFX thread gets a copy of the added images  */
            List<GeneratedImage> added = new ArrayList<>();
            boolean changed = false;
            while (change.next()) {
                if (change.wasAdded())
                    added.addAll(change.getAddedSubList());
                changed |= change.wasAdded() || change.wasRemoved();
            }
            if (!changed)
                return;
            Platform.runLater(() -> {
                // Add property listeners to newly added images
                for (GeneratedImage addedImage : added)
                    setupImagePropertyListeners(addedImage);

                /*  The constructor showed the images of that time, so also the first placeholders of a new request
                    show at once  */
                updateImagesDisplay();
                updateBadgesDisplay();
                updateInfoDisplay();
            });
        };

        /*  The listener goes on the shared list under the lock of the chat models, as the threads that change the
            list call it, and the images of that moment are taken in the same section, so none is missed  */
        List<GeneratedImage> existing = ModelLock.read(() -> {
            chatEntry.getImages().addListener(imagesListener);
            return new ArrayList<>(chatEntry.getImages());
        });
        addSubscription(Subscription.of(() -> ModelLock.read(() -> {
            chatEntry.getImages().removeListener(imagesListener);
            return null;
        }), "ChatEntry images collection listener"));
        for (GeneratedImage image : existing)
            setupImagePropertyListeners(image);

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
                (obs, oldStatus, newStatus) -> Platform.runLater(() -> showImageChange(image))));
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
        int imageCount = ChatLibrary.imagesOf(chatEntry).size();
        if (imageCount > 0)
            parts.add(I18nManager.getText("params.image.count", imageCount));
        if (chatEntry.getCost() > 0)
            parts.add(I18nManager.getText("params.cost") + " $" + I18nManager.formatCost(chatEntry.getCost()));
        infoLabel.setText(String.join(" · ", parts));
    }

    /**
     * Updates the badges of mood, frame and model, last the badge "MCP" for an entry of an AI agent. The tooltip of a
     * badge shows its details: the building blocks of the mood, target and result of the frame, the full name and price
     * of the model with the safety level of a BFL model.
     */
    private void updateBadgesDisplay() {
        badgesBox.getChildren().clear();

        /*  An entry from before 2.0 may carry its style at its first image only (BFL)  */
        String style = chatEntry.getStyle();
        List<GeneratedImage> images = ChatLibrary.imagesOf(chatEntry);
        if (style == null && !images.isEmpty())
            style = images.get(0).getStylePreset();

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
        if (chatEntry.getSource() == ChatEntry.Source.MCP)
            badgesBox.getChildren().add(createBadge(new EntryBadge(ChatEntry.Source.MCP.name(), false), "mcp",
                    new Tooltip(I18nManager.getText("entry.badge.mcp"))));
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
        GeneratedImage sized = ChatLibrary.imagesOf(chatEntry).stream()
                .filter((image) -> image.getWidth() > 0 && image.getHeight() > 0).findFirst().orElse(null);
        String result = EntryBadge.join(chatEntry.getSize(),
                sized != null ? sized.getWidth() + " × " + sized.getHeight() : null);
        return I18nManager.getText("entry.badge.frame.target", target) + "\n"
                + I18nManager.getText("entry.badge.frame.result", result);
    }

    /** The model under its full name with its price per image, a model with a safety tolerance with its level. */
    private String modelDetails() {
        GenerationModel known = GenerationModel.fromEntryModel(chatEntry.getModel());
        String name = ModelLabels.entryName(chatEntry.getModel());
        if (chatEntry.getSafetyTolerance() >= 0 && known != null && known.supportsSafetyTolerance())
            return name + "\n" + SafetyTolerance.label(chatEntry.getSafetyTolerance());
        return name;
    }


    /** The entry the card shows. */
    public ChatEntry entry() {
        return chatEntry;
    }

    /**
     * Shows what changed at the entry beside its status, prompt and images, which the card follows itself: its cost
     * and its error message.
     */
    public void showChanges() {
        updateStatusDisplay();
        updateInfoDisplay();
    }

    /**
     * Shows the change of an image of the entry, e.g. its download status or that it became a video: only its tile is
     * built anew, the other tiles and the card stay.
     *
     * @param image the image
     */
    public void showImageChange(GeneratedImage image) {
        List<GeneratedImage> images = ChatLibrary.imagesOf(chatEntry);
        int index = images.indexOf(image);
        if (index < 0)
            return;

        /*  A change of the list, still on its way to the JavaFX thread, builds all tiles  */
        if (images.size() != imagesContainer.getChildren().size()) {
            updateImagesDisplay();
            return;
        }
        imagesContainer.getChildren().set(index, createImageTile(image, index));
    }

    /**
     * Updates the images display: one row of tiles of the same width.
     */
    private void updateImagesDisplay() {
        imagesContainer.getChildren().clear();
        imageBars.clear();

        List<GeneratedImage> images = ChatLibrary.imagesOf(chatEntry);
        imageCount.set(images.size());
        if (images.isEmpty()) {
            imagesContainer.setVisible(false);
            return;
        }

        imagesContainer.setVisible(true);
        for (int i = 0; i < images.size(); i++)
            imagesContainer.getChildren().add(createImageTile(images.get(i), i));
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
                String path = pictures.getActualImagePath(generatedImage, false); // full-resolution original, not thumbnail
                if (path != null) {
                    File imageFile = new File(path);
                    if (imageFile.exists())
                        ImageDragExporter.startDrag(imageView, imageFile);
                }
                event.consume();
            });
        }

        ImageDetails.install(tile, imageView, generatedImage, size);

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
                : pictures.getActualImagePath(generatedImage, false);
        switch (action) {
            case VIEW -> actions.open(chatEntry, imageIndex);
            case EDIT -> {
                if (path != null)
                    actions.edit(path);
            }
            case DOWNLOAD -> {
                if (path != null)
                    actions.download(path);
            }
            case MOOD -> {
                /*  An entry with its building blocks hands over its mood, an older one has the image analyzed  */
                if (chatEntry.getMood() != null)
                    actions.useImageMood(chatEntry);
                else if (path != null && new File(path).exists())
                    actions.moodFromImage(new File(path));
            }
            case DELETE -> actions.deleteImage(chatEntry, generatedImage);
        }
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
        int imageIndex = ChatLibrary.imagesOf(chatEntry).indexOf(generatedImage);

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
                        pictures.createVideoPlaceholder(generatedImage, imageView, imageIndex);
                    } else {
                        // Image: Load thumbnail for better performance
                        pictures.loadThumbnailImage(generatedImage, imageView, loadingOverlay, progressIndicator, imageIndex);
                    }
                } else {
                    // No local path available yet
                    pictures.createPlaceholderImage(generatedImage, imageView, EntryTilePictures.Placeholder.NO_PATH);
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
                Tooltip.install(loadingOverlay, new Tooltip(ImageDetails.failure(generatedImage)));
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
     * Shows the context menu with "Use mood" and "Save as mood...", available for an entry with building blocks
     * or with a former look style (1.x), which lives on as the standard mood of its name.
     */
    private void showMoodMenu(ContextMenuEvent event) {
        EntryMenus.moodMenu(EntryMenus.hasMood(chatEntry), () -> actions.takeOverMood(chatEntry, false),
                () -> actions.takeOverMood(chatEntry, true)).show(this, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    /**
     * Shows the context menu of the frame badge with "Use frame" and "Save as frame...", the latter available
     * for an entry that carries its frame.
     */
    private void showFrameMenu(ContextMenuEvent event) {
        EntryMenus.frameMenu(() -> reuse(Reuse.FRAME), chatEntry.getFrame() != null ? this::saveFrame : null).show(this, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    /**
     * Shows the context menu of an image with "Use mood" or "Mood from image..." and, for an image of a provider
     * with seeds, "Copy seed", which takes effect while a model of such a provider is chosen.
     */
    private void showImageMenu(GeneratedImage generatedImage, ContextMenuEvent event) {
        String path = pictures.getActualImagePath(generatedImage, false);
        Runnable useMood = chatEntry.getMood() != null ? () -> actions.useImageMood(chatEntry) : null;
        Runnable fromImage = chatEntry.getMood() == null && path != null && new File(path).exists()
                ? () -> actions.moodFromImage(new File(path)) : null;
        Runnable copySeed = EntryMenus.offersSeed(generatedImage, actions::usesSeeds)
                ? () -> actions.copySeed(generatedImage.getSeed()) : null;
        ContextMenu menu = EntryMenus.imageMenu(useMood, fromImage, copySeed, actions.seedApplies());
        if (menu == null)
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

    @Override
    protected void onDispose() {
        /*  The views let go of their pictures, which the cache of the window keeps  */
        forceUnloadAllImages();

        /*  The shared width listens to the images of the entry, the list holding the card and the image size  */
        tileWidth.dispose();
        availableWidth.unbind();
        maxTileSize.unbind();

        logger.trace("ChatEntryCard disposed for entry: {}", chatEntry.getId());
    }

    /** Lets go of the pictures of the tiles and of their handlers. */
    private void forceUnloadAllImages() {
        for (Node tile : imagesContainer.getChildren()) {
            if (tile instanceof StackPane stackPane && stackPane.getChildren().get(0) instanceof ImageView imageView) {
                imageView.setImage(null);
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
