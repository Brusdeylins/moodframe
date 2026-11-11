package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.Frame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fresh SQLite database, so migration V021 is applied together with all earlier ones. */
class FrameRepositoryTest {

    @TempDir
    static Path tempDir;

    private final FrameRepository repository = new FrameRepository();

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void storesUpdatesAndDeletesFramesOfTheUser() {
        Frame frame = frame("Instagram Story", "gemini-3.1-flash-image", "2K", "9:16", 4, 1000);
        assertTrue(repository.save(frame));
        assertNotNull(frame.getId());

        Frame stored = repository.findByName("Instagram Story");
        assertEquals(List.of("gemini-3.1-flash-image"), stored.getModels());
        assertEquals("2K", stored.getResolution());
        assertEquals("9:16", stored.getAspectRatio());
        assertEquals(4, stored.getImageCount());
        assertNull(stored.getGuidance());
        assertNull(stored.getSteps());
        assertFalse(stored.isDefault());

        frame.setName("Druck A3");
        frame.setModel("flux.2-flex");
        frame.setResolution(null);
        frame.setAspectRatio("2:3");
        frame.setImageCount(1);
        frame.setGuidance(3.5);
        frame.setSteps(28);
        assertTrue(repository.update(frame));

        stored = repository.findById(frame.getId());
        assertEquals("Druck A3", stored.getName());
        assertEquals("flux.2-flex", stored.getModel());
        assertNull(stored.getResolution());
        assertEquals("2:3", stored.getAspectRatio());
        assertEquals(1, stored.getImageCount());
        assertEquals(3.5, stored.getGuidance());
        assertEquals(28, stored.getSteps());
        assertNull(repository.findByName("Instagram Story"));

        assertTrue(repository.delete(frame.getId()));
        assertNull(repository.findById(frame.getId()));
        assertFalse(repository.exists(frame.getId()));
    }

    @Test
    void keepsAnOpenAiModelWithItsId() {
        Frame frame = frame("OpenAI Square", "openai:gpt-image-1", null, "1:1", 2, 1001);
        assertTrue(repository.save(frame));
        assertEquals("openai:gpt-image-1", repository.findById(frame.getId()).getModel());
    }

    @Test
    void movesFramesToTheTrashAndBack() {
        Frame kept = frame("Kept Frame", "flux.3-image", "1K", "1:1", 1, 1002);
        Frame trashed = frame("Trashed Frame", "flux.3-image", "2K", "16:9", 2, 1003);
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
    void doesNotStoreAFrameWithoutModel() {
        Frame frame = frame("No Model", null, null, "1:1", 1, 1004);
        assertFalse(repository.save(frame));
        assertNull(repository.findByName("No Model"));
    }

    private static Frame frame(String name, String model, String resolution, String aspectRatio, int imageCount,
            int sortOrder) {
        Frame frame = new Frame(null, name);
        frame.setModel(model);
        frame.setResolution(resolution);
        frame.setAspectRatio(aspectRatio);
        frame.setImageCount(imageCount);
        frame.setSortOrder(sortOrder);
        return frame;
    }
}
