package art.moodframe.ui.component;

import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Container section for prompt context attachments in the sidebar.
 *
 * <p>This section displays above the image attachment panel and contains:</p>
 * <ul>
 * <li>A title label "Prompt-Kontext"</li>
 * <li>PromptContextCard(s) for attached contexts (e.g., YouTube video analysis)</li>
 * <li>An "Add" button to open the YouTube analysis dialog</li>
 * </ul>
 *
 * <p><strong>Visibility:</strong> This section is only visible when a Gemini API key
 * is configured in the application settings.</p>
 *
 * @since 1.3.0
 */
public class PromptContextSection extends VBox implements I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(PromptContextSection.class);

    // UI Components
    private final Label titleLabel;
    private final VBox cardsContainer;
    private final Button addYouTubeButton;

    // Current card (only one YouTube context at a time)
    private PromptContextCard currentCard;

    // Callbacks
    private Runnable onAddYouTubeClicked;
    private Consumer<String> onEditYouTubeClicked;
    private Runnable onDeleteClicked;

    /**
     * Creates a new PromptContextSection.
     */
    public PromptContextSection() {
        // Setup VBox
        setSpacing(8);
        setPadding(new Insets(0, 0, 10, 0));

        // Title (same style as "Image Attachment" label)
        titleLabel = new Label();
        titleLabel.getStyleClass().add("text-bold");

        // Cards container
        cardsContainer = new VBox(5);

        // Add YouTube button
        addYouTubeButton = new Button();
        addYouTubeButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-border-color: -color-border-default; " +
            "-fx-border-style: dashed; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-padding: 8 12; " +
            "-fx-cursor: hand; " +
            "-fx-font-size: 12px;"
        );
        addYouTubeButton.setMaxWidth(Double.MAX_VALUE);
        addYouTubeButton.setAlignment(Pos.CENTER_LEFT);
        addYouTubeButton.setOnAction(e -> {
            if (onAddYouTubeClicked != null) {
                onAddYouTubeClicked.run();
            }
        });

        // Hover effect for button
        addYouTubeButton.setOnMouseEntered(e -> addYouTubeButton.setStyle(
            "-fx-background-color: -color-bg-subtle; " +
            "-fx-border-color: -color-accent-emphasis; " +
            "-fx-border-style: dashed; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-padding: 8 12; " +
            "-fx-cursor: hand; " +
            "-fx-font-size: 12px;"
        ));
        addYouTubeButton.setOnMouseExited(e -> addYouTubeButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-border-color: -color-border-default; " +
            "-fx-border-style: dashed; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-padding: 8 12; " +
            "-fx-cursor: hand; " +
            "-fx-font-size: 12px;"
        ));

        // Add components
        getChildren().addAll(titleLabel, cardsContainer, addYouTubeButton);

        // Register for i18n updates
        I18nManager.addChangeListener(this);

        // Initial text update
        updateTexts();

        // Initially hidden (shown when Gemini API key is available)
        setVisible(false);
        setManaged(false);

        logger.debug("PromptContextSection created");
    }

    /**
     * Updates all text elements with localized strings.
     */
    private void updateTexts() {
        titleLabel.setText(I18nManager.getText("prompt.context.title", "YouTube Content"));
        addYouTubeButton.setText(I18nManager.getText("prompt.context.add.youtube", "+ YouTube-Video analysieren"));
    }

    @Override
    public void onLanguageChanged() {
        Platform.runLater(this::updateTexts);
    }

    /**
     * Sets whether the section is visible.
     * Call this based on whether Gemini API key is configured.
     *
     * @param visible true to show, false to hide
     */
    public void setSectionVisible(boolean visible) {
        setVisible(visible);
        setManaged(visible);
    }

    /**
     * Sets the YouTube video context.
     *
     * @param title short title for the card
     * @param content full analysis content
     */
    public void setYouTubeContext(String title, String content) {
        // Remove existing card if any
        clearContext();

        // Create new card
        currentCard = new PromptContextCard(
            PromptContextCard.ContextType.YOUTUBE_VIDEO,
            title,
            content
        );

        // Setup callbacks
        currentCard.setOnEditRequest(editContent -> {
            if (onEditYouTubeClicked != null) {
                onEditYouTubeClicked.accept(editContent);
            }
        });

        currentCard.setOnDeleteRequest(() -> {
            clearContext();
            if (onDeleteClicked != null) {
                onDeleteClicked.run();
            }
        });

        // Add to container
        cardsContainer.getChildren().add(currentCard);

        // Hide add button when context is set
        addYouTubeButton.setVisible(false);
        addYouTubeButton.setManaged(false);

        logger.debug("YouTube context set: {}", title);
    }

    /**
     * Sets a direct video-to-image context card (the video itself is sent to
     * gemini-3.1-flash-image). Unlike the analysis card this is informational with
     * a delete action only.
     *
     * @param title short title for the card (e.g. the video URL or task name)
     * @param content full detail shown in the tooltip (e.g. URL + time range)
     */
    public void setDirectVideoContext(String title, String content) {
        // Remove existing card if any
        clearContext();

        currentCard = new PromptContextCard(
            PromptContextCard.ContextType.YOUTUBE_VIDEO_DIRECT,
            title,
            content
        );

        currentCard.setOnDeleteRequest(() -> {
            clearContext();
            if (onDeleteClicked != null) {
                onDeleteClicked.run();
            }
        });

        cardsContainer.getChildren().add(currentCard);

        // Hide add button when context is set
        addYouTubeButton.setVisible(false);
        addYouTubeButton.setManaged(false);

        logger.debug("Direct video context set: {}", title);
    }

    /**
     * Clears the current context.
     */
    public void clearContext() {
        if (currentCard != null) {
            currentCard.dispose();
            cardsContainer.getChildren().remove(currentCard);
            currentCard = null;
        }

        // Show add button again
        addYouTubeButton.setVisible(true);
        addYouTubeButton.setManaged(true);

        logger.debug("Context cleared");
    }

    /**
     * Sets the callback for "Add YouTube" button clicks.
     *
     * @param onAddYouTubeClicked callback
     */
    public void setOnAddYouTubeClicked(Runnable onAddYouTubeClicked) {
        this.onAddYouTubeClicked = onAddYouTubeClicked;
    }

    /**
     * Sets the callback for edit requests on YouTube cards.
     *
     * @param onEditYouTubeClicked callback that receives current content
     */
    public void setOnEditYouTubeClicked(Consumer<String> onEditYouTubeClicked) {
        this.onEditYouTubeClicked = onEditYouTubeClicked;
    }

    /**
     * Sets the callback for delete requests.
     *
     * @param onDeleteClicked callback
     */
    public void setOnDeleteClicked(Runnable onDeleteClicked) {
        this.onDeleteClicked = onDeleteClicked;
    }

    /**
     * Cleans up resources.
     */
    public void dispose() {
        I18nManager.removeChangeListener(this);
        clearContext();
        onAddYouTubeClicked = null;
        onEditYouTubeClicked = null;
        onDeleteClicked = null;
    }
}
