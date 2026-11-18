package art.moodframe.config;

/**
 * The look and the remembered state of the windows: theme, language, window bounds, dividers, the selections of the
 * main window and the display options.
 *
 * <p>Values dragged with the mouse (window bounds, dividers, tile size) are written after a quiet time, all others
 * at once.</p>
 */
public class UiState {

    private static final String[] THEMES = {
            "primer-light", "primer-dark", "nord-light", "nord-dark", "cupertino-light", "cupertino-dark", "dracula"};

    private final ConfigStore store;

    UiState(ConfigStore store) {
        this.store = store;
    }

    /*  ===== Theme and language =====  */

    /** @return the AtlantaFX theme name (default: "primer-dark") */
    public String getUiTheme() {
        return store.get("ui.theme", "primer-dark");
    }

    /**
     * Sets the theme and remembers that the user chose it, so the system theme no longer decides.
     *
     * @param theme the AtlantaFX theme name
     */
    public void setUiTheme(String theme) {
        store.put("ui.theme", theme);
        store.put("ui.theme.user_set", "true");
        store.save();
    }

    /** @return true if the user has explicitly chosen a theme */
    public boolean hasUserSetTheme() {
        return store.getBoolean("ui.theme.user_set", false);
    }

    /** @return the names of all available AtlantaFX themes */
    public String[] getAvailableThemes() {
        return THEMES.clone();
    }

    /** @return the language code, "de" (default) or "en" */
    public String getUiLanguage() {
        return store.get("ui.language", "de");
    }

    /** @param language the language code, "de" or "en" */
    public void setUiLanguage(String language) {
        store.set("ui.language", language);
    }

    /*  ===== Main window =====  */

    /** @return the width of the main window (default 1200) */
    public int getWindowWidth() {
        return Integer.parseInt(store.get("ui.window.width", "1200"));
    }

    /** @return the height of the main window (default 800) */
    public int getWindowHeight() {
        return Integer.parseInt(store.get("ui.window.height", "800"));
    }

    /** @return the x position of the main window, -1 if none is remembered */
    public double getWindowX() {
        return Double.parseDouble(store.get("ui.window.x", "-1"));
    }

    /** @return the y position of the main window, -1 if none is remembered */
    public double getWindowY() {
        return Double.parseDouble(store.get("ui.window.y", "-1"));
    }

    /** @return true if the main window was maximized */
    public boolean getWindowMaximized() {
        return store.getBoolean("ui.window.maximized", false);
    }

    /**
     * Remembers the bounds of the main window; written after a quiet time.
     *
     * @param x         the x position
     * @param y         the y position
     * @param width     the width
     * @param height    the height
     * @param maximized true if maximized
     */
    public void setWindowBounds(double x, double y, double width, double height, boolean maximized) {
        store.put("ui.window.x", String.valueOf(x));
        store.put("ui.window.y", String.valueOf(y));
        store.put("ui.window.width", String.valueOf((int) width));
        store.put("ui.window.height", String.valueOf((int) height));
        store.put("ui.window.maximized", String.valueOf(maximized));
        store.saveSoon();
    }

    /** @return the position of the main divider (0.0 - 1.0), default 0.2 */
    public double getSplitterPosition() {
        return Double.parseDouble(store.get("ui.splitter_position", "0.2"));
    }

    /** @param position the position of the main divider between 0.0 and 1.0; written after a quiet time */
    public void setSplitterPosition(double position) {
        store.put("ui.splitter_position", String.valueOf(position));
        store.saveSoon();
    }

    /** @return the height of the input area below the chat in pixels, 0 if none is remembered (default height) */
    public double getChatInputHeight() {
        return Double.parseDouble(store.get("ui.chat_input_height", "0"));
    }

    /** @param height the height of the input area below the chat in pixels; written after a quiet time */
    public void setChatInputHeight(double height) {
        store.put("ui.chat_input_height", String.valueOf(height));
        /*  The former divider position as a share of the height does not count any more  */
        store.remove("ui.chat_input_splitter_position");
        store.saveSoon();
    }

