/**
 * Settings of the application and the service locator: the folders of the library, the properties file in
 * {@code ~/.moodframe}, the encryption of the stored API keys and the registry through which the classes find their
 * services.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link LibraryPaths}: the folders and files of the library, created once from {@code user.home}</li>
 * <li>{@link ApplicationConfig}: the settings, stored in {@code config.properties}, grouped in the sections
 * {@link UiState}, {@link GenerationDefaults}, {@link ProviderAccounts}, {@link ProxySettings} and
 * {@link McpSettings}</li>
 * <li>{@code ConfigStore}: the properties file, its immediate and delayed writing and the encrypted secrets</li>
 * <li>{@link ServiceFactory}: singleton service locator with eager instances and lazy providers</li>
 * <li>{@link EncryptionUtil}: AES-GCM encryption of the API keys with a random key per installation</li>
 * <li>{@link DetailedLogging}: turns the setting "detailed logging" into the log level of the application</li>
 * </ul>
 */
package art.moodframe.config;
