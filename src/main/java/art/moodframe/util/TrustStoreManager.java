package art.moodframe.util;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages custom trust stores for corporate certificates and ZScaler
 * integration.
 * 
 * <p>
 * This class handles the creation and management of custom trust stores that
 * include
 * both default Java certificates and corporate certificates (e.g., ZScaler root
 * CA).
 * </p>
 * 
 * <p>
 * <strong>Certificate Format Support:</strong>
 * </p>
 * <ul>
 * <li>.crt files - Base64 encoded X.509 certificates</li>
 * <li>.cer files - DER encoded X.509 certificates</li>
 * <li>.pem files - Base64 encoded with headers</li>
 * </ul>
 * 
 * <p>
 * <strong>External References:</strong>
 * </p>
 * <ul>
 * <li><a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/package-summary.html">Java
 * SSL Documentation</a></li>
 * <li><a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html">KeyStore
 * Documentation</a></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class TrustStoreManager {

    private static final Logger logger = LoggerFactory.getLogger(TrustStoreManager.class);

    private KeyStore corporateKeyStore;
    private final ApplicationConfig config;
    private List<X509Certificate> loadedCertificates;
    private TrustManager[] customTrustManagers;

    /**
     * Creates a new trust store manager.
     */
    public TrustStoreManager() {
        this.loadedCertificates = new ArrayList<>();
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
    }

    /**
     * Loads certificates from the specified paths and creates a corporate trust
     * store.
     * 
     * @param certificatePaths list of paths to certificate files
     * @return KeyStore containing default and corporate certificates
     * @throws RuntimeException if trust store creation fails
     * @since 1.0.0
     */
    public KeyStore createCorporateKeyStore(List<Path> certificatePaths) {
        try {
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Creating corporate trust store with {} certificate paths", certificatePaths.size());
            }

            // Initialize empty KeyStore
            corporateKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            corporateKeyStore.load(null, null);

            // Load default Java certificates first
            loadDefaultCertificates();

            // Load corporate certificates
            List<X509Certificate> corporateCerts = loadCorporateCertificates(certificatePaths);
            addCorporateCertificates(corporateCerts);

            logger.info("Corporate trust store created successfully with {} total certificates",
                    corporateKeyStore.size());

            return corporateKeyStore;

        } catch (Exception e) {
            logger.error("Failed to create corporate trust store", e);
            throw new RuntimeException("Corporate trust store creation failed", e);
        }
    }

    /**
     * Loads default Java certificates from the system trust store.
     */
    private void loadDefaultCertificates() throws Exception {
        // Get default trust manager factory
        TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        defaultTmf.init((KeyStore) null); // Use default KeyStore

        // Extract certificates from default trust managers
        TrustManager[] defaultTrustManagers = defaultTmf.getTrustManagers();
        for (TrustManager trustManager : defaultTrustManagers) {
            if (trustManager instanceof X509TrustManager) {
                X509TrustManager x509TrustManager = (X509TrustManager) trustManager;
                X509Certificate[] acceptedIssuers = x509TrustManager.getAcceptedIssuers();

                // Add each default certificate to our corporate KeyStore
                for (int i = 0; i < acceptedIssuers.length; i++) {
                    X509Certificate cert = acceptedIssuers[i];
                    String alias = "default-cert-" + i;
                    corporateKeyStore.setCertificateEntry(alias, cert);
                }

            }
        }
    }

    /**
     * Loads corporate certificates from the specified file paths.
     */
    private List<X509Certificate> loadCorporateCertificates(List<Path> certificatePaths) {
        List<X509Certificate> certificates = new ArrayList<>();
        CertificateFactory certificateFactory;

        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (Exception e) {
            logger.error("Failed to create certificate factory", e);
            return certificates;
        }

        for (Path certPath : certificatePaths) {
            try {
                if (!Files.exists(certPath)) {
                    logger.warn("Certificate file not found: {}", certPath);
                    continue;
                }

                // Load certificate based on file extension
                X509Certificate certificate = loadCertificateFromFile(certPath, certificateFactory);
                if (certificate != null) {
                    certificates.add(certificate);

                    if (config != null && config.getLoggingEnabled()) {
                        logger.info("Loaded corporate certificate: {} (expires: {})",
                                certificate.getSubjectX500Principal().getName(), certificate.getNotAfter());
                    }
                } else {
                    logger.warn("Failed to load certificate from: {}", certPath);
                }

            } catch (Exception e) {
                logger.error("Error loading certificate from: {}", certPath, e);
            }
        }

        if (config != null && config.getLoggingEnabled()) {
            logger.info("Loaded {} corporate certificates", certificates.size());
        }
        this.loadedCertificates = new ArrayList<>(certificates);
        return certificates;
    }

    /**
     * Loads a single certificate from a file.
     */
    private X509Certificate loadCertificateFromFile(Path certPath, CertificateFactory certificateFactory) {
        try (InputStream inputStream = Files.newInputStream(certPath)) {

            // Load certificate - CertificateFactory handles both DER and PEM formats
            java.security.cert.Certificate cert = certificateFactory.generateCertificate(inputStream);

            if (cert instanceof X509Certificate) {
                X509Certificate x509Cert = (X509Certificate) cert;

                // Validate certificate
                validateCertificate(x509Cert);

                return x509Cert;
            } else {
                logger.warn("Certificate is not X.509 format: {}", certPath);
                return null;
            }

        } catch (Exception e) {
            logger.error("Failed to load certificate from: {}", certPath, e);
            return null;
        }
    }

    /**
     * Validates a loaded certificate.
     */
    private void validateCertificate(X509Certificate certificate) throws Exception {
        // Check if certificate is expired
        certificate.checkValidity();

        // Additional validation could be added here
    }

    /**
     * Adds corporate certificates to the corporate KeyStore.
     */
    private void addCorporateCertificates(List<X509Certificate> certificates) throws Exception {
        for (int i = 0; i < certificates.size(); i++) {
            X509Certificate cert = certificates.get(i);
            String alias = "corporate-cert-" + i;

            corporateKeyStore.setCertificateEntry(alias, cert);

        }

        if (config != null && config.getLoggingEnabled()) {
            logger.info("Added {} corporate certificates to trust store", certificates.size());
        }
    }

    /**
     * Creates an SSL context using the corporate trust store.
     * 
     * @return configured SSL context with corporate certificates
     * @throws RuntimeException if SSL context creation fails
     * @since 1.0.0
     */
    public SSLContext createSSLContext() {
        try {
            if (corporateKeyStore == null) {
                throw new IllegalStateException(
                        "Corporate KeyStore not initialized. Call createCorporateKeyStore() first.");
            }

            // Create trust manager factory with corporate KeyStore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(corporateKeyStore);

            // Store custom trust managers for validation
            customTrustManagers = trustManagerFactory.getTrustManagers();

            // Initialize SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, customTrustManagers, new SecureRandom());

            logger.info("SSL context created successfully with corporate trust store");
            return sslContext;

        } catch (Exception e) {
            logger.error("Failed to create SSL context with corporate certificates", e);
            throw new RuntimeException("SSL context creation failed", e);
        }
    }


    /**
     * Gets the corporate KeyStore.
     * 
     * @return the corporate KeyStore, or null if not initialized
     * @since 1.0.0
     */
    public KeyStore getCorporateKeyStore() {
        return corporateKeyStore;
    }

    /**
     * Gets the list of loaded corporate certificates.
     * 
     * @return list of loaded X509 certificates
     * @since 1.0.0
     */
    public List<X509Certificate> getLoadedCertificates() {
        return new ArrayList<>(loadedCertificates);
    }

    /**
     * Gets the number of certificates in the corporate trust store.
     * 
     * @return number of certificates, or 0 if trust store not initialized
     * @since 1.0.0
     */
    public int getCertificateCount() {
        try {
            return corporateKeyStore != null ? corporateKeyStore.size() : 0;
        } catch (Exception e) {
            logger.warn("Failed to get certificate count from trust store", e);
            return 0;
        }
    }
}