    /** @return {@code chat} (default), {@code mood} or {@code frame}: what the sidebar lists */
    public String getSidebarMode() {
        /*  The former switch between chats and moods counts until a mode is remembered  */
        return store.get("sidebar.mode", store.getBoolean("sidebar.by.mood", false) ? "mood" : "chat");
    }

    /** @param mode {@code chat}, {@code mood} or {@code frame} */
    public void setSidebarMode(String mode) {
        store.put("sidebar.mode", mode);
        store.remove("sidebar.by.mood");
        store.save();
    }

    /** @return {@code chat} (default) or {@code images}: how the chat area shows a chat */
    public String getChatView() {
        return store.get("chat.view", "chat");
    }

    /** @param view {@code chat} or {@code images} */
    public void setChatView(String view) {
        store.set("chat.view", view);
    }

    /** @return the size of the image tiles in screen pixels (default 180) */
    public double getImageTileSize() {
        return Double.parseDouble(store.get("chat.image.size", "180"));
    }

    /** @param size the size of the image tiles in screen pixels; written after a quiet time */
    public void setImageTileSize(double size) {
        store.put("chat.image.size", String.valueOf(Math.round(size)));
        store.saveSoon();
    }

    /** @return {@code mood} (default), {@code frame}, {@code generation}, or empty if all groups are folded */
    public String getOpenPanelGroup() {
        return store.get("ui.panel.open", "mood");
    }

    /** @param group {@code mood}, {@code frame}, {@code generation}, or empty if all groups are folded */
    public void setOpenPanelGroup(String group) {
        store.set("ui.panel.open", group);
    }

    /*  ===== Selections of the main window =====  */

    /** @return the id of the selected mood, empty for "No mood" (default) */
    public String getSelectedMood() {
        return store.get("mood.selected", "");
    }

    /**
     * Remembers the selected mood.
     *
     * @param moodId  the id of the mood, empty for "No mood"
     * @param changed true if a field differs from the mood
     */
    public void setSelectedMood(String moodId, boolean changed) {
        store.put("mood.selected", moodId);
        store.put("mood.selected.changed", String.valueOf(changed));
        store.save();
    }

    /** @return the id of the selected frame, empty for "No frame" (default) */
    public String getSelectedFrame() {
        return store.get("frame.selected", "");
    }

    /** @param frameId the id of the frame, empty for "No frame" */
    public void setSelectedFrame(String frameId) {
        store.set("frame.selected", frameId);
    }

    /**
     * Tells whether a field of the building blocks keeps its value when a mood is chosen.
     *
     * @param field the field ("atmosphere", "style", "palette", "light" or "camera")
     * @return true if locked (default: unlocked)
     */
    public boolean isBlockLocked(String field) {
        return store.getBoolean("block.lock." + field, false);
    }

    /**
     * Locks or unlocks a field of the building blocks.
     *
     * @param field  the field ("atmosphere", "style", "palette", "light" or "camera")
     * @param locked true to lock it
     */
    public void setBlockLocked(String field, boolean locked) {
        store.set("block.lock." + field, String.valueOf(locked));
    }

    /** @return true if the building blocks of the mood go into the prompt while a reference image is attached */
    public boolean isMoodAppliedToReference() {
        return store.getBoolean("mood.apply.reference", false);
    }

    /** @param applied true to apply the mood to a reference image */
    public void setMoodAppliedToReference(boolean applied) {
        store.set("mood.apply.reference", String.valueOf(applied));
    }

    /*  ===== Display options =====  */

    /** @return true unless the user switched the background image off (default: on) */
    public boolean isBackgroundImageEnabled() {
        return store.getBoolean("ui.background.image", true);
    }

    /** @param enabled true to show the background image */
    public void setBackgroundImageEnabled(boolean enabled) {
        store.set("ui.background.image", String.valueOf(enabled));
    }

    /** @return true unless the user stopped the frames of the preview from wandering (default: on) */
    public boolean isPreviewFrameAnimated() {
        return store.getBoolean("ui.preview.frame.animated", true);
    }

    /** @param animated true to let the frames of the preview wander */
    public void setPreviewFrameAnimated(boolean animated) {
        store.set("ui.preview.frame.animated", String.valueOf(animated));
    }
}
