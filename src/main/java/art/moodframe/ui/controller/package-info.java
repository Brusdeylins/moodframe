/**
 * The controllers of the FXML views in {@code src/main/resources/fxml}. The main window is split into parts, each
 * with its FXML file and controller, which the coordinator {@link MainWindowController} wires together; it looks up
 * the services once and hands them to the parts, which never ask the service locator themselves (only the
 * coordinators and {@link SettingsController} do), and lets go of what
 * the window registered beyond itself when it closes. The sidebar keeps its own list of the chats, which follows the
 * {@code ChatLibrary}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link MainWindowController}: the main window ({@code MainWindow.fxml}) with its header, the coordinator</li>
 * <li>{@link LibrarySidebarController}: the chats and the groups by mood or frame ({@code LibrarySidebar.fxml})</li>
 * <li>{@link ChatViewController}: the entries of the chat or group ({@code ChatView.fxml})</li>
 * <li>{@link InputBarController}: the prompt and the send button ({@code InputBar.fxml})</li>
 * <li>{@link ChatViewController} shows its full view with {@link EntryFullView}</li>
 * <li>{@link GenerationPanelController}: the group Generation ({@code GenerationPanel.fxml}), with its parts
 * {@link GenerationAttachments} and {@link ProviderParameters}</li>
 * <li>{@link StatusBarController}: the status bar ({@code StatusBar.fxml}), where the parts report through
 * {@code ui.support.StatusSink}</li>
 * <li>{@link SettingsController}: the settings dialog ({@code SettingsDialog.fxml}), coordinator of its sections in
 * {@code ui.controller.settings}</li>
 * </ul>
 *
 * <p>The groups Mood and Frame ({@code ui.panel.MoodFramePanelController}) lie in {@code ui.panel}, below the
 * brainstorm dialog, which may show them as well.</p>
 */
package art.moodframe.ui.controller;
