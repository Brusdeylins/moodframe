package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.database.DatabaseManager;
import art.moodframe.model.Frame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fresh SQLite database, so migration V023 is applied together with all earlier ones. */
class FrameRepositoryTest {

    /*  The columns of the frames table once V022 dropped model, resolution, image count, guidance and steps, and
        V023 added the resolution again as target  */
    private static final List<String> COLUMNS = List.of("id", "name", "aspect_ratio", "sort_order", "deleted_at",
            "created_at", "updated_at", "resolution");

    @TempDir
    static Path tempDir;

    private final FrameRepository repository = new FrameRepository();

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void storesUpdatesAndDeletesFramesOfTheUser() {
        Frame frame = frame("Instagram Story", "9:16", 1000);
        frame.setResolution("2K");
        assertTrue(repository.save(frame));
        assertNotNull(frame.getId());

        Frame stored = repository.findByName("Instagram Story");
        assertEquals("9:16", stored.getAspectRatio());
        assertEquals("2K", stored.getResolution());
        assertEquals(1000, stored.getSortOrder());
        assertFalse(stored.isDefault());

        frame.setName("Druck A3");
        frame.setAspectRatio("2:3");
        frame.setResolution("4K");
        frame.setSortOrder(1005);
        assertTrue(repository.update(frame));

        stored = repository.findById(frame.getId());
        assertEquals("Druck A3", stored.getName());
        assertEquals("2:3", stored.getAspectRatio());
        assertEquals("4K", stored.getResolution());
        assertEquals(1005, stored.getSortOrder());

        frame.setResolution(null);
        assertTrue(repository.update(frame));
        assertNull(repository.findById(frame.getId()).getResolution(), "a frame without resolution keeps it as is");
        assertNull(repository.findByName("Instagram Story"));

        assertTrue(repository.delete(frame.getId()));
        assertNull(repository.findById(frame.getId()));
        assertFalse(repository.exists(frame.getId()));
    }

    @Test
    void movesFramesToTheTrashAndBack() {
        Frame kept = frame("Kept Frame", "1:1", 1002);
        Frame trashed = frame("Trashed Frame", "16:9", 1003);
        assertTrue(repository.save(kept));
        assertTrue(repository.save(trashed));

        assertTrue(repository.moveToTrash(trashed.getId()));
        assertFalse(repository.moveToTrash(trashed.getId()), "a frame in the trash is not moved again");
        assertNull(repository.findById(trashed.getId()));
        assertNull(repository.findByName("Trashed Frame"));
        assertTrue(repository.exists(trashed.getId()), "the id stays taken while the frame is in the trash");
        assertTrue(repository.findAll().stream().anyMatch((Frame frame) -> frame.getId().equals(kept.getId())));

        Frame deleted = repository.findDeleted().stream().filter((Frame frame) -> frame.getId().equals(trashed.getId()))
                .findFirst().orElseThrow();
        assertEquals("Trashed Frame", deleted.getName());
        assertNotNull(deleted.getDeletedAt());

        assertTrue(repository.restore(trashed.getId()));
        assertNotNull(repository.findById(trashed.getId()));
        assertTrue(repository.findDeleted().stream().noneMatch((Frame frame) -> frame.getId().equals(trashed.getId())));
    }

    @Test
    void doesNotStoreAFrameWithoutAspectRatio() {
        Frame frame = frame("No Ratio", null, 1004);
        assertFalse(repository.save(frame));
        assertNull(repository.findByName("No Ratio"));
    }

    @Test
    void aFreshDatabaseHasNoModelColumnsButATargetResolution() throws Exception {
        try (Connection conn = DatabaseConfig.getConnection()) {
            assertEquals(COLUMNS, columns(conn));
        }
    }

    @Test
    void migrationsV022AndV023DropTheModelColumnsAndKeepTheFramesWithoutResolution() throws Exception {
        Path file = tempDir.resolve("v021").resolve("v021.db");
        Files.createDirectories(file.getParent());
        String url = "jdbc:sqlite:" + file;

        /*  A database as V021 left it: all migrations up to V021 recorded, a frame with its model  */
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             InputStream v021 = FrameRepositoryTest.class.getResourceAsStream("/db/migration/V021__Add_frames.sql")) {
            stmt.execute("CREATE TABLE schema_migrations (version TEXT PRIMARY KEY, description TEXT NOT NULL, "
                    + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            for (int version = 1; version <= 21; version++)
                stmt.execute(String.format("INSERT INTO schema_migrations (version, description) VALUES ('%03d', 'x')",
                        version));
            for (String sql : new String(v021.readAllBytes(), StandardCharsets.UTF_8).replaceAll("--[^\n]*", "")
                    .split(";")) {
                if (!sql.isBlank())
                    stmt.execute(sql);
            }
            stmt.execute("INSERT INTO frames (id, name, model_id, resolution, aspect_ratio, image_count, guidance, "
                    + "steps, sort_order) VALUES ('old', 'Old Frame', 'flux.2-flex', '2K', '3:2', 4, 3.5, 28, 1000)");

            /*  The tables of the chat entries, which V024 extends, and of the chats, which V025 numbers  */
            stmt.execute("CREATE TABLE chat_entries (id TEXT PRIMARY KEY)");
            stmt.execute("CREATE TABLE chats (id TEXT PRIMARY KEY, updated_at TIMESTAMP, sort_order INTEGER)");
        }

        new DatabaseManager(file).initialize();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, aspect_ratio, resolution, sort_order FROM frames "
                     + "WHERE id = 'old'")) {
            assertEquals(COLUMNS, columns(conn));
            assertTrue(rs.next());
            assertEquals("Old Frame", rs.getString("name"));
            assertEquals("3:2", rs.getString("aspect_ratio"));
            assertNull(rs.getString("resolution"), "the resolution of the model is not taken as target");
            assertEquals(1000, rs.getInt("sort_order"));
        }
    }

    /** The column names of the frames table in their order. */
    private static List<String> columns(Connection conn) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(frames)")) {
            while (rs.next())
                columns.add(rs.getString("name"));
        }
        return columns;
    }

    private static Frame frame(String name, String aspectRatio, int sortOrder) {
        Frame frame = new Frame(null, name);
        frame.setAspectRatio(aspectRatio);
        frame.setSortOrder(sortOrder);
        return frame;
    }
}
