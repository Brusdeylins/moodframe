/**
 * Settings of the application and the service locator: the properties file in {@code ~/.moodframe}, the encryption of
 * the stored API keys and the registry through which the classes find their services.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ApplicationConfig}: all settings, stored in {@code config.properties}</li>
 * <li>{@link ServiceFactory}: singleton service locator with eager instances and lazy providers</li>
 * <li>{@link EncryptionUtil}: AES-GCM encryption of the API keys with a random key per installation</li>
 * <li>{@link DetailedLogging}: turns the setting "detailed logging" into the log level of the application</li>
 * </ul>
 */
package art.moodframe.config;
