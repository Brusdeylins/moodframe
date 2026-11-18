/**
 * The controllers of the FXML views in {@code src/main/resources/fxml}. The main window is split into parts, each
 * with its FXML file and controller, which the coordinator {@link MainWindowController} wires together; it looks up
 * the services once and hands them to the parts, which never ask the service locator themselves, and lets go of what
 * the window registered beyond itself when it closes. The sidebar keeps its own list of the chats, which follows the
 * {@code ChatLibrary}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link MainWindowController}: the main window ({@code MainWindow.fxml}) with its header, the coordinator</li>
 * <li>{@link LibrarySidebarController}: the chats and the groups by mood or frame ({@code LibrarySidebar.fxml})</li>
 * <li>{@link ChatViewController}: the entries of the chat or group ({@code ChatView.fxml})</li>
 * <li>{@link InputBarController}: the prompt and the send button ({@code InputBar.fxml})</li>
 * <li>{@link MoodFramePanelController}: the groups Mood and Frame ({@code MoodFramePanel.fxml}), also for other
 * windows</li>
 * <li>{@link GenerationPanelController}: the group Generation ({@code GenerationPanel.fxml})</li>
 * <li>{@link StatusBarController}: the status bar ({@code StatusBar.fxml})</li>
 * <li>{@link SettingsController}: the settings dialog ({@code SettingsDialog.fxml}), coordinator of its sections in
 * {@code ui.controller.settings}</li>
 * </ul>
 */
package art.moodframe.ui.controller;
