package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.library.PromptBlockService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fresh SQLite database, so migration V017 is applied together with all earlier ones. */
class PromptBlockRepositoryTest {

    @TempDir
    static Path tempDir;

    private final PromptBlockRepository repository = new PromptBlockRepository(DatabaseConfig.getDatabase());

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void migrationV017TakesOverTheCustomStylesButNotTheOldDefaults() throws Exception {
        execute("INSERT INTO style_presets (id, name, prompt_text, is_default, sort_order) "
                + "VALUES ('legacy-1', 'My Ink Wash', 'ink wash painting', FALSE, 1000)");
        execute("DELETE FROM schema_migrations WHERE version = '017'");
        new DatabaseManager(tempDir.resolve("db").resolve("test.db")).initialize();

        PromptBlock migrated = repository.findById("legacy-1").orElseThrow();
        assertNotNull(migrated);
        assertEquals(PromptBlock.Type.STYLE, migrated.getType());
        assertEquals("My Ink Wash", migrated.getName());
        assertEquals("ink wash painting", migrated.getText());
        assertTrue(migrated.isPhotographic(), "styles of the user count as photographic until deselected");
        assertFalse(migrated.isDefault());
        assertEquals(1000, migrated.getSortOrder());
        assertTrue(repository.findByName(PromptBlock.Type.STYLE, "Anime").isEmpty(),
                "V002 defaults are not taken over");
    }

    @Test
    void storesUpdatesAndDeletesBlocksOfTheUser() throws Exception {
        PromptBlock block = new PromptBlock(null, PromptBlock.Type.ATMOSPHERE, "Harbor Fog", "foggy harbor", null, true);
        assertTrue(repository.save(block));
        assertNotNull(block.getId());

        block.setText("cold foggy harbor at dawn");
        block.setPhotographic(false);
        block.setParams("{\"strength\": 2}");
        assertTrue(repository.update(block));

        PromptBlock stored = repository.findByName(PromptBlock.Type.ATMOSPHERE, "Harbor Fog").orElseThrow();
        assertEquals("cold foggy harbor at dawn", stored.getText());
        assertFalse(stored.isPhotographic());
        assertEquals("{\"strength\": 2}", stored.getParams());
        assertTrue(repository.findByName(PromptBlock.Type.LIGHT, "Harbor Fog").isEmpty(),
                "names are looked up per type");

        assertTrue(repository.delete(block.getId()));
        assertTrue(repository.findById(block.getId()).isEmpty());
    }

    @Test
    void skipsBlocksInTheTrash() throws Exception {
        PromptBlock kept = new PromptBlock(null, PromptBlock.Type.LIGHT, "Kept", "kept light", null, true);
        PromptBlock trashed = new PromptBlock(null, PromptBlock.Type.LIGHT, "Trashed", "trashed light", null, true);
        repository.save(kept);
        repository.save(trashed);
        execute("UPDATE prompt_blocks SET deleted_at = CURRENT_TIMESTAMP WHERE id = '" + trashed.getId() + "'");

        List<String> names = repository.findAll(PromptBlock.Type.LIGHT).stream().map(PromptBlock::getName).toList();
        assertEquals(List.of("Kept"), names);
        assertTrue(repository.findById(trashed.getId()).isEmpty());
        assertTrue(repository.findByName(PromptBlock.Type.LIGHT, "Trashed").isEmpty());
    }

    @Test
    void movesABlockToTheTrashAndBackOnlyOnce() throws Exception {
        PromptBlock block = new PromptBlock(null, PromptBlock.Type.STYLE, "Twice", "twice", null, false);
        repository.save(block);

        assertFalse(repository.restore(block.getId()), "a block in use is not restored");
        assertTrue(repository.moveToTrash(block.getId()));
        java.time.LocalDateTime deletedAt = repository.findInTrash(block.getId()).orElseThrow().getDeletedAt();
        assertFalse(repository.moveToTrash(block.getId()), "a block in the trash keeps its first deletion");
        assertEquals(deletedAt, repository.findInTrash(block.getId()).orElseThrow().getDeletedAt());
        assertTrue(repository.restore(block.getId()));
        assertFalse(repository.restore(block.getId()));
        assertTrue(repository.findById(block.getId()).isPresent());
    }

    @Test
    void storesTheSettingsOfACameraAndLoadsThemBack() throws Exception {
        CameraSettings settings = new CameraSettings(CameraSettings.Body.MEDIUM_FORMAT, CameraSettings.Lens.MM_85,
                CameraSettings.Aperture.F_4, CameraSettings.Shutter.NORMAL, CameraSettings.Iso.ISO_3200,
                CameraSettings.Perspective.DUTCH_ANGLE, CameraSettings.Shot.MEDIUM);
        PromptBlock camera = PromptBlockService.camera(null, "My Pentax", settings);
        assertTrue(repository.save(camera));

        PromptBlock stored = repository.findByName(PromptBlock.Type.CAMERA, "My Pentax").orElseThrow();
        assertEquals(PromptBlock.Type.CAMERA, stored.getType());
        assertEquals(settings, CameraSettings.fromJson(stored.getParams()));

        PromptBlock changed = PromptBlockService.camera(stored.getId(), "My Pentax", new CameraSettings(null, null,
                null, null, null, CameraSettings.Perspective.TOP_DOWN, null));
        assertTrue(repository.update(changed));
        assertEquals(CameraSettings.Perspective.TOP_DOWN,
                CameraSettings.fromJson(repository.findById(stored.getId()).orElseThrow().getParams()).perspective());
        assertEquals("a top-down view from high above, looking straight down: the main subject seen from above like "
                + "everything around it, no larger than people or things beside it, only ground and tops visible, no "
                + "horizon", repository.findById(stored.getId()).orElseThrow().getText());
    }

    private static void execute(String sql) throws Exception {
        try (Connection conn = DatabaseConfig.getDatabase().getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
