package art.moodframe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The copy of the frame at a chat entry keeps the frame a changed setting started from. */
class EntryFrameTest {

    private final Frame landscape = new Frame("std:frame:landscape-print", "Landscape print");

    @Test
    void aChangedFrameBecomesTheBaseOfASettingOfItsOwn() {
        EntryFrame setting = EntryFrame.of(landscape, true, "16:9", "4K");

        assertNull(setting.frameId());
        assertNull(setting.frameName());
        assertEquals("std:frame:landscape-print", setting.baseId());
        assertEquals("Landscape print", setting.baseName());
        assertEquals(setting, EntryFrame.fromJson(setting.toJson()));
    }

    @Test
    void aChosenFrameAndNoFrameHaveNoBase() {
        EntryFrame chosen = EntryFrame.of(landscape, false, "3:2", "2K");
        assertEquals(new EntryFrame("std:frame:landscape-print", "Landscape print", "3:2", "2K"), chosen);
        assertEquals(chosen, EntryFrame.fromJson(chosen.toJson()));

        assertEquals(new EntryFrame(null, null, "1:1", "1K"), EntryFrame.of(null, false, "1:1", "1K"));
    }

    @Test
    void readsACopyFromBeforeTheBaseAsSettingWithoutBase() {
        assertEquals(new EntryFrame(null, null, "21:9", "2K"),
                EntryFrame.fromJson("{\"aspectRatio\": \"21:9\", \"resolution\": \"2K\"}"));
        assertEquals(new EntryFrame("std:frame:social-post", "Social Post", "4:5", "2K"),
                EntryFrame.fromJson("{\"frame\": {\"id\": \"std:frame:social-post\", \"name\": \"Social Post\"}, "
                        + "\"aspectRatio\": \"4:5\", \"resolution\": \"2K\"}"));
    }
}
