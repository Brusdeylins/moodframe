package art.moodframe.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
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
 * EncryptionUtil encryption = new EncryptionUtil(paths.secretKey());
 *
 * // Encrypt sensitive data
 * String apiKey = "bfl_api_key_12345";
 * String encrypted = encryption.encrypt(apiKey);
 *
 * // Decrypt when needed
 * String decrypted = encryption.decrypt(encrypted);
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
class EncryptionUtil {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    
    private final Path keyFile;
    private SecretKey masterKey;

    /**
     * Creates the encryption with the key in the given file, which is read or created on first use.
     *
     * @param keyFile the key file, {@link LibraryPaths#secretKey()}
     */
    public EncryptionUtil(Path keyFile) {
        this.keyFile = keyFile;
    }

    /**
     * Gets the master encryption key, read or created on first use.
     *
     * <p>Every installation has its own random 256-bit key in {@code ~/.moodframe/secret.key},
     * created on first use and readable only by the user (where the file system supports it).
     * The key never leaves this file; deleting it makes the stored API keys unreadable.</p>
     *
     * @return the key
     * @throws RuntimeException if the master key cannot be initialized
     * @since 2.0.0
     */
    private synchronized SecretKey masterKey() {
        if (masterKey != null)
            return masterKey;
        try {
            byte[] key = Files.exists(keyFile) ? readKey() : createKey();
            if (key.length != 32)
                throw new IllegalStateException("Invalid key file: " + keyFile);
            masterKey = new SecretKeySpec(key, "AES");
            return masterKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize master encryption key", e);
        }
    }
    
    /*  A new random key; if another instance created the file meanwhile, its key is taken, so both use one key  */
    private byte[] createKey() throws IOException, InterruptedException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Files.createDirectories(keyFile.getParent());
        return writeKeyFile(Base64.getEncoder().encodeToString(key)) ? key : readKey();
    }

    /*  The key of the file; a file another instance has just created without POSIX permissions may still be empty  */
    private byte[] readKey() throws IOException, InterruptedException {
        String text = Files.readString(keyFile, StandardCharsets.US_ASCII).trim();
        for (int attempt = 0; text.isEmpty() && attempt < 20; attempt++) {
            Thread.sleep(50);
            text = Files.readString(keyFile, StandardCharsets.US_ASCII).trim();
        }
        return Base64.getDecoder().decode(text);
    }

    /**
     * Writes a new key file that nobody but the user can read at any time, unless it exists: where the file system
     * has POSIX permissions, the key goes into a temporary file created with {@code rw-------}, which is then linked
     * into place, so the key file appears at once with its content and permissions; elsewhere the file is created
     * new. Either way, of two instances starting at once only one creates the file.
     *
     * @param key the key, Base64
     * @return true if the file was written, false if it existed
     * @throws IOException if the file cannot be written
     */
    private boolean writeKeyFile(String key) throws IOException {
        Path directory = keyFile.getParent();
        if (!Files.getFileStore(directory).supportsFileAttributeView(PosixFileAttributeView.class)) {
            try {
                Files.writeString(keyFile, key, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            }
            catch (FileAlreadyExistsException e) {
                return false;
            }

            /*  Windows: the file stays in the user profile, which only the user can read by default  */
            keyFile.toFile().setReadable(false, false);
            keyFile.toFile().setReadable(true, true);
            return true;
        }

        Path temporary = Files.createTempFile(directory, keyFile.getFileName().toString(), ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(temporary, key, StandardCharsets.US_ASCII);
            Files.createLink(keyFile, temporary);
            return true;
        }
        catch (FileAlreadyExistsException e) {
            return false;
        }
        finally {
            Files.deleteIfExists(temporary);
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
    public String encrypt(String plainText) {
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
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(), parameterSpec);
            
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
    public String decrypt(String encryptedText) {
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
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), parameterSpec);
            
            byte[] plainBytes = cipher.doFinal(cipherText);
            
            return new String(plainBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}