/**
 * The sections of the settings dialog. Each is an FXML file of its own under {@code src/main/resources/fxml/settings},
 * included into {@code SettingsDialog.fxml}, with a controller here that loads, saves, resets and relabels its own
 * values ({@link SettingsSection}). {@link art.moodframe.ui.controller.SettingsController} coordinates them and hands
 * them the services once ({@link SettingsServices}).
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link BflSettingsPane}, {@link GeminiSettingsPane}, {@link OpenAiSettingsPane}: the provider tabs with key,
 * connection test and activation</li>
 * <li>{@link ProxySettingsPane}: the corporate proxy and the certificate folder</li>
 * <li>{@link AppearanceSettingsPane}, {@link LanguageSettingsPane}: theme and language, shown at once</li>
 * <li>{@link AdvancedSettingsPane}: detailed logging, backup and import, the reset</li>
 * <li>{@link McpSettingsPane}: the MCP server, applied at once</li>
 * <li>{@link ConnectionTest}, {@link StatusLine}, {@link SettingsPrompts}: the background connection tests, the
 * status lines and the message boxes and file choosers the sections share</li>
 * </ul>
 */
package art.moodframe.ui.controller.settings;
