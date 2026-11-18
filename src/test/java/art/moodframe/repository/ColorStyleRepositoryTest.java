package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.ColorStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The trash of the palettes: a palette goes in and comes back once, the finders answer with an Optional. */
class ColorStyleRepositoryTest {

    @TempDir
    static Path tempDir;

    private final ColorStyleRepository repository = new ColorStyleRepository(DatabaseConfig.getDatabase());

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void movesAPaletteToTheTrashAndBackOnlyOnce() throws Exception {
        ColorStyle style = new ColorStyle("Harbor Blues", List.of("#112233", "#445566"), false);
        assertTrue(repository.save(style));

        assertFalse(repository.restore(style.getId()), "a palette in use is not restored");
        assertTrue(repository.moveToTrash(style.getId()));
        assertTrue(repository.findById(style.getId()).isEmpty());
        assertTrue(repository.findByName("Harbor Blues").isEmpty());
        LocalDateTime deletedAt = repository.findInTrash(style.getId()).orElseThrow().getDeletedAt();
        assertFalse(repository.moveToTrash(style.getId()), "a palette in the trash keeps its first deletion");
        assertEquals(deletedAt, repository.findInTrash(style.getId()).orElseThrow().getDeletedAt());

        assertTrue(repository.restore(style.getId()));
        assertFalse(repository.restore(style.getId()));
        assertEquals("Harbor Blues", repository.findById(style.getId()).orElseThrow().getName());
        assertTrue(repository.findInTrash(style.getId()).isEmpty());
    }
}
