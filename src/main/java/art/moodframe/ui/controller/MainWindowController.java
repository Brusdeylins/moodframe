package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryGroup;
import art.moodframe.model.FrameGroup;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.MoodGroup;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ImageAttachmentService;
import art.moodframe.service.chat.ImageSizeCheck;
import art.moodframe.service.chat.ThumbnailManager;
import art.moodframe.service.generation.GenerationOrder;
import art.moodframe.service.generation.GenerationService;
import art.moodframe.service.generation.RequestResponseManager;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.prompt.PromptComposer;
import art.moodframe.service.provider.ApiProviderValidator;
import art.moodframe.service.trash.TrashCenter;
import art.moodframe.service.trash.TrashCleanupService;
import art.moodframe.service.trash.TrashGroup;
import art.moodframe.ui.panel.MoodFramePanelController;
import art.moodframe.ui.support.BoxInset;
import art.moodframe.ui.entry.ChatEntryCard;
import art.moodframe.ui.component.DialogButtons;
import art.moodframe.ui.entry.ImageSources;
import art.moodframe.ui.support.ImageCache;
import art.moodframe.ui.dialog.TrashWindow;
import art.moodframe.ui.support.FxI18n;
import art.moodframe.ui.support.FxLibraryEvents;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The main window ({@code MainWindow.fxml}), a coordinator of its parts. The window holds the header (version, update
 * hint, info, settings) and includes the parts with their own FXML and controller:
 * <ul>
 * <li>{@link LibrarySidebarController}: the chats, or the moods or frames as groups of entries, and the selection</li>
 * <li>{@link ChatViewController}: the entries of the chosen chat or group, with the {@link InputBarController}</li>
 * <li>{@link MoodFramePanelController}: the groups Mood and Frame of the right panel</li>
 * <li>{@link GenerationPanelController}: the group Generation of the right panel, with the price of the order</li>
 * <li>{@link StatusBarController}: the status message, memory and API key status</li>
 * </ul>
 * The coordinator looks up the services once and hands them to the parts, wires their events to each other and
 * carries out what spans several parts: sending, the brainstorm, reusing and editing an entry, the changes of the
 * library and of the language.
 *
 * @since 1.0.0
 */
