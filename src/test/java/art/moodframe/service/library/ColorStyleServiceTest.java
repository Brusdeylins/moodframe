package art.moodframe.service.library;

import art.moodframe.database.DatabaseManager;
import art.moodframe.model.ColorStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The repository throws on a database error, the service logs it and answers as before: false, null, empty. */
class ColorStyleServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void answersADatabaseErrorWithFalseNullOrNothing() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("db").resolve("test.db"));
        database.initialize();
        ColorStyleService service = new ColorStyleService(database, new LibraryEvents());
        ColorStyle own = new ColorStyle("Own Palette", List.of("#112233"));
        assertTrue(service.addCustomStyle(own));

        try (Connection connection = database.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE color_styles");
        }

        assertEquals(20, service.getAllStyles().size(), "the standard palettes only");
        assertTrue(service.getCustomStyles().isEmpty());
        assertTrue(service.getDeletedStyles().isEmpty());
        assertNull(service.getStyleById(own.getId()));
        assertNull(service.getStyleByName("Own Palette"));
        assertFalse(service.styleExists("Own Palette"));
        assertFalse(service.addCustomStyle(new ColorStyle("Another", List.of("#445566"))));
        assertFalse(service.deleteCustomStyle("Own Palette"));
        assertFalse(service.deleteStylePermanently(own.getId()));
        assertEquals(0, service.importStyles(List.of(new ColorStyle("Imported", List.of("#778899")))));
        assertEquals(0, service.resetToDefaults());
    }
}
