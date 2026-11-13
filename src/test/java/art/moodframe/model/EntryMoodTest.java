package art.moodframe.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The copy of the mood at a chat entry keeps the mood a changed combination started from. */
class EntryMoodTest {

    private static final Map<Mood.Field, EntryMood.Block> BLOCKS = Map.of(Mood.Field.LIGHT,
            new EntryMood.Block("std:light:golden-hour", "Golden Hour", "warm low sun", null, false, List.of(), null));

    @Test
    void writesAndReadsTheBaseOfAChangedMood() {
        EntryMood mix = new EntryMood(null, null, BLOCKS, "std:mood:golden-coast", "Golden Coast",
                Set.of(Mood.Field.LIGHT, Mood.Field.STYLE));

        EntryMood read = EntryMood.fromJson(mix.toJson());

        assertNull(read.moodId());
        assertEquals("std:mood:golden-coast", read.baseId());
        assertEquals("Golden Coast", read.baseName());
        assertEquals(Set.of(Mood.Field.LIGHT, Mood.Field.STYLE), read.changed());
        assertEquals(mix, read);
    }

    @Test
    void aChosenMoodHasNoBase() {
        EntryMood chosen = new EntryMood("std:mood:film-noir", "Film Noir", BLOCKS);

        assertFalse(chosen.toJson().contains("\"base\""));
        assertEquals(chosen, EntryMood.fromJson(chosen.toJson()));
    }

    @Test
    void readsACopyFromBeforeTheBaseAsCombinationWithoutBase() {
        EntryMood read = EntryMood.fromJson("{\"blocks\": {\"light\": {\"id\": \"std:light:golden-hour\", "
                + "\"name\": \"Golden Hour\", \"text\": \"warm low sun\"}}}");

        assertNull(read.moodId());
        assertNull(read.baseId());
        assertNull(read.baseName());
        assertTrue(read.changed().isEmpty());
        assertEquals("Golden Hour", read.blocks().get(Mood.Field.LIGHT).name());
    }
}
