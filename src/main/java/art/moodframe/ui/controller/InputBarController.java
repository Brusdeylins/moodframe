package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.ui.BoxInset;
import art.moodframe.util.I18nManager;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

/**
 * The input area below the chat ({@code InputBar.fxml}): the prompt with the count of the characters of the final
 * prompt, the send button with the price of the order and the brainstorm button. Shift+Enter sends, Enter breaks the
 * line.
 *
 * @since 2.1.3
 */
public class InputBarController {

    /** What the input area asks of the window. */
    public interface Listener {

        /** Sends the prompt. */
        void send();

        /** Opens the brainstorm popup. */
        void brainstorm();

        /** The prompt changed. */
        void promptChanged();
    }

    @FXML
    private VBox chatInputArea;
    @FXML
    private TextArea promptTextArea;
    @FXML
    private Button sendButton;
    @FXML
    private Tooltip sendTooltip;
    @FXML
    private Button brainstormButton;
    @FXML
    private Label characterCountLabel;

    private Listener listener;

    @FXML
    private void initialize() {
        /*  the prompt text behind the 1 px border, on the inset of the boxes  */
        promptTextArea.setPadding(new Insets(2.0, 2.0, 2.0, BoxInset.LEFT - 1.0 - BoxInset.TEXT));
    }

    /**
     * Connects the input area to the window.
     *
     * @param price    the price of the order, which the send button shows
     * @param listener sends, brainstorms and learns of the changes of the prompt
     */
    public void init(ObservableValue<String> price, Listener listener) {
        this.listener = listener;
        sendButton.textProperty().bind(price);
        promptTextArea.textProperty().addListener((obs, oldText, newText) -> listener.promptChanged());

        /*  Shift+Enter sends; plain Enter keeps the default of the text area, a line break  */
        promptTextArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) {
                if (!sendButton.isDisabled())
                    listener.send();
                event.consume();
            }
        });
    }

    /** The area, whose height the divider above it keeps. */
    VBox area() {
        return chatInputArea;
    }

    /** The text of the prompt as typed. */
    public String prompt() {
        return promptTextArea.getText();
    }

    /**
     * Sets the text of the prompt.
     *
     * @param prompt the text
     */
    public void setPrompt(String prompt) {
        promptTextArea.setText(prompt);
    }

    /** Clears the prompt. */
    public void clearPrompt() {
        promptTextArea.clear();
    }

    /** Puts the focus into the prompt. */
    public void focusPrompt() {
        promptTextArea.requestFocus();
    }

    /**
     * Shows the length of the final prompt against the limit of the model: warning above 90 %, danger above it.
     *
     * @param finalPrompt the final prompt (the text of the user with the building blocks)
     * @param limit       the limit of the model in characters
     */
    public void showCount(String finalPrompt, int limit) {
        int length = finalPrompt != null ? finalPrompt.length() : 0;
        characterCountLabel.setText(length + "/" + limit);
        characterCountLabel.getStyleClass().removeAll("text-warning", "text-danger", "text-muted");
        characterCountLabel.getStyleClass().add(length > limit ? "text-danger"
                : length > limit * 0.9 ? "text-warning" : "text-muted");
    }

    /**
     * Enables sending.
     *
     * @param enabled whether the prompt can be sent
     */
    public void setSendEnabled(boolean enabled) {
        sendButton.setDisable(!enabled);
    }

    /**
     * Enables the prompt and shows the hint of its empty field.
     *
     * @param enabled        whether the prompt can be typed
     * @param placeholderKey the i18n key of the hint
     */
    public void setInputEnabled(boolean enabled, String placeholderKey) {
        promptTextArea.setDisable(!enabled);
        showPlaceholder(placeholderKey);
    }

    /**
     * Shows the hint of the empty prompt.
     *
     * @param placeholderKey the i18n key of the hint
     */
    public void showPlaceholder(String placeholderKey) {
        promptTextArea.setPromptText(I18nManager.getText(placeholderKey));
    }

    /**
     * Decides on the hint of the empty prompt: a missing key of the provider of the chosen model, no chat, an attached
     * image, else the hint for the provider.
     *
     * @param hasApiKey     whether the key is there
     * @param hasChat       whether a chat takes the prompt
     * @param hasAttachment whether an image is attached
     * @param provider      the provider of the chosen model, null without
     * @return the i18n key of the hint
     */
    static String placeholder(boolean hasApiKey, boolean hasChat, boolean hasAttachment, ApiProvider provider) {
        boolean gemini = provider == ApiProvider.GOOGLE_GEMINI;
        if (!hasApiKey)
            return gemini ? "status.error.api.key.missing.gemini" : "status.error.api.key.missing.bfl";
        if (!hasChat)
            return "input.prompt.placeholder.nochat";
        if (hasAttachment)
            return "status.image.edit.attached";
        return gemini ? "input.prompt.placeholder.gemini" : "input.prompt.placeholder";
    }

    /**
     * Enables the brainstorm button only if an account (Google Gemini or OpenAI) has a language model and a web search
     * and a chat is selected (like the other input controls); a disabled button names its reason.
     *
     * @param config  the configuration, may be null
     * @param hasChat whether a chat is selected
     */
    public void updateBrainstorm(ApplicationConfig config, boolean hasChat) {
        String hint = brainstormHint(config, hasChat);
        brainstormButton.setDisable(!"brainstorm.tooltip".equals(hint));
        brainstormButton.setTooltip(new Tooltip(I18nManager.getText(hint)));
    }

    /**
     * Decides on the hint of the brainstorm button: the reason why it is disabled (no API key, no account with a
     * language model and a web search, no chat), or the normal hint if it is enabled.
     *
     * @param config          the application configuration, may be null
     * @param hasChatSelected true if a chat is selected
     * @return the i18n key of the hint, {@code brainstorm.tooltip} if the button is enabled
     */
    static String brainstormHint(ApplicationConfig config, boolean hasChatSelected) {
        if (config == null || !config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)
                && !config.accounts().hasApiKey(ApiProvider.OPENAI))
            return "brainstorm.tooltip.no.key";
        if (!config.accounts().isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI)
                && !config.accounts().isBrainstormAvailable(ApiProvider.OPENAI))
            return "brainstorm.tooltip.no.search";
        return hasChatSelected ? "brainstorm.tooltip" : "brainstorm.tooltip.no.chat";
    }

    /** Shows the texts of the input area in the current language. */
    public void updateTexts() {
        sendTooltip.setText(I18nManager.getText("input.send.tooltip"));
        brainstormButton.setText(I18nManager.getText("brainstorm.button"));
    }

    @FXML
    private void onSendClicked() {
        listener.send();
    }

    @FXML
    private void onBrainstormClicked() {
        listener.brainstorm();
    }
}
