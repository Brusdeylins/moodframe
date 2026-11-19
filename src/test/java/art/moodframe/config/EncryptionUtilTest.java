package art.moodframe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The key file of an installation is created readable only by the user, from its first moment on, and is used
 * again by the next start.
 */
class EncryptionUtilTest {

    private static final Set<PosixFilePermission> USER_ONLY = PosixFilePermissions.fromString("rw-------");

    @TempDir
    Path tempDir;

    @Test
    void createsTheKeyReadableOnlyByTheUserAndUsesItAgain() throws Exception {
        assumePosix();
        Path keyFile = tempDir.resolve("library").resolve("secret.key");

        String encrypted = new EncryptionUtil(keyFile).encrypt("bfl_api_key_12345");

        assertEquals(USER_ONLY, Files.getPosixFilePermissions(keyFile));
        try (Stream<Path> files = Files.list(keyFile.getParent())) {
            assertEquals(List.of(keyFile), files.toList(), "no temporary file is left");
        }
        assertEquals("bfl_api_key_12345", new EncryptionUtil(keyFile).decrypt(encrypted));
    }

    @Test
    void theKeyFileNeverExistsWithPermissionsForOthers() throws Exception {
        assumePosix();
        ConcurrentLinkedQueue<String> exposed = new ConcurrentLinkedQueue<>();
        AtomicBoolean done = new AtomicBoolean();

        /*  watches every file of the folders while the keys are created  */
        Thread watcher = new Thread(() -> {
            while (!done.get())
                try (Stream<Path> files = Files.walk(tempDir)) {
                    files.filter(Files::isRegularFile).forEach((Path file) -> {
                        try {
                            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
                            if (!USER_ONLY.containsAll(permissions))
                                exposed.add(file.getFileName() + " " + PosixFilePermissions.toString(permissions));
                        } catch (NoSuchFileException gone) {
                            /*  a temporary file moved meanwhile  */
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (IOException | UncheckedIOException e) {
                    /*  a folder or file changed while walking, the next round looks again  */
                }
        });
        watcher.start();
        try {
            for (int i = 0; i < 200; i++)
                new EncryptionUtil(tempDir.resolve("library-" + i).resolve("secret.key")).encrypt("key");
        } finally {
            done.set(true);
            watcher.join();
        }

        assertEquals(List.of(), List.copyOf(exposed));
    }

    private void assumePosix() throws IOException {
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView(PosixFileAttributeView.class),
                "the file system has POSIX permissions");
    }
}
