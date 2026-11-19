/**
 * Corporate network access: builds the HTTP clients of the application with the proxy and the corporate certificates
 * of the settings.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link HttpClients}: factory of the HTTP clients</li>
 * <li>{@link CorporateProxyConfig}: proxy and certificate settings</li>
 * <li>{@link TrustStoreManager}: trust store with the corporate certificates</li>
 * <li>{@link CertificateStore}: the folder of the corporate certificates (list, import, delete)</li>
 * <li>{@link IoExecutor}: the threads of the blocking network work</li>
 * </ul>
 */
package art.moodframe.net;