public class MainWindowController implements I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(MainWindowController.class);

    // Main Layout
    @FXML
    private BorderPane mainBorderPane;
    @FXML
    private SplitPane mainSplitPane;
    @FXML
    private ScrollPane panelScrollPane;

    // Header Controls
    @FXML
    private HBox appHeader;
    @FXML
    private Label appVersionLabel;
    @FXML
    private Label updateHintLabel;
    @FXML
    private Button updateButton;
    @FXML
    private Tooltip updateTooltip;
    @FXML
    private Button infoButton;
    @FXML
    private Tooltip infoTooltip;
    @FXML
    private Button settingsButton;
    @FXML
    private Tooltip settingsTooltip;

    /*  Looks for a newer version, and the newer version the update hint in the header stands for, null while there
        is none  */
    private art.moodframe.service.UpdateCheckService updates;
    private art.moodframe.service.UpdateCheckService.Result update;

    // The parts of the window
    @FXML
    private LibrarySidebarController librarySidebarController;
    @FXML
    private ChatViewController chatViewController;
    @FXML
    private MoodFramePanelController moodFramePanelController;
    @FXML
    private GenerationPanelController generationPanelController;
    @FXML
    private StatusBarController statusBarController;
    private InputBarController inputBar;
    private PanelGroups panelGroups;
    private CornerCloud cornerCloud;

    // Services
    private ApplicationConfig config;
    private ChatHistoryService chatHistory;
    private GenerationService generationService;
    private PromptBlockService blockService;
    private ColorStyleService colorService;
    private MoodService moodService;
    private art.moodframe.service.brainstorm.BrainstormService.Factory brainstorms;

    /*  Whether all parts are set up: until then, a change in one part does not update the others  */
    private boolean ready;

    /*  What the window registered with the services of the application, removed when it closes  */
    private RequestResponseManager requests;
    private LibraryEvents events;
    private TrashCenter trashCenter;
    private ApiProviderValidator validator;
    private final java.util.function.Consumer<ChatEntry> entryListener = this::entryUpdated;
    private final java.util.function.Consumer<art.moodframe.model.GeneratedImage> imageListener =
            (image) -> Platform.runLater(() -> chatViewController.imageUpdated(image));
    private final LibraryEvents.Listener libraryListener = this::onLibraryChanged;
    private final Runnable validationListener = () -> Platform.runLater(this::refreshApiKeyStatus);
    private TrashCenter.Listener trashListener;
    private boolean disposed;

    /*  A worker for the blocking work of the window (queries of the groups, copies into the Downloads folder,
        deletes), off the FX thread and off the common pool; its thread ends when idle  */
    private final ExecutorService worker = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), (runnable) -> {
                Thread thread = new Thread(runnable, "MainWindowWorker");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Looks up the services once, hands them to the parts of the window and wires the parts to each other.
     */
    @FXML
    public void initialize() {
        ServiceFactory services = ServiceFactory.getInstance();
        config = services.getService(ApplicationConfig.class);
        chatHistory = services.getService(ChatHistoryService.class);
        generationService = services.getService(GenerationService.class);
        blockService = services.getService(PromptBlockService.class);
        colorService = services.getService(ColorStyleService.class);
        moodService = services.getService(MoodService.class);
        brainstorms = services.getService(art.moodframe.service.brainstorm.BrainstormService.Factory.class);
        ChatLibrary chatLibrary = services.getService(ChatLibrary.class);
        validator = services.getService(ApiProviderValidator.class);
        updates = services.getService(art.moodframe.service.UpdateCheckService.class);
        logger.debug("Initializing MainWindow controller");

        statusBarController.init(config, validator);
        FxI18n.addChangeListener(this);
        appVersionLabel.setText("v" + art.moodframe.util.VersionManager.getVersion());

        /*  The "M" of the logo starts on the line of the names of the sidebar, 1 px further in for the side bearing of
            their letters (behind the 10 px padding of the sidebar)  */
        appHeader.setPadding(new Insets(appHeader.getPadding().getTop(), appHeader.getPadding().getRight(),
                appHeader.getPadding().getBottom(), 10.0 + BoxInset.LEFT + 1.0));

        inputBar = chatViewController.inputBar();
        TrashWindow.Opener trash = new TrashWindow.Opener(services.getService(TrashCenter.class),
                services.getService(TrashCleanupService.class), worker);
        ImageCache images = new ImageCache();
        chatViewController.useImageSources(new ImageSources(services.getService(ThumbnailManager.class),
                services.getService(ImageSizeCheck.class), images));
        statusBarController.countImagesOf(images);
        chatViewController.init(config, chatLibrary, chatHistory, worker, (StackPane) mainBorderPane.getParent(),
                librarySidebarController::currentChat, librarySidebarController::shownGroup, statusBarController,
                chatActions());
        librarySidebarController.useTrash(trash);
        librarySidebarController.init(chatLibrary, chatHistory, config, worker, statusBarController,
                sidebarListener());
        setupSplitter();

        /*  The model follows the target resolution of the frame, the hints of the frame name the model  */
        moodFramePanelController.followModel(generationPanelController.modelProperty());
        generationPanelController.init(config, generationService, services.getService(ImageAttachmentService.class),
                moodFramePanelController.resolutionProperty(), statusBarController, generationListener());
        moodFramePanelController.init(new MoodFramePanelController.Services(config, blockService, colorService,
                moodService, services.getService(FrameService.class), services.getService(FavoriteService.class),
                services.getService(art.moodframe.service.brainstorm.MoodAnalysisService.class), trash),
                statusBarController, this::updateUIState);
        inputBar.init(generationPanelController.costTextProperty(), inputListener());
        statusBarController.ready();

        panelGroups = new PanelGroups(panelScrollPane, moodFramePanelController.moodGroup(),
                moodFramePanelController.frameGroup(), generationPanelController.group(), config);
        librarySidebarController.setupSidebarMode();
        cornerCloud = new CornerCloud(mainBorderPane, config);
        mainBorderPane.addEventFilter(KeyEvent.KEY_PRESSED, chatViewController.reuseKeyFilter());

        /*  The check of the accounts runs in the background since the start: its end refreshes button and status bar  */
        if (validator != null)
            validator.addCompletionListener(validationListener);
        statusBarController.checkApiKeys();
        ready = true;
        updateUIState();
        setupServices(services.getService(RequestResponseManager.class), services.getService(LibraryEvents.class),
                services.getService(TrashCenter.class));

        /*  Moods that lost their last block while the window was closed, for example by the automatic cleanup  */
        Platform.runLater(moodFramePanelController::askToDeleteEmptiedMoods);
        setupUpdateCheck();

        // Apply initial localization
        onLanguageChanged();
        disposeOnClose();
        logger.debug("MainWindow controller initialization complete");
    }

    /*  The window lets go of what it registered beyond itself once it is closed  */
    private void disposeOnClose() {
        mainBorderPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null)
                scene.windowProperty().addListener((winObs, oldWindow, window) -> {
                    if (window != null)
                        window.addEventHandler(WindowEvent.WINDOW_HIDDEN, (event) -> dispose());
                });
        });
    }

    /**
     * Lets go of what the window registered beyond itself, so a closed window stays reachable from nowhere: its
     * listeners of the language, the theme, the generations, the library, the trash and the checks of the accounts,
     * the timelines of its parts and its worker. Runs once, when the window is closed.
     */
    public void dispose() {
        if (disposed)
            return;
        disposed = true;
        I18nManager.removeChangeListener(this);
        if (requests != null) {
            requests.removeEntryListener(entryListener);
            requests.removeImageListener(imageListener);
            requests.setOnSafetyToleranceLowered((value) -> { });
        }
        if (events != null)
            events.removeListener(libraryListener);
        if (trashCenter != null && trashListener != null)
            trashCenter.removeListener(trashListener);
        if (validator != null)
            validator.removeCompletionListener(validationListener);
        chatViewController.dispose();
        generationPanelController.dispose();
        statusBarController.dispose();
        cornerCloud.dispose();
        worker.shutdown();
    }

    /*  A change of an entry during its generation; a finished entry changes the image counts of the groups  */
    private void entryUpdated(ChatEntry entry) {
        Platform.runLater(() -> {
            chatViewController.entryUpdated(entry);
            if (entry.getStatus() != ChatEntry.Status.PENDING && entry.getStatus() != ChatEntry.Status.PROCESSING)
                librarySidebarController.refreshGroups();
        });
    }

    /** The selection of the sidebar opens a group or shows the chat or the group anew. */
    private LibrarySidebarController.Listener sidebarListener() {
        return new LibrarySidebarController.Listener() {
            @Override
            public void groupChosen(EntryGroup group) {
                switch (group) {
                    case MoodGroup mood -> {
                        if (mood.kind() == MoodGroup.Kind.MOOD)
                            moodFramePanelController.chooseMoodById(mood.moodId());
                    }
                    case FrameGroup frame -> {
                        if (frame.kind() == FrameGroup.Kind.FRAME)
                            moodFramePanelController.chooseFrameById(frame.frameId());
                    }
                }
            }

            @Override
            public void showSelection() {
                chatViewController.showSelection();
                updateUIState();
            }

            @Override
            public void showHeader() {
                chatViewController.updateChatHeader();
            }
        };
    }

    /** What an entry or an image of the chat area asks of the other parts. */
    private ChatViewController.Actions chatActions() {
        return new ChatViewController.Actions() {
            @Override
            public void reuse(ChatEntry entry, ChatEntryCard.Reuse part) {
                reuseEntry(entry, part);
            }

            @Override
            public void edit(String imagePath) {
                editImage(imagePath);
            }

            @Override
            public void copySeed(long seed) {
                generationPanelController.setSeed(seed);
            }

            @Override
            public boolean seedApplies() {
                return generationPanelController.isSeedModelSelected();
            }

            @Override
            public boolean takeOverMood(ChatEntry entry, boolean save) {
                return moodFramePanelController.takeOverEntryMood(entry, save);
            }

            @Override
            public void saveFrame(ChatEntry entry) {
                moodFramePanelController.saveEntryFrame(entry);
            }

            @Override
            public void moodFromImage(File image) {
                moodFramePanelController.openMoodFromImage(image);
            }

            @Override
            public void useImageMood(ChatEntry entry) {
                moodFramePanelController.useImageMood(entry);
            }

            @Override
            public void openChatOf(ChatEntry entry) {
                if (librarySidebarController.openChatOf(entry))
                    Platform.runLater(() -> chatViewController.scrollTo(entry));
            }
        };
    }

    /** A change of the group Generation updates the input area, the frame and the cards. */
    private GenerationPanelController.Listener generationListener() {
        return new GenerationPanelController.Listener() {
            @Override
            public void changed() {
                updateUIState();
            }

            @Override
            public void modelChanged() {
                updateUIState();
            }

            @Override
            public void attachmentsChanged(File firstImage) {
                if (firstImage != null)
                    moodFramePanelController.matchAspectRatio(firstImage);
                updateUIState();
            }

            @Override
            public void moodFromImage(File image) {
                moodFramePanelController.openMoodFromImage(image);
            }
        };
    }

    /** The input area sends, brainstorms and counts the final prompt. */
    private InputBarController.Listener inputListener() {
        return new InputBarController.Listener() {
            @Override
            public void send() {
                onSend();
            }

            @Override
            public void brainstorm() {
                onBrainstorm();
            }

            @Override
            public void promptChanged() {
                updateUIState();
            }
        };
    }

    /**
     * Restores the position of the divider between the sidebar and the chat once the window is shown, and remembers
     * where the user drags it.
     */
    private void setupSplitter() {
        if (config == null) {
            mainSplitPane.setDividerPositions(0.2);
            return;
        }
        try {
            final double savedPosition = config.ui().getSplitterPosition();
            mainSplitPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null)
                    newScene.windowProperty().addListener((winObs, oldWindow, newWindow) -> {
                        if (newWindow != null)
                            newWindow.showingProperty().addListener((showObs, wasShowing, isShowing) -> {
                                if (isShowing)
                                    Platform.runLater(() -> mainSplitPane.setDividerPosition(0, savedPosition));
                            });
                    });
            });

            // Immediate attempt (may not work if layout isn't ready)
            Platform.runLater(() -> mainSplitPane.setDividerPositions(savedPosition));
            mainSplitPane.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) -> {
                try {
                    config.ui().setSplitterPosition(newPos.doubleValue());
                } catch (Exception ex) {
                    logger.warn("Failed to save splitter position", ex);
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to restore splitter position, using default", e);
            mainSplitPane.setDividerPositions(0.2);
        }
    }

    /**
     * Follows the generations, the library and the trash: the entries and images of a running generation, every
     * change of the library made here or by another caller (the MCP server), what leaves the trash.
     */
    private void setupServices(RequestResponseManager requests, LibraryEvents events, TrashCenter trashCenter) {
        this.requests = requests;
        this.events = events;
        this.trashCenter = trashCenter;
        if (requests != null) {
            requests.addEntryListener(entryListener);
            requests.addImageListener(imageListener);
            requests.setOnSafetyToleranceLowered((value) -> Platform.runLater(() -> {
                generationPanelController.setFlux2Safety(value);
                statusBarController.status(I18nManager.getText("status.bfl.safety.lowered", value));
            }));
        } else
            logger.warn("RequestResponseManager not available - using mock mode");

        if (events != null)
            FxLibraryEvents.addListener(events, libraryListener);
        else
            logger.warn("LibraryEvents not available - no automatic UI refresh");

        /*  What leaves the trash shows by the changes of the library; the trash adds the status and the moods that
            lost their last block  */
        if (trashCenter != null) {
            trashListener = new TrashCenter.Listener() {
                @Override
                public void restored(TrashGroup.Kind kind, String id) {
                    if (kind == TrashGroup.Kind.CHATS)
                        Platform.runLater(() -> statusBarController.status(I18nManager.getText("status.chat.restored")));
                    else if (kind == TrashGroup.Kind.IMAGES)
                        showImagesRestoredStatus(id);
                }

                /*  A mood that lost its last block with these blocks may go to the trash as well  */
                @Override
                public void deleted(TrashGroup.Kind kind, List<String> ids) {
                    if (kind == TrashGroup.Kind.BLOCKS)
                        Platform.runLater(moodFramePanelController::askToDeleteEmptiedMoods);
                }
            };
            trashCenter.addListener(trashListener);
        }
        else
            logger.warn("TrashCenter not available - no automatic UI refresh");
    }

    /**
     * Shows a change of the library in the lists and the open chat, whoever made it, and a download of another caller
     * in the status bar. A burst of changes, for example an import, reloads a list once per change.
     *
     * @param kind what changed
     * @param id   the ID of the changed entry, null if several changed
     */
    private void onLibraryChanged(LibraryEvents.Kind kind, String id) {
        try {
            switch (kind) {
                case MOOD, FRAME, ATMOSPHERE, STYLE, LIGHT, CAMERA, COLOR -> {
                    moodFramePanelController.onLibraryChanged(kind);
                    librarySidebarController.refreshGroups();
                }
                case FAVORITE -> moodFramePanelController.onLibraryChanged(kind);
                case CHAT -> {
                    librarySidebarController.showChatChange(id);
                    updateUIState();
                }
                case ENTRY -> {
                    chatViewController.showEntryChange(id);
                    librarySidebarController.refreshGroups();
                }
                case DOWNLOAD -> statusBarController.status(java.text.MessageFormat.format(
                        I18nManager.getText("download.image.success"), id));
            }
        } catch (Exception e) {
            logger.error("Failed to show the change of {} {}", kind, id, e);
        }
    }

    /** Tells whether an entry or one of its images came back from the trash; the worker asks the database. */
    private void showImagesRestoredStatus(String id) {
        try {
            worker.execute(() -> {
                boolean image = false;
                try {
                    image = chatHistory != null && chatHistory.findGeneratedImageById(id).isPresent();
                } catch (Exception e) {
                    logger.warn("Failed to find the restored image {}", id, e);
                }
                String key = image ? "status.image.restored" : "status.entry.restored";
                Platform.runLater(() -> statusBarController.status(I18nManager.getText(key)));
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            logger.debug("The window closed before the restore of {} was shown", id);
        }
    }

    /**
     * Builds the final prompt from the user input and the selected building blocks, with the attached image as
     * reference, see {@link PromptComposer}.
     */
    private String buildFinalPrompt() {
        moodFramePanelController.setReference(generationPanelController.hasAttachment());
        return new PromptComposer(generationPanelController.promptLimit()).compose(inputBar.prompt(),
                moodFramePanelController.promptBlocks(), generationPanelController.hasAttachment());
    }

    /**
     * Updates what depends on the state of the window: the count of the final prompt against the limit of the model,
     * the send button (text within the limit, a key for the provider of the model, a chat, a model) and the fields,
     * which need a key, a chat and a model to choose.
     */
    private void updateUIState() {
        if (!ready)
            return;
        String promptText = inputBar.prompt();
        boolean hasText = promptText != null && !promptText.trim().isEmpty();

        /*  the limit of the model applies to the final prompt, which the counter counts  */
        String finalPrompt = buildFinalPrompt();
        int limit = generationPanelController.promptLimit();
        inputBar.showCount(finalPrompt, limit);
        boolean hasValidText = hasText && new PromptComposer(limit).fits(finalPrompt);

        boolean hasApiKey = generationPanelController.hasApiKeyForSelectedModel();
        boolean hasChat = librarySidebarController.hasActiveChat();
        boolean hasModel = generationPanelController.modelItem() != null;
        inputBar.setSendEnabled(hasValidText && hasApiKey && hasChat && hasModel);

        /*  "New chat" needs a key of any provider, the fields a key for the chosen model and a chat  */
        boolean enableInput = hasApiKey && hasChat && generationPanelController.hasModels();
        librarySidebarController.setNewChatEnabled(config != null && config.accounts().hasAnyApiKey());
        generationPanelController.setInputEnabled(enableInput);
        moodFramePanelController.setInputEnabled(enableInput);
        inputBar.setInputEnabled(enableInput, placeholder(hasApiKey, hasChat));
        inputBar.updateBrainstorm(config, hasChat);
    }

    /*  the hint of the empty prompt: a missing key, no chat, an attached image, else the hint of the provider  */
    private String placeholder(boolean hasApiKey, boolean hasChat) {
        GenerationModel model = generationPanelController.selectedModel();
        return InputBarController.placeholder(hasApiKey, hasChat, generationPanelController.hasAttachment(),
                model != null ? model.getProvider() : null);
    }

    /**
     * Refreshes the API key status and updates the UI accordingly.
     * This method should be called after API key configuration changes.
     */
    public void refreshApiKeyStatus() {
        statusBarController.checkApiKeys();
        updateUIState();
    }

    /**
     * Hands the order of the window to the generation service and shows the result or the failure.
     */
    private void onSend() {
        String userPrompt = inputBar.prompt().trim();
        if (userPrompt.isEmpty() || userPrompt.length() > generationPanelController.promptLimit())
            return;
        if (generationService == null) {
            statusBarController.error(I18nManager.getText("status.error.service.unavailable"));
            logger.error("GenerationService not available");
            return;
        }

        /*  Without selection the default model  */
        String modelId = Objects.requireNonNullElse(generationPanelController.selectedModelId(),
                GenerationModel.getDefault().name());
        moodFramePanelController.setReference(generationPanelController.hasAttachment());
        MoodFramePanelController.Selection selection = moodFramePanelController.selection();
        GenerationService.Submission submission;
        try {
            submission = generationService.submit(new GenerationOrder(librarySidebarController.currentChat(),
                    userPrompt, moodFramePanelController.promptBlocks(), selection.mood(), selection.moodChanged(),
                    new GenerationOrder.Target(selection.frame(), selection.frameChanged(), selection.aspectRatio(),
                            selection.resolution()),
                    modelId, generationPanelController.imageCount(), generationPanelController.parameters(),
                    generationPanelController.attachedImage(), generationPanelController.attachedImagesBase64(),
                    ChatEntry.Source.UI));
        } catch (GenerationService.GenerationException e) {
            switch (e.getFailure()) {
                case PROMPT_TOO_LONG -> statusBarController.error(I18nManager.getText("status.error.prompt.too.long",
                        e.getPromptLength(), e.getMaxPromptLength()));
                case MISSING_KEY -> statusBarController.error(I18nManager.getText("status.error.api.key.missing"));
                case UNKNOWN_MODEL -> statusBarController.status(I18nManager.getText("status.request.failed"));
                case EMPTY_PROMPT, NO_CHAT -> logger.debug("Nothing to send: {}", e.getMessage());
            }
            return;
        }

        statusBarController.status(I18nManager.getText("status.processing.request"));

        /*  The entry shows at once, without reloading the chat; an open group counts it  */
        chatViewController.addEntry(submission.entry());
        if (librarySidebarController.shownGroup() != null)
            librarySidebarController.refreshGroups();
        inputBar.clearPrompt();

        /*  Provider-specific status message once the request is submitted  */
        GenerationModel model = GenerationModel.fromId(modelId);
        String providerName = model.getProvider() == ApiProvider.GOOGLE_GEMINI ? "Gemini"
                : model.getProvider() == ApiProvider.OPENAI ? "OpenAI" : "BFL";
        submission.submitted().whenComplete((Void done, Throwable throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                statusBarController.status(I18nManager.getText("status.request.failed"));
                chatViewController.showSelection();
                return;
            }
            String statusKey = "status.request.submitted." + providerName.toLowerCase();
            String statusMessage = I18nManager.getText(statusKey);

            // Fallback to generic message if provider-specific key doesn't exist
            if (statusMessage.equals(statusKey))
                statusMessage = I18nManager.getText("status.request.submitted");
            statusBarController.status(statusMessage);
        }));
    }

    /**
     * Opens the brainstorm popup, a guided AI chat that develops the prompt and its settings.
     */
    private void onBrainstorm() {
        try {
            /*  The names stay as shown: the OpenAI models share one constant and differ only by their name  */
            List<String> modelNames = generationPanelController.modelItems().stream()
                    .filter((name) -> GenerationModel.fromLocalizedDisplayName(name) != null).toList();
            List<GenerationModel> models = modelNames.stream().map(GenerationModel::fromLocalizedDisplayName).toList();

            /*  the building blocks and the mood as chosen, a locked field keeps its choice on "Adopt"  */
            Set<Mood.Field> locked = moodFramePanelController.lockedMoodFields();
            String resolution = moodFramePanelController.modelResolution();
            String prompt = inputBar.prompt();
            String currentState = "Target model: " + generationPanelController.modelItem()
                    + (resolution != null ? "\nResolution: " + resolution : "")
                    + moodFramePanelController.describe()
                    + "\nAspect ratio: " + moodFramePanelController.modelAspectRatio()
                    + "\nText currently in the input field: " + (prompt.isBlank() ? "(empty)" : prompt.trim());

            art.moodframe.service.brainstorm.BrainstormContext brainstormContext =
                    new art.moodframe.service.brainstorm.BrainstormContext(
                            blockService.getAll(PromptBlock.Type.STYLE), blockService.getAll(PromptBlock.Type.ATMOSPHERE),
                            blockService.getAll(PromptBlock.Type.LIGHT), blockService.getAll(PromptBlock.Type.CAMERA),
                            colorService.getAllStyles(), moodService != null ? moodService.getAll() : List.of(),
                            models, currentState, modelNames, locked);
            art.moodframe.service.brainstorm.BrainstormService brainstormService = brainstorms.create();

            Stage ownerStage = (Stage) mainBorderPane.getScene().getWindow();
            new art.moodframe.ui.brainstorm.BrainstormDialog(ownerStage, config, brainstormService,
                    brainstormContext, generationPanelController.modelItem(), this::applyBrainstormResult).show();
        } catch (Exception e) {
            logger.error("Failed to open brainstorm dialog", e);
        }
    }

    /**
     * Applies the values adopted in the brainstorm popup. The target model is set first, as it decides which aspect
     * ratios and video parameters are available.
     *
     * @param result the adopted values; a {@code null} value is left unchanged
     */
    private void applyBrainstormResult(art.moodframe.ui.brainstorm.BrainstormDialog.Result result) {
        generationPanelController.setModel(result.targetModel());

        /*  The adopted resolution (or the one of a model without resolution variants) as remembered choice of the
            family and as target, of which the model takes the same; any other leaves the target as it is  */
        String resolution = result.resolution() != null ? result.resolution()
                : art.moodframe.model.ModelFamilies.resolutionOf(result.targetModel());
        if (resolution != null) {
            generationPanelController.rememberResolution(result.targetModel(), resolution);
            moodFramePanelController.setResolution(resolution);
        }
        inputBar.setPrompt(result.prompt());
        moodFramePanelController.adoptBrainstorm(result.moodId(), result.blocks());
        moodFramePanelController.setAspectRatio(result.aspectRatio());
        generationPanelController.setVideoSeconds(result.durationSeconds());
        if (result.saveMood())
            moodFramePanelController.saveMoodAsNew();
        inputBar.focusPrompt();
    }

    /**
     * Takes over prompt, model, frame and mood of a chat entry ("Reuse"), or one of them from the menu of the entry. The
     * model comes first, as it decides the aspect ratios and resolutions the frame can take.
     *
     * @param entry the chat entry
     * @param part  what to take over
     */
    void reuseEntry(ChatEntry entry, ChatEntryCard.Reuse part) {
        boolean all = part == ChatEntryCard.Reuse.ALL;
        if (all || part == ChatEntryCard.Reuse.PROMPT)
            inputBar.setPrompt(entry.getUserPrompt());
        if (all)
            generationPanelController.reuse(entry);
        if (all || part == ChatEntryCard.Reuse.FRAME)
            moodFramePanelController.reuseFrame(entry);
        if (all || part == ChatEntryCard.Reuse.MOOD)
            moodFramePanelController.reuseMood(entry);
        if (all)
            statusBarController.status(I18nManager.getText("status.entry.reused"));
        inputBar.focusPrompt();
    }

    /**
     * Edits an image, the edit pencil of the cards, the grid and the full view: attaches it and unfolds Generation like
     * a click on its title; an image that is not attached changes no group. The prompt is cleared for the
     * instructions of the edit.
     *
     * @param filePath the file of the image
     */
    void editImage(String filePath) {
        if (filePath == null || filePath.isEmpty())
            return;
        File imageFile = new File(filePath);
        if (!imageFile.exists())
            return;
        try {
            chatViewController.closePreview();
            if (generationPanelController.attach(imageFile))
                panelGroups.open("generation");
            inputBar.clearPrompt();
            inputBar.focusPrompt();
            statusBarController.status(I18nManager.getText("status.image.edit.attached"));
        } catch (Exception e) {
            logger.error("Failed to initiate edit mode for image: {}", filePath, e);
            statusBarController.status(java.text.MessageFormat.format(
                    I18nManager.getText("status.error.image.edit"), e.getMessage()));
        }
    }

    /**
     * Opens a chat as if chosen in the sidebar, for the tests of the window.
     *
     * @param chat the chat
     */
    void chooseChat(Chat chat) {
        librarySidebarController.chooseChat(chat);
    }

    /** The groups Mood and Frame, for the tests of the window. */
    MoodFramePanelController moodFramePanel() {
        return moodFramePanelController;
    }

    /** The group Generation, for the tests of the window. */
    GenerationPanelController generationPanel() {
        return generationPanelController;
    }

    /** The chat area, for the tests of the window. */
    ChatViewController chatView() {
        return chatViewController;
    }

    /**
     * Shows the splash screen again as an info window; it closes with Esc or a click.
     */
    @FXML
    private void onInfoClicked() {
        art.moodframe.ui.SplashScreen.showInfo(infoButton.getScene().getWindow());
    }

    /**
     * Looks for a newer version in the background, on every system, and shows the hint behind the version if there is
     * one.
     */
    private void setupUpdateCheck() {
        if (updates != null)
            updates.checkAtStart().thenAccept((found) -> found.ifPresent((result) ->
                    Platform.runLater(() -> showUpdateHint(result))));
    }

    /*  Shows the hint of a newer version behind the version, its text and its symbol, or hides it for null  */
    private void showUpdateHint(art.moodframe.service.UpdateCheckService.Result result) {
        update = result;
        updateHintLabel.setVisible(result != null);
        updateHintLabel.setManaged(result != null);
        updateHintLabel.setText(I18nManager.getText("update.hint"));
        updateButton.setVisible(result != null);
        updateButton.setManaged(result != null);
        updateTooltip.setText(result != null ? I18nManager.getText("update.newer", result.available()) : "");
    }

    /**
     * Opens the steps of the update; hiding the hint for this version removes it from the header.
     */
    @FXML
    private void onUpdateClicked() {
        if (art.moodframe.ui.dialog.UpdateDialog.showAndWait(updateButton.getScene().getWindow(), updates,
                CompletableFuture.completedFuture(update)))
            showUpdateHint(null);
    }

    /**
     * Opens the settings; when they close, they are applied and the window follows them: theme, keys, models.
     */
    @FXML
    private void onSettingsClicked() {
        statusBarController.status(I18nManager.getText("status.opening.settings"));

        try {
            // Load the Settings Dialog FXML
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fxml/SettingsDialog.fxml"));
            loader.setResources(I18nManager.getCurrentResourceBundle());
            DialogPane dialogPane = loader.load();

            // Get the controller
            SettingsController settingsController = loader.getController();

            // Load settings dialog CSS for header styling
            try {
                String settingsCss = getClass().getResource("/css/settings-dialog.css").toExternalForm();
                dialogPane.getStylesheets().add(settingsCss);
            } catch (Exception ex) {
                logger.warn("Failed to load settings dialog CSS", ex);
            }

            // Create and configure the dialog
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle(I18nManager.getText("settings.window.title"));
            dialog.initOwner(settingsButton.getScene().getWindow());

            // Make dialog resizable for user customization
            dialog.setResizable(true);

            // Set minimum and maximum size on the actual window (Stage)
            dialog.setOnShown(e -> {
                Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                stage.setMinWidth(730);
                stage.setMinHeight(810);
                stage.setMaxWidth(1400);
                stage.setMaxHeight(1000);

                // Apply title-3 style class to header label
                Platform.runLater(() -> {
                    javafx.scene.Node header = dialogPane.lookup(".header-panel");
                    if (header instanceof javafx.scene.layout.Region headerRegion) {
                        javafx.scene.Node headerText = headerRegion.lookup(".label");
                        if (headerText instanceof javafx.scene.control.Label label) {
                            if (!label.getStyleClass().contains("title-3")) {
                                label.getStyleClass().add("title-3");
                            }
                        }
                    }
                });
            });

            // Reset status to ready after dialog is shown
            statusBarController.ready();

            // Show the dialog and handle the result
            dialog.showAndWait();
            cornerCloud.update();

            // Auto-save settings when dialog is closed (regardless of how it was closed)
            // Always apply settings and refresh UI after dialog closes
            settingsController.applySettings();

            // Refresh API key status in case it changed
            refreshApiKeyStatus();

            // Refresh available models based on enabled providers
            generationPanelController.refreshModels();

            statusBarController.ready();

        } catch (IOException e) {
            logger.error("Failed to load Settings Dialog", e);
            statusBarController.status(I18nManager.getText("status.error.settings.failed"));

            // Show error alert
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.getButtonTypes().setAll(DialogButtons.ok());
            alert.setTitle("Settings Error");
            alert.setHeaderText("Could not open settings dialog");
            alert.setContentText("Error: " + e.getMessage());
            alert.initOwner(settingsButton.getScene().getWindow());
            alert.showAndWait();

        } catch (Exception e) {
            logger.error("Unexpected error opening Settings Dialog", e);
            statusBarController.status(I18nManager.getText("status.error.settings.occurred"));
        }
    }

    @Override
    public void onLanguageChanged() {
        try {
            chatViewController.updateTexts();

            /*  the hint of the empty prompt follows the status of the keys in the status bar until the next change  */
            GenerationModel model = generationPanelController.selectedModel();
            inputBar.showPlaceholder(InputBarController.placeholder(statusBarController.hasValidKeys(),
                    librarySidebarController.hasActiveChat(), generationPanelController.hasAttachment(),
                    model != null ? model.getProvider() : null));
            inputBar.showCount(buildFinalPrompt(), generationPanelController.promptLimit());
            statusBarController.updateTexts();
            moodFramePanelController.updateTexts();
            librarySidebarController.updateTexts();
            librarySidebarController.refreshGroups();
            infoTooltip.setText(I18nManager.getText("main.info.tooltip"));
            settingsTooltip.setText(I18nManager.getText("main.settings.tooltip"));
            if (update != null)
                showUpdateHint(update);
            inputBar.updateTexts();
            inputBar.updateBrainstorm(config, librarySidebarController.hasActiveChat());

            /*  last, as the localized names of the models may choose another item  */
            generationPanelController.updateTexts();
        } catch (Exception e) {
            logger.error("Failed to update MainWindow UI texts", e);
        }
    }
}
