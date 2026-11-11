package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.PromptBlockService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fresh SQLite database, so migration V017 is applied together with all earlier ones. */
class PromptBlockRepositoryTest {

    @TempDir
    static Path tempDir;

    private final PromptBlockRepository repository = new PromptBlockRepository();

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

        PromptBlock migrated = repository.findById("legacy-1");
        assertNotNull(migrated);
        assertEquals(PromptBlock.Type.STYLE, migrated.getType());
        assertEquals("My Ink Wash", migrated.getName());
        assertEquals("ink wash painting", migrated.getText());
        assertTrue(migrated.isPhotographic(), "styles of the user count as photographic until deselected");
        assertFalse(migrated.isDefault());
        assertEquals(1000, migrated.getSortOrder());
        assertNull(repository.findByName(PromptBlock.Type.STYLE, "Anime"), "V002 defaults are not taken over");
    }

    @Test
    void storesUpdatesAndDeletesBlocksOfTheUser() {
        PromptBlock block = new PromptBlock(null, PromptBlock.Type.ATMOSPHERE, "Harbor Fog", "foggy harbor", null, true);
        assertTrue(repository.save(block));
        assertNotNull(block.getId());

        block.setText("cold foggy harbor at dawn");
        block.setPhotographic(false);
        block.setParams("{\"strength\": 2}");
        assertTrue(repository.update(block));

        PromptBlock stored = repository.findByName(PromptBlock.Type.ATMOSPHERE, "Harbor Fog");
        assertEquals("cold foggy harbor at dawn", stored.getText());
        assertFalse(stored.isPhotographic());
        assertEquals("{\"strength\": 2}", stored.getParams());
        assertNull(repository.findByName(PromptBlock.Type.LIGHT, "Harbor Fog"), "names are looked up per type");

        assertTrue(repository.delete(block.getId()));
        assertNull(repository.findById(block.getId()));
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
        assertNull(repository.findById(trashed.getId()));
        assertNull(repository.findByName(PromptBlock.Type.LIGHT, "Trashed"));
    }

    @Test
    void storesTheSettingsOfACameraAndLoadsThemBack() {
        CameraSettings settings = new CameraSettings(CameraSettings.Body.MEDIUM_FORMAT, "Pentax 67",
                CameraSettings.Lens.MM_85, CameraSettings.Aperture.F_4, CameraSettings.Shutter.NORMAL,
                CameraSettings.Iso.ISO_3200, CameraSettings.Perspective.DUTCH_ANGLE, CameraSettings.Shot.MEDIUM);
        PromptBlock camera = PromptBlockService.camera(null, "My Pentax", settings);
        assertTrue(repository.save(camera));

        PromptBlock stored = repository.findByName(PromptBlock.Type.CAMERA, "My Pentax");
        assertEquals(PromptBlock.Type.CAMERA, stored.getType());
        assertEquals(settings, CameraSettings.fromJson(stored.getParams()));

        PromptBlock changed = PromptBlockService.camera(stored.getId(), "My Pentax", new CameraSettings(null, null,
                null, null, null, null, CameraSettings.Perspective.TOP_DOWN, null));
        assertTrue(repository.update(changed));
        assertEquals(CameraSettings.Perspective.TOP_DOWN,
                CameraSettings.fromJson(repository.findById(stored.getId()).getParams()).perspective());
        assertEquals("top-down view from directly above", repository.findById(stored.getId()).getText());
    }

    private static void execute(String sql) throws Exception {
        try (Connection conn = DatabaseConfig.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
