/**
 * Corporate network access.
 *
 * Builds the HTTP clients of the application with the proxy
 * and the corporate certificates of the settings.
 *
 * Components:
 * - HttpClients: factory of the HTTP clients
 * - CorporateProxyConfig: proxy and certificate settings
 * - TrustStoreManager: trust store with the corporate certificates
 */
package art.moodframe.net;
