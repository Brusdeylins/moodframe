package art.moodframe.ui.dialog;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.service.GeminiApiService;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import art.moodframe.util.YouTubeUrlValidator;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Modal dialog for YouTube video analysis using Google Gemini API.
 *
 * <p>This dialog allows users to:</p>
 * <ul>
 * <li>Enter a YouTube URL for analysis</li>
 * <li>Preview the video thumbnail</li>
 * <li>Optionally specify a time range (MM:SS format)</li>
 * <li>Provide an optional analysis prompt</li>
 * <li>View and edit the analysis result</li>
 * <li>Set the result as a prompt attachment for image generation</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong></p>
 * <ul>
 * <li>Gemini API key must be configured</li>
 * <li>Only public YouTube videos are supported</li>
 * <li>Free tier limit: 8 hours of YouTube video per day</li>
 * </ul>
 *
 * @since 1.3.0
 */
public class YouTubeAnalysisDialog extends Stage implements I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeAnalysisDialog.class);

    // Dialog dimensions - taller for better result area
    private static final double DIALOG_WIDTH = 700;
    private static final double DIALOG_HEIGHT = 800;

    // Thumbnail dimensions for YouTube preview
    private static final double THUMBNAIL_WIDTH = 320;
    private static final double THUMBNAIL_HEIGHT = 180;

    // UI Components
    private TextField urlField;
    private Label urlValidationLabel;
    private StackPane thumbnailContainer;
    private TextField startTimeField;
    private TextField endTimeField;
    private TextArea promptTextArea;
    private Button analyzeButton;
    private TextArea resultTextArea;
    private ProgressIndicator progressIndicator;
    private Button cancelButton;
    private Button setAttachmentButton;
    private Label hintLabel;

    // Labels for i18n
    private Label urlLabel;
    private Label timeRangeLabel;
    private Label promptLabel;
    private Label resultLabel;

    // State
    private String currentVideoId;
    private boolean isAnalyzing = false;
    private boolean geminiAvailable = false;
    private boolean directMode = false; // false = Beschreiben (Analyse), true = Direkt verwenden

    // Mode UI (direct video-to-image)
    private ToggleGroup modeGroup;
    private ToggleButton analyzeModeButton;
    private ToggleButton directModeButton;
    private VBox promptSection;
    private VBox analyzeButtonBox;
    private Separator separator;
    private VBox resultSection;

    // Callbacks for result
    private final Consumer<String> onResultAccepted;
    private final Consumer<VideoDirectResult> onDirectAccepted;

    // Service reference
    private GeminiApiService geminiService;
    private ApplicationConfig config;

    /**
     * Result of the "Direkt verwenden" mode: a YouTube video to be passed directly
     * into gemini-3.1-flash-image as multimodal context. The user writes the actual
     * image prompt in the main prompt field.
     *
     * @param url the YouTube URL
     * @param startSeconds start offset in seconds (0 = from start)
     * @param endSeconds end offset in seconds (0 = until end)
     */
    public record VideoDirectResult(String url, int startSeconds, int endSeconds) {}

    /**
     * Creates a new YouTubeAnalysisDialog.
     *
     * @param owner the owner window
     * @param onResultAccepted callback when user clicks "Als Promptanhang setzen"
     */
    public YouTubeAnalysisDialog(Stage owner, Consumer<String> onResultAccepted) {
        this(owner, onResultAccepted, null);
    }

    /**
     * Creates a new YouTubeAnalysisDialog with both analyze and direct-video callbacks.
     *
     * @param owner the owner window
     * @param onResultAccepted callback for the "Beschreiben (Analyse)" mode (text)
     * @param onDirectAccepted callback for the "Direkt verwenden" mode (video source)
     */
    public YouTubeAnalysisDialog(Stage owner, Consumer<String> onResultAccepted,
                                 Consumer<VideoDirectResult> onDirectAccepted) {
        this.onResultAccepted = onResultAccepted;
        this.onDirectAccepted = onDirectAccepted;

        // Initialize stage
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        initStyle(StageStyle.DECORATED);

        // Get Gemini service and config
        geminiService = ServiceFactory.getInstance().getService(GeminiApiService.class);
        config = ServiceFactory.getInstance().getService(ApplicationConfig.class);

        // Check if Gemini API is available (key present AND provider enabled)
        geminiAvailable = config != null
            && config.hasGeminiApiKey()
            && config.isGeminiProviderEnabled();

        // Build UI
        VBox root = createContent();
        Scene scene = new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
        setScene(scene);

        // Register for i18n updates
        I18nManager.addChangeListener(this);

        // Update all texts
        updateTexts();

        // Set resize constraints
        setMinWidth(600);
        setMinHeight(700);
        setMaxWidth(900);
        setMaxHeight(1000);

        // Center on owner
        if (owner != null) {
            setX(owner.getX() + (owner.getWidth() - DIALOG_WIDTH) / 2);
            setY(owner.getY() + (owner.getHeight() - DIALOG_HEIGHT) / 2);
        }

        // Cleanup on close
        setOnHidden(e -> {
            I18nManager.removeChangeListener(this);
        });

        logger.debug("YouTubeAnalysisDialog created");
    }

    /**
     * Creates the main content layout.
     */
    private VBox createContent() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setFillWidth(true);

        // URL input section
        VBox urlSection = createUrlSection();

        // Preview and time range section (side by side)
        HBox previewAndTimeSection = createPreviewAndTimeSection();

        // Mode selector (Beschreiben vs. Direkt verwenden)
        HBox modeSection = createModeSection();

        // Analysis prompt section (only relevant in analyze mode)
        promptSection = createPromptSection();

        // Analyze button
        analyzeButton = new Button();
        analyzeButton.setStyle("-fx-font-size: 14px;");
        analyzeButton.setPrefWidth(250);
        analyzeButton.setDisable(true);
        analyzeButton.setOnAction(e -> performAnalysis());

        // Info label about Gemini API usage
        Label apiInfoLabel = new Label();
        apiInfoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
        apiInfoLabel.setText(I18nManager.getText("youtube.dialog.api.info", "Verwendet Gemini API (Free Tier verfügbar)"));

        analyzeButtonBox = new VBox(5);
        analyzeButtonBox.setAlignment(Pos.CENTER);
        analyzeButtonBox.getChildren().addAll(analyzeButton, apiInfoLabel);

        // Separator
        separator = new Separator();

        // Result section
        resultSection = createResultSection();
        VBox.setVgrow(resultSection, Priority.ALWAYS);

        // Button bar
        HBox buttonBar = createButtonBar();

        // Add all sections
        root.getChildren().addAll(
            modeSection,
            urlSection,
            previewAndTimeSection,
            promptSection,
            analyzeButtonBox,
            separator,
            resultSection,
            buttonBar
        );

        // Apply initial mode (Beschreiben)
        applyMode(false);

        return root;
    }

    /**
     * Creates the mode selector: "Beschreiben (Analyse)" vs. "Direkt verwenden".
     */
    private HBox createModeSection() {
        modeGroup = new ToggleGroup();

        analyzeModeButton = new ToggleButton(
            I18nManager.getText("youtube.dialog.mode.analyze", "Beschreiben (Analyse)"));
        directModeButton = new ToggleButton(
            I18nManager.getText("youtube.dialog.mode.direct", "Direkt verwenden (Nano Banana 2)"));
        analyzeModeButton.setToggleGroup(modeGroup);
        directModeButton.setToggleGroup(modeGroup);
        analyzeModeButton.setSelected(true);
        analyzeModeButton.setMaxWidth(Double.MAX_VALUE);
        directModeButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(analyzeModeButton, Priority.ALWAYS);
        HBox.setHgrow(directModeButton, Priority.ALWAYS);

        // Direct mode requires the direct callback; disable it if not provided.
        if (onDirectAccepted == null) {
            directModeButton.setDisable(true);
        }

        // Prevent deselecting both: always keep one selected.
        modeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) {
                oldT.setSelected(true);
                return;
            }
            applyMode(newT == directModeButton);
        });

        HBox section = new HBox(8, analyzeModeButton, directModeButton);
        section.setAlignment(Pos.CENTER);
        return section;
    }

    /**
     * Switches between analyze and direct mode, toggling section visibility and
     * relabeling the accept control. In direct mode only the video source (URL +
     * time range) is captured; the user writes the prompt in the main prompt field.
     *
     * @param direct true for "Direkt verwenden", false for "Beschreiben (Analyse)"
     */
    private void applyMode(boolean direct) {
        this.directMode = direct;

        // Direct mode = video source only: hide the analysis prompt, analyze button and result.
        setNodeVisible(promptSection, !direct);
        setNodeVisible(analyzeButtonBox, !direct);
        setNodeVisible(separator, !direct);
        setNodeVisible(resultSection, !direct);

        if (setAttachmentButton != null) {
            setAttachmentButton.setText(direct
                ? I18nManager.getText("youtube.dialog.button.useDirect", "Übernehmen")
                : I18nManager.getText("youtube.dialog.button.setContext", "Als Promptanhang setzen"));
            updateAcceptButtonState();
        }
    }

    /**
     * Enables/disables the accept button depending on the current mode and inputs.
     */
    private void updateAcceptButtonState() {
        if (setAttachmentButton == null) {
            return;
        }
        if (directMode) {
            // Direct mode: ready as soon as a valid video URL is present.
            setAttachmentButton.setDisable(currentVideoId == null);
        } else {
            // Analyze mode: ready when there is result text.
            boolean hasResult = resultTextArea != null
                && resultTextArea.getText() != null
                && !resultTextArea.getText().trim().isEmpty();
            setAttachmentButton.setDisable(!hasResult);
        }
    }

    private void setNodeVisible(javafx.scene.Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    /**
     * Creates the URL input section.
     */
    private VBox createUrlSection() {
        VBox section = new VBox(5);

        urlLabel = new Label();

        HBox urlRow = new HBox(10);
        urlRow.setAlignment(Pos.CENTER_LEFT);

        urlField = new TextField();
        urlField.setPrefWidth(500);
        HBox.setHgrow(urlField, Priority.ALWAYS);
        urlField.textProperty().addListener((obs, oldVal, newVal) -> validateUrl(newVal));

        // Help button with tooltip
        Button helpButton = new Button("?");
        helpButton.setStyle("-fx-background-radius: 50%; -fx-min-width: 25px; -fx-min-height: 25px;");
        Tooltip helpTooltip = new Tooltip("youtube.com/watch?v=...\nyoutu.be/...\nyoutube.com/shorts/...");
        Tooltip.install(helpButton, helpTooltip);

        urlRow.getChildren().addAll(urlField, helpButton);

        urlValidationLabel = new Label();
        urlValidationLabel.setStyle("-fx-font-size: 11px;");

        // Disable URL input if Gemini is not available
        if (!geminiAvailable) {
            urlField.setDisable(true);
            urlField.setPromptText(I18nManager.getText("youtube.dialog.url.disabled", "Gemini API nicht verfügbar"));
            helpButton.setDisable(true);
        }

        section.getChildren().addAll(urlLabel, urlRow, urlValidationLabel);

        return section;
    }

    /**
     * Creates the preview thumbnail and time range section side by side.
     */
    private HBox createPreviewAndTimeSection() {
        HBox section = new HBox(20);
        section.setAlignment(Pos.CENTER_LEFT);

        // Left: Thumbnail container with black background
        thumbnailContainer = createThumbnailContainer();

        // Right: Time range inputs
        VBox timeSection = createTimeRangeSection();
        HBox.setHgrow(timeSection, Priority.ALWAYS);

        section.getChildren().addAll(thumbnailContainer, timeSection);

        return section;
    }

    /**
     * Creates the thumbnail container for YouTube video preview.
     * Uses a static thumbnail image (YouTube blocks embedding in desktop apps).
     */
    private StackPane createThumbnailContainer() {
        StackPane container = new StackPane();
        container.setPrefSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        container.setMinSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        container.setMaxSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        // Solid black background
        container.setStyle("-fx-background-color: #000000; -fx-border-color: #333; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");

        // Placeholder label with play icon
        Label placeholder = new Label("▶");
        placeholder.setStyle("-fx-font-size: 48px; -fx-text-fill: #444;");

        container.getChildren().add(placeholder);

        return container;
    }

    /**
     * Creates the time range input section.
     */
    private VBox createTimeRangeSection() {
        VBox section = new VBox(10);
        section.setAlignment(Pos.TOP_LEFT);

        timeRangeLabel = new Label();
        timeRangeLabel.setStyle("-fx-font-weight: bold;");

        // Time range hint
        Label timeHint = new Label();
        timeHint.setText(I18nManager.getText("youtube.dialog.timerange.hint",
            "Optional: Zeitbereich im Format MM:SS oder HH:MM:SS"));
        timeHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
        timeHint.setWrapText(true);

        // Start time input
        HBox startRow = new HBox(10);
        startRow.setAlignment(Pos.CENTER_LEFT);
        Label startLabel = new Label("Start:");
        startLabel.setMinWidth(50);
        startTimeField = new TextField();
        startTimeField.setPromptText("00:00");
        startTimeField.setPrefWidth(150);
        startTimeField.setStyle("-fx-font-family: monospace;");
        startRow.getChildren().addAll(startLabel, startTimeField);

        // End time input
        HBox endRow = new HBox(10);
        endRow.setAlignment(Pos.CENTER_LEFT);
        Label endLabel = new Label("Ende:");
        endLabel.setMinWidth(50);
        endTimeField = new TextField();
        endTimeField.setPromptText(I18nManager.getText("youtube.dialog.timerange.end.placeholder", "leer = Ende"));
        endTimeField.setPrefWidth(150);
        endTimeField.setStyle("-fx-font-family: monospace;");
        endRow.getChildren().addAll(endLabel, endTimeField);

        // Example label
        Label exampleLabel = new Label();
        exampleLabel.setText(I18nManager.getText("youtube.dialog.timerange.example",
            "z.B. 01:30 bis 05:00"));
        exampleLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        // Disable time fields if Gemini is not available
        if (!geminiAvailable) {
            startTimeField.setDisable(true);
            endTimeField.setDisable(true);
        }

        section.getChildren().addAll(timeRangeLabel, timeHint, startRow, endRow, exampleLabel);

        return section;
    }

    /**
     * Creates the analysis prompt section.
     */
    private VBox createPromptSection() {
        VBox section = new VBox(5);

        promptLabel = new Label();

        promptTextArea = new TextArea();
        promptTextArea.setPrefRowCount(5);
        promptTextArea.setWrapText(true);

        // Disable prompt input if Gemini is not available
        if (!geminiAvailable) {
            promptTextArea.setDisable(true);
        }

        section.getChildren().addAll(promptLabel, promptTextArea);

        return section;
    }

    /**
     * Creates the result section.
     */
    private VBox createResultSection() {
        VBox section = new VBox(5);

        HBox resultHeader = new HBox(10);
        resultHeader.setAlignment(Pos.CENTER_LEFT);

        resultLabel = new Label();

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        resultHeader.getChildren().addAll(resultLabel, progressIndicator);

        resultTextArea = new TextArea();
        resultTextArea.setPrefRowCount(12);
        resultTextArea.setWrapText(true);
        resultTextArea.setEditable(true);
        VBox.setVgrow(resultTextArea, Priority.ALWAYS);

        // Enable "Set Attachment" button when user manually types text
        // But NOT during analysis (when isAnalyzing is true)
        resultTextArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (setAttachmentButton != null && !isAnalyzing && !directMode) {
                boolean hasContent = newVal != null && !newVal.trim().isEmpty();
                setAttachmentButton.setDisable(!hasContent);
            }
        });

        section.getChildren().addAll(resultHeader, resultTextArea);
        VBox.setVgrow(section, Priority.ALWAYS);

        return section;
    }

    /**
     * Creates the bottom button bar.
     */
    private HBox createButtonBar() {
        HBox buttonBar = new HBox(15);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        // Hint label (left side)
        hintLabel = new Label();
        hintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(300);
        HBox.setHgrow(hintLabel, Priority.ALWAYS);

        cancelButton = new Button();
        cancelButton.setOnAction(e -> close());

        setAttachmentButton = new Button();
        setAttachmentButton.setStyle("-fx-font-weight: bold;");
        setAttachmentButton.setDisable(true);
        setAttachmentButton.setOnAction(e -> acceptResult());

        buttonBar.getChildren().addAll(hintLabel, cancelButton, setAttachmentButton);

        return buttonBar;
    }

    /**
     * Validates the entered URL and updates UI accordingly.
     */
    private void validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            urlValidationLabel.setText("");
            urlValidationLabel.setStyle("-fx-text-fill: #888;");
            analyzeButton.setDisable(true);
            hideThumbnail();
            currentVideoId = null;
            updateAcceptButtonState();
            return;
        }

        String trimmedUrl = url.trim();

        if (YouTubeUrlValidator.isValidYouTubeUrl(trimmedUrl)) {
            Optional<String> videoId = YouTubeUrlValidator.extractVideoId(trimmedUrl);

            if (videoId.isPresent()) {
                String newVideoId = videoId.get();

                // Check if this is a different video than before
                boolean isNewVideo = !newVideoId.equals(currentVideoId);
                currentVideoId = newVideoId;

                urlValidationLabel.setText("✓ " + I18nManager.getText("youtube.dialog.url.valid", "Gültige YouTube URL"));
                urlValidationLabel.setStyle("-fx-text-fill: #4CAF50;");
                // Only enable analyze button if Gemini is available
                analyzeButton.setDisable(!geminiAvailable);

                // Load video thumbnail
                loadThumbnail(currentVideoId);

                // Clear time fields for new video
                if (isNewVideo) {
                    startTimeField.clear();
                    endTimeField.clear();
                }

                updateAcceptButtonState();
                logger.debug("Valid YouTube URL detected, video ID: {}", currentVideoId);
            }
        } else {
            urlValidationLabel.setText("✗ " + I18nManager.getText("youtube.dialog.error.invalid.url", "Ungültige YouTube URL"));
            urlValidationLabel.setStyle("-fx-text-fill: #f44336;");
            analyzeButton.setDisable(true);
            hideThumbnail();
            currentVideoId = null;
            updateAcceptButtonState();
        }
    }

    /**
     * Loads the YouTube video thumbnail.
     */
    private void loadThumbnail(String videoId) {
        Platform.runLater(() -> {
            try {
                // Get thumbnail URL
                String thumbnailUrl = YouTubeUrlValidator.getThumbnailUrl(videoId);

                // Create ImageView for thumbnail (2px smaller to leave 1px border visible on each side)
                javafx.scene.image.Image thumbnailImage = new javafx.scene.image.Image(
                    thumbnailUrl, THUMBNAIL_WIDTH - 2, THUMBNAIL_HEIGHT - 2, true, true, true);
                javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(thumbnailImage);
                imageView.setFitWidth(THUMBNAIL_WIDTH - 2);
                imageView.setFitHeight(THUMBNAIL_HEIGHT - 2);
                imageView.setPreserveRatio(true);

                // Play button overlay
                Label playOverlay = new Label("▶");
                playOverlay.setStyle("-fx-font-size: 48px; -fx-text-fill: rgba(255,255,255,0.8); -fx-effect: dropshadow(gaussian, black, 10, 0.5, 0, 0);");

                // Clear container and add thumbnail with overlay
                thumbnailContainer.getChildren().clear();
                thumbnailContainer.getChildren().addAll(imageView, playOverlay);

                logger.debug("Loading YouTube thumbnail: {}", thumbnailUrl);

            } catch (Exception e) {
                logger.error("Failed to load YouTube thumbnail", e);
                showThumbnailError();
            }
        });
    }

    /**
     * Hides the thumbnail and shows placeholder.
     */
    private void hideThumbnail() {
        Platform.runLater(() -> {
            thumbnailContainer.getChildren().clear();
            Label placeholder = new Label("▶");
            placeholder.setStyle("-fx-font-size: 48px; -fx-text-fill: #444;");
            thumbnailContainer.getChildren().add(placeholder);
        });
    }

    /**
     * Shows an error message in the thumbnail container.
     */
    private void showThumbnailError() {
        Platform.runLater(() -> {
            thumbnailContainer.getChildren().clear();
            Label errorLabel = new Label(I18nManager.getText("youtube.dialog.error.webview", "Vorschau nicht verfügbar"));
            errorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
            thumbnailContainer.getChildren().add(errorLabel);
        });
    }

    /**
     * Parses time from text field in MM:SS or HH:MM:SS format.
     * @return seconds, or 0 if empty/invalid
     */
    private int parseTimeField(TextField field) {
        String text = field.getText();
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return YouTubeUrlValidator.parseDuration(text.trim());
    }

    /**
     * Performs the video analysis using Gemini API.
     */
    private void performAnalysis() {
        if (currentVideoId == null || isAnalyzing) {
            return;
        }

        if (geminiService == null || !geminiService.isConfigured()) {
            showError(I18nManager.getText("youtube.dialog.error.no.api.key", "Gemini API-Key nicht konfiguriert"));
            return;
        }

        art.moodframe.config.ApplicationConfig appConfig = ServiceFactory.getInstance()
                .getService(art.moodframe.config.ApplicationConfig.class);
        if (appConfig != null && art.moodframe.service.ProviderPolicy.isOpenAiOnly(appConfig)) {
            showError(I18nManager.getText("youtube.dialog.error.openai.only"));
            return;
        }

        isAnalyzing = true;
        setAnalyzingState(true);

        // Get parameters
        String youtubeUrl = urlField.getText().trim();
        int startSeconds = parseTimeField(startTimeField);
        int endSeconds = parseTimeField(endTimeField);
        String analysisPrompt = promptTextArea.getText().trim();

        logger.info("Starting YouTube video analysis: {} ({}s - {}s)", youtubeUrl, startSeconds, endSeconds);

        // Call Gemini API
        geminiService.analyzeYouTubeVideo(youtubeUrl, startSeconds, endSeconds, analysisPrompt)
            .thenAccept(result -> {
                Platform.runLater(() -> {
                    resultTextArea.setText(result);
                    setAttachmentButton.setDisable(false);
                    setAnalyzingState(false);
                    isAnalyzing = false;
                    logger.info("Video analysis completed successfully");
                });
            })
            .exceptionally(throwable -> {
                Platform.runLater(() -> {
                    String errorMsg = throwable.getCause() != null
                        ? throwable.getCause().getMessage()
                        : throwable.getMessage();
                    showError(errorMsg);
                    setAnalyzingState(false);
                    isAnalyzing = false;
                    logger.error("Video analysis failed", throwable);
                });
                return null;
            });
    }

    /**
     * Updates UI to show analyzing state.
     */
    private void setAnalyzingState(boolean analyzing) {
        progressIndicator.setVisible(analyzing);
        analyzeButton.setDisable(analyzing);
        urlField.setDisable(analyzing);
        startTimeField.setDisable(analyzing);
        endTimeField.setDisable(analyzing);
        promptTextArea.setDisable(analyzing);

        if (analyzing) {
            // Disable the attachment button during analysis
            setAttachmentButton.setDisable(true);
            resultTextArea.setText(I18nManager.getText("youtube.dialog.status.analyzing", "Video wird analysiert..."));
            resultTextArea.setDisable(true);
        } else {
            resultTextArea.setDisable(false);
            // Note: setAttachmentButton will be enabled by the result callback when analysis completes
        }
    }

    /**
     * Shows an error message in the result area.
     */
    private void showError(String message) {
        resultTextArea.setText(I18nManager.getText("youtube.dialog.error.prefix", "Fehler") + ": " + message);
        resultTextArea.setStyle("-fx-text-fill: #f44336;");
    }

    /**
     * Accepts the result and passes it to the callback.
     */
    private void acceptResult() {
        if (directMode) {
            if (currentVideoId == null || onDirectAccepted == null) {
                return;
            }
            String youtubeUrl = urlField.getText().trim();
            int startSeconds = parseTimeField(startTimeField);
            int endSeconds = parseTimeField(endTimeField);
            onDirectAccepted.accept(new VideoDirectResult(youtubeUrl, startSeconds, endSeconds));
            logger.info("Video direct source accepted: {} ({}s - {}s)", youtubeUrl, startSeconds, endSeconds);
            close();
            return;
        }

        String result = resultTextArea.getText();

        if (result != null && !result.trim().isEmpty() && onResultAccepted != null) {
            onResultAccepted.accept(result.trim());
            logger.info("Video analysis result accepted as prompt attachment");
        }

        close();
    }

    /**
     * Updates all text elements with localized strings.
     */
    private void updateTexts() {
        // Window title
        setTitle(I18nManager.getText("youtube.dialog.title", "YouTube Video-Analyse"));

        // URL section
        if (urlLabel != null) {
            urlLabel.setText(I18nManager.getText("youtube.dialog.url.label", "YouTube URL:"));
        }
        if (urlField != null) {
            urlField.setPromptText(I18nManager.getText("youtube.dialog.url.placeholder",
                "https://youtube.com/watch?v=... oder https://youtu.be/..."));
        }

        // Time range section
        if (timeRangeLabel != null) {
            timeRangeLabel.setText(I18nManager.getText("youtube.dialog.timerange.label", "Zeitbereich (optional):"));
        }

        // Prompt section
        if (promptLabel != null) {
            promptLabel.setText(I18nManager.getText("youtube.dialog.prompt.label", "Analyse-Anweisung (optional):"));
        }
        if (promptTextArea != null) {
            promptTextArea.setPromptText(I18nManager.getText("youtube.dialog.prompt.placeholder",
                "z.B. \"Extrahiere die 5 wichtigsten Punkte\""));
        }

        // Analyze button
        if (analyzeButton != null) {
            analyzeButton.setText(I18nManager.getText("youtube.dialog.button.analyze", "Video analysieren"));
        }

        // Result section
        if (resultLabel != null) {
            resultLabel.setText(I18nManager.getText("youtube.dialog.result.label", "Analyse-Ergebnis:"));
        }

        // Buttons
        if (cancelButton != null) {
            cancelButton.setText(I18nManager.getText("youtube.dialog.button.cancel", "Abbrechen"));
        }
        if (setAttachmentButton != null) {
            setAttachmentButton.setText(I18nManager.getText("youtube.dialog.button.setContext", "Als Promptanhang setzen"));
        }

        // Hint
        if (hintLabel != null) {
            hintLabel.setText(I18nManager.getText("youtube.dialog.hint",
                "Nur öffentliche YouTube-Videos. Limit: 8h/Tag."));
        }

        // Mode selector
        if (analyzeModeButton != null) {
            analyzeModeButton.setText(I18nManager.getText("youtube.dialog.mode.analyze", "Beschreiben (Analyse)"));
        }
        if (directModeButton != null) {
            directModeButton.setText(I18nManager.getText("youtube.dialog.mode.direct", "Direkt verwenden (Nano Banana 2)"));
        }

        // Re-apply mode so its labels (prompt/accept button) reflect current language
        applyMode(directMode);
    }

    @Override
    public void onLanguageChanged() {
        Platform.runLater(this::updateTexts);
    }


    /**
     * Sets the initial analysis result (for editing existing attachments).
     *
     * @param result the existing analysis result
     */
    public void setInitialResult(String result) {
        if (resultTextArea != null && result != null) {
            resultTextArea.setText(result);
            setAttachmentButton.setDisable(false);
        }
    }
}
