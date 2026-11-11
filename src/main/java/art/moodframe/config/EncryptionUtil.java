package art.moodframe.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for encrypting sensitive data using AES-GCM encryption.
 * 
 * <p>This class provides secure encryption and decryption capabilities for sensitive
 * information such as API keys. It uses AES-256 encryption in Galois/Counter Mode (GCM)
 * which provides both confidentiality and authenticity.</p>
 * 
 * <p><strong>Security Features:</strong></p>
 * <ul>
 * <li>AES-256-GCM encryption with 128-bit authentication tag</li>
 * <li>Cryptographically secure random IV generation</li>
 * <li>Base64 encoding for safe string storage</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Encrypt sensitive data
 * String apiKey = "bfl_api_key_12345";
 * String encrypted = EncryptionUtil.encrypt(apiKey);
 * 
 * // Decrypt when needed
 * String decrypted = EncryptionUtil.decrypt(encrypted);
 * }</pre>
 * 
 * <p><strong>External References:</strong></p>
 * <ul>
 * <li><a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/package-summary.html">Java Cryptography Architecture</a></li>
 * <li><a href="https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf">NIST SP 800-38D (GCM Specification)</a></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class EncryptionUtil {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    
    private static SecretKey masterKey;
    
    static {
        initializeMasterKey();
    }
    
    /**
     * Initializes the master encryption key.
     *
     * <p>Every installation has its own random 256-bit key in {@code ~/.moodframe/secret.key},
     * created on first use and readable only by the user (where the file system supports it).
     * The key never leaves this file; deleting it makes the stored API keys unreadable.</p>
     *
     * @throws RuntimeException if the master key cannot be initialized
     * @since 2.0.0
     */
    private static void initializeMasterKey() {
        try {
            Path keyFile = Path.of(System.getProperty("user.home"), ".moodframe", "secret.key");
            byte[] key;
            if (Files.exists(keyFile))
                key = Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.US_ASCII).trim());
            else {
                key = new byte[32];
                new SecureRandom().nextBytes(key);
                Files.createDirectories(keyFile.getParent());
                Files.writeString(keyFile, Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII);
                try {
                    Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
                }
                catch (UnsupportedOperationException e) {

                    /*  Windows: the file stays in the user profile, which only the user can read by default  */
                    keyFile.toFile().setReadable(false, false);
                    keyFile.toFile().setReadable(true, true);
                }
            }
            if (key.length != 32)
                throw new IllegalStateException("Invalid key file: " + keyFile);
            masterKey = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize master encryption key", e);
        }
    }
    
    /**
     * Encrypts a plaintext string using AES-256-GCM encryption.
     * 
     * <p>The encryption process:</p>
     * <ol>
     * <li>Generates a cryptographically secure random 12-byte IV</li>
     * <li>Encrypts the plaintext using AES-GCM with the master key</li>
     * <li>Combines IV and ciphertext into a single byte array</li>
     * <li>Encodes the result as Base64 for safe string storage</li>
     * </ol>
     * 
     * @param plainText the text to encrypt (null/empty returns null)
     * @return Base64-encoded encrypted text with embedded IV, or null if input is null/empty
     * @throws RuntimeException if encryption fails due to cryptographic errors
     * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html">Java Cipher API</a>
     * @since 1.0.0
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        
        try {
            byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
            
            // Generate random IV
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // Encrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);
            
            byte[] cipherText = cipher.doFinal(plainBytes);
            
            // Combine IV and encrypted text
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            
            // Kodiere als Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypts an encrypted string that was created using {@link #encrypt(String)}.
     * 
     * <p>The decryption process:</p>
     * <ol>
     * <li>Decodes the Base64-encoded input</li>
     * <li>Extracts the IV from the first 12 bytes</li>
     * <li>Extracts the ciphertext from the remaining bytes</li>
     * <li>Decrypts using AES-GCM with the extracted IV</li>
     * </ol>
     * 
     * @param encryptedText Base64-encoded encrypted text (null/empty returns null)
     * @return the decrypted plaintext, or null if input is null/empty
     * @throws RuntimeException if decryption fails (invalid format, wrong key, or tampering detected)
     * @since 1.0.0
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return null;
        }
        
        try {
            // Decode from Base64
            byte[] cipherMessage = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV and encrypted text
            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherMessage);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);
            
            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);
            
            byte[] plainBytes = cipher.doFinal(cipherText);
            
            return new String(plainBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}