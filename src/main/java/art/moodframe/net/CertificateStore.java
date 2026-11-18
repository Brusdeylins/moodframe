package art.moodframe.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * The folder of the corporate root certificates ({@code .crt}, {@code .cer}, {@code .pem}), which the HTTP clients
 * trust besides the certificates of Java.
 *
 * <p>An import or a deletion makes the HTTP clients build themselves anew ({@link HttpClients#settingsChanged()}),
 * so the next request already trusts the changed set.</p>
 *
 * @since 2.1.3
 */
public class CertificateStore {

    private final Path directory;

    /**
     * Creates the store on a folder, which need not exist yet.
     *
     * @param directory the folder, see {@code ProxySettings.getCertificatePath()}
     */
    public CertificateStore(Path directory) {
        this.directory = directory;
    }

    /**
     * Tells whether a file is a certificate by its extension.
     *
     * @param path the file
     * @return true for a regular file ending in {@code .crt}, {@code .cer} or {@code .pem}
     */
    public static boolean isCertificateFile(Path path) {
        if (path == null || !Files.isRegularFile(path))
            return false;
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".crt") || name.endsWith(".cer") || name.endsWith(".pem");
    }

    /** @return the folder */
    public Path directory() {
        return directory;
    }

    /** @return true if the folder exists */
    public boolean exists() {
        return Files.isDirectory(directory);
    }

    /**
     * Creates the folder if it does not exist.
     *
     * @throws IOException if it cannot be created
     */
    public void createDirectory() throws IOException {
        Files.createDirectories(directory);
    }

    /**
     * Lists the certificates.
     *
     * @return the file names, sorted; empty if the folder does not exist
     * @throws IOException if the folder cannot be read
     */
    public List<String> list() throws IOException {
        if (!exists())
            return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(CertificateStore::isCertificateFile).map((path) -> path.getFileName().toString())
                    .sorted().toList();
        }
    }

    /**
     * Tells whether a file of that name is in the folder.
     *
     * @param name the file name
     * @return true if it exists
     */
    public boolean contains(String name) {
        return Files.exists(directory.resolve(name));
    }

    /**
     * Copies a certificate into the folder under its own name, replacing a file of the same name; creates the folder
     * if needed.
     *
     * @param source the certificate file
     * @throws IOException if it cannot be copied
     */
    public void importFile(Path source) throws IOException {
        createDirectory();
        Files.copy(source, directory.resolve(source.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
        HttpClients.settingsChanged();
    }

    /**
     * Deletes a certificate.
     *
     * @param name the file name
     * @return false if there is no such file
     * @throws IOException if it cannot be deleted
     */
    public boolean delete(String name) throws IOException {
        boolean deleted = Files.deleteIfExists(directory.resolve(name));
        if (deleted)
            HttpClients.settingsChanged();
        return deleted;
    }
}
