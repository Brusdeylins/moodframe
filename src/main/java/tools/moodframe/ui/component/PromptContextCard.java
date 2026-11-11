package tools.moodframe.ui.component;

import tools.moodframe.util.I18nChangeListener;
import tools.moodframe.util.I18nManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Compact card component for displaying a prompt context attachment in the sidebar.
 *
 * <p>This card shows a summary of attached context (e.g., YouTube video analysis)
 * that will be appended to the prompt during image generation.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 * <li>Icon indicating context type (e.g., play button for video)</li>
 * <li>Truncated description with tooltip for full text</li>
 * <li>Click to edit the context</li>
 * <li>Delete button to remove the attachment</li>
 * </ul>
 *
 * @since 1.3.0
 */
public class PromptContextCard extends HBox implements I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(PromptContextCard.class);

    // Display constraints
    private static final int MAX_DISPLAY_LENGTH = 25;

    /**
     * Type of context attached.
     */
    public enum ContextType {
        /** Video analysis: text description appended to the prompt (any model). */
        YOUTUBE_VIDEO,
        /** Direct video-to-image: the video itself is sent to gemini-3.1-flash-image. */
        YOUTUBE_VIDEO_DIRECT
    }

    // UI Components
    private final Label iconLabel;
    private final Label descriptionLabel;
    private final Button deleteButton;

    // State
    private final ContextType contextType;
    private String contextContent;
    private String contextTitle;

    // Callbacks
    private Consumer<String> onEditRequest;
    private Runnable onDeleteRequest;

    /**
     * Creates a new PromptContextCard.
     *
     * @param contextType the type of context (e.g., YOUTUBE_VIDEO)
     * @param title a short title for display (will be truncated)
     * @param content the full context content
     */
    public PromptContextCard(ContextType contextType, String title, String content) {
        this.contextType = contextType;
        this.contextTitle = title;
        this.contextContent = content;

        // Setup HBox
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setPadding(new Insets(8, 10, 8, 10));
        setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 6; -fx-cursor: hand;");

        // Icon
        iconLabel = new Label(getIconForType(contextType));
        iconLabel.setStyle("-fx-font-size: 14px;");

        // Description
        descriptionLabel = new Label();
        descriptionLabel.setStyle("-fx-font-size: 12px;");
        HBox.setHgrow(descriptionLabel, Priority.ALWAYS);
        updateDescriptionLabel();

        // Delete button
        deleteButton = new Button("\uD83D\uDDD1"); // Wastebasket emoji
        deleteButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-font-size: 12px; " +
            "-fx-padding: 2 6; " +
            "-fx-cursor: hand;"
        );
        deleteButton.setOnAction(e -> {
            e.consume(); // Don't trigger card click
            if (onDeleteRequest != null) {
                onDeleteRequest.run();
            }
        });

        // Add components
        getChildren().addAll(iconLabel, descriptionLabel, deleteButton);

        // Card click handler (for editing)
        setOnMouseClicked(e -> {
            if (onEditRequest != null) {
                onEditRequest.accept(contextContent);
            }
        });

        // Hover effect
        setOnMouseEntered(e -> setStyle(
            "-fx-background-color: -color-bg-default; -fx-background-radius: 6; -fx-cursor: hand;"
        ));
        setOnMouseExited(e -> setStyle(
            "-fx-background-color: -color-bg-subtle; -fx-background-radius: 6; -fx-cursor: hand;"
        ));

        // Register for i18n updates
        I18nManager.addChangeListener(this);

        // Initial text update
        updateTexts();

        logger.debug("PromptContextCard created: type={}, title={}", contextType, title);
    }

    /**
     * Gets the icon for a context type.
     */
    private String getIconForType(ContextType type) {
        return switch (type) {
            case YOUTUBE_VIDEO -> "\u25B6"; // Play symbol ▶
            case YOUTUBE_VIDEO_DIRECT -> "\uD83C\uDFAC"; // Clapper board
        };
    }

    /**
     * Updates the description label with truncated text.
     */
    private void updateDescriptionLabel() {
        String displayText = contextTitle;

        if (displayText == null || displayText.isEmpty()) {
            displayText = getDefaultTitleForType(contextType);
        }

        // Truncate if needed
        if (displayText.length() > MAX_DISPLAY_LENGTH) {
            displayText = displayText.substring(0, MAX_DISPLAY_LENGTH) + "...";
        }

        descriptionLabel.setText(displayText);

        // Tooltip with full content preview
        String tooltipText = contextTitle != null ? contextTitle : "";
        if (contextContent != null && !contextContent.isEmpty()) {
            String preview = contextContent.length() > 200
                ? contextContent.substring(0, 200) + "..."
                : contextContent;
            tooltipText = tooltipText + "\n\n" + preview;
        }
        Tooltip.install(descriptionLabel, new Tooltip(tooltipText));
    }

    /**
     * Gets default title for a context type.
     */
    private String getDefaultTitleForType(ContextType type) {
        return switch (type) {
            case YOUTUBE_VIDEO -> I18nManager.getText("youtube.dialog.title", "YouTube Video");
            case YOUTUBE_VIDEO_DIRECT -> I18nManager.getText("prompt.context.direct.title", "Video (direkt)");
        };
    }

    /**
     * Updates all text elements with localized strings.
     */
    private void updateTexts() {
        // Update delete button tooltip
        Tooltip deleteTooltip = new Tooltip(
            I18nManager.getText("prompt.context.card.delete.tooltip", "Kontext entfernen")
        );
        Tooltip.install(deleteButton, deleteTooltip);

        // Update card tooltip
        Tooltip cardTooltip = new Tooltip(
            I18nManager.getText("prompt.context.card.edit.tooltip", "Klicken zum Bearbeiten")
        );
        Tooltip.install(this, cardTooltip);
    }

    @Override
    public void onLanguageChanged() {
        Platform.runLater(() -> {
            updateTexts();
            updateDescriptionLabel();
        });
    }

    /**
     * Sets the callback for edit requests.
     *
     * @param onEditRequest callback that receives the current content
     */
    public void setOnEditRequest(Consumer<String> onEditRequest) {
        this.onEditRequest = onEditRequest;
    }

    /**
     * Sets the callback for delete requests.
     *
     * @param onDeleteRequest callback when delete is clicked
     */
    public void setOnDeleteRequest(Runnable onDeleteRequest) {
        this.onDeleteRequest = onDeleteRequest;
    }

    /**
     * Cleans up resources when this card is removed.
     */
    public void dispose() {
        I18nManager.removeChangeListener(this);
        onEditRequest = null;
        onDeleteRequest = null;
    }
}
