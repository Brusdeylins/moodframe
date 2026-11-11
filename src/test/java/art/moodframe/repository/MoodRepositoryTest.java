package art.moodframe.repository;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.Mood;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fresh SQLite database, so migration V019 is applied together with all earlier ones. */
class MoodRepositoryTest {

    @TempDir
    static Path tempDir;

    private final MoodRepository repository = new MoodRepository();

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void storesUpdatesAndDeletesMoodsOfTheUser() {
        Mood mood = new Mood(null, "Harbor Dawn");
        mood.setReference(Mood.Field.ATMOSPHERE, "atmosphere-1");
        mood.setReference(Mood.Field.COLOR, "std:color:ocean-breeze");
        mood.setSortOrder(1000);
        assertTrue(repository.save(mood));
        assertNotNull(mood.getId());

        mood.setName("Harbor Morning");
        mood.setReference(Mood.Field.ATMOSPHERE, null);
        mood.setReference(Mood.Field.CAMERA, "std:camera:street-35mm");
        assertTrue(repository.update(mood));

        Mood stored = repository.findByName("Harbor Morning");
        assertEquals(mood.getId(), stored.getId());
        assertNull(stored.getReference(Mood.Field.ATMOSPHERE));
        assertEquals("std:color:ocean-breeze", stored.getReference(Mood.Field.COLOR));
        assertEquals("std:camera:street-35mm", stored.getReference(Mood.Field.CAMERA));
        assertFalse(stored.isDefault());
        assertNull(repository.findByName("Harbor Dawn"));

        assertTrue(repository.delete(mood.getId()));
        assertNull(repository.findById(mood.getId()));
        assertFalse(repository.exists(mood.getId()));
    }

    @Test
    void movesMoodsToTheTrashAndBack() {
        Mood kept = save("Kept Mood", 1001);
        Mood trashed = save("Trashed Mood", 1002);

        assertTrue(repository.moveToTrash(trashed.getId()));
        assertFalse(repository.moveToTrash(trashed.getId()), "a mood in the trash is not moved again");
        assertNull(repository.findById(trashed.getId()));
        assertNull(repository.findByName("Trashed Mood"));
        assertTrue(repository.exists(trashed.getId()), "the id stays taken while the mood is in the trash");
        assertTrue(repository.findAll().stream().anyMatch((Mood mood) -> mood.getId().equals(kept.getId())));

        Mood deleted = deleted(trashed.getId());
        assertEquals("Trashed Mood", deleted.getName());
        assertNotNull(deleted.getDeletedAt());

        assertTrue(repository.restore(trashed.getId()));
        assertNotNull(repository.findById(trashed.getId()));
        assertNull(deleted(trashed.getId()));
    }

    @Test
    void dropsTheReferencesToABlockInEveryMood() {
        Mood first = save("Drop First", 1003);
        first.setReference(Mood.Field.LIGHT, "light-gone");
        first.setReference(Mood.Field.STYLE, "std:style:watercolor");
        repository.update(first);
        Mood second = save("Drop Second", 1004);
        second.setReference(Mood.Field.LIGHT, "light-gone");
        repository.update(second);
        repository.moveToTrash(second.getId());

        assertEquals(2, repository.dropReference("light-gone"), "moods in the trash lose the reference, too");
        Mood stored = repository.findById(first.getId());
        assertNull(stored.getReference(Mood.Field.LIGHT));
        assertEquals("std:style:watercolor", stored.getReference(Mood.Field.STYLE));
        assertTrue(deleted(second.getId()).isEmpty());
    }

    private Mood deleted(String id) {
        return repository.findDeleted().stream().filter((Mood mood) -> mood.getId().equals(id)).findFirst()
                .orElse(null);
    }

    private Mood save(String name, int sortOrder) {
        Mood mood = new Mood(null, name);
        mood.setSortOrder(sortOrder);
        repository.save(mood);
        return mood;
    }
}
