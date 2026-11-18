package art.moodframe.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The folder of the corporate certificates: list, import and delete. */
class CertificateStoreTest {

    @TempDir
    Path temp;

    @Test
    void aMissingFolderListsNothingAndIsCreatedByTheFirstImport() throws Exception {
        CertificateStore store = new CertificateStore(temp.resolve("certificates"));
        assertFalse(store.exists());
        assertEquals(List.of(), store.list());

        Path source = Files.writeString(temp.resolve("zscaler.pem"), "-----BEGIN CERTIFICATE-----");
        store.importFile(source);

        assertTrue(store.exists());
        assertTrue(store.contains("zscaler.pem"));
        assertEquals(List.of("zscaler.pem"), store.list());
    }

    @Test
    void listsOnlyCertificatesSortedByName() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("certificates"));
        Files.writeString(folder.resolve("b.CRT"), "");
        Files.writeString(folder.resolve("a.cer"), "");
        Files.writeString(folder.resolve("notes.txt"), "");
        Files.createDirectories(folder.resolve("c.pem"));

        assertEquals(List.of("a.cer", "b.CRT"), new CertificateStore(folder).list());
    }

    @Test
    void anImportReplacesAFileOfTheSameNameAndADeletionRemovesIt() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("certificates"));
        Files.writeString(folder.resolve("root.crt"), "old");
        CertificateStore store = new CertificateStore(folder);

        Path source = Files.writeString(Files.createDirectories(temp.resolve("new")).resolve("root.crt"), "new");
        store.importFile(source);
        assertEquals("new", Files.readString(folder.resolve("root.crt")));

        assertTrue(store.delete("root.crt"));
        assertFalse(store.contains("root.crt"));
        assertFalse(store.delete("root.crt"), "a missing file is reported");
    }
}
